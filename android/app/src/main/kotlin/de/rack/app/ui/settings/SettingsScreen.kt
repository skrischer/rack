package de.rack.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.rack.app.BuildConfig
import de.rack.app.domain.ReminderPreferences
import de.rack.app.domain.UserSettings
import de.rack.app.domain.WeightUnit
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompSegmentedToggle
import de.rack.app.ui.theme.RecompTheme
import de.rack.app.ui.theme.recompClick
import java.time.DayOfWeek

/**
 * Recomp-styled Settings screen (docs/specs/spec-recomp-ux-overhaul.md, settings.html): a
 * stack of `.card`s with panel-elevated heads and divided rows — the weight-unit and theme
 * segmented toggles, the four rest-timer steppers, the reminder section, and the
 * profile/account block, over a version footer. Purely presentational — it renders [state]
 * and emits each edit / retry / back event upward; no Supabase access and no business logic
 * live here, and every value is read from the ViewModel's StateFlow.
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
                is SettingsUiState.Loading -> RecompLoading()
                is SettingsUiState.Error -> RecompError(message = state.message, onRetry = actions.onRetry)
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
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item { UnitSection(unit = settings.weightUnit, onSelect = actions.onSelectUnit) }
        item { ThemeSection(theme = settings.theme, onSelect = actions.onSelectTheme) }
        item { RestDefaultsSection(settings = settings, onSet = actions.onSetRestSeconds) }
        item { ReminderSection(prefs = reminder, actions = reminderActions) }
        item { ProfileSection(settings = settings, email = email, onCommit = actions.onDisplayNameChange) }
        item { SettingsFooter() }
    }
}

@Composable
private fun UnitSection(
    unit: WeightUnit,
    onSelect: (WeightUnit) -> Unit,
) {
    SettingsCard(title = "Gewichtseinheit") {
        SettingsRow(label = "Einheit") {
            RecompSegmentedToggle(
                options = UNIT_OPTIONS.map { it.second },
                selectedIndex = UNIT_OPTIONS.indexOfFirst { it.first == unit },
                onSelect = { onSelect(UNIT_OPTIONS[it].first) },
            )
        }
    }
}

@Composable
private fun ThemeSection(
    theme: String,
    onSelect: (String) -> Unit,
) {
    SettingsCard(title = "Darstellung") {
        SettingsRow(label = "Theme") {
            RecompSegmentedToggle(
                options = THEME_OPTIONS.map { it.second },
                selectedIndex = THEME_OPTIONS.indexOfFirst { it.first == theme }.coerceAtLeast(0),
                onSelect = { onSelect(THEME_OPTIONS[it].first) },
            )
        }
    }
}

@Composable
private fun RestDefaultsSection(
    settings: UserSettings,
    onSet: (RestDefaultKind, Int) -> Unit,
) {
    SettingsCard(title = "Pausen-Defaults") {
        SettingsRow(label = "Verbund") {
            RestStepper(seconds = settings.restCompoundSeconds) { onSet(RestDefaultKind.COMPOUND, it) }
        }
        SettingsRow(label = "Isolation") {
            RestStepper(seconds = settings.restIsolationSeconds) { onSet(RestDefaultKind.ISOLATION, it) }
        }
        SettingsRow(label = "Supersatz") {
            RestStepper(seconds = settings.restSupersetSeconds) { onSet(RestDefaultKind.SUPERSET, it) }
        }
        SettingsRow(label = "Zirkel") {
            RestStepper(seconds = settings.restCircuitSeconds) { onSet(RestDefaultKind.CIRCUIT, it) }
        }
    }
}

@Composable
private fun ProfileSection(
    settings: UserSettings,
    email: String,
    onCommit: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    SettingsCard(title = "Profil") {
        SettingsBlock { DisplayNameField(displayName = settings.displayName, onCommit = onCommit) }
        SettingsRow(label = "Mail") {
            Text(
                text = email.takeIf { it.isNotBlank() } ?: "Unbekannt",
                style = RecompTheme.typography.label,
                color = colors.dim,
            )
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(modifier = Modifier.fillMaxWidth().background(colors.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.gutter, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack = onBack)
            Spacer(Modifier.width(spacing.md))
            Text(text = "EINSTELLUNGEN", style = RecompTheme.typography.kicker, color = colors.volt)
        }
        RecompDivider()
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier =
            Modifier
                .size(BACK_BUTTON_SIZE)
                .recompClick(onClick = onBack)
                .border(spacing.border, colors.line, RecompTheme.shapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "‹", style = RecompTheme.typography.exerciseName, color = colors.dim)
    }
}

@Composable
private fun SettingsFooter() {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Rack · v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
            style = RecompTheme.typography.history,
            color = colors.dim,
            textAlign = TextAlign.Center,
        )
    }
}

private val BACK_BUTTON_SIZE = 34.dp

/** Weight-unit options in display order; storage stays canonical kg (see [WeightUnit]). */
private val UNIT_OPTIONS =
    listOf(
        WeightUnit.KG to "KG",
        WeightUnit.LB to "LB",
    )

/** Theme options in display order; both resolve to the Recomp dark palette this phase. */
private val THEME_OPTIONS =
    listOf(
        UserSettings.THEME_DARK to "Dark",
        UserSettings.THEME_SYSTEM to "System",
    )
