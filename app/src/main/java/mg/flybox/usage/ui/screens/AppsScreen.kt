package mg.flybox.usage.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mg.flybox.usage.data.AppUsageRow
import mg.flybox.usage.data.UsagePeriod
import mg.flybox.usage.domain.Units
import mg.flybox.usage.ui.AppsUi
import mg.flybox.usage.ui.components.CenterMessage
import mg.flybox.usage.ui.components.HorizontalBar
import mg.flybox.usage.ui.components.PrimaryButton
import mg.flybox.usage.ui.components.ScreenTitle
import mg.flybox.usage.ui.components.SegmentedPills
import mg.flybox.usage.ui.components.SkeletonBar
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

@Composable
fun AppsScreen(
    ui: AppsUi,
    period: UsagePeriod,
    alwaysGo: Boolean,
    reducedMotion: Boolean,
    onPeriod: (UsagePeriod) -> Unit,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ui) {
        AppsUi.NeedsPermission -> CenterMessage(
            "Accès aux données d'utilisation requis",
            "Pour classer les applications de ce téléphone par données consommées, Android demande un accès spécial. Il ne sert qu'à cet écran et rien ne quitte le téléphone.",
            modifier,
        ) { PrimaryButton("Ouvrir les réglages Android", onGrant) }
        else -> Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTitle("Applis")
            SegmentedPills(UsagePeriod.entries.map { it.label }, period.ordinal, { onPeriod(UsagePeriod.entries[it]) }, Modifier.fillMaxWidth().heightIn(min = 48.dp))
            when (ui) {
                AppsUi.Loading -> repeat(6) { SkeletonBar(Modifier.fillMaxWidth().height(56.dp), reducedMotion = reducedMotion) }
                is AppsUi.Empty -> Text("Aucune donnée pour cette période.", style = Type.Body, color = Palette.InkSecondary)
                is AppsUi.Ready -> {
                    val max = ui.rows.maxOf { it.bytes }.coerceAtLeast(1)
                    val total = ui.rows.sumOf { it.bytes }.coerceAtLeast(1)
                    ui.rows.forEachIndexed { i, r -> AppRow(r, i, max, total, alwaysGo) }
                }
                AppsUi.NeedsPermission -> {}
            }
            Text("Chiffres de ce téléphone uniquement", style = Type.Caption, color = Palette.InkSecondary)
        }
    }
}

@Composable
private fun AppRow(r: AppUsageRow, index: Int, max: Long, total: Long, alwaysGo: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceMuted)) {
            r.icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.label, style = Type.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HorizontalBar(r.bytes.toFloat() / max, Palette.Brand)
        }
        Column(Modifier.width(104.dp), horizontalAlignment = Alignment.End) {
            Text(Units.formatBytes(r.bytes, alwaysGo), style = Type.RowValue, color = Palette.Ink, maxLines = 1)
            Text("${r.bytes * 100 / total} % du total", style = Type.Caption, color = Palette.InkSecondary, maxLines = 1)
        }
    }
}
