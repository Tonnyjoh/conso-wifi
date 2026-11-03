package mg.flybox.usage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mg.flybox.usage.domain.Formats
import mg.flybox.usage.domain.OfflineGroup
import mg.flybox.usage.domain.OfflineGrouping
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import mg.flybox.usage.domain.OnlineDevice
import mg.flybox.usage.domain.Units
import mg.flybox.usage.ui.DevicesUi
import mg.flybox.usage.ui.components.CenterMessage
import mg.flybox.usage.ui.components.Glyph
import mg.flybox.usage.ui.components.GlyphKind
import mg.flybox.usage.ui.components.Hairline
import mg.flybox.usage.ui.components.HorizontalBar
import mg.flybox.usage.ui.components.PrimaryButton
import mg.flybox.usage.ui.components.ScreenTitle
import mg.flybox.usage.ui.components.SkeletonBar
import mg.flybox.usage.ui.components.TextAction
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

@Composable
fun DevicesScreen(
    ui: DevicesUi,
    alwaysGo: Boolean,
    reducedMotion: Boolean,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onToggleSort: () -> Unit,
    onSetBlocked: (OnlineDevice?, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ui) {
        DevicesUi.Loading -> Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenTitle("Appareils")
            repeat(4) { SkeletonBar(Modifier.fillMaxWidth().height(56.dp), reducedMotion = reducedMotion) }
        }
        DevicesUi.NotLoggedIn -> CenterMessage(
            "Connexion au routeur nécessaire",
            "La liste des appareils demande l'identifiant et le mot de passe du routeur. Saisissez-les dans les réglages.",
            modifier,
        ) { PrimaryButton("Ouvrir les réglages", onOpenSettings) }
        DevicesUi.Unreachable -> CenterMessage(
            "Routeur injoignable",
            "Connectez-vous au Wi-Fi de la Flybox pour voir les appareils.",
            modifier,
        ) { PrimaryButton("Réessayer", onRetry) }
        DevicesUi.AccountInUse -> CenterMessage(
            "Compte déjà utilisé",
            "Un autre utilisateur est connecté au routeur avec ce compte. Réessayez plus tard.",
            modifier,
        ) { PrimaryButton("Réessayer", onRetry) }
        is DevicesUi.Ready -> DevicesList(ui, alwaysGo, onToggleSort, onRetry, onSetBlocked, modifier)
    }
}

@Composable
private fun DevicesList(ui: DevicesUi.Ready, alwaysGo: Boolean, onToggleSort: () -> Unit, onRefresh: () -> Unit, onSetBlocked: (OnlineDevice?, String, Boolean) -> Unit, modifier: Modifier) {
    var pending by remember { mutableStateOf<Triple<OnlineDevice?, String, Boolean>?>(null) }
    pending?.let { (dev, mac, block) ->
        val label = dev?.name ?: ui.lists.offline.firstOrNull { it.mac == mac }?.name ?: mac
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (block) "Bloquer $label ?" else "Débloquer $label ?") },
            text = { Text(if (block) "Cet appareil n'aura plus accès à Internet. Vous pourrez le rétablir ici." else "Cet appareil retrouvera l'accès à Internet.") },
            confirmButton = { TextButton({ pending = null; onSetBlocked(dev, mac, block) }) { Text(if (block) "Bloquer" else "Débloquer") } },
            dismissButton = { TextButton({ pending = null }) { Text("Annuler") } },
        )
    }
    val online: List<OnlineDevice> = if (ui.sortByUsage && ui.hasTraffic) {
        ui.lists.online.sortedByDescending { ui.traffic[it.mac] ?: -1L }
    } else ui.lists.online
    val maxUse = ui.traffic.values.maxOrNull() ?: 0L
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Appareils")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("En ligne (${online.size})", style = Type.Section, color = Palette.Ink)
            Spacer(Modifier.weight(1f))
            TextAction("Actualiser", onRefresh)
        }
        ui.notice?.let { Text(it, style = Type.Caption, color = Palette.InkSecondary) }
        if (ui.hasTraffic) TextAction(if (ui.sortByUsage) "Ordre du routeur" else "Trier par usage", onToggleSort)
        if (online.isEmpty()) Text("Aucun appareil en ligne pour le moment.", style = Type.Body, color = Palette.InkSecondary)
        online.forEach { d ->
            OnlineRow(
                d, ui.traffic[d.mac], ui.totalTraffic, maxUse, ui.hasTraffic, alwaysGo,
                isSelf = d.ip.isNotEmpty() && d.ip == ui.ownIp, busy = ui.busyMac != null,
                onToggle = { pending = Triple(d, d.mac, d.internetEnabled) },
            )
            Hairline()
        }
        if (!ui.hasTraffic) {
            Text("Le routeur ne fournit pas l'usage par appareil.", style = Type.Caption, color = Palette.InkSecondary)
        }
        val groups = remember(ui.lists) { OfflineGrouping.group(ui.lists.offline, ui.lists.online) }
        if (groups.isNotEmpty()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            val shown = if (expanded) groups else groups.take(OFFLINE_FOLDED)
            Spacer(Modifier.height(12.dp))
            Text("Hors ligne", style = Type.Section, color = Palette.InkSecondary)
            shown.forEach { g -> OfflineRow(g, busy = ui.busyMac != null, onUnblock = { pending = Triple(null, g.representative.mac, false) }); Hairline() }
            if (groups.size > OFFLINE_FOLDED) {
                TextAction(if (expanded) "Réduire" else "Voir tout (${groups.size})", { expanded = !expanded })
            }
        }
    }
}

