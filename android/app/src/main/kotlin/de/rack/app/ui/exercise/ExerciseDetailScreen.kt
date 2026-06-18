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
import de.rack.app.domain.ExerciseDetail
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
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
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DetailTopBar(title = titleOf(state), onBack = onBack, onOpenProgress = onOpenProgress)
        RecompDivider()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ExerciseDetailUiState.Loading -> RecompLoading()
                is ExerciseDetailUiState.Error -> RecompError(message = state.message, onRetry = onRetry)
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
            item { ChipSection(label = "Primärmuskeln", values = muscles, selected = true) }
        }
        detail.secondaryMuscles.takeIf { it.isNotEmpty() }?.let { muscles ->
            item { ChipSection(label = "Sekundär", values = muscles) }
        }
        detail.equipment.takeIf { it.isNotEmpty() }?.let { equipment ->
            item { ChipSection(label = "Equipment", values = equipment, accent = colors.pull) }
        }
        item { InstructionsCard(steps = detail.instructions) }
        attributionLine(detail)?.let { text ->
            item { Attribution(text = text) }
        }
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    onOpenProgress: () -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TopBarButton(text = "Progress", onClick = onOpenProgress)
            TopBarButton(text = "Zurück", onClick = onBack)
        }
    }
}

@Composable
private fun TopBarButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Text(
        text = text.uppercase(),
        style = RecompTheme.typography.label,
        color = colors.dim,
        modifier =
            Modifier
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}

private fun titleOf(state: ExerciseDetailUiState): String =
    when (state) {
        is ExerciseDetailUiState.Content -> state.detail.name.uppercase()
        else -> "ÜBUNG"
    }
