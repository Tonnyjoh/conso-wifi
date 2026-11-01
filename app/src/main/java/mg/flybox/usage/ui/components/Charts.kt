package mg.flybox.usage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import mg.flybox.usage.domain.Units
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

// ---------------------------------------------------------------------------------------------
// Anneau
// ---------------------------------------------------------------------------------------------

/**
 * Anneau de l'accueil. [progress] est reste/total (0 à 1). [projectionSpan] est la part du total
 * qui sera consommée d'ici l'expiration : hachures sur la piste pâle après l'arc plein.
 * [revealFraction] et [projectionReveal] pilotent l'animation d'ouverture (1 = état final).
 */
@Composable
fun RingGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    projectionSpan: Float? = null,
    projectionColor: Color = Palette.Green,
    exhaustedMarker: Boolean = false,
    revealFraction: Float = 1f,
    projectionReveal: Float = 1f,
    description: String = "",
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.aspectRatio(1f).semantics { if (description.isNotEmpty()) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 28.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawCircle(Palette.AccentSoft, radius = arcSize.width / 2, style = Stroke(stroke))
            val shown = (progress.coerceIn(0f, 1f) * revealFraction.coerceIn(0f, 1f))
            if (shown > 0.001f) {
                drawArc(Palette.Brand, -90f, shown * 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (projectionSpan != null && projectionReveal > 0f) {
                // Hachures uniquement sur la piste pâle, à la suite de l'arc plein, bouts droits.
                // Le bout rond de l'arc dépasse de stroke/2 de part et d'autre : on s'en écarte.
                val end = progress.coerceIn(0f, 1f) * revealFraction.coerceIn(0f, 1f)
                val capDeg = Math.toDegrees((inset / (arcSize.width / 2)).toDouble()).toFloat()
                val startDeg = end * 360f + (if (shown > 0.001f) capDeg else 0f)
                val maxEndDeg = 360f - (if (progress > 0.001f) capDeg else 0f)
                val endDeg = (end * 360f + projectionSpan.coerceIn(0f, 1f) * 360f).coerceAtMost(maxEndDeg)
                val sweep = (endDeg - startDeg) * projectionReveal.coerceIn(0f, 1f)
                if (sweep > 0.5f) {
                    val dash = 6.dp.toPx()
                    drawArc(
                        projectionColor, -90f + startDeg, sweep, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                    )
                }
            }
            if (exhaustedMarker) {
                drawLine(Palette.Ink, Offset(size.width / 2, 0f), Offset(size.width / 2, stroke + 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        if (exhaustedMarker) {
            Text("épuisé", style = Type.Caption, color = Palette.Red, modifier = Modifier.align(Alignment.TopCenter).padding(top = 42.dp))
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Barres quotidiennes
// ---------------------------------------------------------------------------------------------

data class BarDatum(val axisLabel: String, val bubbleLabel: String, val value: Long)

private fun niceMax(v: Double): Double {
    if (v <= 0.0) return 1.0
    val exp = Math.pow(10.0, Math.floor(Math.log10(v)))
    val f = v / exp
    val n = when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 5 -> 5.0; else -> 10.0 }
    return n * exp
}

/** Graphique à barres quotidiennes avec bulle au toucher (AC-8). */
@Composable
fun DailyBarChart(
    bars: List<BarDatum>,
    averageBytes: Long?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    thirtyDays: Boolean = false,
    alwaysGo: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var width by remember { mutableStateOf(0) }
    var bubbleW by remember { mutableStateOf(0) }
    var bubbleH by remember { mutableStateOf(0) }
    val chartH = 200.dp
    val labelBand = 22.dp
    val maxVal = max(bars.maxOfOrNull { it.value } ?: 0L, averageBytes ?: 0L).toDouble()
    val top = niceMax(maxVal / Units.GO) * Units.GO
    val summary = bars.joinToString(". ") { "${it.bubbleLabel}" }

    val axisLabels = listOf(1, 2).map { k -> Units.formatBytes((top * k / 2).toLong(), alwaysGo, if (top * k / 2 >= Units.GO * 10) 0 else 1) }
    val leftPx = with(density) { 8.dp.toPx() } + axisLabels.maxOf { measurer.measure(it, Type.Caption).size.width }
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
    if (averageBytes != null && averageBytes > 0) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            Canvas(Modifier.width(24.dp).height(2.dp)) {
                drawLine(Palette.InkSecondary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }
            Text("moy. " + Units.formatBytes(averageBytes, alwaysGo, 1), style = Type.Caption, color = Palette.InkSecondary)
        }
    }
    Box(Modifier.fillMaxWidth().height(chartH + labelBand + 44.dp).semantics { contentDescription = summary }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartH + labelBand)
                .align(Alignment.BottomStart)
                .onSizeChanged { width = it.width }
                .pointerInput(bars.size) {
                    detectTapGestures { pos ->
                        val slot = (size.width - leftPx) / max(1, bars.size)
                        val i = ((pos.x - leftPx) / slot).toInt().coerceIn(0, bars.size - 1)
                        onSelect(if (selected == i) null else i)
                    }
                }
                .pointerInput(bars.size) {
                    detectHorizontalDragGestures { change, _ ->
                        val slot = (size.width - leftPx) / max(1, bars.size)
                        onSelect(((change.position.x - leftPx) / slot).toInt().coerceIn(0, bars.size - 1))
                    }
                },
        ) {
            val plotH = chartH.toPx()
            val slot = (size.width - leftPx) / max(1, bars.size)
            val barW = slot * if (thirtyDays) 0.7f else 0.6f
            val radius = (if (thirtyDays) 4.dp else 8.dp).toPx()
            // 3 graduations : 0, moitié, maximum.
            for (k in 0..2) {
                val y = plotH - plotH * (k / 2f)
                drawLine(Palette.Hairline, Offset(leftPx, y), Offset(size.width, y), 1.dp.toPx())
                if (k > 0) {
                    val m = measurer.measure(axisLabels[k - 1], Type.Caption.copy(color = Palette.InkSecondary))
                    drawText(m, topLeft = Offset(0f, y - m.size.height / 2f))
                }
            }
            bars.forEachIndexed { i, b ->
                val h = if (top > 0) (b.value / top * plotH).toFloat() else 0f
                if (h > 0f) {
                    val isToday = i == bars.lastIndex
                    val alpha = when {
                        selected != null -> if (i == selected) 1f else 0.3f
                        isToday -> 1f
                        else -> 0.55f
                    }
                    val x = leftPx + i * slot + (slot - barW) / 2
                    val rect = Rect(x, plotH - h, x + barW, plotH)
                    val path = Path().apply {
                        val r = min(radius, min(barW / 2, h))
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(r), androidx.compose.ui.geometry.CornerRadius(r), androidx.compose.ui.geometry.CornerRadius.Zero, androidx.compose.ui.geometry.CornerRadius.Zero))
                    }
                    drawPath(path, Palette.Brand.copy(alpha = alpha))
                }
            }
            if (averageBytes != null && averageBytes > 0 && top > 0) {
                val y = plotH - (averageBytes / top * plotH).toFloat()
                drawLine(
                    Palette.InkSecondary, Offset(leftPx, y), Offset(size.width, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
            // Étiquettes de l'axe horizontal.
            val idx: List<Int> = if (thirtyDays) listOf(0, bars.size / 2, bars.lastIndex).distinct() else bars.indices.toList()
            idx.forEach { i ->
                val m = measurer.measure(bars[i].axisLabel, Type.Caption.copy(color = Palette.InkSecondary))
                var x = leftPx + i * slot + slot / 2 - m.size.width / 2
                x = x.coerceIn(leftPx, size.width - m.size.width)
                drawText(m, topLeft = Offset(x, plotH + 4.dp.toPx()))
            }
        }
        val sel = selected
        if (sel != null && sel in bars.indices && width > 0) {
            val slot = (width - leftPx) / bars.size
            val barH = if (top > 0) (bars[sel].value / top * with(density) { chartH.toPx() }).toFloat() else 0f
            val cx = leftPx + sel * slot + slot / 2
            val x = (cx - bubbleW / 2f).coerceIn(0f, max(0f, width - bubbleW.toFloat()))
            val yBar = with(density) { (chartH + labelBand + 44.dp).toPx() } - with(density) { labelBand.toPx() } - barH
            val y = (yBar - bubbleH - with(density) { 6.dp.toPx() }).coerceAtLeast(0f)
            Bubble(bars[sel].bubbleLabel, Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.onSizeChanged { bubbleW = it.width; bubbleH = it.height })
        }
    }
    }
}

@Composable
fun Bubble(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = Palette.Surface, shadowElevation = 4.dp) {
        Text(text, style = Type.RowValue, color = Palette.Ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Courbe de débit
// ---------------------------------------------------------------------------------------------

data class RatePoint(val tsMillis: Long, val down: Double, val up: Double)

/**
 * Tangentes d'une interpolation cubique monotone (Fritsch-Carlson) : courbe lissée sans dépassement.
 */
internal fun monotoneTangents(x: DoubleArray, y: DoubleArray): DoubleArray {
    val n = x.size
    val m = DoubleArray(n)
    if (n < 2) return m
    val d = DoubleArray(n - 1) { (y[it + 1] - y[it]) / (x[it + 1] - x[it]).let { dx -> if (dx == 0.0) 1.0 else dx } }
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
    for (i in 0 until n - 1) {
        if (d[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; continue }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9) {
            val t = 3 / Math.sqrt(s)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
        }
    }
    return m
}

private fun smoothPath(xs: FloatArray, ys: FloatArray, close: Float? = null): Path {
    val path = Path()
    if (xs.isEmpty()) return path
    val tx = monotoneTangents(DoubleArray(xs.size) { xs[it].toDouble() }, DoubleArray(ys.size) { ys[it].toDouble() })
    if (close != null) { path.moveTo(xs[0], close); path.lineTo(xs[0], ys[0]) } else path.moveTo(xs[0], ys[0])
    for (i in 0 until xs.size - 1) {
        val dx = (xs[i + 1] - xs[i]) / 3f
        path.cubicTo(xs[i] + dx, ys[i] + (tx[i] * dx).toFloat(), xs[i + 1] - dx, ys[i + 1] - (tx[i + 1] * dx).toFloat(), xs[i + 1], ys[i + 1])
    }
    if (close != null) { path.lineTo(xs.last(), close); path.close() }
    return path
}

/** Courbe de débit récent : descendant plein orange avec dégradé fonctionnel, montant en trait encre. */
@Composable
fun ThroughputChart(points: List<RatePoint>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var touch by remember { mutableStateOf<Int?>(null) }
    var width by remember { mutableStateOf(0) }
    var bubbleW by remember { mutableStateOf(0) }
    val maxRate = max(1.0, points.maxOfOrNull { max(it.down, it.up) } ?: 1.0)
    val topRate = niceMax(maxRate / Units.MO) * Units.MO
    val summary = if (points.isEmpty()) "Pas de débit récent" else "Dernier débit descendant ${Units.formatRate(points.last().down)}, montant ${Units.formatRate(points.last().up)}"
    val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE) }
    val axisTop = Units.formatRate(topRate)
    val lastLabel = if (points.isEmpty()) "" else Units.formatRate(points.last().down)
    val leftPx = with(density) { 8.dp.toPx() } + measurer.measure(axisTop, Type.Caption).size.width
    val rightPx = with(density) { 10.dp.toPx() } + measurer.measure(lastLabel, Type.Caption).size.width
    val bandPx = with(density) { 22.dp.toPx() }
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem("Descendant", Palette.Brand, 2.5.dp, Palette.AccentText)
            LegendItem("Montant", Palette.CurveInk, 2.dp, Palette.CurveInk)
        }
        Box(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = summary }) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { width = it.width }
                    .pointerInput(points.size) {
                        detectTapGestures { pos -> touch = nearest(points, pos.x, leftPx, size.width - rightPx) }
                    }
                    .pointerInput(points.size) {
                        detectHorizontalDragGestures(onDragEnd = { touch = null }) { change, _ -> touch = nearest(points, change.position.x, leftPx, size.width - rightPx) }
                    },
            ) {
                val padT = 8.dp.toPx()
                val h = size.height - padT - bandPx
                val x0 = leftPx
                val w = size.width - rightPx - leftPx
                for (k in 0..1) {
                    val y = padT + h - h * k
                    drawLine(Palette.Hairline, Offset(x0, y), Offset(size.width - rightPx, y), 1.dp.toPx())
                }
                val tm = measurer.measure(axisTop, Type.Caption.copy(color = Palette.InkSecondary))
                drawText(tm, topLeft = Offset(0f, padT - tm.size.height / 2f))
                if (points.size < 2) return@Canvas
                val t0 = points.first().tsMillis.toDouble()
                val span = max(1.0, (points.last().tsMillis - points.first().tsMillis).toDouble())
                val xs = FloatArray(points.size) { (x0 + (points[it].tsMillis - t0) / span * w).toFloat() }
                val yd = FloatArray(points.size) { (padT + h - (points[it].down / topRate * h)).toFloat() }
                val yu = FloatArray(points.size) { (padT + h - (points[it].up / topRate * h)).toFloat() }
                drawPath(smoothPath(xs, yd, close = padT + h), Brush.verticalGradient(listOf(Palette.Brand.copy(alpha = 0.18f), Palette.Brand.copy(alpha = 0f)), startY = padT, endY = padT + h))
                drawPath(smoothPath(xs, yd), Palette.Brand, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(smoothPath(xs, yu), Palette.CurveInk, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                val last = points.size - 1
                drawCircle(Color.White, 6.dp.toPx(), Offset(xs[last], yd[last]))
                drawCircle(Palette.Brand, 4.dp.toPx(), Offset(xs[last], yd[last]))
                // Valeur finale dans la marge de droite, hors de la courbe.
                val lbl = measurer.measure(lastLabel, Type.Caption.copy(color = Palette.AccentText))
                drawText(lbl, topLeft = Offset(size.width - rightPx + 8.dp.toPx(), (yd[last] - lbl.size.height / 2f).coerceIn(0f, padT + h - lbl.size.height)))
                // Axe du temps : début, milieu, fin.
                listOf(0, last / 2, last).distinct().forEach { i ->
                    val m = measurer.measure(timeFmt.format(java.util.Date(points[i].tsMillis)), Type.Caption.copy(color = Palette.InkSecondary))
                    val x = (xs[i] - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                    drawText(m, topLeft = Offset(x, padT + h + 4.dp.toPx()))
                }
                touch?.let { i ->
                    if (i in points.indices) drawLine(Palette.Ink.copy(alpha = 0.5f), Offset(xs[i], padT), Offset(xs[i], padT + h), 1.dp.toPx())
                }
            }
            val i = touch
            if (i != null && i in points.indices && width > 0 && points.size >= 2) {
                val frac = (points[i].tsMillis - points.first().tsMillis).toFloat() / max(1L, points.last().tsMillis - points.first().tsMillis)
                val cx = leftPx + frac * (width - rightPx - leftPx)
                val x = (cx - bubbleW / 2f).coerceIn(0f, max(0f, width - bubbleW.toFloat()))
                Bubble(
                    timeFmt.format(java.util.Date(points[i].tsMillis)) + ", " + Units.formatRate(points[i].down),
                    Modifier.offset { IntOffset(x.toInt(), 0) }.onSizeChanged { bubbleW = it.width },
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, lineColor: Color, stroke: androidx.compose.ui.unit.Dp, textColor: Color) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.width(20.dp).height(stroke)) {
            drawLine(lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
        }
        Text(label, style = Type.Caption, color = textColor)
    }
}

private fun nearest(points: List<RatePoint>, x: Float, left: Float, right: Float): Int? {
    if (points.size < 2) return null
    val frac = ((x - left) / max(1f, right - left)).coerceIn(0f, 1f)
    val target = points.first().tsMillis + frac * (points.last().tsMillis - points.first().tsMillis)
    return points.indices.minByOrNull { Math.abs(points[it].tsMillis - target) }
}

// ---------------------------------------------------------------------------------------------
// Barre horizontale (applis, appareils)
// ---------------------------------------------------------------------------------------------

/** Piste arrondie avec remplissage proportionnel. [fraction] entre 0 et 1. */
@Composable
fun HorizontalBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val r = size.height / 2
        drawRoundRect(Palette.SurfaceMuted, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        val w = (size.width * fraction.coerceIn(0f, 1f))
        if (w > 0f) drawRoundRect(color, size = Size(max(w, size.height), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    }
}

@Suppress("unused")
private fun DrawScope.noop(m: TextMeasurer) = Unit
