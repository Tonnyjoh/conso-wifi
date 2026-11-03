package mg.flybox.usage.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.flybox.usage.AppContainer
import mg.flybox.usage.FlyboxApp
import mg.flybox.usage.data.AppSettings
import mg.flybox.usage.data.AppUsageRow
import mg.flybox.usage.data.DevicesResult
import mg.flybox.usage.data.OrangeStatus
import mg.flybox.usage.data.RouterReach
import mg.flybox.usage.data.UsagePeriod
import mg.flybox.usage.data.net.LoginOutcome
import mg.flybox.usage.data.net.RouterUnreachableException
import mg.flybox.usage.data.work.WorkScheduler
import mg.flybox.usage.domain.DashboardData
import mg.flybox.usage.domain.DeviceLists
import mg.flybox.usage.domain.DeviceTraffic
import mg.flybox.usage.domain.PinLockState
import mg.flybox.usage.domain.PinPolicy
import mg.flybox.usage.domain.Sample
import mg.flybox.usage.domain.UsageCalculator
import mg.flybox.usage.ui.components.BarDatum
import mg.flybox.usage.ui.components.RatePoint
import mg.flybox.usage.domain.Formats
import mg.flybox.usage.domain.Units

private fun nowTicker(periodMs: Long = 30_000) = flow { while (true) { emit(System.currentTimeMillis()); delay(periodMs) } }

// -------------------------------------------------------------------------------------------
// Application : onboarding, verrouillage
// -------------------------------------------------------------------------------------------

sealed interface AppPhase {
    data object Loading : AppPhase
    data object Onboarding : AppPhase
    data object Locked : AppPhase
    data object Unlocked : AppPhase
}

data class PinUi(val lockSeconds: Int = 0, val wrongAttempt: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c: AppContainer = (app as FlyboxApp).container
    private val _phase = MutableStateFlow<AppPhase>(AppPhase.Loading)
    val phase: StateFlow<AppPhase> = _phase.asStateFlow()
    private val _pin = MutableStateFlow(PinUi())
    val pinUi: StateFlow<PinUi> = _pin.asStateFlow()
    private var backgroundedAt: Long? = null
    private var countdown: Job? = null
    /** Vrai tant que l'animation d'ouverture de l'anneau n'a pas été jouée depuis le dernier déverrouillage. */
    var ringPending: Boolean = true
        private set

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            _phase.value = if (!s.hasPin) AppPhase.Onboarding else AppPhase.Locked
            refreshLockCountdown()
        }
    }

    fun ringPlayed() { ringPending = false }

    fun onBackgrounded() { backgroundedAt = System.currentTimeMillis() }

    fun onForegrounded() {
        if (_phase.value == AppPhase.Unlocked && PinPolicy.needsUnlock(backgroundedAt, System.currentTimeMillis())) {
            _phase.value = AppPhase.Locked
            refreshLockCountdown()
        }
    }

    private fun refreshLockCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            val st = c.settings.pinLockState()
            while (true) {
                val left = st.remainingLockSeconds(System.currentTimeMillis())
                _pin.update { it.copy(lockSeconds = left) }
                if (left <= 0) break
                delay(500)
            }
        }
    }

    fun createPin(pin: String, onDone: () -> Unit = {}) {
        if (!PinPolicy.isValidFormat(pin)) return
        viewModelScope.launch {
            val salt = PinPolicy.newSalt()
            c.settings.savePin(PinPolicy.hash(pin, salt), android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            c.settings.setOnboardingDone()
            ringPending = true
            _phase.value = AppPhase.Unlocked
            onDone()
        }
    }

    fun tryUnlock(pin: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val state = c.settings.pinLockState()
            if (state.isLocked(now)) { refreshLockCountdown(); return@launch }
            val creds = c.settings.pinCredentials() ?: return@launch
            val salt = android.util.Base64.decode(creds.second, android.util.Base64.NO_WRAP)
            if (PinPolicy.verify(pin, salt, creds.first)) {
                c.settings.setPinLockState(state.afterSuccess())
                _pin.value = PinUi()
                ringPending = true
                _phase.value = AppPhase.Unlocked
            } else {
                val next = state.afterFailure(now)
                c.settings.setPinLockState(next)
                _pin.value = PinUi(lockSeconds = next.remainingLockSeconds(now), wrongAttempt = true)
                if (next.isLocked(now)) refreshLockCountdown()
            }
        }
    }

    /** Empreinte reconnue : même effet qu'un PIN correct, y compris la remise à zéro des échecs. */
    fun unlockWithBiometric() {
        if (_phase.value != AppPhase.Locked) return
        viewModelScope.launch {
            c.settings.setPinLockState(PinLockState())
            countdown?.cancel()
            _pin.value = PinUi()
            ringPending = true
            _phase.value = AppPhase.Unlocked
        }
    }

    fun clearWrong() { _pin.update { it.copy(wrongAttempt = false) } }

    /** Changement de PIN : vérifie l'ancien puis enregistre le nouveau. Retourne vrai si réussi. */
    suspend fun changePin(old: String, new: String): Boolean {
        val creds = c.settings.pinCredentials() ?: return false
        val salt = android.util.Base64.decode(creds.second, android.util.Base64.NO_WRAP)
        if (!PinPolicy.verify(old, salt, creds.first) || !PinPolicy.isValidFormat(new)) return false
        val newSalt = PinPolicy.newSalt()
        c.settings.savePin(PinPolicy.hash(new, newSalt), android.util.Base64.encodeToString(newSalt, android.util.Base64.NO_WRAP))
        return true
    }
}

