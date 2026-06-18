package de.rack.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import de.rack.app.domain.SessionSummary
import de.rack.app.domain.VolumeTotals
import de.rack.app.domain.WeeklyVolume
import de.rack.app.ui.theme.RecompBadge
import de.rack.app.ui.theme.RecompBadgeStyle
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.recompClick
import de.rack.app.ui.theme.tagColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * The Home overview content sections: the current-week
 * "Wochenvolumen" card (per-muscle and per-tag Vico charts under a total-volume badge),
 * the training-streak hero card, and the recent-sessions list. Purely presentational
 * helpers consumed by HomeScreen; no Supabase access or business logic here.
 */

// Streak hero numerals (kit `.mono` 42px) — the dashboard's largest metric voice.
private val StreakValueSize = 42.sp
private val SessionTitleSize = 15.sp
private val ChevronSize = 18.sp
private const val THOUSANDS_GROUP = 3
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

@Composable
internal fun WeekVolumeSection(
    weekly: WeeklyVolume,
    totals: VolumeTotals,
) {
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SectionKicker(text = "Diese Woche")
        if (totals.workingSets == 0) {
            RecompEmpty(text = "Noch keine Sätze diese Woche.")
        } else {
            HomeCard {
                HomeCardHead(label = "Wochenvolumen") {
                    RecompBadge(text = "${formatVolume(totals.volume)} kg", style = RecompBadgeStyle.Volt)
                }
                RecompDivider()
                HomeChartBlock(caption = "Pro Muskel") {
                    VolumeColumnChart(bars = muscleBars(weekly.perMuscle), modifier = Modifier.fillMaxWidth())
                }
                RecompDivider()
                HomeChartBlock(caption = "Pro Tag") {
                    VolumeColumnChart(bars = tagBars(weekly.perTag), modifier = Modifier.fillMaxWidth())
                }
                RecompDivider()
                TagLegend()
            }
        }
    }
}

@Composable
internal fun StreakSection(
    current: Int,
    longest: Int,
) {
    val colors = RecompTheme.colors
    HomeCard {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            StreakCell(
                value = current.toString(),
                caption = "Aktuell · Wochen",
                color = colors.volt,
                modifier = Modifier.weight(1f),
            )
            VerticalLine()
            StreakCell(
                value = longest.toString(),
                caption = "Längste Serie",
                color = colors.txt,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VerticalLine() {
    Box(
        modifier =
            Modifier
                .width(RecompTheme.spacing.border)
                .fillMaxHeight()
                .background(RecompTheme.colors.line),
    )
}

@Composable
private fun StreakCell(
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = modifier.padding(horizontal = spacing.sm, vertical = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = value,
            style = type.loadValue.copy(fontSize = StreakValueSize, lineHeight = StreakValueSize),
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(text = caption.uppercase(), style = type.caption, color = colors.dim, textAlign = TextAlign.Center)
    }
}

/** The recent-sessions card: hairline-divided session rows (kit `.card` + `.row`). */
@Composable
internal fun SessionsCard(
    sessions: List<SessionSummary>,
    onOpen: (LocalDate) -> Unit,
) {
    HomeCard {
        sessions.forEachIndexed { index, session ->
            if (index > 0) RecompDivider()
            SessionRow(session = session, onOpen = onOpen)
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onOpen: (LocalDate) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .recompClick(onClick = { onOpen(session.date) })
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(text = session.date.format(DAY_MONTH), style = type.history, color = colors.dim)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(text = "·", style = type.exerciseName.copy(fontSize = SessionTitleSize), color = colors.txt)
                Text(
                    text = sessionTitle(session),
                    style = type.exerciseName.copy(fontSize = SessionTitleSize),
                    color = colors.tagColor(session.tag),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(text = "${formatVolume(session.volume)} kg", style = type.history, color = colors.dim)
            Text(text = "›", style = type.loadValue.copy(fontSize = ChevronSize), color = colors.voltDim)
        }
    }
}

@Composable
internal fun SectionKicker(text: String) {
    Text(text = text.uppercase(), style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt)
}

/** A muted section title (kit `.section-title`): a dim uppercase mono label. */
@Composable
internal fun SectionTitle(text: String) {
    Text(text = text.uppercase(), style = RecompTheme.typography.label, color = RecompTheme.colors.dim)
}

/** The session's day title, falling back to its tag, then to a generic label. */
private fun sessionTitle(session: SessionSummary): String =
    session.title?.takeIf { it.isNotBlank() }
        ?: session.tag?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        ?: "Einheit"

/** Whole-kilogram volume with space-grouped thousands to match the design's mono numerals. */
private fun formatVolume(volume: Double): String =
    volume.toLong().toString().reversed().chunked(THOUSANDS_GROUP).joinToString(" ").reversed()
