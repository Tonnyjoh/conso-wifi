package mg.flybox.usage.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mg.flybox.usage.ui.theme.Palette
import mg.flybox.usage.ui.theme.Type

/** Cliquable avec anneau de focus de 3 dp décalé de 2 dp et voile d'appui (brief, état Focus). */
@Composable
fun Modifier.flyClickable(
    onClick: () -> Unit,
    role: Role = Role.Button,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    return this
        .drawBehind {
            if (focused) {
                val off = 2.dp.toPx()
                val w = 3.dp.toPx()
                drawRoundRect(
                    color = Palette.Ink,
                    topLeft = Offset(-off - w / 2, -off - w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width + 2 * (off + w / 2), size.height + 2 * (off + w / 2)),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(w),
                )
            }
        }
        .clip(shape)
        .clickable(
            interactionSource = source,
            indication = ripple(color = Palette.Ink),
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .background(if (enabled) Palette.Brand else Palette.SurfaceMuted, RoundedCornerShape(26.dp))
            .flyClickable(onClick, shape = RoundedCornerShape(26.dp), enabled = enabled)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.RowValue, color = if (enabled) Palette.OnBrand else Palette.InkSecondary, maxLines = 1)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .flyClickable(onClick, shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Label, color = Palette.OrangeText, maxLines = 1, softWrap = false)
    }
}

/** Puce d'état (signal, hors ligne, âge de la donnée). */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, background: Color = Palette.SurfaceMuted, content: Color = Palette.Ink, lead: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .background(background, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lead?.invoke()
        Text(text, style = Type.Label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Sélecteur de période en pastilles, 48 dp de haut. */
@Composable
fun SegmentedPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(Palette.SurfaceMuted, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (on) Palette.OrangeSoft else Color.Transparent, CircleShape)
                    .flyClickable({ onSelect(i) }, role = Role.RadioButton, shape = CircleShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.Label, color = Palette.Ink, maxLines = 1)
            }
        }
    }
}

enum class Verdict { OnTrack, AtRisk, Collecting }

/** Bannière de verdict (accueil) : mot d'état, phrase, un glyphe fonctionnel. Annoncée en live region polie. */
@Composable
fun VerdictBanner(verdict: Verdict, message: String, modifier: Modifier = Modifier) {
    val (bg, fg, word) = when (verdict) {
        Verdict.OnTrack -> Triple(Palette.GreenSoft, Palette.Green, "Dans les temps")
        Verdict.AtRisk -> Triple(Palette.RedSoft, Palette.Red, "Risque de dépassement")
        Verdict.Collecting -> Triple(Palette.SurfaceMuted, Palette.InkSecondary, "")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(bg, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (verdict) {
            Verdict.OnTrack -> Glyph(GlyphKind.Check, fg, 24)
            Verdict.AtRisk -> Glyph(GlyphKind.Alert, fg, 24)
            Verdict.Collecting -> {}
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (word.isNotEmpty()) {
                Text(word, style = Type.RowValue.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = fg)
            }
            Text(message, style = Type.Body, color = if (verdict == Verdict.Collecting) Palette.InkSecondary else Palette.Ink)
        }
    }
}

/** Fine ligne de séparation de 1 dp. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Palette.Hairline))
}

/** Barre de squelette avec balayage lent limité à un ton plus clair (1,4 s). */
@Composable
fun SkeletonBar(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp), reducedMotion: Boolean = false) {
    val shift = if (reducedMotion) 0f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val v by t.animateFloat(
            initialValue = -1f, targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "shift",
        )
        v
    }
    Box(
        modifier
            .clip(shape)
            .background(Palette.SurfaceMuted)
            .drawBehind {
                if (!reducedMotion) {
                    val w = size.width
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent, 0.5f to Color(0x66FFFFFF), 1f to Color.Transparent,
                            startX = shift * w - w * 0.5f, endX = shift * w + w * 0.5f,
                        ),
                    )
                }
            },
    )
}

/** Feuille blanche arrondie portant le graphique principal (une seule surface élevée par écran). */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, Palette.Hairline), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.Title, color = Palette.Ink, modifier = modifier.padding(top = 8.dp))
}

@Composable
fun CenterMessage(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = Type.Section, color = Palette.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(body, style = Type.Body, color = Palette.InkSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.size(4.dp))
        action?.invoke()
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp)
