package mg.flybox.usage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class GlyphKind { Home, History, Devices, Apps, Settings, Check, Alert, Signal, Device, Lock }

/**
 * Glyphes fonctionnels dessinés au trait, sur une grille de 24 unités. Seuls les onglets,
 * le signal, l'appareil, l'appli et le verdict en utilisent : aucun glyphe décoratif.
 */
@Composable
fun Glyph(kind: GlyphKind, color: Color, sizeDp: Int = 24, modifier: Modifier = Modifier, signalLevel: Int = 4) {
    Canvas(modifier.size(sizeDp.dp)) {
        val u = size.minDimension / 24f
        val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        when (kind) {
            GlyphKind.Home -> {
                val path = Path().apply {
                    moveTo(4 * u, 11 * u); lineTo(12 * u, 4 * u); lineTo(20 * u, 11 * u)
                    moveTo(6 * u, 10 * u); lineTo(6 * u, 20 * u); lineTo(18 * u, 20 * u); lineTo(18 * u, 10 * u)
                }
                drawPath(path, color, style = st)
            }
            GlyphKind.History -> {
                drawLine(color, p(5f, 20f), p(5f, 13f), st.width, StrokeCap.Round)
                drawLine(color, p(12f, 20f), p(12f, 5f), st.width, StrokeCap.Round)
                drawLine(color, p(19f, 20f), p(19f, 10f), st.width, StrokeCap.Round)
            }
            GlyphKind.Devices -> {
                drawRoundRect(color, p(4f, 5f), Size(16 * u, 11 * u), androidx.compose.ui.geometry.CornerRadius(2 * u), style = st)
                drawLine(color, p(9f, 20f), p(15f, 20f), st.width, StrokeCap.Round)
                drawLine(color, p(12f, 16f), p(12f, 20f), st.width, StrokeCap.Round)
            }
            GlyphKind.Apps -> {
                val s = 6f * u
                for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f)) {
                    drawRoundRect(color, p(x, y), Size(s, s), androidx.compose.ui.geometry.CornerRadius(1.5f * u), style = st)
                }
            }
            GlyphKind.Settings -> {
                for ((y, kx) in listOf(6f to 9f, 12f to 15f, 18f to 8f)) {
                    drawLine(color, p(4f, y), p(20f, y), st.width, StrokeCap.Round)
                    drawCircle(Color.White, 2.6f * u, p(kx, y))
                    drawCircle(color, 2.6f * u, p(kx, y), style = st)
                }
            }
            GlyphKind.Check -> {
                val path = Path().apply { moveTo(5 * u, 12.5f * u); lineTo(10 * u, 17.5f * u); lineTo(19 * u, 7 * u) }
                drawPath(path, color, style = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            GlyphKind.Alert -> {
                val path = Path().apply { moveTo(12 * u, 4 * u); lineTo(21 * u, 20 * u); lineTo(3 * u, 20 * u); close() }
                drawPath(path, color, style = st)
                drawLine(color, p(12f, 10f), p(12f, 14.5f), st.width, StrokeCap.Round)
                drawCircle(color, 1.1f * u, p(12f, 17.3f))
            }
            GlyphKind.Signal -> {
                for (i in 0 until 4) {
                    val h = (5 + i * 4.5f) * u
                    val x = (4f + i * 4.6f) * u
                    drawRoundRect(
                        if (i < signalLevel) color else color.copy(alpha = 0.25f),
                        Offset(x, 20 * u - h), Size(3 * u, h), androidx.compose.ui.geometry.CornerRadius(1f * u),
                    )
                }
            }
            GlyphKind.Device -> {
                drawRoundRect(color, p(7f, 3f), Size(10 * u, 18 * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = st)
                drawLine(color, p(10.5f, 18f), p(13.5f, 18f), st.width, StrokeCap.Round)
            }
            GlyphKind.Lock -> {
                drawRoundRect(color, p(5f, 11f), Size(14 * u, 9 * u), androidx.compose.ui.geometry.CornerRadius(2 * u), style = st)
                val arc = Path().apply { moveTo(8 * u, 11 * u); lineTo(8 * u, 8 * u); arcTo(Rect(8 * u, 4 * u, 16 * u, 12 * u), 180f, 180f, false); lineTo(16 * u, 11 * u) }
                drawPath(arc, color, style = st)
            }
        }
    }
}

@Suppress("unused")
private fun DrawScope.unused() = Unit
