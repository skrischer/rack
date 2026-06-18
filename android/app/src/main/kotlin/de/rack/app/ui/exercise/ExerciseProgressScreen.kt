package de.rack.app.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.domain.ExerciseProgressPoint
import de.rack.app.ui.theme.RecompTheme

/**
 * Read-only per-exercise progress screen (spec-dashboards.md, Phase 11). For an
 * exercise with two or more logged dates it draws a Vico line chart of top-set
 * weight and per-session volume plus a tabular fallback of the same (date, top-set
 * weight, session volume) rows; with too few logged dates it shows a Recomp
 * insufficient-data state and never feeds the chart an empty series. Values are kg
 * only with no unit toggle and no estimated-1RM metric (Phase 12 / Phase 13). Purely
 * presentational — the read and aggregation live in [ExerciseProgressViewModel].
 */
@Composable
fun ExerciseProgressScreen(
    state: ExerciseProgressUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        ProgressTopBar(title = titleOf(state), onBack = onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ExerciseProgressUiState.Loading -> CenterSpinner()
                is ExerciseProgressUiState.Error -> ProgressErrorPane(message = state.message, onRetry = onRetry)
                is ExerciseProgressUiState.InsufficientData -> InsufficientDataPane(points = state.points)
                is ExerciseProgressUiState.Content -> ProgressContent(points = state.points)
            }
        }
    }
}

@Composable
private fun ProgressContent(points: List<ExerciseProgressPoint>) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { SectionKicker(text = "PROGRESS") }
        item { ExerciseProgressChart(points = points) }
        item { ChartLegend() }
        item { SectionKicker(text = "LOG") }
        item { ProgressTableHeader() }
        items(points.asReversed()) { point -> ProgressTableRow(point = point) }
    }
}

@Composable
private fun InsufficientDataPane(points: List<ExerciseProgressPoint>) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { EmptyState() }
        if (points.isNotEmpty()) {
            item { SectionKicker(text = "LOG") }
            item { ProgressTableHeader() }
            items(points.asReversed()) { point -> ProgressTableRow(point = point) }
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(RecompTheme.spacing.sm)) {
        Text(text = "Not enough history yet.", style = type.body, color = colors.mutedEmpty)
        Text(
            text = "Log this exercise on at least two days to chart your progress.",
            style = type.body,
            color = colors.mutedEmpty,
        )
    }
}

@Composable
private fun SectionKicker(text: String) {
    Text(text = text, style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt)
}

@Composable
private fun ProgressTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = type.kicker, color = colors.volt)
        Text(
            text = "BACK",
            style = type.label,
            color = colors.dim,
            modifier =
                Modifier
                    .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                    .clickable(onClick = onBack)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}

@Composable
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RecompTheme.colors.volt)
    }
}

@Composable
private fun ProgressErrorPane(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = type.body, color = colors.legs, textAlign = TextAlign.Center)
        Text(
            text = "RETRY",
            style = type.label,
            color = colors.bg,
            modifier =
                Modifier
                    .background(colors.volt, RecompTheme.shapes.md)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
    }
}

private fun titleOf(state: ExerciseProgressUiState): String =
    when (state) {
        is ExerciseProgressUiState.Content -> state.exerciseName.uppercase().ifBlank { DEFAULT_TITLE }
        is ExerciseProgressUiState.InsufficientData -> state.exerciseName.uppercase().ifBlank { DEFAULT_TITLE }
        else -> DEFAULT_TITLE
    }

private const val DEFAULT_TITLE = "PROGRESS"
