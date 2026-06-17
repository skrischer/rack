package de.rack.app.ui.exercise

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import de.rack.app.domain.ExerciseDetail
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.tagColor

/** The detail screen's image slot: a decoded licensed image or the deterministic placeholder. */
@Composable
internal fun ExerciseImageSlot(
    image: ExerciseImage,
    name: String,
) {
    val colors = RecompTheme.colors
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(IMAGE_ASPECT)
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(RecompTheme.spacing.border, colors.line, RecompTheme.shapes.xl),
        contentAlignment = Alignment.Center,
    ) {
        when (image) {
            is ExerciseImage.Loaded ->
                Image(
                    bitmap = image.image,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(RecompTheme.spacing.md),
                )
            is ExerciseImage.Placeholder -> ImagePlaceholder(key = image.key)
        }
    }
}

@Composable
private fun ImagePlaceholder(key: String) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val accent = colors.tagColor(key)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box(modifier = Modifier.size(spacing.dayChip).background(accent, RecompTheme.shapes.md))
        Text(text = key.uppercase(), style = type.kicker, color = colors.dim)
        Text(text = "NO IMAGE", style = type.caption, color = colors.mutedEmpty)
    }
}

/** Name + category header. */
@Composable
internal fun ExerciseHeader(detail: ExerciseDetail) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(RecompTheme.spacing.xs)) {
        detail.category?.takeIf { it.isNotBlank() }?.let { category ->
            Text(text = category.uppercase(), style = type.kicker, color = colors.volt)
        }
        Text(text = detail.name, style = type.dayTitle, color = colors.txt)
    }
}

/** A labeled wrap of accent-bordered chips (muscles, equipment). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipSection(
    label: String,
    values: List<String>,
    accent: Color,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = label, style = type.kicker, color = colors.dim)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            values.forEach { value -> TagChip(text = value, accent = accent) }
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    accent: Color,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Text(
        text = text.uppercase(),
        style = RecompTheme.typography.label,
        color = accent,
        modifier =
            Modifier
                .background(colors.panel, RecompTheme.shapes.sm)
                .border(spacing.border, accent, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
    )
}

/** The INSTRUCTIONS section kicker. */
@Composable
internal fun InstructionsHeader() {
    Text(text = "INSTRUCTIONS", style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt)
}

/** One numbered how-to step. */
@Composable
internal fun InstructionStep(
    number: Int,
    step: String,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = type.loadValue,
            color = colors.volt,
            modifier = Modifier.width(spacing.xxl),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(text = step, style = type.body, color = colors.txt, modifier = Modifier.weight(1f))
    }
}

/** Explicit empty state when no instruction source has text. */
@Composable
internal fun EmptyInstructions() {
    Text(
        text = "No instructions available yet",
        style = RecompTheme.typography.body,
        color = RecompTheme.colors.mutedEmpty,
    )
}

/** The CC-BY-SA attribution line shown when wger-sourced content is displayed. */
@Composable
internal fun Attribution(text: String) {
    Text(text = text, style = RecompTheme.typography.caption, color = RecompTheme.colors.mutedEmpty)
}

internal const val IMAGE_ASPECT = 16f / 10f
