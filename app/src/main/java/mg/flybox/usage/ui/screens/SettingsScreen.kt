package mg.flybox.usage.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import mg.flybox.usage.BuildConfig
import mg.flybox.usage.data.AppSettings
import mg.flybox.usage.domain.PinPolicy
import mg.flybox.usage.ui.LoginUi
import mg.flybox.usage.ui.components.Hairline
import mg.flybox.usage.ui.components.PrimaryButton
import mg.flybox.usage.ui.components.ScreenTitle
import mg.flybox.usage.ui.components.StatusChip
import mg.flybox.usage.ui.components.TextAction
import mg.flybox.usage.ui.components.flyClickable
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

@Composable
fun SettingsScreen(
    settings: AppSettings,
    login: LoginUi,
    onConnect: (String, String) -> Unit,
    onDismissError: () -> Unit,
    onDisconnect: () -> Unit,
    onReminder: (Boolean, Int) -> Unit,
    onAlwaysGo: (Boolean) -> Unit,
    onChangePin: suspend (String, String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var account by rememberSaveable(settings.routerAccount) { mutableStateOf(settings.routerAccount.ifBlank { "admin" }) }
    var password by remember { mutableStateOf("") }
    var showPinDialog by rememberSaveable { mutableStateOf(false) }
    val connected = settings.routerLoggedIn && !settings.routerCredentialsRejected && settings.routerAccount.isNotBlank()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onReminder(true, settings.reminderHour)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenTitle("Réglages")

        // Routeur
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Routeur", style = Type.Section, color = Palette.Ink)
            if (connected) {
                StatusChip("Connecté, compte ${settings.routerAccount}", background = Palette.GreenSoft, content = Palette.Green)
                TextAction("Oublier la connexion", onDisconnect)
            } else {
                if (settings.routerCredentialsRejected) {
                    Text("Les identifiants enregistrés ont été refusés. Saisissez-les à nouveau.", style = Type.Label, color = Palette.Red)
                }
                val failed = login as? LoginUi.Failed
                Field("Identifiant", account, { account = it; onDismissError() }, false, KeyboardType.Text, null)
                Field("Mot de passe du routeur", password, { password = it; onDismissError() }, true, KeyboardType.Password, failed?.message)
                PrimaryButton(
                    if (login == LoginUi.Working) "Connexion en cours" else "Se connecter",
                    onClick = { onConnect(account, password) },
                    enabled = login != LoginUi.Working,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Le mot de passe est chiffré dans le coffre Android et n'est jamais affiché ni journalisé.",
                    style = Type.Caption, color = Palette.InkSecondary,
                )
            }
        }
        Hairline()

        // Rappel
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rappel quotidien", style = Type.Section, color = Palette.Ink)
            SwitchRow("Notification chaque jour", settings.reminderEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else onReminder(on, settings.reminderHour)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Heure du rappel", style = Type.Body, color = Palette.Ink, modifier = Modifier.weight(1f))
                Stepper(
                    "%02d:00".format(settings.reminderHour),
                    onMinus = { onReminder(settings.reminderEnabled, (settings.reminderHour + 23) % 24) },
                    onPlus = { onReminder(settings.reminderEnabled, (settings.reminderHour + 1) % 24) },
                )
            }
        }
        Hairline()

        // Code
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Code de verrouillage", style = Type.Section, color = Palette.Ink)
            Text("Le code est demandé à l'ouverture et après plus d'une minute en arrière-plan.", style = Type.Body, color = Palette.InkSecondary)
            TextAction("Changer le code", { showPinDialog = true })
        }
        Hairline()

        // Unités
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Unités", style = Type.Section, color = Palette.Ink)
            SwitchRow("Toujours afficher en Go", settings.alwaysGo, onAlwaysGo)
            Text("1 Go = 1 000 000 000 octets. Par défaut, les montants sous 1 Go sont en Mo.", style = Type.Caption, color = Palette.InkSecondary)
        }
        Hairline()

        // À propos
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("À propos", style = Type.Section, color = Palette.Ink)
            Text("Conso Flybox ${BuildConfig.VERSION_NAME}", style = Type.Body, color = Palette.Ink)
            Text(
                "Application indépendante, non affiliée à l'opérateur. Aucune donnée ne quitte le téléphone : le routeur et la page de consommation sont lus directement.",
                style = Type.Body, color = Palette.InkSecondary,
            )
            Text("Police Montserrat, licence SIL Open Font 1.1.", style = Type.Caption, color = Palette.InkSecondary)
        }
        Spacer(Modifier.size(8.dp))
    }

    if (showPinDialog) PinChangeDialog(onChangePin) { showPinDialog = false }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, secret: Boolean, type: KeyboardType, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Type.Label, color = Palette.Ink)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            isError = error != null,
            textStyle = Type.Body,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Ink,
                unfocusedBorderColor = Palette.Hairline,
                errorBorderColor = Palette.Red,
                                cursorColor = Palette.Ink,
                focusedTextColor = Palette.Ink,
                unfocusedTextColor = Palette.Ink,
                focusedContainerColor = Palette.Surface,
                unfocusedContainerColor = Palette.Surface,
                errorContainerColor = Palette.Surface,
            ),
        )
        if (error != null) Text(error, style = Type.Label, color = Palette.Red)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.Body, color = Palette.Ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Ink, checkedTrackColor = Palette.Brand, checkedBorderColor = Palette.Brand,
                uncheckedThumbColor = Palette.InkSecondary, uncheckedTrackColor = Palette.SurfaceMuted, uncheckedBorderColor = Palette.InkSecondary,
            ),
        )
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StepButton("-", "Heure précédente", onMinus)
        Text(value, style = Type.RowValue, color = Palette.Ink, modifier = Modifier.padding(horizontal = 8.dp))
        StepButton("+", "Heure suivante", onPlus)
    }
}

@Composable
private fun StepButton(sign: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).background(Palette.OrangeSoft, androidx.compose.foundation.shape.CircleShape)
            .flyClickable(onClick, shape = androidx.compose.foundation.shape.CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(sign, style = Type.Section, color = Palette.Ink) }
}

@Composable
private fun PinChangeDialog(onChangePin: suspend (String, String) -> Boolean, onClose: () -> Unit) {
    var step by remember { mutableStateOf(0) } // 0 ancien, 1 nouveau, 2 confirmation
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.background(Palette.Background, RoundedCornerShape(24.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                when (step) { 0 -> "Code actuel"; 1 -> "Nouveau code"; else -> "Confirmez le nouveau code" },
                style = Type.Section, color = Palette.Ink,
            )
            PinDots(pin.length, error = error != null)
            Box(Modifier.heightIn(min = 20.dp)) { error?.let { Text(it, style = Type.Label, color = Palette.Red) } }
            PinPad(
                entered = pin.length, enabled = true,
                onDigit = { if (pin.length < PinPolicy.MAX_LENGTH) { pin += it; error = null } },
                onDelete = { pin = pin.dropLast(1) },
                onSubmit = {
                    when (step) {
                        0 -> { old = pin; pin = ""; step = 1 }
                        1 -> if (PinPolicy.isValidFormat(pin)) { new = pin; pin = ""; step = 2 }
                        else -> if (pin != new) { error = "Les codes sont différents."; pin = ""; step = 1 } else {
                            scope.launch {
                                if (onChangePin(old, new)) onClose() else { error = "Code actuel incorrect."; pin = ""; step = 0 }
                            }
                        }
                    }
                },
            )
            TextAction("Annuler", onClose)
        }
    }
}
