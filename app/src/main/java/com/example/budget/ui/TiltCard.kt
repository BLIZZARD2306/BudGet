package com.example.budget.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val MAX_GLARE = 0.35f
private const val FOLLOW = 0.10f // fraction of the remaining distance covered each frame
private val ShadowShift = 14.dp
private val ShadowDrop = 12.dp

/** Lets content inside a [TiltCard] float above its surface. */
interface TiltScope {
    /** Parallax-lifts this element by [z]; 30–50dp reads like CSS translateZ(30–50px). */
    fun Modifier.depth(z: Dp): Modifier
}

@Stable
private class TiltState : TiltScope {
    // Tilt in −1…1 on each axis; x > 0 = pointer on the right, y > 0 = pointer at the bottom.
    val x = Animatable(0f)
    val y = Animatable(0f)
    var target by mutableStateOf<Offset?>(null)
    var pointer by mutableStateOf(Offset.Unspecified)

    val strength get() = max(abs(x.value), abs(y.value)).coerceAtMost(1f)

    override fun Modifier.depth(z: Dp) = graphicsLayer {
        // A point raised off a rotated plane slides toward the side that tips away.
        val shift = z.toPx() * 0.35f
        translationX = x.value * shift
        translationY = y.value * shift
    }
}

/**
 * A card that tips toward the pointer in 3D, with a sliding glare and a shadow that shifts opposite the tilt.
 * Follows a mouse on hover, and a finger while it is pressed (touch has no hover on Android).
 * Stays flat when the system has animations turned off.
 */
@Composable
fun TiltCard(
    background: Brush,
    shape: Shape,
    modifier: Modifier = Modifier,
    maxTilt: Float = 14f,
    content: @Composable TiltScope.() -> Unit,
) {
    val state = remember { TiltState() }
    val enabled = !rememberReducedMotion()

    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { state.target }.collectLatest { target ->
            if (target == null) {
                // Spring back flat in ~0.5s.
                val back = spring<Float>(dampingRatio = 0.65f, stiffness = 250f)
                coroutineScope {
                    launch { state.x.animateTo(0f, back) }
                    launch { state.y.animateTo(0f, back) }
                }
            } else {
                // Ease ~10% toward the target every frame so it feels weighty, not twitchy.
                while (true) {
                    withFrameNanos { }
                    state.x.snapTo(state.x.value + (target.x - state.x.value) * FOLLOW)
                    state.y.snapTo(state.y.value + (target.y - state.y.value) * FOLLOW)
                }
            }
        }
    }

    // Stage: owns the single pointer listener and the shadow, and never rotates itself.
    Box(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.first()
                    val active = event.type != PointerEventType.Exit &&
                        (change.type == PointerType.Mouse || change.pressed)
                    if (active) {
                        val p = change.position
                        state.pointer = p
                        state.target = Offset(
                            (p.x / size.width * 2 - 1).coerceIn(-1f, 1f),
                            (p.y / size.height * 2 - 1).coerceIn(-1f, 1f),
                        )
                    } else {
                        state.target = null
                    }
                }
            }
        }
    ) {
        // Shadow: soft, drops below the card and slides opposite the tilt.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = -state.x.value * ShadowShift.toPx()
                    translationY = ShadowDrop.toPx() - state.y.value * ShadowShift.toPx()
                    scaleX = 0.94f
                    scaleY = 0.94f
                }
                .blur(24.dp, BlurredEdgeTreatment.Unbounded)
                .background(Color.Black.copy(alpha = 0.35f), shape)
        )
        // Card: the only element that rotates; transform-only, so it never relayouts.
        Box(
            Modifier
                .graphicsLayer {
                    cameraDistance = 12f * density // ≈ perspective: 800px
                    rotationY = state.x.value * maxTilt
                    rotationX = -state.y.value * maxTilt
                }
                .clip(shape)
                .background(background)
                .drawWithContent {
                    drawContent()
                    // Glare: a white highlight under the pointer that brightens as the tilt grows.
                    val alpha = MAX_GLARE * state.strength
                    if (alpha > 0.005f && state.pointer.isSpecified) {
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = alpha), Color.Transparent),
                                center = state.pointer,
                                radius = size.maxDimension * 0.75f,
                            )
                        )
                    }
                }
        ) {
            state.content()
        }
    }
}
