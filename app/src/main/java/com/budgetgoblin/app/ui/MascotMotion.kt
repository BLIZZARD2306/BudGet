package com.budgetgoblin.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * How the goblin's whole body moves. He always stays on the ground: everything pivots at his feet
 * and nothing lifts him up.
 */
enum class Body { SWAY, BECKON, SOB, SHUFFLE, DROOP, STARTLE, FLAG, SHIVER, TILT, PULSE, STAND_TALL, REACH, HUG, NOD, CHOP, STILL }

/** The pixel-art detail drawn around him, at [at] (fractions of the sprite). */
sealed interface Effect {
    data class Lines(val at: Offset) : Effect
    data class Ping(val at: Offset) : Effect
    data class Tears(val from: List<Offset>) : Effect
    data class Dust(val from: Offset) : Effect
    data class Sigh(val eye: Offset, val mouth: Offset) : Effect
    data class Bang(val at: Offset) : Effect
    data class Rays(val at: Offset) : Effect
    data class Sweat(val from: Offset) : Effect
    data class Dots(val at: Offset) : Effect
    data object Confetti : Effect
    data object Twinkle : Effect
    data class Glint(val at: Offset) : Effect
    data class Heart(val from: Offset) : Effect
    data class Scribble(val at: Offset) : Effect
    data class Flash(val at: Offset) : Effect
    data class Water(val spout: Offset, val sprout: Offset) : Effect
}

/** One pose's action: a body movement plus a detail, played [cycles] times. */
data class Act(val body: Body, val effect: Effect, val cycleMillis: Int, val cycles: Int)

/** Each pose acts out its own mood. Positions match the rebuilt sprites. */
fun Mood.act(): Act = when (this) {
    Mood.HAPPY -> Act(Body.SWAY, Effect.Lines(Offset(0.93f, 0.40f)), 900, 3)
    Mood.HELP -> Act(Body.BECKON, Effect.Ping(Offset(0.80f, 0.17f)), 1000, 3)
    Mood.SAD -> Act(Body.SOB, Effect.Tears(listOf(Offset(0.38f, 0.52f), Offset(0.68f, 0.52f))), 1100, 4)
    Mood.BROKE -> Act(Body.SHUFFLE, Effect.Dust(Offset(0.27f, 0.74f)), 1000, 3)
    Mood.REGRETFUL -> Act(Body.DROOP, Effect.Sigh(Offset(0.56f, 0.52f), Offset(0.42f, 0.57f)), 3200, 1)
    Mood.SHOCKED -> Act(Body.STARTLE, Effect.Bang(Offset(0.16f, 0.08f)), 1400, 1)
    Mood.ALERT -> Act(Body.FLAG, Effect.Rays(Offset(0.86f, 0.42f)), 900, 3)
    Mood.ANXIOUS -> Act(Body.SHIVER, Effect.Sweat(Offset(0.30f, 0.34f)), 1200, 3)
    Mood.CURIOUS -> Act(Body.TILT, Effect.Dots(Offset(0.86f, 0.18f)), 2400, 1)
    Mood.CELEBRATE -> Act(Body.PULSE, Effect.Confetti, 900, 4)
    Mood.PROUD -> Act(Body.STAND_TALL, Effect.Twinkle, 2600, 1)
    Mood.LEVEL_UP -> Act(Body.REACH, Effect.Glint(Offset(0.84f, 0.24f)), 2400, 1)
    Mood.GUARDING -> Act(Body.HUG, Effect.Heart(Offset(0.76f, 0.58f)), 1100, 3)
    Mood.ADDING -> Act(Body.NOD, Effect.Scribble(Offset(0.48f, 0.80f)), 2000, 1)
    Mood.SPLITTING -> Act(Body.CHOP, Effect.Flash(Offset(0.84f, 0.66f)), 1000, 3)
    Mood.INVESTING -> Act(Body.STILL, Effect.Water(Offset(0.37f, 0.80f), Offset(0.33f, 0.88f)), 1100, 4)
}

private fun frac(x: Float) = x - x.toInt()
private fun bump(x: Float) = sin(x.coerceIn(0f, 1f) * PI.toFloat()) // 0 -> 1 -> 0

