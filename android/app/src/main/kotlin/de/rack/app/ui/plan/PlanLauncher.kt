package de.rack.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.ui.logging.setCount
import de.rack.app.ui.theme.RecompBadge
import de.rack.app.ui.theme.RecompBadgeStyle
import de.rack.app.ui.theme.RecompDayRow
import de.rack.app.ui.theme.RecompDayRowData
import de.rack.app.ui.theme.RecompMoreRow
import de.rack.app.ui.theme.RecompPreviewRow
import de.rack.app.ui.theme.RecompPrimaryButton
import de.rack.app.ui.theme.RecompStat
import de.rack.app.ui.theme.RecompStatStrip
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.agentHighlight
import de.rack.app.ui.theme.recompClick
import de.rack.app.ui.theme.tagColor

// How many of today's exercises preview in the hero before the "+ N weitere" overflow line.
private const val HERO_PREVIEW_LIMIT = 4

/**
 * Today's training day rendered as the launcher hero (kit `.card` + `.day-head`): a
 * tag-colored number chip, title, focus, and a "Heute" badge, a summary stat strip
 * (Übungen / Sätze), a tap-to-open exercise preview, and the block "Session starten"
 * CTA that opens the session player for [dayContent]. [highlighted] glows the head band
 * when an agent just touched the day.
 */
@Composable
fun TodayHeroCard(
    dayContent: DayContent,
    highlighted: Boolean,
    onStartSession: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val day = dayContent.day
    val exercises = dayContent.groups.flatMap { it.exercises }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        HeroHead(day = day, highlighted = highlighted)
        RecompStatStrip(
            stats = heroStats(exercises),
            modifier = Modifier.padding(horizontal = spacing.cardInsetH, vertical = spacing.md),
        )
        HeroPreview(exercises = exercises, onOpenExercise = onOpenExercise)
        Box(modifier = Modifier.padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV)) {
            RecompPrimaryButton(text = "Session starten", onClick = onStartSession, fillMaxWidth = true)
        }
    }
}

/**
 * The selected plan's days as a single bordered card of compact, tappable launcher rows
 * (kit `.day-row`). Tapping a row starts that day's session; the first day carries the
 * "Heute" badge. [highlightedIds] glows the row an agent just touched.
 */
@Composable
fun PlanDayList(
    days: List<DayContent>,
    highlightedIds: Set<String>,
    onStartSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        days.forEachIndexed { index, dayContent ->
            PlanDayRow(
                dayContent = dayContent,
                isToday = index == 0,
                highlighted = dayContent.day.id in highlightedIds,
                onClick = { onStartSession(dayContent.day.id) },
                showDivider = index < days.lastIndex,
            )
        }
    }
}

@Composable
private fun HeroHead(
    day: PlanDay,
    highlighted: Boolean,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val accent = colors.tagColor(day.tag)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panelElevated)
                .agentHighlight(highlighted = highlighted)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayNumberChip(position = day.position, accent = accent)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = (day.title ?: "DAY").uppercase(), style = type.dayTitle, color = colors.txt)
            day.focus?.takeIf { it.isNotBlank() }?.let { focus ->
                Text(text = focus.uppercase(), style = type.label, color = accent)
            }
        }
        Spacer(Modifier.width(spacing.md))
        RecompBadge(text = "Heute", style = RecompBadgeStyle.Volt)
    }
}

@Composable
private fun HeroPreview(
    exercises: List<PlanExercise>,
    onOpenExercise: (String) -> Unit,
) {
    val shown = exercises.take(HERO_PREVIEW_LIMIT)
    val rest = exercises.drop(HERO_PREVIEW_LIMIT)
    Column(modifier = Modifier.padding(horizontal = RecompTheme.spacing.cardInsetH)) {
        shown.forEachIndexed { index, exercise ->
            RecompPreviewRow(
                name = exercise.name,
                detail = exercise.target.orEmpty(),
                showDivider = index < shown.lastIndex || rest.isNotEmpty(),
                modifier = Modifier.recompClick(onClick = { onOpenExercise(exercise.exerciseId) }),
            )
        }
        if (rest.isNotEmpty()) {
            RecompMoreRow(text = "+ ${rest.size} weitere · ${rest.joinToString(", ") { it.name }}")
        }
    }
}

@Composable
private fun PlanDayRow(
    dayContent: DayContent,
    isToday: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val colors = RecompTheme.colors
    val day = dayContent.day
    val exerciseCount = dayContent.groups.sumOf { it.exercises.size }
    RecompDayRow(
        data =
            RecompDayRowData(
                number = dayNumber(day.position),
                accent = colors.tagColor(day.tag),
                title = day.title ?: "Day",
                focus = dayFocusLine(day.focus, exerciseCount),
            ),
        onClick = onClick,
        modifier = Modifier.agentHighlight(highlighted = highlighted),
        trailing = { if (isToday) RecompBadge(text = "Heute", style = RecompBadgeStyle.Volt) },
        showDivider = showDivider,
    )
}

@Composable
private fun DayNumberChip(
    position: Int,
    accent: Color,
) {
    Box(
        modifier = Modifier.size(RecompTheme.spacing.dayChip).background(accent, RecompTheme.shapes.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = dayNumber(position), style = RecompTheme.typography.loadValue, color = RecompTheme.colors.bg)
    }
}

/** Hero summary cells from the day's exercises: exercise count and total planned sets. */
private fun heroStats(exercises: List<PlanExercise>): List<RecompStat> =
    listOf(
        RecompStat(value = exercises.size.toString(), label = "Übungen"),
        RecompStat(value = exercises.sumOf { setCount(it.target) }.toString(), label = "Sätze"),
    )

/** A day-row focus line: the day focus (when set) plus its exercise count, e.g. `Druck · 6 Übungen`. */
private fun dayFocusLine(
    focus: String?,
    exerciseCount: Int,
): String {
    val count = "$exerciseCount Übungen"
    return focus?.takeIf { it.isNotBlank() }?.let { "$it · $count" } ?: count
}

/** Two-digit day label from the 1-based [position] (e.g. position 1 -> "01"). */
private fun dayNumber(position: Int): String = position.toString().padStart(2, '0')
