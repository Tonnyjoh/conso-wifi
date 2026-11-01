package mg.flybox.usage.data

import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import mg.flybox.usage.data.db.AppDatabase
import mg.flybox.usage.data.db.OrangeEntity
import mg.flybox.usage.data.db.SampleEntity
import mg.flybox.usage.data.net.OrangeClient
import mg.flybox.usage.data.net.RouterClient
import mg.flybox.usage.domain.DashboardCalculator
import mg.flybox.usage.domain.DashboardData
import mg.flybox.usage.domain.OrangeSnapshot
import mg.flybox.usage.domain.RouterStatus
import mg.flybox.usage.domain.Sample
import org.json.JSONException

/** Résultat de la dernière lecture de la page Orange. */
enum class OrangeStatus { UNKNOWN, OK, PLAN_NOT_FOUND, UNREACHABLE }

/** Joignabilité du routeur lors de la dernière tentative. */
enum class RouterReach { UNKNOWN, REACHABLE, UNREACHABLE }

data class RefreshResult(val router: RouterReach, val orange: OrangeStatus)

/**
 * Collecte et lecture de l'historique (BR-1). N'écrit jamais de valeur inventée :
 * un échec de lecture ne produit aucun relevé.
 */
class UsageRepository(
    private val db: AppDatabase,
    private val router: RouterClient,
    private val orange: OrangeClient,
    private val settings: SettingsStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val _routerReach = MutableStateFlow(RouterReach.UNKNOWN)
    val routerReach: StateFlow<RouterReach> = _routerReach.asStateFlow()

    private val _orangeStatus = MutableStateFlow(OrangeStatus.UNKNOWN)
    val orangeStatus: StateFlow<OrangeStatus> = _orangeStatus.asStateFlow()

    /** Dernier relevé du routeur, ou null (hors ligne, champs vides). Enregistre le relevé. */
    suspend fun collectRouter(): RouterStatus? {
        val status = try {
            router.systemStatus()
        } catch (_: IOException) {
            _routerReach.value = RouterReach.UNREACHABLE
            return null
        } catch (_: JSONException) {
            _routerReach.value = RouterReach.UNREACHABLE
            return null
        }
        _routerReach.value = RouterReach.REACHABLE
        if (!status.hasCounters) return status
        db.samples().insert(
            SampleEntity.from(
                Sample(
                    ts = clock(),
                    down = status.downBytes!!,
                    up = status.upBytes!!,
                    downRate = status.downRate ?: 0,
                    upRate = status.upRate ?: 0,
                    onlineTime = status.onlineTime ?: 0,
                    network = status.networkType,
                    rsrp = status.rsrp,
                    rssi = status.rssi,
                    sinr = status.sinr,
                ),
            ),
        )
        return status
    }

    suspend fun collectOrange(): OrangeStatus {
        val plan = try {
            orange.fetch()
        } catch (_: IOException) {
            _orangeStatus.value = OrangeStatus.UNREACHABLE
            return OrangeStatus.UNREACHABLE
        }
        if (plan == null) {
            _orangeStatus.value = OrangeStatus.PLAN_NOT_FOUND
            return OrangeStatus.PLAN_NOT_FOUND
        }
        val now = clock()
        db.orange().insert(
            OrangeEntity.from(OrangeSnapshot(now, plan.remainingGo, plan.totalGo, plan.expires, plan.plan, plan.balanceAr)),
        )
        settings.setLastOrangeOk(now)
        _orangeStatus.value = OrangeStatus.OK
        return OrangeStatus.OK
    }

    /** Lecture du routeur et, si [withOrange] ou si le dernier succès est ancien, de la page Orange. */
    suspend fun refresh(withOrange: Boolean = true): RefreshResult {
        collectRouter()
        val lastOk = settings.current().lastOrangeOk
        val status = if (withOrange || clock() - lastOk >= ORANGE_MIN_INTERVAL_MILLIS) collectOrange() else _orangeStatus.value
        purge()
        return RefreshResult(_routerReach.value, status)
    }

    private suspend fun purge() {
        val cutoff = clock() - RETENTION_DAYS * 86_400_000L
        db.samples().deleteBefore(cutoff)
        db.orange().deleteBefore(cutoff)
    }

    suspend fun snapshot(): DashboardData {
        val now = clock()
        val z = zone()
        val samples = db.samples().since(DashboardCalculator.sampleWindowStart(now, z)).map { it.toDomain() }
        val orange = db.orange().since(DashboardCalculator.orangeWindowStart(now)).map { it.toDomain() }
        return DashboardCalculator.build(samples, orange, now, z)
    }

    /** Flux du tableau de bord ; [tick] fait avancer l'heure de référence. */
    fun observe(tick: Flow<Long>): Flow<DashboardData> {
        val start = DashboardCalculator.sampleWindowStart(clock(), zone())
        val ostart = DashboardCalculator.orangeWindowStart(clock())
        return combine(db.samples().observeSince(start), db.orange().observeSince(ostart), tick) { s, o, now ->
            DashboardCalculator.build(s.map { it.toDomain() }, o.map { it.toDomain() }, now, zone())
        }
    }

    /** Relevés des [days] derniers jours pour l'historique et la courbe de débit. */
    fun observeSamples(): Flow<List<Sample>> =
        db.samples().observeSince(DashboardCalculator.sampleWindowStart(clock(), zone())).map { rows -> rows.map { it.toDomain() } }

    companion object {
        const val ORANGE_MIN_INTERVAL_MILLIS = 30L * 60 * 1000
        const val RETENTION_DAYS = 120L
    }
}
