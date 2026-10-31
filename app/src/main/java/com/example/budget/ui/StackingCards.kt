package com.example.budget.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

private const val MIN_SCALE = 0.9f
private const val MIN_BRIGHTNESS = 0.6f

/**
 * Where each card sits. Written once per layout pass, read every frame by the
 * layer and draw blocks, so scrolling only touches transform and draw, never layout.
 */
private class StackGeometry {
    var trackTop by mutableFloatStateOf(0f) // track's y inside the scrolling content
    var tops = FloatArray(0)                 // each card's natural y inside the track
    var heights = FloatArray(0)
    var trackHeight = 0f
    var pinBase = 0f
    var pinStep = 0f

    private fun pin(i: Int) = pinBase + i * pinStep

    /** How far card [i] is pushed down to stay pinned, like position: sticky inside its track. */
    fun stick(i: Int, scroll: Int): Float {
        val naturalTop = trackTop + tops[i] - scroll
        return (pin(i) - naturalTop).coerceIn(0f, trackHeight - tops[i] - heights[i])
    }

    private fun shownTop(i: Int, scroll: Int) = trackTop + tops[i] - scroll + stick(i, scroll)

    /** 0 → 1 as the next card slides over card [i]. */
    fun covered(i: Int, scroll: Int): Float {
        if (i >= tops.lastIndex) return 0f
        val overlap = shownTop(i, scroll) + heights[i] - shownTop(i + 1, scroll)
        return (overlap / (heights[i] - pinStep)).coerceIn(0f, 1f)
    }
}

/**
 * Cards that pin near the top as you scroll and pile up like a deck; covered cards shrink and dim.
 * Must sit inside the Column that [scrollState] scrolls, and that column must start at
 * the top of the screen. Cards stay in normal reading order for TalkBack.
 */
@Composable
fun <T> StackingCards(
    items: List<T>,
    scrollState: ScrollState,
    shape: Shape,
    modifier: Modifier = Modifier,
    card: @Composable (T) -> Unit,
) {
    val geo = remember { StackGeometry() }
    val reducedMotion = rememberReducedMotion()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compact = screenHeight < 700.dp
    with(LocalDensity.current) {
        // Pin offset: top = 80dp + index × 16dp; tighter on short screens.
        geo.pinBase = (if (compact) 56.dp else 80.dp).toPx()
        geo.pinStep = (if (compact) 10.dp else 16.dp).toPx()
    }
    val viewport = with(LocalDensity.current) { screenHeight.toPx() }

    Layout(
        modifier = modifier.onPlaced { geo.trackTop = it.positionInRoot().y + scrollState.value },
        content = {
            items.forEachIndexed { i, item ->
                Box(
                    Modifier.drawWithContent {
                        drawContent()
                        // Dim toward 60% brightness as the next card covers this one.
                        val dim = (1f - MIN_BRIGHTNESS) * geo.covered(i, scrollState.value)
                        if (dim > 0f) {
                            drawOutline(shape.createOutline(size, layoutDirection, this), Color.Black, alpha = dim)
                        }
                    }
                ) { card(item) }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        }
        // Space the cards so each scrolls for most of a screen before the next one arrives.
        var y = 0f
        geo.tops = FloatArray(placeables.size) { i ->
            y.also { y += maxOf(placeables[i].height + 24.dp.toPx(), viewport * 0.7f) }
        }
        geo.heights = FloatArray(placeables.size) { placeables[it].height.toFloat() }
        geo.trackHeight = (geo.tops.lastOrNull() ?: 0f) + (geo.heights.lastOrNull() ?: 0f) +
            if (placeables.isEmpty()) 0f else viewport * 0.35f // let the finished deck rest briefly

        layout(constraints.maxWidth, geo.trackHeight.toInt()) {
            placeables.forEachIndexed { i, p ->
                p.placeWithLayer(0, geo.tops[i].toInt(), zIndex = i.toFloat()) {
                    val scroll = scrollState.value
                    translationY = geo.stick(i, scroll)
                    if (!reducedMotion) {
                        val scale = 1f - (1f - MIN_SCALE) * geo.covered(i, scroll)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                }
            }
        }
    }
}
