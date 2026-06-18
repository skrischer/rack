package de.rack.app.ui.plan

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import de.rack.app.domain.Plan
import de.rack.app.ui.RackDestinations
import de.rack.app.ui.theme.RecompChip
import de.rack.app.ui.theme.RecompChipRow
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The launcher's navigation/selection callbacks, bundled so the host route stays compact. */
fun planActions(
    navController: NavHostController,
    planViewModel: PlanViewModel,
): PlanActions =
    PlanActions(
        onSelectPlan = planViewModel::selectPlan,
        onRetry = planViewModel::load,
        onOpenExercise = { id -> navController.navigate(RackDestinations.exerciseDetailRoute(id)) },
        onStartSession = { dayId -> navController.navigate(RackDestinations.sessionRoute(dayId)) },
    )

/**
 * Plan/Home as a launcher: a today-hero for the next
 * session with a "Session starten" CTA, then the selected plan's days as a compact,
 * tappable row list. Set logging now lives in the session player, so the overview holds
 * no inline weight/reps/RIR inputs. Purely presentational — it observes [state] and emits
 * selection / retry / open-exercise / start-session events upward; no Supabase access and
 * no business logic live here. Cross-screen navigation is the bottom-nav dock's job.
 */
@Composable
fun PlanScreen(
    state: PlanUiState,
    actions: PlanActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        PlanTopBar()
        RecompDivider()
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is PlanUiState.Loading -> RecompLoading()
                is PlanUiState.Empty -> RecompEmpty(text = "Noch kein Plan.\nLass deinen Agent einen schreiben.")
                is PlanUiState.Error -> RecompError(message = state.message, onRetry = actions.onRetry)
                is PlanUiState.Content -> PlanLauncherPane(content = state.content, actions = actions)
            }
        }
    }
}

@Composable
private fun PlanLauncherPane(
    content: PlanContent,
    actions: PlanActions,
) {
    val spacing = RecompTheme.spacing
    val today = content.days.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { DateKicker() }
        if (today != null) {
            item {
                TodayHeroCard(
                    dayContent = today,
                    highlighted = today.day.id in content.highlightedIds,
                    onStartSession = { actions.onStartSession(today.day.id) },
                    onOpenExercise = actions.onOpenExercise,
                )
            }
        }
        item { SectionTitle(text = "Dein Plan") }
        item {
            PlanChipSelector(
                plans = content.plans,
                selectedPlanId = content.selectedPlanId,
                onSelectPlan = actions.onSelectPlan,
            )
        }
        if (content.days.isEmpty()) {
            item { RecompEmpty(text = "Dieser Plan hat noch keine Tage.") }
        } else {
            item {
                PlanDayList(
                    days = content.days,
                    highlightedIds = content.highlightedIds,
                    onStartSession = actions.onStartSession,
                )
            }
        }
        item { PlanFooter() }
    }
}

/** The day's weekday + date in the kit kicker voice (`Mittwoch · 18.06.`), resolved once. */
@Composable
private fun DateKicker() {
    val kicker =
        remember {
            LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE · dd.MM.", Locale.GERMAN))
                .uppercase(Locale.GERMAN)
        }
    Text(text = kicker, style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt)
}

@Composable
private fun PlanChipSelector(
    plans: List<Plan>,
    selectedPlanId: String,
    onSelectPlan: (String) -> Unit,
) {
    RecompChipRow {
        plans.forEach { plan ->
            RecompChip(
                label = plan.name,
                selected = plan.id == selectedPlanId,
                onClick = { onSelectPlan(plan.id) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text.uppercase(), style = RecompTheme.typography.label, color = RecompTheme.colors.dim)
}

@Composable
private fun PlanFooter() {
    Text(
        text = "Plan vom Agent geschrieben",
        style = RecompTheme.typography.history,
        color = RecompTheme.colors.dim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = RecompTheme.spacing.xs),
    )
}

@Composable
private fun PlanTopBar() {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "RACK", style = RecompTheme.typography.kicker, color = colors.volt)
    }
}
