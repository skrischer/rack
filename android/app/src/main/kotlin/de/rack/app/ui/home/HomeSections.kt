package de.rack.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.rack.app.domain.SessionSummary
import de.rack.app.domain.VolumeTotals
import de.rack.app.domain.WeeklyVolume
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.tagColor
import java.time.LocalDate

/**
 * The Home overview content sections (Phase 11): the current-week volume summary plus
 * its per-muscle/per-tag Vico charts, the streak stat tiles, and a recent-session row.
 * Purely presentational helpers consumed by [HomeScreen]; no Supabase access or
 * business logic here.
 */
@Composable
internal fun WeekVolumeSection(
    weekly: WeeklyVolume,
    totals: VolumeTotals,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SectionKicker(text = "THIS WEEK")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            StatTile(
                value = formatVolume(totals.volume),
                label = "VOLUME KG",
                color = colors.volt,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = totals.workingSets.toString(),
                label = "WORKING SETS",
                color = colors.txt,
                modifier = Modifier.weight(1f),
            )
        }
        if (totals.workingSets == 0) {
            Text(text = "No sets logged this week yet.", style = type.body, color = colors.mutedEmpty)
        } else {
            ChartCard(title = "PER MUSCLE", bars = muscleBars(weekly.perMuscle))
            ChartCard(title = "PER TAG", bars = tagBars(weekly.perTag))
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    bars: List<VolumeBar>,
) {
    val type = RecompTheme.typography
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = title, style = type.caption, color = colors.dim)
        VolumeColumnChart(bars = bars, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun StreakSection(
    current: Int,
    longest: Int,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SectionKicker(text = "STREAK")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            StatTile(
                value = current.toString(),
                label = "CURRENT WEEKS",
                color = colors.volt,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = longest.toString(),
                label = "LONGEST WEEKS",
                color = colors.txt,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            modifier
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(text = value, style = type.dayTitle, color = color)
        Text(text = label, style = type.caption, color = colors.dim)
    }
}

@Composable
internal fun SessionRow(
    session: SessionSummary,
    onOpen: (LocalDate) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .clickable { onOpen(session.date) }
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = sessionTitle(session), style = type.exerciseName, color = colors.txt)
            session.tag?.let { tag ->
                Text(text = tag.uppercase(), style = type.label, color = colors.tagColor(tag))
            }
        }
        Text(text = session.date.toString(), style = type.caption, color = colors.dim)
        Text(
            text = "${formatVolume(session.volume)} KG · ${session.workingSets} SETS",
            style = type.loadValue,
            color = colors.volt,
        )
    }
}

@Composable
internal fun SectionKicker(text: String) {
    Text(text = text, style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt)
}

/** The session's day title, falling back to its tag, then to a generic label. */
private fun sessionTitle(session: SessionSummary): String =
    session.title?.takeIf { it.isNotBlank() }
        ?: session.tag?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        ?: "Session"

/** Round weighted volume to a whole kilogram for the compact stat/list display. */
private fun formatVolume(volume: Double): String = volume.toLong().toString()