// -------------------------------------------------------------------------------------------
// Accueil
// -------------------------------------------------------------------------------------------

data class HomeUi(
    val loading: Boolean = true,
    val data: DashboardData? = null,
    val now: Long = System.currentTimeMillis(),
    val routerReach: RouterReach = RouterReach.UNKNOWN,
    val orangeStatus: OrangeStatus = OrangeStatus.UNKNOWN,
    val alwaysGo: Boolean = false,
    val refreshing: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as FlyboxApp).container
    private val refreshing = MutableStateFlow(false)

    val ui: StateFlow<HomeUi> = combine(
        c.usage.observe(nowTicker()),
        c.usage.routerReach,
        c.usage.orangeStatus,
        c.settings.settings,
        refreshing,
    ) { data, reach, orange, settings, busy ->
        HomeUi(false, data, System.currentTimeMillis(), reach, orange, settings.alwaysGo, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try { c.usage.refresh(withOrange = true) } finally { refreshing.value = false }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Historique
// -------------------------------------------------------------------------------------------

data class HistoryUi(
    val loading: Boolean = true,
    val thirtyDays: Boolean = false,
    val bars: List<BarDatum> = emptyList(),
    val averageBytes: Long? = null,
    val hasData: Boolean = false,
    val throughput: List<RatePoint> = emptyList(),
    val selected: Int? = null,
    val alwaysGo: Boolean = false,
    val offline: Boolean = false,
    val lastSampleTs: Long? = null,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as FlyboxApp).container
    private val thirty = MutableStateFlow(false)
    private val selected = MutableStateFlow<Int?>(null)

    val ui: StateFlow<HistoryUi> = combine(c.usage.observeSamples(), thirty, selected, c.settings.settings, c.usage.routerReach) { samples, th, sel, settings, reach ->
        build(samples, th, sel, settings, reach)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun setThirty(v: Boolean) { thirty.value = v; selected.value = null }
    fun select(i: Int?) { selected.value = i }

    private fun build(samples: List<Sample>, th: Boolean, sel: Int?, settings: AppSettings, reach: RouterReach): HistoryUi {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = if (th) 30 else 7
        val daily = UsageCalculator.dailyUsage(samples, zone)
        val series = UsageCalculator.series(daily, today, days)
        val bars = series.map { (d, v) ->
            BarDatum(
                axisLabel = if (th) Formats.dayMonth(d) else Formats.weekdayShort(d),
                bubbleLabel = Formats.bubbleDate(d).trimEnd('.') + ", " + Units.formatBytes(v, settings.alwaysGo),
                value = v,
            )
        }
        val nonZero = series.map { it.second }.filter { it > 0 }
        val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
        val rates = samples.filter { it.ts >= cutoff }.map { RatePoint(it.ts, it.downRate.toDouble(), it.upRate.toDouble()) }
        return HistoryUi(
            loading = false, thirtyDays = th, bars = bars,
            averageBytes = if (nonZero.isEmpty()) null else nonZero.average().toLong(),
            hasData = samples.size >= 2, throughput = rates, selected = sel?.takeIf { it in bars.indices },
            alwaysGo = settings.alwaysGo, offline = reach == RouterReach.UNREACHABLE, lastSampleTs = samples.lastOrNull()?.ts,
        )
    }
}

// -------------------------------------------------------------------------------------------
// Appareils
// -------------------------------------------------------------------------------------------

sealed interface DevicesUi {
    data object Loading : DevicesUi
    data object NotLoggedIn : DevicesUi
    data object Unreachable : DevicesUi
    data object AccountInUse : DevicesUi
    data class Ready(val lists: DeviceLists, val traffic: Map<String, Long>, val totalTraffic: Long, val sortByUsage: Boolean, val hasTraffic: Boolean, val ownIp: String? = null, val busyMac: String? = null, val notice: String? = null) : DevicesUi
}

class DevicesViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as FlyboxApp).container
    private val _ui = MutableStateFlow<DevicesUi>(DevicesUi.Loading)
    val ui: StateFlow<DevicesUi> = _ui.asStateFlow()
    private var sort = false

    fun load() {
        viewModelScope.launch {
            if (_ui.value !is DevicesUi.Ready) _ui.value = DevicesUi.Loading
            _ui.value = when (val r = c.routerSession.devices()) {
                DevicesResult.NotLoggedIn -> DevicesUi.NotLoggedIn
                DevicesResult.Unreachable -> (_ui.value as? DevicesUi.Ready) ?: DevicesUi.Unreachable
                DevicesResult.AccountInUse -> DevicesUi.AccountInUse
                is DevicesResult.Ready -> toReady(r.lists, r.traffic)
            }
        }
    }

    private fun toReady(lists: DeviceLists, traffic: List<DeviceTraffic>): DevicesUi.Ready {
        val byMac = traffic.mapNotNull { t -> t.mac?.let { it to t.totalBytes } }.toMap()
        val total = byMac.values.sum()
        return DevicesUi.Ready(lists, byMac, total, sort, hasTraffic = byMac.isNotEmpty() && total > 0, ownIp = ownWifiIp())
    }

    /** Adresse IPv4 de ce téléphone sur le Wi-Fi : sert à interdire le blocage de soi-même. */
    private fun ownWifiIp(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (_: java.net.SocketException) {
        null
    }

    /** Bloque ou débloque un appareil après confirmation de l'utilisateur, puis relit la liste. */
    fun setBlocked(device: mg.flybox.usage.domain.OnlineDevice?, mac: String, blocked: Boolean) {
        val ready = _ui.value as? DevicesUi.Ready ?: return
        if (ready.busyMac != null) return
        if (blocked && device != null && device.ip.isNotEmpty() && device.ip == ready.ownIp) return
        _ui.value = ready.copy(busyMac = mac, notice = null)
        viewModelScope.launch {
            val notice = when (c.routerSession.setDeviceBlocked(mac, blocked)) {
                mg.flybox.usage.data.RouterSession.BlockOutcome.Done -> null
                mg.flybox.usage.data.RouterSession.BlockOutcome.Failed -> "Le routeur a refusé la commande."
                mg.flybox.usage.data.RouterSession.BlockOutcome.NotLoggedIn -> "Connexion au routeur nécessaire."
                mg.flybox.usage.data.RouterSession.BlockOutcome.Unreachable -> "Routeur injoignable."
            }
            val r = c.routerSession.devices()
            val applied = (r as? DevicesResult.Ready)?.lists?.let { l ->
                val now = l.online.firstOrNull { it.mac == mac }?.internetEnabled ?: l.offline.firstOrNull { it.mac == mac }?.internetEnabled
                now == null || now == !blocked
            } ?: true
            val finalNotice = notice ?: if (!applied) "Le routeur a répondu mais l'état de l'appareil n'a pas changé." else null
            _ui.value = if (r is DevicesResult.Ready) toReady(r.lists, r.traffic).copy(notice = finalNotice)
            else ready.copy(busyMac = null, notice = notice ?: "Liste non actualisée.")
        }
    }

    fun toggleSort() {
        sort = !sort
        (_ui.value as? DevicesUi.Ready)?.let { _ui.value = it.copy(sortByUsage = sort) }
    }
}

// -------------------------------------------------------------------------------------------
// Applis
// -------------------------------------------------------------------------------------------

sealed interface AppsUi {
    data object Loading : AppsUi
    data object NeedsPermission : AppsUi
    data class Ready(val rows: List<AppUsageRow>, val period: UsagePeriod) : AppsUi
    data class Empty(val period: UsagePeriod) : AppsUi
}

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as FlyboxApp).container
    private val _ui = MutableStateFlow<AppsUi>(AppsUi.Loading)
    val ui: StateFlow<AppsUi> = _ui.asStateFlow()
    var period: UsagePeriod = UsagePeriod.TODAY
        private set

    fun settingsIntent() = c.appUsage.settingsIntent()

    fun load() {
        viewModelScope.launch {
            if (!c.appUsage.hasPermission()) { _ui.value = AppsUi.NeedsPermission; return@launch }
            _ui.value = AppsUi.Loading
            val rows = try { c.appUsage.load(period) } catch (_: SecurityException) { _ui.value = AppsUi.NeedsPermission; return@launch }
            _ui.value = if (rows.isEmpty()) AppsUi.Empty(period) else AppsUi.Ready(rows, period)
        }
    }

    fun setPeriod(p: UsagePeriod) { period = p; load() }
}

