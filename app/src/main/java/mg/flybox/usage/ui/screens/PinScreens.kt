package mg.flybox.usage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mg.flybox.usage.domain.PinPolicy
import mg.flybox.usage.ui.PinUi
import mg.flybox.usage.ui.components.Glyph
import mg.flybox.usage.ui.components.GlyphKind
import mg.flybox.usage.ui.components.flyClickable
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

/** Pavé numérique : touches rondes de 72 dp, grisé pendant un verrouillage. */
@Composable
fun PinPad(
    entered: Int,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { ch -> Key(ch.toString(), enabled) { onDigit(ch) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Key("Effacer", enabled && entered > 0, small = true, description = "Effacer le dernier chiffre", onClick = onDelete)
            Key("0", enabled) { onDigit('0') }
            Key("OK", enabled && entered >= PinPolicy.MIN_LENGTH && onSubmit != null, small = true, description = "Valider") { onSubmit?.invoke() }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, small: Boolean = false, description: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .background(if (enabled) Palette.Surface else Palette.SurfaceMuted, CircleShape)
            .border(1.dp, Palette.InkSecondary, CircleShape)
            .flyClickable(onClick, role = Role.Button, shape = CircleShape, enabled = enabled)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (small) Type.Label else Type.Section.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = if (enabled) Palette.Ink else Palette.InkSecondary,
        )
    }
}

@Composable
fun PinDots(count: Int, maxLen: Int = PinPolicy.MAX_LENGTH, error: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "$count chiffres saisis" }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxLen) { i ->
            Box(
                Modifier.size(16.dp).background(
                    when {
                        error -> Palette.Red
                        i < count -> Palette.Brand
                        else -> Palette.AccentSoft
                    },
                    CircleShape,
                ).border(1.5.dp, Palette.InkSecondary, CircleShape),
            )
        }
    }
}

/** Écran de déverrouillage (AC-2). */
@Composable
fun LockScreen(
    ui: PinUi,
    onSubmit: (String) -> Unit,
    onTyping: () -> Unit,
    modifier: Modifier = Modifier,
    /** Non nul si l'empreinte est activée et disponible : proposée d'emblée, le PIN reste le repli. */
    onBiometric: (() -> Unit)? = null,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    val locked = ui.lockSeconds > 0
    LaunchedEffect(ui.wrongAttempt, locked) { if (ui.wrongAttempt || locked) pin = "" }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(onBiometric != null) {
        if (onBiometric != null && !autoPrompted) { autoPrompted = true; onBiometric() }
    }
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Glyph(GlyphKind.Lock, Palette.Ink, 32)
        Text("Saisissez votre code", style = Type.Title, color = Palette.Ink, textAlign = TextAlign.Center)
        PinDots(pin.length, error = ui.wrongAttempt && !locked)
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            when {
                locked -> Text("Réessayez dans ${ui.lockSeconds} s", style = Type.RowValue, color = Palette.Red, textAlign = TextAlign.Center)
                ui.wrongAttempt -> Text("Code incorrect.", style = Type.Label, color = Palette.Red)
                else -> Spacer(Modifier)
            }
        }
        PinPad(
            entered = pin.length, enabled = !locked,
            onDigit = { if (pin.length < PinPolicy.MAX_LENGTH) { pin += it; onTyping() } },
            onDelete = { pin = pin.dropLast(1) },
            onSubmit = { onSubmit(pin) },
        )
        if (onBiometric != null) {
            mg.flybox.usage.ui.components.TextAction("Utiliser l'empreinte", onBiometric)
        }
    }
}

/** Création du code à l'ouverture (AC-1) : saisie, confirmation, puis accès à l'application. */
@Composable
fun OnboardingScreen(onCreate: (String) -> Unit, modifier: Modifier = Modifier) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    var mismatch by rememberSaveable { mutableStateOf(false) }
    val confirming = first != null
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
    val minH = maxHeight
    Column(
        Modifier.fillMaxWidth().heightIn(min = minH).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Conso Flybox", style = Type.Title, color = Palette.Ink)
        Text(
            if (confirming) "Confirmez votre code" else "Créez un code de 4 à 6 chiffres",
            style = Type.Section, color = Palette.Ink, textAlign = TextAlign.Center,
        )
        Text(
            if (confirming) "Saisissez le même code une seconde fois." else "Ce code protège l'application sur ce téléphone. Il est distinct du mot de passe du routeur.",
            style = Type.Body, color = Palette.InkSecondary, textAlign = TextAlign.Center,
        )
        PinDots(pin.length, error = mismatch)
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            if (mismatch) Text("Les deux codes sont différents. Recommencez.", style = Type.Label, color = Palette.Red, textAlign = TextAlign.Center)
        }
        PinPad(
            entered = pin.length, enabled = true,
            onDigit = { if (pin.length < PinPolicy.MAX_LENGTH) { pin += it; mismatch = false } },
            onDelete = { pin = pin.dropLast(1) },
            onSubmit = {
                val f = first
                if (f == null) {
                    if (PinPolicy.isValidFormat(pin)) { first = pin; pin = "" }
                } else if (f == pin) {
                    onCreate(pin)
                } else {
                    mismatch = true; first = null; pin = ""
                }
            },
        )
    }
    }
}
