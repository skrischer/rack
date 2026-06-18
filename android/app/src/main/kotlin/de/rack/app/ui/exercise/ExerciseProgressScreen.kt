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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.ExerciseProgressPoint
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
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
        ProgressTopBar(onBack = onBack)
        RecompDivider()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ExerciseProgressUiState.Loading -> RecompLoading()
                is ExerciseProgressUiState.Error -> RecompError(message = state.message, onRetry = onRetry)
                is ExerciseProgressUiState.InsufficientData ->
                    InsufficientDataPane(exerciseName = state.exerciseName, points = state.points)
                is ExerciseProgressUiState.Content ->
                    ProgressContent(exerciseName = state.exerciseName, points = state.points)
            }
        }
    }
}

@Composable
private fun ProgressContent(
    exerciseName: String,
    points: List<ExerciseProgressPoint>,
) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { SectionKicker(text = nameKicker(exerciseName)) }
        item { ProgressChartCard(points = points) }
        item { SectionTitle(text = "Verlauf") }
        item { ProgressTableCard(points = points.asReversed()) }
    }
}

@Composable
private fun InsufficientDataPane(
    exerciseName: String,
    points: List<ExerciseProgressPoint>,
) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { SectionKicker(text = nameKicker(exerciseName)) }
        item {
            RecompEmpty(
                text = "Noch nicht genug Verlauf.\nDiese Übung an mindestens zwei Tagen protokollieren.",
            )
        }
        if (points.isNotEmpty()) {
            item { SectionTitle(text = "Verlauf") }
            item { ProgressTableCard(points = points.asReversed()) }
        }
    }
}

@Composable
private fun SectionKicker(text: String) {
    Text(text = text, style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text.uppercase(), style = RecompTheme.typography.label, color = RecompTheme.colors.dim)
}

@Composable
private fun ProgressTopBar(onBack: () -> Unit) {
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
        Text(text = "PROGRESS", style = type.kicker, color = colors.volt)
        Text(
            text = "ZURÜCK",
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

private fun nameKicker(exerciseName: String): String = exerciseName.uppercase().ifBlank { "PROGRESS" }