/** Moves his body for [act] at time [t] (cycles elapsed). */
fun GraphicsLayerScope.applyAct(act: Act, t: Float) {
    transformOrigin = TransformOrigin(0.5f, 1f)
    if (t <= 0f || t >= act.cycles) return
    val phase = t * 2f * PI.toFloat()
    val f = frac(t)
    val whole = t / act.cycles // 0..1 over the whole action
    val art = size.width / 46f
    when (act.body) {
        Body.SWAY -> rotationZ = sin(phase) * 6f
        Body.BECKON -> rotationZ = 5f * maxOf(0f, sin(phase))
        Body.SOB -> {
            val s = 0.5f + 0.5f * sin(phase * 2f)
            scaleY = 1f - 0.04f * s
            scaleX = 1f + 0.02f * s
        }
        Body.SHUFFLE -> translationX = if (sin(phase) > 0f) art else -art
        Body.DROOP -> rotationZ = -5f * bump(whole)
        Body.STARTLE -> translationX = sin(phase * 8f) * size.width * 0.03f * (1f - whole * 2.5f).coerceIn(0f, 1f)
        Body.FLAG -> rotationZ = sin(phase * 3f) * 3f
        Body.SHIVER -> translationX = sin(phase * 8f) * size.width * 0.008f
        Body.TILT -> rotationZ = -6f * bump(whole)
        Body.PULSE -> {
            val s = 1f + 0.05f * maxOf(0f, sin(phase))
            scaleX = s
            scaleY = s
        }
        Body.STAND_TALL -> scaleY = 1f + 0.04f * bump(whole)
        Body.REACH -> rotationZ = 4f * bump(whole)
        Body.HUG -> {
            val s = maxOf(0f, sin(phase))
            scaleX = 1f - 0.035f * s
            scaleY = 1f + 0.02f * s
        }
        Body.NOD -> rotationZ = -2f * (0.5f + 0.5f * sin(phase * 4f))
        Body.CHOP -> rotationZ = when {
            f < 0.15f -> -4f * f / 0.15f
            f < 0.3f -> -4f * (1f - (f - 0.15f) / 0.15f)
            else -> 0f
        }
        Body.STILL -> Unit
    }
}

// Tiny pixel stamps ('#' filled), drawn at the sprite's own pixel size.
private val TEAR = listOf(".#.", "###", ".#.")
private val SPARKLE = listOf(".#.", "###", ".#.")
private val STAR = listOf("..#..", "..#..", "#####", "..#..", "..#..")
private val BANG = listOf("##", "##", "##", "##", "..", "##")
private val ARC = listOf("#.", ".#", ".#", "#.")
private val HEART = listOf(".#.#.", "#####", "#####", ".###.", "..#..")
private val CLOUD = listOf(".##.", "####", ".##.")
private val DOT = listOf("##", "##")

private val TearBlue = Color(0xFF7FD3FF)
private val Gold = Color(0xFFFFD45C)
private val Coral = Color(0xFFFF9A6C)
private val Red = Color(0xFFFF5A4E)
private val Pink = Color(0xFFFF7FA8)
private val Dust = Color(0xFFB9B2A6)
private val Leaf = Color(0xFF6CCB5F)
private val Confetti = listOf(Gold, Coral, Leaf, TearBlue, Pink)

private fun DrawScope.stamp(pattern: List<String>, at: Offset, color: Color, alpha: Float) {
    val u = size.width / 46f // one art pixel of the sprites
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0f) return
    val origin = Offset(at.x - pattern[0].length * u / 2, at.y - pattern.size * u / 2)
    pattern.forEachIndexed { row, line ->
        line.forEachIndexed { col, c ->
            if (c == '#') drawRect(color, Offset(origin.x + col * u, origin.y + row * u), Size(u, u), alpha = a)
        }
    }
}

