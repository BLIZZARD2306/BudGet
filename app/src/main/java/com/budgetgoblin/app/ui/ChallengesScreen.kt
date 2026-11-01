package com.budgetgoblin.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.data.BudgetState
import com.budgetgoblin.app.data.Challenge
import com.budgetgoblin.app.data.ChallengeStatus
import com.budgetgoblin.app.data.ChallengeType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun ChallengesScreen(state: BudgetState, onReaction: (Tip) -> Unit) {
    val scroll = rememberScrollState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick every 30s so the countdown stays current, and finish challenges that have run their course.
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

    ScreenColumn(scroll, headerHeight = 230.dp) {
        ScreenTitle("Challenges", "Small streaks, big savings")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TabPill("Active", tab == 0) { tab = 0 }
            Spacer(Modifier.width(10.dp))
            TabPill("Completed", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(20.dp))

        if (tab == 0) {
            if (active.isEmpty()) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mascot(Mood.PROUD, size = 72.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("No challenge running. Pick one below and I'll keep score!")
                    }
                }
            }
            active.forEach { c ->
                ActiveCard(c, now, onGiveUp = {
                    state.giveUp(c)
                    onReaction(Tip(Mood.REGRETFUL, "Aww… we'll get it next time."))
                })
                Spacer(Modifier.height(16.dp))
            }
            val available = ChallengeType.entries.filter { t -> active.none { it.type == t } }
            if (available.isNotEmpty()) {
                SectionTitle("Start a challenge")
                Panel(padding = 0.dp) {
                    available.forEachIndexed { i, type ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(type.emoji, fontSize = 26.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(type.title, fontWeight = FontWeight.SemiBold)
                                Text(rules(type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                state.start(type)
                                onReaction(Tip(Mood.PROUD, "${type.title} for ${type.days} days. You've got this!"))
                            }) { Text("START", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        } else {
            if (finished.isEmpty()) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mascot(Mood.CURIOUS, size = 72.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Nothing finished yet. Your trophies will show up here.")
                    }
                }
            } else {
                Panel(padding = 0.dp) {
                    finished.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        FinishedRow(c)
                    }
                }
            }
        }
    }
}

private fun rules(type: ChallengeType) =
    "Avoid ${type.category?.label?.lowercase() ?: "all"} spending for ${type.days} days"

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (selected) 4.dp else 0.dp,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    }
}

/** The kit's challenge card: title, boxed countdown digits, and "I give up". */
@Composable
private fun ActiveCard(c: Challenge, now: Long, onGiveUp: () -> Unit) {
    val left = (c.endsAt - now).coerceAtLeast(0)
    val days = TimeUnit.MILLISECONDS.toDays(left)
    val hours = TimeUnit.MILLISECONDS.toHours(left) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(left) % 60
    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(Mood.PROUD, size = 44.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${c.type.title}!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(rules(c.type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$days days, $hours hours, $minutes minutes left"
                },
            ) {
                Digits(days, "Days")
                Digits(hours, "Hours")
                Digits(minutes, "Minutes")
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { 1f - left.toFloat() / c.type.durationMillis },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            TextButton(onClick = onGiveUp) {
                Text("I GIVE UP", fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Digits(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            "%02d".format(value.coerceAtMost(99)).forEach { d ->
                Box(
                    Modifier
                        .size(width = 32.dp, height = 40.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("$d", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinishedRow(c: Challenge) {
    val won = c.status == ChallengeStatus.COMPLETED
    val date = SimpleDateFormat("MMM d").format(Date(c.endedAt ?: c.startedAt))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Mascot(if (won) Mood.LEVEL_UP else Mood.SAD, size = 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("${c.type.emoji} ${c.type.title}", fontWeight = FontWeight.SemiBold)
            Text(
                if (won) "Completed $date" else "Ended $date",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (won) "WON" else "MISSED",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (won) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
