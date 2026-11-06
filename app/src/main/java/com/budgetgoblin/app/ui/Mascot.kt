package com.budgetgoblin.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgetgoblin.app.R
import com.budgetgoblin.app.data.cents
import com.budgetgoblin.app.data.total
import com.budgetgoblin.app.data.Category
import com.budgetgoblin.app.data.Transaction

/** Every pose of the Budget Goblin, named after what it means in the app. */
enum class Mood(@param:DrawableRes val res: Int, val description: String) {
    HAPPY(R.drawable.mascot_happy, "happy"),
    SAD(R.drawable.mascot_sad, "sad"),
    SHOCKED(R.drawable.mascot_shocked, "shocked"),
    CELEBRATE(R.drawable.mascot_celebrate, "celebrating"),
    ADDING(R.drawable.mascot_adding, "writing down a transaction"),
    GUARDING(R.drawable.mascot_guarding, "guarding your savings"),
    BROKE(R.drawable.mascot_broke, "showing empty pockets"),
    ALERT(R.drawable.mascot_alert, "raising a warning sign"),
    CURIOUS(R.drawable.mascot_curious, "reading a report"),
    ANXIOUS(R.drawable.mascot_anxious, "anxious about a bill"),
    PROUD(R.drawable.mascot_proud, "proud"),
    REGRETFUL(R.drawable.mascot_regretful, "regretting an impulse buy"),
    LEVEL_UP(R.drawable.mascot_level_up, "holding a trophy"),
    SPLITTING(R.drawable.mascot_splitting, "splitting a coin"),
    INVESTING(R.drawable.mascot_investing, "watering a money plant"),
    HELP(R.drawable.mascot_help, "offering help"),
}

/**
 * The goblin in a pose, acting it out with that pose's own movement and detail (see [Mood.act]),
 * always staying on the ground. The action plays when he appears or changes mood, and again when
 * tapped. Nothing moves when the system has animations turned off.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 64.dp, tappable: Boolean = true) {
    val still = rememberReducedMotion()
    val act = mood.act()
    val clock = remember { Animatable(0f) } // cycles elapsed; 0 means resting
    var plays by remember { mutableIntStateOf(0) }
    val detail = MaterialTheme.colorScheme.onSurfaceVariant // lines, dots and puffs that show in light and dark

    LaunchedEffect(mood, plays, still) {
        clock.snapTo(0f)
        if (still) return@LaunchedEffect
        clock.animateTo(act.cycles.toFloat(), tween(act.cycleMillis * act.cycles, easing = LinearEasing))
        clock.snapTo(0f)
    }

    Image(
        bitmap = ImageBitmap.imageResource(mood.res),
        contentDescription = "Budget Goblin ${mood.description}",
        filterQuality = FilterQuality.None, // keep the pixel art crisp
        modifier = modifier
            .size(size)
            // Read the clock only while drawing, so the animation never recomposes the screen.
            .graphicsLayer { applyAct(act, clock.value) }
            .drawWithContent {
                drawContent()
                drawAct(act, clock.value, detail)
            }
            // Tap to see it again. Inside a button, the tap goes to the button instead.
            .pointerInput(still, tappable) {
                if (still || !tappable) return@pointerInput
                detectTapGestures { plays++ }
            },
    )
}

/** What the goblin says about the month as a whole. */
data class Tip(val mood: Mood, val text: String)

const val SAVINGS_GOAL = 0.20 // save 20% of income

/** [transactions] are this pay period's; [budget] is its spending budget; [period] is "month", "week"... */
fun overallTip(transactions: List<Transaction>, budget: Double, period: String = "month"): Tip {
    if (transactions.isEmpty()) return Tip(Mood.HELP, "Hi, I'm your Budget Goblin! Tap + to log your first transaction.")
    val income = transactions.filter { it.isIncome }.total()
    val spent = transactions.filterNot { it.isIncome }.total()
    if (spent >= budget) return Tip(Mood.BROKE, "Pockets are empty! This $period's budget is all used up.")
    if (income > 0 && income - spent <= 0) return Tip(Mood.BROKE, "Spending has caught up with income.")
    val usage = Category.entries.associateWith { c -> transactions.filter { it.category == c }.total() / c.monthlyLimit }
    usage.entries.firstOrNull { it.value > 1 }?.let { return Tip(Mood.SAD, "We went over on ${it.key.label}. Let's ease off a bit.") }
    usage.entries.firstOrNull { it.value >= 0.85 }?.let { return Tip(Mood.ALERT, "Careful! ${it.key.label} is almost maxed out.") }
    if (spent >= budget * 0.85) return Tip(Mood.ALERT, "Careful! Only ${((1 - spent / budget) * 100).toInt()}% of this $period's budget is left.")
    if (income == 0.0) return Tip(Mood.HAPPY, "All budgets on track. Keep it up!")
    val rate = (income - spent) / income
    return when {
        rate >= 0.5 -> Tip(Mood.CELEBRATE, "Half your income saved. Treasure secured!")
        rate >= SAVINGS_GOAL -> Tip(Mood.PROUD, "Good habits! You're saving ${(rate * 100).toInt()}% this $period.")
        else -> Tip(Mood.HAPPY, "All budgets on track. Keep it up!")
    }
}

fun categoryMood(spent: Double, limit: Double): Mood {
    val used = spent / limit
    return when {
        used > 1 -> Mood.SAD
        used >= 0.85 -> Mood.ALERT
        used >= 0.6 -> Mood.ANXIOUS
        used <= 0.3 -> Mood.PROUD
        else -> Mood.HAPPY
    }
}

/** The goblin's reaction to [t], just added to this month's [before]. */
fun reactionTo(t: Transaction, before: List<Transaction>, budget: Double, period: String = "month"): Tip {
    val after = before + t
    fun savingsRate(list: List<Transaction>): Double {
        val income = list.filter { it.isIncome }.total()
        return if (income == 0.0) 0.0 else (income - list.filterNot { it.isIncome }.total()) / income
    }
    if (savingsRate(before) < SAVINGS_GOAL && savingsRate(after) >= SAVINGS_GOAL) {
        return Tip(Mood.LEVEL_UP, "Savings goal reached! Level up!")
    }
    if (t.isIncome && t.toBudget) return Tip(Mood.GUARDING, "Added to this $period's budget. More room to spend!")
    if (t.isIncome) return Tip(Mood.INVESTING, "Money in! Watch it grow.")
    val category = t.category ?: return Tip(Mood.HAPPY, "Logged it!")
    val spentBefore = before.filter { it.category == category }.total()
    val totalBefore = before.filterNot { it.isIncome }.total()
    if (totalBefore < budget && cents(totalBefore + t.amount) >= budget) {
        return Tip(Mood.BROKE, "That's this $period's whole budget gone…")
    }
    return when {
        spentBefore <= category.monthlyLimit && cents(spentBefore + t.amount) > category.monthlyLimit ->
            Tip(Mood.SAD, "That pushed ${category.label} over budget…")
        t.amount >= category.monthlyLimit * 0.3 -> Tip(Mood.SHOCKED, "Whoa, that's a big one!")
        category == Category.FUN && t.amount >= 40 -> Tip(Mood.REGRETFUL, "Hmm… was that an impulse buy?")
        else -> Tip(Mood.HAPPY, "Logged it!")
    }
}
