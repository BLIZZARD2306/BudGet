package com.budgetgoblin.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.ui.theme.CoinGold
import com.budgetgoblin.app.ui.theme.lip
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

val money: NumberFormat = NumberFormat.getCurrencyInstance()
val CardShape = RoundedCornerShape(24.dp)
val PanelShape = RoundedCornerShape(16.dp)
val monthName: String get() = SimpleDateFormat("MMMM").format(Date())

/** Category colours: flat and bright, like the kit's icons. Pairs give the stacking cards a gentle gradient. */
val categoryColors = mapOf(
    Category.FOOD to listOf(Color(0xFFFF9A84), Color(0xFFFF6F61)),
    Category.TRANSPORT to listOf(Color(0xFF7FB2FF), Color(0xFF4F86F0)),
    Category.BILLS to listOf(Color(0xFFA19BFF), Color(0xFF7468E8)),
    Category.FUN to listOf(Color(0xFFFF95D2), Color(0xFFE35FAE)),
    Category.OTHER to listOf(Color(0xFFFFC56E), Color(0xFFF5A13A)),
    Category.SHOPPING to listOf(Color(0xFFC79BFF), Color(0xFF9D68F0)),
    Category.COFFEE to listOf(Color(0xFF5FDDE6), Color(0xFF1FBFCF)),
)

fun Category.color() = categoryColors.getValue(this).last()

/**
 * The Duolingo-style building block: a flat [face] sitting on a darker [edge] that shows [depth] below it.
 * Pressing pushes the face down onto the edge. With [border] > 0 the edge also outlines the face (cards, tiles).
 */
@Composable
fun Chunky(
    face: Color,
    edge: Color,
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    depth: Dp = 4.dp,
    border: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateDpAsState(if (pressed && enabled && onClick != null) depth else 0.dp, tween(60), label = "press")
    Box(
        modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
                } else Modifier
            )
            .drawBehind {
                val faceSize = Size(size.width, size.height - depth.toPx())
                translate(top = depth.toPx()) { drawOutline(shape.createOutline(faceSize, layoutDirection, this), edge) }
            }
            .padding(bottom = depth)
            .graphicsLayer { translationY = press.toPx() }
            .background(face, shape)
            .then(if (border > 0.dp) Modifier.border(border, edge, shape) else Modifier),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** The page body of a tab. Starts at the very top of the screen (StackingCards measures from there). */
@Composable
fun ScreenColumn(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        content()
        // Room for the nav bar.
        Spacer(Modifier.navigationBarsPadding().height(112.dp))
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

/** A flat card: 2dp outline with a thicker bottom edge, no shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Chunky(
        face = MaterialTheme.colorScheme.surface,
        edge = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier.fillMaxWidth(),
        depth = 4.dp,
        border = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(padding), content = content)
    }
}

/** A pickable tile; [selected] turns its outline and fill coral. */
@Composable
fun Tile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Chunky(
        face = if (selected) scheme.primaryContainer else scheme.surface,
        edge = if (selected) scheme.primary else scheme.outlineVariant,
        modifier = modifier.semantics { this.selected = selected },
        border = 2.dp,
        onClick = onClick,
        role = Role.RadioButton,
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A category's emoji on a flat coloured circle. */
@Composable
fun CategoryBadge(category: Category, size: Dp = 44.dp, selected: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .background(if (selected) Color.White.copy(alpha = 0.3f) else category.color(), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(category.emoji, fontSize = (size.value * 0.45f).sp) }
}

/** The big chunky button: uppercase, heavy, with a darker lip that presses down. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val scheme = MaterialTheme.colorScheme
    val face = if (enabled) color else scheme.outlineVariant
    Chunky(
        face = face,
        edge = if (enabled) color.lip() else scheme.outlineVariant.lip(),
        modifier = modifier.height(56.dp),
        onClick = onClick,
        enabled = enabled,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = if (enabled) Color.White else scheme.onSurfaceVariant,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** The secondary chunky button: outlined, coloured text. */
@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Chunky(
        face = scheme.surface,
        edge = scheme.outlineVariant,
        modifier = modifier.height(56.dp),
        border = 2.dp,
        onClick = onClick,
        enabled = enabled,
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text.uppercase(),
                color = if (enabled) scheme.primary else scheme.onSurfaceVariant,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                fontSize = 15.sp,
            )
        }
    }
}

