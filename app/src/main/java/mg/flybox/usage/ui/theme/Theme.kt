package mg.flybox.usage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mg.flybox.usage.R

/** Jetons de couleur du brief de design. Thème clair uniquement. */
object Palette {
    val Brand = Color(0xFF2459D6)
    val OnBrand = Color(0xFFFFFFFF)
    val AccentText = Color(0xFF1A46AD)
    val AccentSoft = Color(0xFFDCE6FB)
    val Background = Color(0xFFF6F8FC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFEEF1F8)
    val Ink = Color(0xFF14213D)
    val InkSecondary = Color(0xFF5A6478)
    val Hairline = Color(0xFFDCE2EF)
    val Green = Color(0xFF1E7A4C)
    val GreenSoft = Color(0xFFE2F3E9)
    val Red = Color(0xFFBF2F1B)
    val RedSoft = Color(0xFFFCE4DF)
    val CurveInk = Color(0xFF5F6F94)
}

@OptIn(ExperimentalTextApi::class)
private fun montserrat(weight: Int) = Font(
    R.font.montserrat,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Montserrat = FontFamily(montserrat(500), montserrat(600), montserrat(700), montserrat(800))

private const val TNUM = "tnum"

/** Échelle typographique du brief (taille / interligne, poids). Chiffres tabulaires partout. */
object Type {
    val Hero = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp, fontFeatureSettings = TNUM)
    val Title = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, fontFeatureSettings = TNUM)
    val Section = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM)
    val RowValue = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM)
    val Body = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM)
    val Label = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM)
    val Caption = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM)
}

private val flyboxTypography = Typography(
    displayLarge = Type.Hero,
    headlineMedium = Type.Title,
    titleLarge = Type.Section,
    titleMedium = Type.RowValue,
    bodyLarge = Type.Body,
    bodyMedium = Type.Body,
    labelLarge = Type.Label,
    labelMedium = Type.Label,
    bodySmall = Type.Caption,
    labelSmall = Type.Caption,
)

private val colors = lightColorScheme(
    primary = Palette.Brand,
    onPrimary = Palette.OnBrand,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.AccentText,
    background = Palette.Background,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SurfaceMuted,
    onSurfaceVariant = Palette.InkSecondary,
    outlineVariant = Palette.Hairline,
    outline = Palette.Hairline,
    error = Palette.Red,
)

/** Thème clair imposé : le thème du système n'est jamais consulté (AC-14). */
@Composable
fun FlyboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = flyboxTypography, content = content)
}
