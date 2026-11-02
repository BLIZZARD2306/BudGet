package com.budgetgoblin.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.R

/** Nunito: a rounded, friendly typeface (SIL Open Font License) for the chunky Duolingo-style look. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Nunito = FontFamily(
    listOf(400, 600, 700, 800, 900).map { w ->
        Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private fun TextStyle.nunito(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.nunito(FontWeight.Black),
    displayMedium = base.displayMedium.nunito(FontWeight.Black),
    displaySmall = base.displaySmall.nunito(FontWeight.Black),
    headlineLarge = base.headlineLarge.nunito(FontWeight.Black),
    headlineMedium = base.headlineMedium.nunito(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.nunito(FontWeight.ExtraBold),
    titleLarge = base.titleLarge.nunito(FontWeight.ExtraBold),
    titleMedium = base.titleMedium.nunito(FontWeight.ExtraBold),
    titleSmall = base.titleSmall.nunito(FontWeight.Bold),
    bodyLarge = base.bodyLarge.nunito(FontWeight.SemiBold).copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.nunito(FontWeight.SemiBold),
    bodySmall = base.bodySmall.nunito(FontWeight.SemiBold),
    labelLarge = base.labelLarge.nunito(FontWeight.ExtraBold),
    labelMedium = base.labelMedium.nunito(FontWeight.ExtraBold),
    labelSmall = base.labelSmall.nunito(FontWeight.ExtraBold),
)
