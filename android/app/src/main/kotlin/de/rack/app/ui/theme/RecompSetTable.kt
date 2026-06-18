package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Kit `.settbl` column geometry — fixed metric columns with a flexible "Vorher" column.
private val ColSetNo = 30.dp
private val ColMetric = 56.dp
private val ColRir = 48.dp
private val ColCheck = 40.dp
private val CellInputWidth = 46.dp
private val CheckSize = 28.dp

// Kit `tr.done td` row tint — volt at 4.5%.
private const val DONE_TINT_ALPHA = 0.045f

/**
 * One logged/loggable set in a [RecompSetTable]. [setLabel] is the set marker ("1", "2", or
 * "W" for a warm-up); [isWarmup] tints it legs-orange. [previous] is the matching set from the
 * last session ("82,5 × 8"). When [done] the metric cells render as static volt-tinted text;
 * otherwise they are editable [RecompCellInput]s.
 */
data class RecompSetRow(
    val setLabel: String,
    val weight: String,
    val reps: String,
    val rir: String,
    val previous: String? = null,
    val isWarmup: Boolean = false,
    val done: Boolean = false,
)

/**
 * The edit/toggle callbacks for a [RecompSetTable], keyed by set index (bundled to keep the
 * component's parameter list short, mirroring the app's `LoggingHandlers` pattern). Set
 * [onAddSet] to show the "+ Satz" footer; leave it null to hide it.
 */
data class RecompSetTableCallbacks(
    val onWeightChange: (Int, String) -> Unit,
    val onRepsChange: (Int, String) -> Unit,
    val onRirChange: (Int, String) -> Unit,
    val onToggleDone: (Int) -> Unit,
    val onAddSet: (() -> Unit)? = null,
)

/**
 * The Strong/Hevy-style set table (kit `.settbl`): a `Satz | Vorher | kg | Wdh | RIR | ✓`
 * grid with a previous-performance column and a per-set check. Purely presentational — it
 * renders [rows] and forwards edits through [callbacks]; the session ViewModel owns the
 * values, the rest-timer start, and persistence.
 */
@Composable
fun RecompSetTable(
    rows: List<RecompSetRow>,
    callbacks: RecompSetTableCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SetTableHeader()
        rows.forEachIndexed { index, row ->
            RecompDivider()
            SetTableRow(index = index, row = row, callbacks = callbacks)
        }
        callbacks.onAddSet?.let { onAddSet ->
            RecompDivider()
            AddSetFooter(onClick = onAddSet)
        }
    }
}

@Composable
private fun SetTableHeader() {
    val colors = RecompTheme.colors
    val style = RecompTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = RecompTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell(text = "Satz", width = ColSetNo, color = colors.dim, style = style, align = TextAlign.Start)
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = "Vorher",
                style = style,
                color = colors.dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        HeaderCell(text = "kg", width = ColMetric, color = colors.dim, style = style, align = TextAlign.Center)
        HeaderCell(text = "Wdh", width = ColMetric, color = colors.dim, style = style, align = TextAlign.Center)
        HeaderCell(text = "RIR", width = ColRir, color = colors.dim, style = style, align = TextAlign.Center)
        HeaderCell(text = "✓", width = ColCheck, color = colors.dim, style = style, align = TextAlign.Center)
    }
}

@Composable
private fun SetTableRow(
    index: Int,
    row: RecompSetRow,
    callbacks: RecompSetTableCallbacks,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val rowBackground = if (row.done) Modifier.background(colors.volt.copy(alpha = DONE_TINT_ALPHA)) else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().then(rowBackground).padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val setNoColor = if (row.isWarmup) colors.legs else colors.dim
        FixedCell(width = ColSetNo, align = Alignment.CenterStart) {
            Text(text = row.setLabel, style = type.loadValue.copy(fontWeight = FontWeight.Medium), color = setNoColor)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(text = row.previous ?: "—", style = type.lastTime, color = colors.dim, textAlign = TextAlign.Center)
        }
        MetricCell(width = ColMetric, value = row.weight, done = row.done) { callbacks.onWeightChange(index, it) }
        MetricCell(width = ColMetric, value = row.reps, done = row.done) { callbacks.onRepsChange(index, it) }
        MetricCell(width = ColRir, value = row.rir, done = row.done) { callbacks.onRirChange(index, it) }
        FixedCell(width = ColCheck, align = Alignment.Center) {
            RecompSetCheck(checked = row.done, onClick = { callbacks.onToggleDone(index) })
        }
    }
}

@Composable
private fun MetricCell(
    width: Dp,
    value: String,
    done: Boolean,
    onValueChange: (String) -> Unit,
) {
    val type = RecompTheme.typography
    val colors = RecompTheme.colors
    FixedCell(width = width, align = Alignment.Center) {
        if (done) {
            Text(
                text = value.ifEmpty { "–" },
                style = type.loadValue.copy(fontWeight = FontWeight.Normal),
                color = colors.txt,
            )
        } else {
            RecompCellInput(value = value, onValueChange = onValueChange)
        }
    }
}

@Composable
private fun FixedCell(
    width: Dp,
    align: Alignment,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.width(width), contentAlignment = align) { content() }
}

@Composable
private fun HeaderCell(
    text: String,
    width: Dp,
    color: Color,
    style: TextStyle,
    align: TextAlign,
) {
    Text(text = text, style = style, color = color, textAlign = align, modifier = Modifier.width(width))
}

@Composable
private fun AddSetFooter(onClick: () -> Unit) {
    val colors = RecompTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().recompPress(onClick = onClick).padding(vertical = RecompTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+ SATZ", style = RecompTheme.typography.label, color = colors.voltDim)
    }
}

/**
 * A single metric input cell (kit `.cell-in`): a narrow, centered, mono numeric field on the
 * `bg` surface whose border turns volt on focus. The empty state shows the dim [placeholder].
 */
@Composable
fun RecompCellInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    width: Dp = CellInputWidth,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) colors.volt else colors.line
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .width(width)
                .background(colors.bg, RecompTheme.shapes.sm)
                .border(spacing.border, borderColor, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.xxs, vertical = spacing.xs),
        textStyle =
            type.loadValue.copy(
                fontWeight = FontWeight.Normal,
                color = colors.txt,
                textAlign = TextAlign.Center
            ),
        singleLine = true,
        cursorBrush = SolidColor(colors.volt),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = type.loadValue.copy(fontWeight = FontWeight.Normal),
                        color = colors.mutedEmpty,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            }
        },
    )
}

/**
 * The per-set completion check (kit `.check` / `.check.done`): a small square that fills volt
 * with a `bg` tick when [checked], or shows a faint outline tick when not. Tapping flips it —
 * in the session player this is what persists the set and auto-starts the rest timer.
 */
@Composable
fun RecompSetCheck(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val background = if (checked) colors.volt else colors.bg
    val border = if (checked) colors.volt else colors.line
    val glyph = if (checked) colors.bg else colors.mutedEmpty
    Box(
        modifier =
            modifier
                .size(CheckSize)
                .recompPress(onClick = onClick)
                .background(background, RecompTheme.shapes.sm)
                .border(spacing.border, border, RecompTheme.shapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "✓", style = RecompTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = glyph)
    }
}
