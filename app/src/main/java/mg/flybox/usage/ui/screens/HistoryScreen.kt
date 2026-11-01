package mg.flybox.usage.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import mg.flybox.usage.domain.AgeFormatter
import mg.flybox.usage.ui.HistoryUi
import mg.flybox.usage.ui.components.DailyBarChart
import mg.flybox.usage.ui.components.ScreenTitle
import mg.flybox.usage.ui.components.SegmentedPills
import mg.flybox.usage.ui.components.Sheet
import mg.flybox.usage.ui.components.SkeletonBar
import mg.flybox.usage.ui.components.StatusChip
import mg.flybox.usage.ui.components.ThroughputChart
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

@Composable
fun HistoryScreen(
    ui: HistoryUi,
    reducedMotion: Boolean,
    onThirty: (Boolean) -> Unit,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenTitle("Historique")
        if (ui.offline && ui.lastSampleTs != null) {
            StatusChip("Hors ligne, mis à jour ${AgeFormatter.ago(ui.lastSampleTs, System.currentTimeMillis())}")
        }
        when {
            ui.loading -> {
                SkeletonBar(Modifier.fillMaxWidth().height(48.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), reducedMotion = reducedMotion)
                SkeletonBar(Modifier.fillMaxWidth().height(240.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), reducedMotion = reducedMotion)
            }
            !ui.hasData -> GhostBars()
            else -> {
                Sheet {
                    SegmentedPills(listOf("7 jours", "30 jours"), if (ui.thirtyDays) 1 else 0, { onThirty(it == 1) }, Modifier.fillMaxWidth())
                    Text("Usage par jour", style = Type.Section, color = Palette.Ink)
                    DailyBarChart(ui.bars, ui.averageBytes, ui.selected, onSelect, thirtyDays = ui.thirtyDays, alwaysGo = ui.alwaysGo)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Débit récent", style = Type.Section, color = Palette.Ink)
                    if (ui.throughput.size >= 2) ThroughputChart(ui.throughput)
                    else Text("Pas assez de relevés sur les dernières 24 heures.", style = Type.Body, color = Palette.InkSecondary)
                }
            }
        }
    }
}

@Composable
private fun GhostBars() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
            listOf(60, 100, 80).forEach { h ->
                Canvas(Modifier.padding(0.dp).height(h.dp).fillMaxWidth(0.2f)) {
                    drawRoundRect(Palette.SurfaceMuted, size = Size(size.width, size.height), cornerRadius = CornerRadius(8.dp.toPx()))
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Text(
                "Pas encore de données. Connectez-vous au Wi-Fi de la Flybox, la collecte démarre seule.",
                style = Type.Body, color = Palette.InkSecondary,
            )
        }
    }
}
