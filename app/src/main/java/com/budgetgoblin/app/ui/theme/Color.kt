package com.budgetgoblin.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// BudGet's colours, used Duolingo-style: bright flat fills with a darker "lip" underneath.
val Coral = Color(0xFFFF7B4A)
val CoralLight = Color(0xFFFF9A6C)
val CoralDark = Color(0xFFFF8F61)
val Mint = Color(0xFF2FC46E)
val MintDark = Color(0xFF1FA75A)
val Slate = Color(0xFF4F5363)
val SlateDeep = Color(0xFF3B3E4B)
val CoinGold = Color(0xFFF2B33D)
val Sand = Color(0xFFF7F1EA)

// Light: white page, warm hairline borders.
val Paper = Color(0xFFFFFFFF)
val Ink = Color(0xFF3B3D4A)
val Muted = Color(0xFF9A958F)
val Hairline = Color(0xFFE8E2DC)

// Dark: deep blue-grey page and raised panels, as in Duolingo's dark theme.
val Night = Color(0xFF131F24)
val NightRaised = Color(0xFF202F36)
val NightBorder = Color(0xFF37464F)
val NightText = Color(0xFFF1F7FB)
val NightMuted = Color(0xFF8EA0A9)

/** The darker edge under a chunky button or card in this colour. */
fun Color.lip(): Color = lerp(this, Color.Black, 0.2f)
