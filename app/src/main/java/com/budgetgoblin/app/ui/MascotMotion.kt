package com.budgetgoblin.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

/**
 * The goblin's small actions. He always stays on the ground: these sway, shiver or tilt him in
 * place and add pixel-art details (tears, sparkles, "!") around him. Each plays [cycles] times.
 */
enum class MascotMotion(val cycleMillis: Int, val cycles: Int) {
    WAVE(900, 3),
    CRY(1100, 4),
    CHEER(1000, 3),
    SHOCK(1400, 1),
    THINK(2400, 1),
    NONE(0, 0),
}

/** Which action each pose performs. */
fun Mood.motion(): MascotMotion = when (this) {
    Mood.HAPPY, Mood.HELP -> MascotMotion.WAVE
    Mood.SAD, Mood.BROKE, Mood.REGRETFUL -> MascotMotion.CRY
    Mood.CELEBRATE, Mood.LEVEL_UP, Mood.PROUD -> MascotMotion.CHEER
    Mood.SHOCKED, Mood.ALERT -> MascotMotion.SHOCK
    Mood.CURIOUS, Mood.ANXIOUS -> MascotMotion.THINK
    else -> MascotMotion.NONE
}

/** Where tears start in each crying pose, as fractions of the sprite (just under the eyes). */
private fun Mood.tearSpots(): List<Offset> = when (this) {
    Mood.SAD -> listOf(Offset(0.43f, 0.49f), Offset(0.70f, 0.49f))
    Mood.BROKE -> listOf(Offset(0.34f, 0.50f), Offset(0.56f, 0.50f))
    Mood.REGRETFUL -> listOf(Offset(0.54f, 0.48f))
    else -> emptyList()
}

/** The hand that waves, as a fraction of the sprite; motion lines are drawn beside it. */
private fun Mood.wavingHand(): Offset = when (this) {
    Mood.HELP -> Offset(0.08f, 0.44f)
    else -> Offset(0.92f, 0.48f)
}

/** How the whole body moves at time [t] (cycles elapsed). Pivots at his feet; never leaves the ground. */
fun GraphicsLayerScope.applyMotion(motion: MascotMotion, t: Float) {
    transformOrigin = TransformOrigin(0.5f, 1f)
    if (t <= 0f || t >= motion.cycles) return
    val phase = t * 2f * PI.toFloat()
    when (motion) {
        MascotMotion.WAVE -> rotationZ = sin(phase) * 7f
        MascotMotion.CRY -> translationX = sin(phase * 6f) * size.width * 0.006f // a small sniffle shiver
        MascotMotion.CHEER -> rotationZ = sin(phase * 2f) * 4f // happy rocking
        MascotMotion.SHOCK -> {
            val decay = (1f - t * 2.5f).coerceIn(0f, 1f)
            translationX = sin(phase * 8f) * size.width * 0.03f * decay
        }
        MascotMotion.THINK -> rotationZ = -6f * sin(phase / 2f) // tilt over and back
        MascotMotion.NONE -> Unit
    }
}

// Tiny pixel-art stamps ('#' = filled), drawn in the sprite's own chunky pixel size.
private val TEAR = listOf(".#.", "###", "###", ".#.")
private val SPARKLE = listOf(".#.", "###", ".#.")
private val BANG = listOf("##", "##", "##", "##", "..", "##")
private val ARC_RIGHT = listOf("#.", ".#", ".#", "#.")
private val ARC_LEFT = listOf(".#", "#.", "#.", ".#")

private val TearBlue = Color(0xFF7FD3FF)
private val SparkGold = Color(0xFFFFD45C)
private val BangRed = Color(0xFFFF5A4E)
private val SparkCoral = Color(0xFFFF9A6C)

private fun DrawScope.stamp(pattern: List<String>, at: Offset, color: Color, alpha: Float) {
    val u = size.width / 40f // one "art pixel" of the sprites
    val origin = Offset(at.x - pattern[0].length * u / 2, at.y - pattern.size * u / 2)
    pattern.forEachIndexed { row, line ->
        line.forEachIndexed { col, c ->
            if (c == '#') {
                drawRect(color, Offset(origin.x + col * u, origin.y + row * u), Size(u, u), alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
}

private fun frac(x: Float) = x - x.toInt()

/** The pixel details for [motion] at time [t], drawn over the goblin; [detail] is a colour that shows on the page. */
fun DrawScope.drawMotion(motion: MascotMotion, mood: Mood, t: Float, detail: Color) {
    if (t <= 0f || t >= motion.cycles) return
    val w = size.width
    val h = size.height
    fun at(p: Offset) = Offset(p.x * w, p.y * h)
    val phase = t * 2f * PI.toFloat()
    when (motion) {
        MascotMotion.WAVE -> {
            val hand = at(mood.wavingHand())
            val left = mood.wavingHand().x < 0.5f
            val pulse = 0.65f + 0.35f * sin(phase * 2f)
            val dir = if (left) -1f else 1f
            val arc = if (left) ARC_LEFT else ARC_RIGHT
            stamp(arc, hand + Offset(dir * w * 0.07f, 0f), detail, pulse)
            stamp(arc, hand + Offset(dir * w * 0.13f, 0f), detail, pulse * 0.6f)
        }
        MascotMotion.CRY -> mood.tearSpots().forEachIndexed { i, spot ->
            val p = frac(t + i * 0.35f) // each tear on its own beat
            val fade = if (p < 0.75f) 1f else (1f - p) / 0.25f
            stamp(TEAR, at(spot) + Offset(0f, p * h * 0.28f), TearBlue, fade)
        }
        MascotMotion.CHEER -> {
            val spots = listOf(Offset(0.08f, 0.22f), Offset(0.90f, 0.14f), Offset(0.96f, 0.56f), Offset(0.04f, 0.62f), Offset(0.52f, 0.02f))
            spots.forEachIndexed { i, s ->
                val p = frac(t + i / spots.size.toFloat())
                stamp(SPARKLE, at(s), if (i % 2 == 0) SparkGold else SparkCoral, sin(p * PI.toFloat()))
            }
        }
        MascotMotion.SHOCK -> {
            val fade = if (t < 0.8f) 1f else (1f - t) / 0.2f
            stamp(BANG, at(Offset(0.16f, 0.06f)), BangRed, fade) // top left: the shocked pose has its own "!!" on the right
        }
        MascotMotion.THINK -> {
            val fade = if (t < 0.85f) 1f else (1f - t) / 0.15f
            listOf(Offset(0.80f, 0.14f), Offset(0.88f, 0.10f), Offset(0.96f, 0.06f)).forEachIndexed { i, d ->
                if (t > 0.15f + i * 0.2f) stamp(listOf("##", "##"), at(d), detail, fade)
            }
        }
        MascotMotion.NONE -> Unit
    }
}
