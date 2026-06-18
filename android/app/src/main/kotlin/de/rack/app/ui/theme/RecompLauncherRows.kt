package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Kit launcher type sizes that sit between the named scale steps.
private val DayTitleSize = 15.sp
private val DayFocusSize = 11.sp
private val PreviewDetailSize = 12.sp
private val MoreSize = 11.sp

/**
 * The data for a launcher plan-day row ([RecompDayRow]): the day [number] shown in an
 * [accent]-filled chip, the [title], and the [focus] line (tinted with the same [accent]).
 * Derive [accent] from [tagColor] so push/pull/legs days color-code consistently.
 */
data class RecompDayRowData(
    val number: String,
    val accent: Color,
    val title: String,
    val focus: String,
)

/**
 * A plan-day launcher row (kit `.day-row`): an accent day-number chip, the day title over its
 * focus line, and a [trailing] slot (e.g. a "Heute" badge or a "zuletzt …" stamp). Tappable to
 * open the day. [showDivider] draws the 1px separator below — set false on the last row.
 */
@Composable
fun RecompDayRow(
    data: RecompDayRowData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    showDivider: Boolean = true,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth().recompClick(
                    onClick = onClick
                ).padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier.size(spacing.dayChip).background(data.accent, RecompTheme.shapes.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = data.number, style = type.loadValue, color = colors.bg)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(text = data.title, style = type.exerciseName.copy(fontSize = DayTitleSize), color = colors.txt)
                Text(
                    text = data.focus.uppercase(),
                    style = type.history.copy(fontSize = DayFocusSize, letterSpacing = 0.04.em),
                    color = data.accent,
                )
            }
            trailing()
        }
        if (showDivider) {
            RecompDivider()
        }
    }
}

/**
 * A today-hero exercise preview line (kit `.prev-row`): the exercise [name] on the left and its
 * target [detail] (e.g. "4 × 5–8") on the right. [showDivider] draws the separator below.
 */
@Composable
fun RecompPreviewRow(
    name: String,
    detail: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = name, style = type.body, color = colors.txt, modifier = Modifier.weight(1f))
            Text(text = detail, style = type.history.copy(fontSize = PreviewDetailSize), color = colors.dim)
        }
        if (showDivider) {
            RecompDivider()
        }
    }
}

/**
 * The "+ N weitere …" overflow line under a preview list (kit `.more`): a tracked, uppercased
 * volt-dim mono label summarizing the exercises not shown.
 */
@Composable
fun RecompMoreRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = RecompTheme.typography.label.copy(fontSize = MoreSize, fontWeight = FontWeight.Normal),
        color = RecompTheme.colors.voltDim,
        modifier = modifier.padding(top = RecompTheme.spacing.sm),
    )
}
