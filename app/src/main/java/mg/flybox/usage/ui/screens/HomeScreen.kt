package mg.flybox.usage.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mg.flybox.usage.data.OrangeStatus
import mg.flybox.usage.data.RouterReach
import mg.flybox.usage.domain.AgeFormatter
import mg.flybox.usage.domain.DashboardData
import mg.flybox.usage.domain.Formats
import mg.flybox.usage.domain.Signal
import mg.flybox.usage.domain.Units
import mg.flybox.usage.ui.HomeUi
import mg.flybox.usage.ui.components.Glyph
import mg.flybox.usage.ui.components.GlyphKind
import mg.flybox.usage.ui.components.Hairline
import mg.flybox.usage.ui.components.RingGauge
import mg.flybox.usage.ui.components.SkeletonBar
import mg.flybox.usage.ui.components.StatusChip
import mg.flybox.usage.ui.components.TextAction
import mg.flybox.usage.ui.components.Verdict
import mg.flybox.usage.ui.components.VerdictBanner
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

private val ringEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun HomeScreen(
    ui: HomeUi,
    playRingAnimation: Boolean,
    reducedMotion: Boolean,
    onRingPlayed: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val data = ui.data
        when {
            ui.loading || data == null -> HomeSkeleton(reducedMotion)
            data.isEmpty -> HomeEmpty(ui, onRefresh)
            else -> HomeContent(ui, data, playRingAnimation && !reducedMotion, onRingPlayed, onRefresh)
        }
    }
}

