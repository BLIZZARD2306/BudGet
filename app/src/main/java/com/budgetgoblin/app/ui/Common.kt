package com.budgetgoblin.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.ui.theme.CoralLight
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

val money: NumberFormat = NumberFormat.getCurrencyInstance()
val CardShape = RoundedCornerShape(28.dp)
val PanelShape = RoundedCornerShape(24.dp)
val monthName: String get() = SimpleDateFormat("MMMM").format(Date())

/** Pastel pairs in the style of the kit's category icons. */
val categoryColors = mapOf(
    Category.FOOD to listOf(Color(0xFFFFA48E), Color(0xFFFF7468)),
    Category.TRANSPORT to listOf(Color(0xFF8FB5FF), Color(0xFF5B83F2)),
    Category.BILLS to listOf(Color(0xFF9BA6FF), Color(0xFF6C6FE8)),
    Category.FUN to listOf(Color(0xFFFF9AD5), Color(0xFFE765B3)),
    Category.OTHER to listOf(Color(0xFFFFCB7A), Color(0xFFF7A23C)),
    Category.SHOPPING to listOf(Color(0xFFC59BFF), Color(0xFF9B6BF0)),
    Category.COFFEE to listOf(Color(0xFF6FE3EA), Color(0xFF22BFD0)),
)

fun Category.color() = categoryColors.getValue(this).last()

/**
 * The scrolling body of a tab: a beige header panel with rounded bottom corners scrolls with the
 * content, and the first cards overlap it like in the kit. Starts at the very top of the screen.
 */
@Composable
fun ScreenColumn(
    scroll: ScrollState,
    headerHeight: Dp = 190.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val header = MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .drawBehind {
                val r = CornerRadius(40.dp.toPx())
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(0f, 0f, size.width, headerHeight.toPx(), bottomLeftCornerRadius = r, bottomRightCornerRadius = r)
                    )
                }
                drawPath(path, header)
            }
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        content()
        // Room for the floating nav bar.
        Spacer(Modifier.navigationBarsPadding().height(120.dp))
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

/** A white card with the kit's soft drop shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, PanelShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, PanelShape)
            .padding(padding),
        content = content,
    )
}

/** A category's emoji on a soft gradient circle, like the kit's category icons. */
@Composable
fun CategoryBadge(category: Category, size: Dp = 44.dp, selected: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (selected) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.2f)))
                else Brush.linearGradient(categoryColors.getValue(category))
            ),
        contentAlignment = Alignment.Center,
    ) { Text(category.emoji, fontSize = (size.value * 0.45f).sp) }
}

/** The kit's coral gradient pill button. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        shadowElevation = if (enabled) 6.dp else 0.dp,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
    ) {
        Box(
            Modifier
                .background(Brush.horizontalGradient(listOf(CoralLight, primary)))
                .padding(horizontal = 32.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 13.sp)
        }
    }
}

/** The goblin's comment, in a comic-style speech bubble. */
@Composable
fun SpeechBubble(tip: Tip, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Crossfade(tip.mood, label = "mood") { Mascot(it, size = 72.dp) }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                tip.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** Pops up with the goblin's reaction, then slides away after a few seconds. */
@Composable
fun ReactionBanner(reaction: Tip?, onDone: () -> Unit, modifier: Modifier) {
    var shown by remember { mutableStateOf(reaction) }
    if (reaction != null) shown = reaction
    LaunchedEffect(reaction) {
        if (reaction != null) {
            delay(3500)
            onDone()
        }
    }
    AnimatedVisibility(
        visible = reaction != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val tip = shown ?: return@AnimatedVisibility
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Mascot(tip.mood, size = 60.dp)
                Spacer(Modifier.width(12.dp))
                Text(tip.text, color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Mascot(Mood.HELP, size = 88.dp) },
        title = { Text("Meet your Budget Goblin", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("He keeps an eye on your money and reacts as you go:")
                HelpLine(Mood.PROUD, "Proud when a budget is barely touched")
                HelpLine(Mood.ANXIOUS, "Nervous once you've used 60% of a budget")
                HelpLine(Mood.ALERT, "Waving a warning at 85%")
                HelpLine(Mood.SAD, "Sad when you go over")
                HelpLine(Mood.LEVEL_UP, "Cheering when you finish a challenge")
                Text("Tap the budget amount on Home to change it. Drag the green card to tilt it.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun HelpLine(mood: Mood, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(mood, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
