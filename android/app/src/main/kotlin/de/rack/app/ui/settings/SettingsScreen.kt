package de.rack.app.ui.settings

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.ReminderPreferences
import de.rack.app.domain.UserSettings
import de.rack.app.domain.WeightUnit
import de.rack.app.ui.theme.RecompTheme
import java.time.DayOfWeek

/**
 * Recomp-styled Settings screen (Phase 12, docs/specs/spec-settings.md): edits the
 * weight unit, theme, the four rest-timer defaults, and the editable display name,
 * and shows the read-only Auth email. Purely presentational — it renders [state] and
 * emits each edit / retry / back event upward; no Supabase access and no business
 * logic live here, and every value is read from the ViewModel's StateFlow.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    reminder: ReminderPreferences,
    reminderActions: ReminderActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        SettingsTopBar(onBack = actions.onBack)
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is SettingsUiState.Loading -> CenterSpinner()
                is SettingsUiState.Error -> ErrorPane(message = state.message, onRetry = actions.onRetry)
                is SettingsUiState.Content ->
                    SettingsContent(
                        settings = state.settings,
                        email = state.email,
                        actions = actions,
                        reminder = reminder,
                        reminderActions = reminderActions,
                    )
            }
        }
    }
}

/** The settings edit + navigation events, bundled so the screen takes one parameter. */
@Immutable
data class SettingsActions(
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
    val onSelectUnit: (WeightUnit) -> Unit,
    val onSelectTheme: (String) -> Unit,
    val onSetRestSeconds: (RestDefaultKind, Int) -> Unit,
    val onDisplayNameChange: (String) -> Unit,
)

/** The workout-reminder edit intents, bundled so the reminder section takes one parameter. */
@Immutable
data class ReminderActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onToggleDay: (DayOfWeek) -> Unit,
    val onSetTime: (Int, Int) -> Unit,
)

@Composable
private fun SettingsContent(
    settings: UserSettings,
    email: String,
    actions: SettingsActions,
    reminder: ReminderPreferences,
    reminderActions: ReminderActions,
) {
    val spacing = RecompTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { UnitSection(unit = settings.weightUnit, onSelect = actions.onSelectUnit) }
        item { ThemeSection(theme = settings.theme, onSelect = actions.onSelectTheme) }
        item { RestDefaultsSection(settings = settings, onSet = actions.onSetRestSeconds) }
        item { ReminderSection(prefs = reminder, actions = reminderActions) }
        item { ProfileSection(settings = settings, email = email, onCommit = actions.onDisplayNameChange) }
    }
}

@Composable
private fun UnitSection(
    unit: WeightUnit,
    onSelect: (WeightUnit) -> Unit,
) {
    SettingsSection(title = "WEIGHT UNIT") {
        SegmentedToggle(
            options =
                listOf(
                    ToggleOption(WeightUnit.KG, "KG"),
                    ToggleOption(WeightUnit.LB, "LB"),
                ),
            selected = unit,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun ThemeSection(
    theme: String,
    onSelect: (String) -> Unit,
) {
    SettingsSection(title = "THEME") {
        SegmentedToggle(
            options =
                listOf(
                    ToggleOption(UserSettings.THEME_DARK, "DARK"),
                    ToggleOption(UserSettings.THEME_SYSTEM, "SYSTEM"),
                ),
            selected = theme,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun RestDefaultsSection(
    settings: UserSettings,
    onSet: (RestDefaultKind, Int) -> Unit,
) {
    SettingsSection(title = "REST DEFAULTS") {
        RestDefaultRow(
            label = "COMPOUND",
            seconds = settings.restCompoundSeconds,
            onChange = { onSet(RestDefaultKind.COMPOUND, it) },
        )
        RestDefaultRow(
            label = "ISOLATION",
            seconds = settings.restIsolationSeconds,
            onChange = { onSet(RestDefaultKind.ISOLATION, it) },
        )
        RestDefaultRow(
            label = "SUPERSET",
            seconds = settings.restSupersetSeconds,
            onChange = { onSet(RestDefaultKind.SUPERSET, it) },
        )
        RestDefaultRow(
            label = "CIRCUIT",
            seconds = settings.restCircuitSeconds,
            onChange = { onSet(RestDefaultKind.CIRCUIT, it) },
        )
    }
}

@Composable
private fun ProfileSection(
    settings: UserSettings,
    email: String,
    onCommit: (String) -> Unit,
) {
    SettingsSection(title = "PROFILE") {
        DisplayNameField(displayName = settings.displayName, onCommit = onCommit)
        EmailRow(email = email)
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
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
        Text(text = "SETTINGS", style = type.kicker, color = colors.volt)
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

/**
 * Pure UI clamping for the rest-default stepper: edits stay within [MIN]..[MAX] and
 * move by [STEP] seconds so a stored default is always a clean increment. This is a
 * presentation constraint on the control, not the timer's domain logic.
 */
object RestSeconds {
    const val MIN = 15
    const val MAX = 600
    const val STEP = 15

    fun increment(seconds: Int): Int = (seconds + STEP).coerceAtMost(MAX)

    fun decrement(seconds: Int): Int = (seconds - STEP).coerceAtLeast(MIN)
}