@Composable
private fun HeaderRow(ui: HomeUi, data: DashboardData?, onRefresh: () -> Unit) {
    val offline = ui.routerReach == RouterReach.UNREACHABLE || ui.orangeStatus == OrangeStatus.UNREACHABLE
    val newest = listOfNotNull(data?.lastSample?.ts, data?.plan?.ts).maxOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Accueil", style = Type.Title, color = Palette.Ink, modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val s = data?.lastSample
            if (s != null) {
                StatusChip(
                    Signal.chipText(s.network, s.rsrp),
                    lead = { Glyph(GlyphKind.Signal, Palette.Ink, 16, signalLevel = Signal.level(s.rsrp)) },
                    background = Palette.AccentSoft,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (newest != null) {
                val text = if (offline) "Hors ligne, mis à jour ${AgeFormatter.ago(newest, ui.now)}" else AgeFormatter.updated(newest, ui.now)
                if (offline) StatusChip(text, Modifier.weight(1f, fill = false))
                else Text(text, style = Type.Caption, color = Palette.InkSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            TextAction(if (ui.refreshing) "Actualisation" else "Actualiser", onRefresh)
        }
    }
}

@Composable
private fun HomeContent(ui: HomeUi, data: DashboardData, animate: Boolean, onRingPlayed: () -> Unit, onRefresh: () -> Unit) {
    val plan = data.plan
    val pred = data.prediction
    val stale = AgeFormatter.isStale(plan?.ts ?: 0L, ui.now)
    val planMissing = ui.orangeStatus == OrangeStatus.PLAN_NOT_FOUND || plan == null

    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    val projReveal = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            reveal.animateTo(1f, tween(900, easing = ringEasing))
            projReveal.animateTo(1f, tween(400))
            onRingPlayed()
        }
    }

    HeaderRow(ui, data, onRefresh)

    val total = plan?.totalGo ?: 0.0
    val remaining = plan?.remainingGo ?: 0.0
    val progress = if (!planMissing && total > 0) (remaining / total).toFloat() else 0f
    val showProjection = !planMissing && pred != null && !stale
    val projectionSpan = if (showProjection) (if (pred!!.atRisk) 1f else ((remaining - pred.remainingAtExpiryGo) / total).toFloat()) else null
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        RingGauge(
            progress = progress,
            projectionSpan = projectionSpan,
            projectionColor = if (pred?.atRisk == true) Palette.Red else Palette.Green,
            exhaustedMarker = showProjection && pred!!.atRisk,
            revealFraction = reveal.value,
            projectionReveal = projReveal.value,
            description = if (planMissing) "Forfait indisponible" else "Reste ${Units.formatGo(remaining, ui.alwaysGo)} sur ${Units.formatGo(total, ui.alwaysGo, 0)}",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (planMissing) {
                    Text("Forfait indisponible", style = Type.Section, color = Palette.Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
                } else {
                    val (num, unit) = Units.splitGo(remaining * reveal.value, ui.alwaysGo)
                    Text(num, style = Type.Hero, color = Palette.Ink)
                    Text("$unit restants", style = Type.Label, color = Palette.InkSecondary)
                    if (pred == null) Text("Collecte en cours", style = Type.Caption, color = Palette.InkSecondary)
                }
            }
        }
    }

    if (!planMissing && plan != null) {
        Text(
            "sur ${Units.formatGo(plan.totalGo, ui.alwaysGo, 0)}, valable jusqu'au ${Formats.numericDate(plan.expires)}",
            style = Type.Body, color = Palette.InkSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    // Verdict
    when {
        planMissing -> VerdictBanner(
            Verdict.Collecting,
            if (ui.orangeStatus == OrangeStatus.UNREACHABLE) "La page Orange est injoignable pour le moment. Le reste de l'application fonctionne."
            else "Le forfait n'a pas pu être lu sur la page Orange. Le reste de l'application fonctionne.",
        )
        pred == null -> VerdictBanner(Verdict.Collecting, "Collecte des données en cours, estimation dans quelques heures.")
        pred.atRisk -> {
            val d = pred.emptyDate
            val daysEarly = (pred.daysUntilExpiry - pred.daysUntilEmpty).toInt().coerceAtLeast(0)
            VerdictBanner(
                Verdict.AtRisk,
                if (d != null) "Plus de data vers le ${Formats.shortNumericDate(d)}, soit $daysEarly jour${if (daysEarly > 1) "s" else ""} avant l'expiration."
                else "La data sera épuisée avant l'expiration.",
            )
        }
        else -> VerdictBanner(
            Verdict.OnTrack,
            "Il vous restera environ ${Units.formatGo(pred.remainingAtExpiryGo, ui.alwaysGo, 0)} à l'expiration le ${plan?.expires?.let { Formats.shortNumericDate(it) }}.",
        )
    }

    if (pred != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rythme moyen : ${Units.formatGo(pred.rateGoPerDay, ui.alwaysGo, 1)} par jour", style = Type.Body, color = Palette.Ink)
            val p30 = pred.predRemaining30dGo
            Text(
                if (p30 > 0) "Solde prévu dans 30 jours : ${Units.formatGo(p30, ui.alwaysGo, 1)}" else "Solde prévu dans 30 jours : épuisé",
                style = Type.Body, color = Palette.Ink,
            )
        }
    }

    Hairline()
    TodayUsage(data, ui)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TodayUsage(data: DashboardData, ui: HomeUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aujourd'hui", style = Type.Section, color = Palette.Ink)
            Spacer(Modifier.weight(1f))
            Text(Units.formatBytes(data.todayBytes, ui.alwaysGo), style = Type.RowValue, color = Palette.Ink)
        }
        val avg = data.averageDailyBytes
        val scale = maxOf(data.todayBytes, avg ?: 0L, 1L) * 1.25f
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = size.height / 2
            drawRoundRect(Palette.SurfaceMuted, size = size, cornerRadius = CornerRadius(r))
            val w = (data.todayBytes / scale) * size.width
            if (w > 0f) drawRoundRect(Palette.Brand, size = Size(maxOf(w, size.height), size.height), cornerRadius = CornerRadius(r))
            if (avg != null) {
                val x = (avg / scale) * size.width
                drawLine(Palette.Ink, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 2.dp.toPx())
            }
        }
        Text(
            if (avg != null) "Moyenne des jours précédents : ${Units.formatBytes(avg, ui.alwaysGo)}" else "Pas encore de moyenne à comparer.",
            style = Type.Caption, color = Palette.InkSecondary,
        )
    }
}

@Composable
private fun HomeEmpty(ui: HomeUi, onRefresh: () -> Unit) {
    HeaderRow(ui, null, onRefresh)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        RingGauge(progress = 0f, modifier = Modifier.fillMaxWidth(), description = "Pas encore de données") {
            Text("Pas encore de données", style = Type.RowValue, color = Palette.Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 48.dp))
        }
    }
    Text(
        "Connectez-vous au Wi-Fi de la Flybox, la collecte démarre seule.",
        style = Type.Body, color = Palette.InkSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeSkeleton(reducedMotion: Boolean) {
    Text("Accueil", style = Type.Title, color = Palette.Ink, modifier = Modifier.padding(top = 8.dp))
    SkeletonBar(Modifier.fillMaxWidth(0.6f).height(24.dp), reducedMotion = reducedMotion)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        RingGauge(progress = 0f, modifier = Modifier.fillMaxWidth(), description = "Chargement") {}
    }
    SkeletonBar(Modifier.fillMaxWidth().height(72.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), reducedMotion = reducedMotion)
    SkeletonBar(Modifier.fillMaxWidth(0.8f).height(20.dp), reducedMotion = reducedMotion)
    SkeletonBar(Modifier.fillMaxWidth(0.7f).height(20.dp), reducedMotion = reducedMotion)
}
