package mg.flybox.usage

import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import mg.flybox.usage.data.UsagePeriod
import mg.flybox.usage.ui.AppPhase
import mg.flybox.usage.ui.AppsViewModel
import mg.flybox.usage.ui.DevicesViewModel
import mg.flybox.usage.ui.HistoryViewModel
import mg.flybox.usage.ui.HomeViewModel
import mg.flybox.usage.ui.MainViewModel
import mg.flybox.usage.ui.SettingsViewModel
import mg.flybox.usage.ui.components.Glyph
import mg.flybox.usage.ui.components.GlyphKind
import mg.flybox.usage.ui.components.Hairline
import mg.flybox.usage.ui.components.flyClickable
import mg.flybox.usage.ui.Biometrics
import mg.flybox.usage.ui.screens.AppsScreen
import mg.flybox.usage.ui.screens.DevicesScreen
import mg.flybox.usage.ui.screens.HistoryScreen
import mg.flybox.usage.ui.screens.HomeScreen
import mg.flybox.usage.ui.screens.LockScreen
import mg.flybox.usage.ui.screens.OnboardingScreen
import mg.flybox.usage.ui.screens.SettingsScreen
import mg.flybox.usage.ui.theme.FlyboxTheme
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            // Thème clair imposé, indépendant du système (AC-14).
            FlyboxTheme { Root() }
        }
    }
}

private enum class Tab(val label: String, val glyph: GlyphKind) {
    Home("Accueil", GlyphKind.Home),
    History("Historique", GlyphKind.History),
    Devices("Appareils", GlyphKind.Devices),
    Apps("Applis", GlyphKind.Apps),
    Settings("Réglages", GlyphKind.Settings),
}

@Composable
private fun Root() {
    val main: MainViewModel = viewModel()
    val phase by main.phase.collectAsStateWithLifecycle()
    val pin by main.pinUi.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> main.onBackgrounded()
                Lifecycle.Event.ON_START -> main.onForegrounded()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Box(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().navigationBarsPadding()) {
        when (phase) {
            AppPhase.Loading -> {}
            AppPhase.Onboarding -> OnboardingScreen(onCreate = { main.createPin(it) })
            AppPhase.Locked -> {
                val ctx = LocalContext.current
                val settingsVm: SettingsViewModel = viewModel()
                val st by settingsVm.settings.collectAsStateWithLifecycle()
                val canBio = st.biometricEnabled && remember { Biometrics.isAvailable(ctx) }
                LockScreen(
                    pin, onSubmit = main::tryUnlock, onTyping = main::clearWrong,
                    onBiometric = if (canBio) {
                        { (ctx as? FragmentActivity)?.let { Biometrics.prompt(it, "Déverrouiller Conso Flybox", onSuccess = main::unlockWithBiometric) } }
                    } else null,
                )
            }
            AppPhase.Unlocked -> MainScaffold(main)
        }
    }
}

@Composable
private fun MainScaffold(main: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val reduced = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (Tab.entries[tab]) {
                Tab.Home -> {
                    val vm: HomeViewModel = viewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { vm.refresh() }
                    HomeScreen(ui, playRingAnimation = main.ringPending, reducedMotion = reduced, onRingPlayed = main::ringPlayed, onRefresh = vm::refresh)
                }
                Tab.History -> {
                    val vm: HistoryViewModel = viewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    HistoryScreen(ui, reduced, vm::setThirty, vm::select)
                }
                Tab.Devices -> {
                    val vm: DevicesViewModel = viewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { vm.load() }
                    DevicesScreen(ui, settings.alwaysGo, reduced, onOpenSettings = { tab = Tab.Settings.ordinal }, onRetry = vm::load, onToggleSort = vm::toggleSort, onSetBlocked = vm::setBlocked)
                }
                Tab.Apps -> {
                    val vm: AppsViewModel = viewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    // Le droit est accordé dans un autre écran : on relit à chaque retour.
                    DisposableEffect(owner) {
                        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.load() }
                        owner.lifecycle.addObserver(obs)
                        onDispose { owner.lifecycle.removeObserver(obs) }
                    }
                    AppsScreen(
                        ui, vm.period, settings.alwaysGo, reduced,
                        onPeriod = vm::setPeriod,
                        onGrant = { ctx.startActivity(vm.settingsIntent()) },
                    )
                }
                Tab.Settings -> {
                    val login by settingsVm.loginUi.collectAsStateWithLifecycle()
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    SettingsScreen(
                        settings, login,
                        onConnect = settingsVm::connect,
                        onDismissError = settingsVm::dismissError,
                        onDisconnect = settingsVm::disconnect,
                        onReminder = settingsVm::setReminder,
                        onAlwaysGo = settingsVm::setAlwaysGo,
                        onChangePin = { o, n -> main.changePin(o, n) },
                        biometricAvailable = remember { Biometrics.isAvailable(ctx) },
                        onBiometric = { on ->
                            if (!on) settingsVm.setBiometricEnabled(false)
                            else (ctx as? FragmentActivity)?.let {
                                Biometrics.prompt(it, "Activer l'empreinte", onSuccess = { settingsVm.setBiometricEnabled(true) })
                            }
                        },
                    )
                }
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    // Libellés d'onglets : l'échelle de police est plafonnée à 1,0 pour que cinq libellés tiennent sans troncature.
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale = minOf(d.fontScale, 1f)),
    ) { BottomBarContent(selected, onSelect) }
}

@Composable
private fun BottomBarContent(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Surface)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Tab.entries.forEachIndexed { i, t ->
                val on = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .flyClickable({ onSelect(i) }, role = Role.Tab, shape = RoundedCornerShape(16.dp))
                        .semantics { this.selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier.background(if (on) Palette.AccentSoft else Color.Transparent, RoundedCornerShape(50)).then(if (on) Modifier.border(1.5.dp, Palette.Brand, RoundedCornerShape(50)) else Modifier).padding(horizontal = 14.dp, vertical = 4.dp),
                    ) { Glyph(t.glyph, Palette.Ink, 24) }
                    Text(
                        t.label,
                        style = Type.Label.copy(letterSpacing = (-0.2).sp),
                        color = Palette.Ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}
