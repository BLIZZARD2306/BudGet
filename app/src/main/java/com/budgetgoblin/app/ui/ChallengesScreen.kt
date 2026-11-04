package com.budgetgoblin.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.Challenge
import com.budgetgoblin.app.data.ChallengeStatus
import com.budgetgoblin.app.data.ChallengeType
import com.budgetgoblin.app.ui.theme.CoinGold
import com.budgetgoblin.app.ui.theme.Mint
import com.budgetgoblin.app.ui.theme.lip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Horizontal offsets of the path's nodes, as a gentle zig-zag. */
private val PathOffsets = listOf(0, -64, 0, 64, 0)

@Composable
fun ChallengesScreen(state: BudgetState, onReaction: (Tip) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var open by rememberSaveable { mutableStateOf<ChallengeType?>(null) }

    // Tick every 30s so countdowns stay current, and finish challenges that have run their course.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            state.completeFinished(now).firstOrNull()?.let {
                onReaction(Tip(Mood.LEVEL_UP, "Challenge complete: ${it.type.title}!"))
            }
            delay(30_000)
        }
    }

    val active = state.activeChallenges
    val finished = state.challenges.filter { it.status != ChallengeStatus.ACTIVE }
    val next = ChallengeType.entries.firstOrNull { t -> active.none { it.type == t } && finished.none { it.type == t && it.status == ChallengeStatus.COMPLETED } }

    ScreenColumn(scroll) {
        Spacer(Modifier.height(12.dp))
        SectionBanner(
            label = "Challenges",
            title = "${state.challengesWon} won · ${active.size} running",
            color = Mint,
            actionIcon = R.drawable.px_nav_challenges,
            actionDescription = "Jump to trophies",
            onAction = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
        )
        Spacer(Modifier.height(28.dp))

        // The path: one round button per challenge, zig-zagging down the page.
        ChallengeType.entries.forEachIndexed { i, type ->
            val running = active.firstOrNull { it.type == type }
            val wins = finished.count { it.type == type && it.status == ChallengeStatus.COMPLETED }
            Box(Modifier.fillMaxWidth().height(if (type == next && running == null) 196.dp else 150.dp), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier.offset(x = PathOffsets[i % PathOffsets.size].dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (type == next && running == null) StartBubble()
                    PathNode(type, running, wins, now, onClick = { open = type })
                    Text(
                        running?.let { timeLeft(it, now) } ?: type.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                // The goblin keeps watch beside the path.
                if (i == 1) {
                    Mascot(
                        if (active.isNotEmpty()) Mood.PROUD else Mood.CURIOUS,
                        size = 96.dp,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    )
                }
            }
        }

        SectionTitle("Trophies")
        if (finished.isEmpty()) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(Mood.CURIOUS, size = 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Nothing finished yet. Your trophies will show up here.")
                }
            }
        } else {
            Panel(padding = 0.dp) {
                finished.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    FinishedRow(c)
                }
            }
        }
    }

    open?.let { type ->
        ChallengeDialog(
            type = type,
            running = active.firstOrNull { it.type == type },
            now = now,
            onStart = {
                state.start(type)
                open = null
                onReaction(Tip(Mood.PROUD, "${type.title} for ${type.days} days. You've got this!"))
            },
            onGiveUp = { c ->
                state.giveUp(c)
                open = null
                onReaction(Tip(Mood.REGRETFUL, "Aww… we'll get it next time."))
            },
            onDismiss = { open = null },
        )
    }
}

private fun rules(type: ChallengeType) =
    "Avoid ${type.category?.label?.lowercase() ?: "all"} spending for ${type.days} days"

private fun timeLeft(c: Challenge, now: Long): String {
    val left = (c.endsAt - now).coerceAtLeast(0)
    val d = TimeUnit.MILLISECONDS.toDays(left)
    val h = TimeUnit.MILLISECONDS.toHours(left) % 24
    return if (d > 0) "${d}d ${h}h left" else "${h}h ${TimeUnit.MILLISECONDS.toMinutes(left) % 60}m left"
}

/** A round chunky button on the path; a ring shows progress while the challenge runs. */
@Composable
private fun PathNode(type: ChallengeType, running: Challenge?, wins: Int, now: Long, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val face = when {
        running != null -> Mint
        wins > 0 -> CoinGold
        else -> scheme.outlineVariant
    }
    val progress = running?.let { 1f - (it.endsAt - now).coerceAtLeast(0).toFloat() / it.type.durationMillis } ?: 0f
    Box(
        Modifier
            .size(96.dp)
            .semantics {
                contentDescription = buildString {
                    append(type.title)
                    if (running != null) append(", running, ${timeLeft(running, now)}")
                    if (wins > 0) append(", won $wins times")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (running != null) {
            val track = scheme.outlineVariant
            Canvas(Modifier.size(96.dp)) {
                val stroke = 8.dp.toPx()
                drawArc(track, 0f, 360f, false, style = Stroke(stroke))
                drawArc(CoinGold, -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Chunky(
            face = face,
            edge = face.lip(),
            shape = CircleShape,
            depth = 6.dp,
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(type.icon(), contentDescription = null, tint = if (face == scheme.outlineVariant) scheme.onSurfaceVariant else Color.White, size = 30.dp)
        }
        if (wins > 0 && running == null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .background(scheme.surface, CircleShape)
                    .border(2.dp, CoinGold, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$wins", fontWeight = FontWeight.Black, fontSize = 12.sp, color = CoinGold.lip()) }
        }
    }
}

/** The "START" call-out above the suggested next challenge; it bobs to catch the eye. */
@Composable
private fun StartBubble() {
    val still = rememberReducedMotion()
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        while (true) {
            nudge.animateTo(-5f, tween(600, easing = FastOutSlowInEasing))
            nudge.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier
            .graphicsLayer { translationY = nudge.value * density }
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text("START", color = Mint, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ChallengeDialog(
    type: ChallengeType,
    running: Challenge?,
    now: Long,
    onStart: () -> Unit,
    onGiveUp: (Challenge) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PixelIcon(type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 40.dp) },
        title = { Text(type.title, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rules(type), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (running != null) {
                    val left = (running.endsAt - now).coerceAtLeast(0)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Digits(TimeUnit.MILLISECONDS.toDays(left), "Days")
                        Digits(TimeUnit.MILLISECONDS.toHours(left) % 24, "Hours")
                        Digits(TimeUnit.MILLISECONDS.toMinutes(left) % 60, "Minutes")
                    }
                    Spacer(Modifier.height(16.dp))
                    ChunkyProgress(1f - left.toFloat() / type.durationMillis, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(20.dp))
                if (running == null) {
                    PillButton("Start challenge", onClick = onStart, color = Mint, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinePillButton("I give up", onClick = { onGiveUp(running) }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", fontWeight = FontWeight.ExtraBold) } },
    )
}

@Composable
private fun Digits(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            "%02d".format(value.coerceAtMost(99)).forEach { d ->
                Box(
                    Modifier
                        .size(width = 30.dp, height = 40.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("$d", fontSize = 18.sp, fontWeight = FontWeight.Black) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinishedRow(c: Challenge) {
    val won = c.status == ChallengeStatus.COMPLETED
    val date = SimpleDateFormat("MMM d").format(Date(c.endedAt ?: c.startedAt))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Mascot(if (won) Mood.LEVEL_UP else Mood.SAD, size = 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(c.type.title, fontWeight = FontWeight.ExtraBold)
            Text(
                if (won) "Completed $date" else "Ended $date",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (won) "WON" else "MISSED",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = if (won) Mint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