// -------------------------------------------------------------------------------------------
// Réglages
// -------------------------------------------------------------------------------------------

sealed interface LoginUi {
    data object Idle : LoginUi
    data object Working : LoginUi
    data class Failed(val message: String) : LoginUi
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as FlyboxApp).container
    private val login = MutableStateFlow<LoginUi>(LoginUi.Idle)
    val loginUi: StateFlow<LoginUi> = login.asStateFlow()
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setBiometricEnabled(on: Boolean) { viewModelScope.launch { c.settings.setBiometricEnabled(on) } }

    fun connect(account: String, password: String) {
        if (account.isBlank() || password.isEmpty()) { login.value = LoginUi.Failed("Saisissez l'identifiant et le mot de passe du routeur."); return }
        viewModelScope.launch {
            login.value = LoginUi.Working
            login.value = try {
                when (val o = c.routerSession.login(account, password)) {
                    LoginOutcome.Success -> LoginUi.Idle
                    is LoginOutcome.WrongPassword ->
                        LoginUi.Failed(
                            "Mot de passe incorrect." + (o.attemptsLeft?.let { " Il reste $it essai${if (it > 1) "s" else ""} avant le blocage du compte." } ?: ""),
                        )
                    LoginOutcome.AccountInUse -> LoginUi.Failed("Ce compte est déjà utilisé sur le routeur par un autre appareil. Réessayez plus tard.")
                    is LoginOutcome.Locked -> LoginUi.Failed("Compte bloqué par le routeur" + (o.seconds?.let { " pendant $it s" } ?: "") + ".")
                    is LoginOutcome.Rejected -> LoginUi.Failed("Connexion refusée par le routeur" + (o.code?.let { " (code $it)" } ?: "") + ".")
                }
            } catch (_: RouterUnreachableException) {
                LoginUi.Failed("Routeur injoignable. Connectez-vous au Wi-Fi de la Flybox.")
            } catch (_: IOException) {
                LoginUi.Failed("Routeur injoignable. Connectez-vous au Wi-Fi de la Flybox.")
            }
        }
    }

    fun dismissError() { login.value = LoginUi.Idle }

    fun disconnect() { viewModelScope.launch { c.routerSession.forget(); login.value = LoginUi.Idle } }

    fun setReminder(enabled: Boolean, hour: Int) {
        viewModelScope.launch {
            c.settings.setReminder(enabled, hour)
            if (enabled) WorkScheduler.scheduleReminder(getApplication(), hour) else WorkScheduler.cancelReminder(getApplication())
        }
    }

    fun setAlwaysGo(v: Boolean) { viewModelScope.launch { c.settings.setAlwaysGo(v) } }
}
