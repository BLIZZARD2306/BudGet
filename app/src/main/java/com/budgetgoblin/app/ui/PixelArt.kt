package com.budgetgoblin.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.data.ChallengeType

/**
 * A rectangle whose corners are cut in [steps] square "pixel" steps of [step] each, so buttons
 * and tiles match the goblin's pixel art instead of having smooth rounded corners.
 */
class PixelShape(private val step: Dp = 3.dp, private val steps: Int = 2) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val u = with(density) { step.toPx() }.coerceAtMost(minOf(size.width, size.height) / (2f * steps + 1))
        val w = size.width
        val h = size.height
        val n = steps
        // Clockwise from the top edge; each corner is a staircase of n steps.
        val path = Path().apply {
            moveTo(n * u, 0f)
            lineTo(w - n * u, 0f)
            for (i in 1..n) { lineTo(w - (n - i) * u - u, i * u); lineTo(w - (n - i) * u, i * u) }      // top-right
            lineTo(w, h - n * u)
            for (i in 1..n) { lineTo(w - i * u, h - (n - i) * u - u); lineTo(w - i * u, h - (n - i) * u) } // bottom-right
            lineTo(n * u, h)
            for (i in 1..n) { lineTo((n - i) * u + u, h - i * u); lineTo((n - i) * u, h - i * u) }      // bottom-left
            lineTo(0f, n * u)
            for (i in 1..n) { lineTo(i * u, (n - i) * u + u); lineTo(i * u, (n - i) * u) }              // top-left
            close()
        }
        return Outline.Generic(path)
    }
}

/** A pixel icon tinted to [tint] (single-colour icons). */
@Composable
fun PixelIcon(@DrawableRes res: Int, contentDescription: String?, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Icon(painterResource(res), contentDescription, modifier.size(size), tint = tint)
}

/** A multi-colour pixel icon shown as drawn (stat icons). */
@Composable
fun PixelImage(@DrawableRes res: Int, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Image(painterResource(res), contentDescription, modifier.size(size))
}

@DrawableRes
fun Category.icon(): Int = when (this) {
    Category.FOOD -> R.drawable.px_cat_food
    Category.TRANSPORT -> R.drawable.px_cat_transport
    Category.BILLS -> R.drawable.px_cat_bills
    Category.FUN -> R.drawable.px_cat_fun
    Category.OTHER -> R.drawable.px_cat_other
    Category.SHOPPING -> R.drawable.px_cat_shopping
    Category.COFFEE -> R.drawable.px_cat_coffee
}

@DrawableRes
fun ChallengeType.icon(): Int = category?.icon() ?: R.drawable.px_calendar
