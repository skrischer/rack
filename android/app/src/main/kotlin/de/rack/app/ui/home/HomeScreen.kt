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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompTheme
import java.time.LocalDate

/**
 * Read-only Home overview: the current ISO week's volume
 * breakdown (per muscle and per plan-day tag as Vico column charts under a total-volume
 * badge), the training-streak hero, and the recent-sessions list (newest first), each
 * session tappable to open that date in calendar/history. Purely presentational — it
 * renders [state] and emits retry / session-open / back events upward; no Supabase access
 * and no business logic live here.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        HomeTopBar(onOpenCalendar = actions.onOpenCalendar, onBack = actions.onBack)
        RecompDivider()
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is HomeUiState.Loading -> RecompLoading()
                is HomeUiState.Empty -> EmptyState()
                is HomeUiState.Error -> RecompError(message = state.message, onRetry = actions.onRetry)
                is HomeUiState.Content ->
                    HomeContentPane(content = state.content, onOpenSession = actions.onOpenSession)
            }
        }
    }
}

/** The home actions, bundled so the screen takes one parameter for retry, navigation, and back. */
@Immutable
data class HomeActions(
    val onRetry: () -> Unit,
    val onOpenSession: (LocalDate) -> Unit,
    val onOpenCalendar: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { WeekVolumeSection(weekly = content.weeklyVolume, totals = content.weekTotals) }
        item { StreakSection(current = content.streak.current, longest = content.streak.longest) }
        item { SectionTitle(text = "Letzte Einheiten") }
        if (content.recentSessions.isEmpty()) {
            item { RecompEmpty(text = "Noch keine Einheiten protokolliert.") }
        } else {
            item { SessionsCard(sessions = content.recentSessions, onOpen = onOpenSession) }
        }
    }
}

@Composable
private fun HomeTopBar(
    onOpenCalendar: () -> Unit,
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
        Text(text = "ÜBERSICHT", style = type.kicker, color = colors.volt)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TopBarAction(label = "VERLAUF", onClick = onOpenCalendar)
            TopBarAction(label = "ZURÜCK", onClick = onBack)
        }
    }
}

@Composable
private fun TopBarAction(
    label: String,
    onClick: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(
        text = label,
        style = type.label,
        color = colors.dim,
        modifier =
            Modifier
                .border(spacing.border, colors.line, RecompTheme.shapes.sm)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}

@Composable
private fun EmptyState() {
    RecompEmpty(
        text =
            "Noch kein Training protokolliert.\n" +
                "Protokolliere ein paar Sätze und deine Wochenübersicht erscheint hier.",
    )
}
