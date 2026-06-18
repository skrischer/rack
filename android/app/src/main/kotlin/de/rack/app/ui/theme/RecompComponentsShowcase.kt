package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A scrollable gallery of the Recomp component foundation (issue #158) — the set table, nav
 * dock + overflow sheet, stat strip, launcher rows, segmented toggle, stepper, chips, badges,
 * and loading/empty/error states. Purely presentational; it doubles as the visual smoke-check
 * for this theme package and as a usage reference for the screen issues that consume it.
 */
@Composable
fun RecompComponentsShowcase() {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SectionLabel("States")
            ShowcaseCard { RecompLoading() }
            ShowcaseCard { RecompEmpty("Noch keine Einheiten geloggt.\nLogge deinen ersten Satz.") }
            ShowcaseCard { RecompError(message = "Daten konnten nicht geladen werden.", onRetry = {}) }

            SectionLabel("Badges")
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RecompBadge("61 240 kg", RecompBadgeStyle.Volt)
                RecompBadge("Agent", RecompBadgeStyle.Agent)
                RecompBadge("Legs", RecompBadgeStyle.Legs)
                RecompBadge("Heute")
            }

            SectionLabel("Chips")
            ChipsRow()

            SectionLabel("Controls")
            ControlsRow()

            SectionLabel("Stat strip")
            RecompStatStrip(
                stats =
                    listOf(
                        RecompStat("12:48", "Dauer"),
                        RecompStat("4 320", "Volumen kg"),
                        RecompStat("7 / 24", "Sätze"),
                    ),
            )

            SectionLabel("Set table")
            ShowcaseCard { SetTableSample() }

            SectionLabel("Launcher rows")
            ShowcaseCard { LauncherSample() }

            SectionLabel("Overflow sheet")
        }
        SheetSample()
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.gutter)) {
            SectionLabel("Navigation")
        }
        NavSample()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text.uppercase(), style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt)
}

@Composable
private fun ShowcaseCard(content: @Composable () -> Unit) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        content()
    }
}

@Composable
private fun ChipsRow() {
    var selected by remember { mutableIntStateOf(0) }
    val labels = listOf("Upper / Lower", "Fullbody", "5er Split")
    RecompChipRow {
        labels.forEachIndexed { index, label ->
            RecompChip(label = label, selected = index == selected, onClick = { selected = index })
        }
    }
}

@Composable
private fun ControlsRow() {
    var unit by remember { mutableIntStateOf(0) }
    val spacing = RecompTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
        RecompSegmentedToggle(options = listOf("KG", "LB"), selectedIndex = unit, onSelect = { unit = it })
        RecompStepper(value = "2:30", onDecrement = {}, onIncrement = {})
    }
}

@Composable
private fun SetTableSample() {
    val rows =
        listOf(
            RecompSetRow(
                setLabel = "W",
                weight = "40",
                reps = "10",
                rir = "–",
                previous = "40 × 10",
                isWarmup = true,
                done = true
            ),
            RecompSetRow(setLabel = "1", weight = "85", reps = "8", rir = "1", previous = "82,5 × 8", done = true),
            RecompSetRow(setLabel = "2", weight = "85", reps = "", rir = "", previous = "82,5 × 7"),
        )
    RecompSetTable(
        rows = rows,
        callbacks =
            RecompSetTableCallbacks(
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onRirChange = { _, _ -> },
                onToggleDone = {},
                onAddSet = {},
            ),
    )
}

@Composable
private fun LauncherSample() {
    val colors = RecompTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        RecompDayRow(
            data = RecompDayRowData("01", colors.tagColor("push"), "Oberkörper A", "Druck · 6 Übungen"),
            onClick = {},
            trailing = { RecompBadge("Heute", RecompBadgeStyle.Volt) },
        )
        RecompDayRow(
            data = RecompDayRowData("02", colors.tagColor("legs"), "Unterkörper A", "Quad · 6 Übungen"),
            onClick = {},
            showDivider = false,
        )
    }
}

@Composable
private fun SheetSample() {
    RecompOverflowSheet(
        tiles = {
            RecompNavTile(label = "Plan", selected = true, onClick = {}) { DotIcon() }
            RecompNavTile(label = "Verlauf", selected = false, onClick = {}) { DotIcon() }
            RecompNavTile(label = "Statistik", selected = false, onClick = {}) { DotIcon() }
            RecompNavTile(label = "Artifacts", selected = false, onClick = {}) { DotIcon() }
        },
        content = {
            RecompMenuItem(
                data = RecompMenuItemData("Übungen", "Katalog · 412 Übungen", chevron = true),
                onClick = {},
                leading = { DotIcon() },
            )
            RecompMenuItem(
                data = RecompMenuItemData("API-Keys", "MCP-Zugang für deinen Agent", chevron = true),
                onClick = {},
                leading = { DotIcon() },
            )
            RecompSheetSectionHeader("Account")
            RecompMenuItem(
                data = RecompMenuItemData("Basti", "basti@rack.local"),
                onClick = {},
                leading = { RecompAvatar("B") },
            )
        },
    )
}

@Composable
private fun NavSample() {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Plan", "Verlauf", "Statistik", "Artifacts")
    RecompNavDock(onHandleClick = {}) {
        labels.forEachIndexed { index, label ->
            RecompNavTile(label = label, selected = index == tab, onClick = { tab = index }) {
                DotIcon()
            }
        }
    }
}

@Composable
private fun DotIcon() {
    val dotSize = 18.dp
    Box(
        modifier =
            Modifier
                .size(dotSize)
                .background(LocalContentColor.current, RoundedCornerShape(percent = 30)),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0E11, heightDp = 1600)
@Composable
private fun RecompComponentsShowcasePreview() {
    RecompTheme {
        RecompComponentsShowcase()
    }
}
