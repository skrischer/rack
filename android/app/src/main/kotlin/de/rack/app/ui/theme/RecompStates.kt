package de.rack.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Kit `.spinner` geometry (34px ring, 3px stroke) and the `.empty` block padding.
private val SpinnerSize = 34.dp
private val SpinnerStroke = 3.dp
private val SpinnerPaddingV = 60.dp
private val StatePaddingV = 40.dp
private val EmptyLineHeight = 20.sp

/**
 * The shared loading state (kit `.spinner`): a volt arc on a `line` track, spinning,
 * centered with generous vertical breathing room. Drop into any card/screen while data loads.
 */
@Composable
fun RecompLoading(modifier: Modifier = Modifier) {
    val colors = RecompTheme.colors
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = SpinnerPaddingV),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(SpinnerSize),
            color = colors.volt,
            trackColor = colors.line,
            strokeWidth = SpinnerStroke,
        )
    }
}

/**
 * The shared empty state (kit `.empty`): centered, muted mono copy with relaxed line height.
 * Use `\n` in [text] for the two-line placeholder voice from the designs.
 */
@Composable
fun RecompEmpty(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Box(
        modifier =
            modifier.fillMaxWidth().padding(
                horizontal = RecompTheme.spacing.cardInsetH,
                vertical = StatePaddingV
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = emptyTextStyle(),
            color = colors.mutedEmpty,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The shared error state (kit `states.html`): the muted [message] over a ghost retry button.
 * [retryLabel] defaults to the designs' "Erneut versuchen".
 */
@Composable
fun RecompError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Erneut versuchen",
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.cardInsetH, vertical = StatePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Text(text = message, style = emptyTextStyle(), color = colors.mutedEmpty, textAlign = TextAlign.Center)
        RecompGhostButton(text = retryLabel, onClick = onRetry)
    }
}

@Composable
private fun emptyTextStyle() =
    RecompTheme.typography.label.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.04.em,
        lineHeight = EmptyLineHeight,
    )
