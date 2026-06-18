package de.rack.app.ui.exercise

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.domain.ExerciseDetail
import de.rack.app.ui.theme.RecompChip
import de.rack.app.ui.theme.RecompChipRow
import de.rack.app.ui.theme.RecompTheme

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
                .background(colors.panelElevated, RecompTheme.shapes.xl),
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
            is ExerciseImage.Placeholder -> ImagePlaceholder()
        }
    }
}

/** Empty image state (kit "ABBILDUNG" placeholder): a muted mono label on the panel surface. */
@Composable
private fun ImagePlaceholder() {
    Text(
        text = "ABBILDUNG",
        style = RecompTheme.typography.kicker,
        color = RecompTheme.colors.mutedEmpty,
    )
}

/** Name over a muted category caption. */
@Composable
internal fun ExerciseHeader(detail: ExerciseDetail) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(RecompTheme.spacing.xs)) {
        Text(text = detail.name, style = type.dayTitle, color = colors.txt)
        detail.category?.takeIf { it.isNotBlank() }?.let { category ->
            Text(text = category.uppercase(), style = type.caption, color = colors.dim)
        }
    }
}

/**
 * A labeled rail of Recomp chips (kit `.chipbar`). [selected] fills the chips volt for the
 * primary muscles; an [accent] tints them (e.g. pull for equipment), per the design refs.
 */
@Composable
internal fun ChipSection(
    label: String,
    values: List<String>,
    selected: Boolean = false,
    accent: Color? = null,
) {
    val colors = RecompTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RecompTheme.spacing.sm)) {
        Text(text = label.uppercase(), style = RecompTheme.typography.label, color = colors.dim)
        RecompChipRow {
            values.forEach { value ->
                RecompChip(label = value, selected = selected, onClick = {}, accent = accent)
            }
        }
    }
}

/** The how-to card (kit `.note`): a volt heading over numbered steps, or an empty line. */
@Composable
internal fun InstructionsCard(steps: List<String>) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = "AUSFÜHRUNG", style = type.noteHeading, color = colors.volt)
        if (steps.isEmpty()) {
            Text(text = "Keine Anleitung verfügbar.", style = type.body, color = colors.mutedEmpty)
        } else {
            steps.forEachIndexed { index, step -> InstructionStep(number = index + 1, step = step) }
        }
    }
}

/** One numbered how-to step. */
@Composable
private fun InstructionStep(
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

/** The CC-BY-SA attribution footer shown when wger-sourced content is displayed. */
@Composable
internal fun Attribution(text: String) {
    Text(
        text = text,
        style = RecompTheme.typography.history,
        color = RecompTheme.colors.mutedEmpty,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal const val IMAGE_ASPECT = 16f / 10f
