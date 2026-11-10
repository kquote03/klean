package app.klean.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.klean.core.design.R

val Cantarell = FontFamily(
    Font(R.font.cantarell_regular, FontWeight.Normal),
    Font(R.font.cantarell_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.cantarell_bold, FontWeight.Bold),
    Font(R.font.cantarell_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

private val base = Typography()

// Cantarell ships only 400 and 700, so the 500/600 roles map to Bold. Roles and their uses: docs/ui/design-system.md
// (displaySmall: Today's weekday; titleLarge: screen titles, regular; titleMedium: section and card titles).
val KleanTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Cantarell),
    displayMedium = base.displayMedium.copy(fontFamily = Cantarell),
    displaySmall = base.displaySmall.copy(fontFamily = Cantarell),
    headlineLarge = base.headlineLarge.copy(fontFamily = Cantarell),
    headlineMedium = base.headlineMedium.copy(fontFamily = Cantarell),
    headlineSmall = base.headlineSmall.copy(fontFamily = Cantarell),
    titleLarge = base.titleLarge.copy(fontFamily = Cantarell, fontWeight = FontWeight.Normal),
    titleMedium = base.titleMedium.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Cantarell),
    bodyMedium = base.bodyMedium.copy(fontFamily = Cantarell),
    bodySmall = base.bodySmall.copy(fontFamily = Cantarell),
    labelLarge = base.labelLarge.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontFamily = Cantarell),
)