/** A thick rounded progress bar with a light highlight stripe on the fill. */
@Composable
fun ChunkyProgress(progress: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.outlineVariant) {
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) {
            val fillW = w.coerceAtLeast(size.height)
            drawRoundRect(color, size = Size(fillW, size.height), cornerRadius = r)
            val inset = size.height / 2
            if (fillW > inset * 2) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(inset, size.height * 0.22f),
                    size = Size(fillW - inset * 2, size.height * 0.22f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.11f),
                )
            }
        }
    }
}

/**
 * The page's headline banner, like a Duolingo unit header: a coloured chunky bar with a small
 * caps label, a big line of text, and an action button behind a divider.
 */
@Composable
fun SectionBanner(
    label: String,
    title: String,
    color: Color,
    actionIcon: Int,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Chunky(face = color, edge = color.lip(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(84.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                Text(label.uppercase(), color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.8.sp)
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.semantics { heading() })
            }
            Box(Modifier.width(2.dp).fillMaxHeight().background(color.lip()))
            Box(
                Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .clickable(onClickLabel = actionDescription, onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(actionIcon), contentDescription = actionDescription, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** One item in the top stat row: a small icon (emoji) and a bold coloured number. */
@Composable
fun StatItem(icon: String, value: String, color: Color, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(6.dp))
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

/** The goblin's comment, in an outlined speech bubble with a tail pointing at him. */
@Composable
fun SpeechBubble(tip: Tip, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Crossfade(tip.mood, label = "mood") { Mascot(it, size = 84.dp) }
        Canvas(Modifier.size(width = 12.dp, height = 20.dp)) {
            val tail = Path().apply {
                moveTo(size.width + 2f, 0f)
                lineTo(0f, size.height / 2)
                lineTo(size.width + 2f, size.height)
                close()
            }
            drawPath(tail, scheme.surface)
            drawPath(tail, scheme.outlineVariant, style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier
                .weight(1f)
                .border(2.dp, scheme.outlineVariant, PanelShape)
                .background(scheme.surface, PanelShape)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(tip.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private enum class Tone(val title: String) { GOOD("Nice!"), WARN("Heads up!"), BAD("Uh oh…") }

private fun Mood.tone() = when (this) {
    Mood.SAD, Mood.BROKE, Mood.REGRETFUL -> Tone.BAD
    Mood.ALERT, Mood.ANXIOUS, Mood.SHOCKED -> Tone.WARN
    else -> Tone.GOOD
}

/**
 * Duolingo-style feedback panel: slides up from the bottom tinted green, gold or coral with the
 * goblin's reaction and a CONTINUE button. Dismisses itself after a few seconds too.
 */
@Composable
fun ReactionBanner(reaction: Tip?, onDone: () -> Unit, modifier: Modifier) {
    var shown by remember { mutableStateOf(reaction) }
    if (reaction != null) shown = reaction
    LaunchedEffect(reaction) {
        if (reaction != null) {
            delay(5000)
            onDone()
        }
    }
    AnimatedVisibility(
        visible = reaction != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        val tip = shown ?: return@AnimatedVisibility
        val tone = tip.mood.tone()
        val accent = when (tone) {
            Tone.GOOD -> MaterialTheme.colorScheme.tertiary
            Tone.WARN -> CoinGold
            Tone.BAD -> MaterialTheme.colorScheme.primary
        }
        // Darker text on the light tint, brighter text on the dark one.
        val ink = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) accent else accent.lip()
        Column(
            Modifier
                .fillMaxWidth()
                .background(lerp(MaterialTheme.colorScheme.surface, accent, 0.16f))
                .navigationBarsPadding()
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(tip.mood, size = 72.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tone.title, color = ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(tip.text, color = ink, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton("Continue", onClick = onDone, color = accent, modifier = Modifier.fillMaxWidth())
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
                Text("Tap the pencil on the green banner to change your budget. Tap the goblin and see what happens.")
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
