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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.domain.ExerciseDetail
import de.rack.app.ui.theme.RecompTheme

/**
 * Read-only exercise detail screen: an image (or deterministic placeholder), the
 * exercise name and category, primary/secondary target muscles, required equipment,
 * ordered how-to instructions (or an explicit empty state), and a CC-BY-SA
 * attribution line whenever wger-sourced content is shown. Purely presentational —
 * it renders [state] and emits retry / back events upward; the detail read, image
 * download/decode, and placeholder fallback live in the [ExerciseDetailViewModel].
 */
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DetailTopBar(title = titleOf(state), onBack = onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ExerciseDetailUiState.Loading -> CenterSpinner()
                is ExerciseDetailUiState.Error -> DetailErrorPane(message = state.message, onRetry = onRetry)
                is ExerciseDetailUiState.Content -> DetailContent(detail = state.detail, image = state.image)
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: ExerciseDetail,
    image: ExerciseImage,
) {
    val spacing = RecompTheme.spacing
    val colors = RecompTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { ExerciseImageSlot(image = image, name = detail.name) }
        item { ExerciseHeader(detail = detail) }
        detail.primaryMuscles.takeIf { it.isNotEmpty() }?.let { muscles ->
            item { ChipSection(label = "PRIMARY MUSCLES", values = muscles, accent = colors.volt) }
        }
        detail.secondaryMuscles.takeIf { it.isNotEmpty() }?.let { muscles ->
            item { ChipSection(label = "SECONDARY MUSCLES", values = muscles, accent = colors.dim) }
        }
        detail.equipment.takeIf { it.isNotEmpty() }?.let { equipment ->
            item { ChipSection(label = "EQUIPMENT", values = equipment, accent = colors.pull) }
        }
        item { InstructionsHeader() }
        if (detail.instructions.isEmpty()) {
            item { EmptyInstructions() }
        } else {
            itemsIndexed(detail.instructions) { index, step ->
                InstructionStep(number = index + 1, step = step)
            }
        }
        attributionLine(detail)?.let { text ->
            item { Attribution(text = text) }
        }
    }
}

@Composable
private fun DetailTopBar(
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
private fun DetailErrorPane(
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

private fun titleOf(state: ExerciseDetailUiState): String =
    when (state) {
        is ExerciseDetailUiState.Content -> state.detail.name.uppercase()
        else -> "EXERCISE"
    }