/** Draws [act]'s detail at time [t]; [detail] is a colour that shows on the page (light or dark). */
fun DrawScope.drawAct(act: Act, t: Float, detail: Color) {
    if (t <= 0f || t >= act.cycles) return
    val w = size.width
    val h = size.height
    val u = w / 46f
    fun at(p: Offset) = Offset(p.x * w, p.y * h)
    val phase = t * 2f * PI.toFloat()
    val whole = t / act.cycles
    val fadeOut = if (whole < 0.85f) 1f else (1f - whole) / 0.15f
    when (val e = act.effect) {
        is Effect.Lines -> {
            val pulse = 0.65f + 0.35f * sin(phase * 2f)
            stamp(ARC, at(e.at) + Offset(w * 0.04f, 0f), detail, pulse)
            stamp(ARC, at(e.at) + Offset(w * 0.10f, 0f), detail, pulse * 0.6f)
        }
        is Effect.Ping -> listOf(Offset(-0.12f, 0f), Offset(0.12f, 0f), Offset(0f, -0.12f)).forEachIndexed { i, d ->
            val on = frac(t + i / 3f) < 0.5f
            if (on) stamp(DOT, at(e.at) + Offset(d.x * w, d.y * h), Gold, 1f)
        }
        is Effect.Tears -> e.from.forEachIndexed { i, spot ->
            val p = frac(t + i * 0.4f)
            stamp(TEAR, at(spot) + Offset(0f, p * h * 0.26f), TearBlue, if (p < 0.75f) 1f else (1f - p) / 0.25f)
        }
        is Effect.Dust -> repeat(3) { i ->
            val p = frac(t + i / 3f)
            stamp(DOT, at(e.from) + Offset((i - 1) * u * 3, -p * h * 0.22f), Dust, 1f - p)
        }
        is Effect.Sigh -> {
            stamp(TEAR, at(e.eye) + Offset(0f, whole * h * 0.22f), TearBlue, fadeOut)
            if (whole > 0.3f) {
                val p = (whole - 0.3f) / 0.7f
                stamp(CLOUD, at(e.mouth) + Offset(-p * w * 0.18f, -p * h * 0.06f), detail, 1f - p)
            }
        }
        is Effect.Bang -> stamp(BANG, at(e.at), Red, fadeOut)
        is Effect.Rays -> if (frac(t * 2f) < 0.5f) {
            stamp(listOf("##"), at(e.at) + Offset(0f, -h * 0.14f), Red, 1f)
            stamp(listOf("#", "#"), at(e.at) + Offset(w * 0.12f, 0f), Red, 1f)
            stamp(listOf("#.", ".#"), at(e.at) + Offset(w * 0.10f, -h * 0.10f), Red, 1f)
        }
        is Effect.Sweat -> {
            val p = frac(t)
            stamp(TEAR, at(e.from) + Offset(-p * w * 0.03f, p * h * 0.18f), TearBlue, if (p < 0.7f) 1f else (1f - p) / 0.3f)
        }
        is Effect.Dots -> listOf(Offset(0f, 0.06f), Offset(0.06f, 0.02f), Offset(0.12f, -0.02f)).forEachIndexed { i, d ->
            if (whole > 0.12f + i * 0.18f) stamp(DOT, at(e.at) + Offset(d.x * w, d.y * h), detail, fadeOut)
        }
        Effect.Confetti -> repeat(10) { i ->
            val p = frac(t * 0.6f + i * 0.137f)
            val x = (0.08f + (i * 0.37f) % 0.86f) * w + sin((p + i) * 6f) * u * 2
            stamp(if (i % 3 == 0) listOf("##") else listOf("#", "#"), Offset(x, p * h * 0.9f), Confetti[i % Confetti.size], fadeOut)
        }
        Effect.Twinkle -> listOf(Offset(0.08f, 0.30f), Offset(0.92f, 0.20f), Offset(0.95f, 0.62f), Offset(0.05f, 0.66f)).forEachIndexed { i, s ->
            stamp(SPARKLE, at(s), if (i % 2 == 0) Gold else Coral, bump(frac(whole * 2f + i * 0.25f)))
        }
        is Effect.Glint -> {
            val g = bump(frac(whole * 2f))
            stamp(if (g > 0.6f) STAR else SPARKLE, at(e.at), Color.White, g)
        }
        is Effect.Heart -> {
            val p = frac(t)
            stamp(HEART, at(e.from) + Offset(0f, -p * h * 0.30f), Pink, if (p < 0.6f) 1f else (1f - p) / 0.4f)
        }
        is Effect.Scribble -> repeat(3) { i ->
            if (whole > 0.15f + i * 0.22f) stamp(listOf("###"), at(e.at) + Offset(0f, (i - 1) * u * 2), Color(0xFF3B3D4A), fadeOut)
        }
        is Effect.Flash -> {
            val f = frac(t)
            if (f in 0.12f..0.4f) stamp(STAR, at(e.at), Color.White, 1f - abs(f - 0.26f) / 0.14f)
        }
        is Effect.Water -> {
            repeat(2) { i ->
                val p = frac(t + i * 0.5f)
                val pos = Offset(
                    at(e.spout).x + (at(e.sprout).x - at(e.spout).x) * p,
                    at(e.spout).y + (at(e.sprout).y - at(e.spout).y) * p,
                )
                stamp(listOf("#", "#"), pos, TearBlue, 1f - p * 0.5f)
            }
            stamp(SPARKLE, at(e.sprout) + Offset(0f, -h * 0.08f), Leaf, bump(whole) * 0.9f)
        }
    }
}
