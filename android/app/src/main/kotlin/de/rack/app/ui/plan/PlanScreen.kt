package de.rack.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.Plan
import de.rack.app.ui.logging.LoggingSection
import de.rack.app.ui.theme.RecompTheme

/**
 * Read-only plan view: a plan selector, then the selected plan's tag-colored day
 * cards with their ordered exercises and superset/circuit grouping. Purely
 * presentational — it observes [state] and emits selection / sign-out / retry
 * events upward; no Supabase access and no business logic live here.
 */
@Composable
fun PlanScreen(
    state: PlanUiState,
    logging: LoggingSection,
    actions: PlanActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        TopBar(
            onOpenKeys = actions.onOpenKeys,
            onOpenArtifacts = actions.onOpenArtifacts,
            onSignOut = actions.onSignOut,
        )
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is PlanUiState.Loading -> CenterSpinner()
                is PlanUiState.Empty -> CenterMessage(message = "No plans yet. Ask your agent to author one.")
                is PlanUiState.Error -> ErrorPane(message = state.message, onRetry = actions.onRetry)
                is PlanUiState.Content ->
                    PlanContentPane(content = state.content, logging = logging, onSelectPlan = actions.onSelectPlan)
            }
        }
    }
}

@Composable
private fun PlanContentPane(
    content: PlanContent,
    logging: LoggingSection,
    onSelectPlan: (String) -> Unit,
) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            PlanSelector(
                plans = content.plans,
                selectedPlanId = content.selectedPlanId,
                onSelectPlan = onSelectPlan,
            )
        }
        items(content.days, key = { it.day.id }) { dayContent ->
            DayCard(dayContent = dayContent, logging = logging, highlightedIds = content.highlightedIds)
        }
    }
}

@Composable
private fun PlanSelector(
    plans: List<Plan>,
    selectedPlanId: String,
    onSelectPlan: (String) -> Unit,
) {
    val type = RecompTheme.typography
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "PLANS", style = type.kicker, color = colors.volt)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            plans.forEach { plan ->
                PlanChip(
                    plan = plan,
                    selected = plan.id == selectedPlanId,
                    onSelect = { onSelectPlan(plan.id) },
                )
            }
        }
    }
}

@Composable
private fun PlanChip(
    plan: Plan,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    val background = if (selected) colors.volt else colors.panel
    val textColor = if (selected) colors.bg else colors.dim
    Text(
        text = plan.name.uppercase(),
        style = type.label,
        color = textColor,
        modifier =
            Modifier
                .background(background, RecompTheme.shapes.lg)
                .border(spacing.border, colors.line, RecompTheme.shapes.lg)
                .clickable(onClick = onSelect)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}

@Composable
private fun TopBar(
    onOpenKeys: () -> Unit,
    onOpenArtifacts: () -> Unit,
    onSignOut: () -> Unit,
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
        Text(text = "RACK", style = type.kicker, color = colors.volt)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TopBarAction(label = "ART", onClick = onOpenArtifacts)
            TopBarAction(label = "KEYS", onClick = onOpenKeys)
            TopBarAction(label = "SIGN OUT", onClick = onSignOut)
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
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RecompTheme.colors.volt)
    }
}

@Composable
private fun CenterMessage(message: String) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, style = RecompTheme.typography.body, color = colors.mutedEmpty)
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
        Text(text = message, style = type.body, color = colors.legs)
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