@Composable
private fun OnlineRow(d: OnlineDevice, bytes: Long?, total: Long, maxUse: Long, hasTraffic: Boolean, alwaysGo: Boolean, isSelf: Boolean, busy: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph(GlyphKind.Device, Palette.Ink, 24)
            Column(Modifier.weight(1f)) {
                Text(d.name, style = Type.RowValue, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${d.ip}  ${d.mac}", style = Type.Caption, color = Palette.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasTraffic && bytes != null) Text(Units.formatBytes(bytes, alwaysGo), style = Type.RowValue, color = Palette.Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.size(10.dp).background(Palette.Green, CircleShape)) {}
            Text("En ligne", style = Type.Caption, color = Palette.Green)
            d.connectedSeconds?.let { Text("connecté depuis ${Formats.duration(it)}", style = Type.Caption, color = Palette.InkSecondary) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isSelf) "Internet (ce téléphone)" else "Internet",
                style = Type.Caption, color = Palette.InkSecondary, modifier = Modifier.weight(1f),
            )
            InternetSwitch(d.internetEnabled, enabled = !isSelf && !busy, onToggle)
        }
        if (hasTraffic && bytes != null && maxUse > 0) {
            HorizontalBar(bytes.toFloat() / maxUse, Palette.Brand)
            if (total > 0) Text("${(bytes * 100 / total)} % du total", style = Type.Caption, color = Palette.InkSecondary)
        }
    }
}

@Composable
private fun OfflineRow(g: OfflineGroup, busy: Boolean, onUnblock: () -> Unit) {
    val d = g.representative
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph(GlyphKind.Device, Palette.InkSecondary, 24)
        Column(Modifier.weight(1f)) {
            Text(g.name, style = Type.RowValue, color = Palette.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (g.addressCount > 1) "${g.addressCount} adresses" else d.mac, style = Type.Caption, color = Palette.InkSecondary, maxLines = 1)
        }
        if (!d.internetEnabled) InternetSwitch(false, enabled = !busy, onUnblock)
    }
}

/** Interrupteur Internet : actif = accès autorisé, inactif = appareil bloqué. Un appui demande confirmation. */
@Composable
private fun InternetSwitch(allowed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Switch(
        checked = allowed, onCheckedChange = { onToggle() }, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Palette.OnBrand, checkedTrackColor = Palette.Brand, checkedBorderColor = Palette.Brand,
            uncheckedThumbColor = Palette.InkSecondary, uncheckedTrackColor = Palette.SurfaceMuted, uncheckedBorderColor = Palette.InkSecondary,
        ),
    )
}

private const val OFFLINE_FOLDED = 5
