package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A self-contained sample screen that renders the Recomp tokens — hero, day card with
 * a tag-colored number chip, an exercise row with target + RIR + cue, and a violet
 * superset header — so the theme can be eyeballed against the artifact.html prototype.
 *
 * Purely presentational: no ViewModel, repository, or Supabase access. The real screens
 * (auth #21, plan view #22, logging #23) replace this, but it stays as the theme preview.
 */
@Composable
fun RecompThemeShowcase() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter, vertical = spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(text = "RECOMP", style = type.kicker, color = colors.volt)
        Text(text = "KRAFT &\nDEFINITION", style = type.hero, color = colors.txt)
        Spacer(Modifier.height(spacing.xs))
        DayCard(
            number = "01",
            title = "OBERKÖRPER A",
            focus = "DRUCK-SCHWERPUNKT",
            tag = "push",
        )
        DayCard(
            number = "03",
            title = "OBERKÖRPER B",
            focus = "ZUG-SCHWERPUNKT",
            tag = "pull",
        )
        DayCard(
            number = "02",
            title = "UNTERKÖRPER A",
            focus = "QUAD-SCHWERPUNKT",
            tag = "legs",
        )
        PaletteRow()
    }
}

@Composable
private fun DayCard(
    number: String,
    title: String,
    focus: String,
    tag: String,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val accent = colors.tagColor(tag)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.panelElevated)
                    .padding(horizontal = spacing.cardInsetH, vertical = spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(spacing.dayChip)
                        .background(accent, RecompTheme.shapes.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = number, style = type.loadValue, color = colors.bg)
            }
            Spacer(Modifier.width(spacing.md))
            Column {
                Text(text = title, style = type.dayTitle, color = colors.txt)
                Text(text = focus, style = type.label, color = colors.dim)
            }
        }
        SupersetHeader(kind = SupersetKind.SUPERSET)
        ExerciseRow(
            name = "Schulterdrücken (KH, sitzend)",
            target = "3 × 8–10",
            rir = "RIR 2",
            cue = "Core fest, kein Hohlkreuz, oben nicht voll durchstrecken",
        )
        ExerciseRow(
            name = "Klimmzug / Latzug",
            target = "3 × 8–10",
            rir = "RIR 2",
            cue = "Schulterblätter zuerst nach unten, Brust führen, voller Stretch oben",
        )
    }
}

@Composable
private fun SupersetHeader(kind: SupersetKind) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val labelText = if (kind == SupersetKind.CIRCUIT) "ZIRKEL" else "SUPERSATZ"
    Text(
        text = labelText,
        style = type.supersetHeader,
        color = colors.superset,
        modifier =
            Modifier.padding(
                start = spacing.cardInsetH,
                top = spacing.md,
                bottom = spacing.xxs,
            ),
    )
}

@Composable
private fun ExerciseRow(
    name: String,
    target: String,
    rir: String,
    cue: String,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.supersetTint)
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = name,
                style = type.exerciseName,
                color = colors.txt,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(spacing.md))
            Row {
                Text(text = target, style = type.loadValue, color = colors.volt)
                Text(text = "  $rir", style = type.loadValue, color = colors.dim)
            }
        }
        Row(modifier = Modifier.padding(top = spacing.xs)) {
            Text(text = "→ ", style = type.cue, color = colors.voltDim)
            Text(text = cue, style = type.cue, color = colors.dim)
        }
    }
}

@Composable
private fun PaletteRow() {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Swatch(colors.volt, "VOLT")
        Swatch(colors.pull, "PULL")
        Swatch(colors.legs, "LEGS")
        Swatch(colors.superset, "SS")
    }
}

@Composable
private fun Swatch(
    color: Color,
    label: String,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val swatchWidth = 56.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(width = swatchWidth, height = 40.dp)
                    .background(color, RecompTheme.shapes.sm),
        )
        Text(
            text = label,
            style = type.caption,
            color = colors.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(swatchWidth).padding(top = spacing.xxs),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0E11, heightDp = 900)
@Composable
private fun RecompThemeShowcasePreview() {
    RecompTheme {
        RecompThemeShowcase()
    }
}
