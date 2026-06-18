package de.rack.app.ui.home

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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.ui.theme.RecompTheme
import java.time.LocalDate

/**
 * Read-only Home overview (docs/specs/spec-dashboards.md, Phase 11): the current ISO
 * week's volume breakdown (per muscle and per plan-day tag as Vico column charts), the
 * training streak stats, and the recent-sessions list (newest first), each session
 * tappable to open that date in calendar/history. Purely presentational — it renders
 * [state] and emits retry / session-open / back events upward; no Supabase access and
 * no business logic live here.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        HomeTopBar(onBack = actions.onBack)
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is HomeUiState.Loading -> CenterSpinner()
                is HomeUiState.Empty -> EmptyState()
                is HomeUiState.Error -> ErrorPane(message = state.message, onRetry = actions.onRetry)
                is HomeUiState.Content ->
                    HomeContentPane(content = state.content, onOpenSession = actions.onOpenSession)
            }
        }
    }
}

/** The home actions, bundled so the screen takes one parameter for retry, opening a session, and back. */
@Immutable
data class HomeActions(
    val onRetry: () -> Unit,
    val onOpenSession: (LocalDate) -> Unit,
    val onBack: () -> Unit,
)

@Composable
private fun HomeContentPane(
    content: HomeContent,
    onOpenSession: (LocalDate) -> Unit,
) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item { WeekVolumeSection(weekly = content.weeklyVolume, totals = content.weekTotals) }
        item { StreakSection(current = content.streak.current, longest = content.streak.longest) }
        item { SectionKicker(text = "RECENT SESSIONS") }
        if (content.recentSessions.isEmpty()) {
            item {
                Text(
                    text = "No sessions logged yet.",
                    style = RecompTheme.typography.body,
                    color = RecompTheme.colors.mutedEmpty,
                )
            }
        } else {
            items(content.recentSessions, key = { it.date.toString() }) { session ->
                SessionRow(session = session, onOpen = onOpenSession)
            }
        }
    }
}

@Composable
private fun HomeTopBar(onBack: () -> Unit) {
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
        Text(text = "OVERVIEW", style = type.kicker, color = colors.volt)
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
private fun EmptyState() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No training logged yet.",
            style = type.body,
            color = colors.mutedEmpty,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Log a few sets and your weekly volume, streak, and sessions appear here.",
            style = type.body,
            color = colors.mutedEmpty,
            textAlign = TextAlign.Center,
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
private fun ErrorPane(
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
