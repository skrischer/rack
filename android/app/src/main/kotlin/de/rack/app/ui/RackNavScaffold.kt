package de.rack.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.rack.app.ui.theme.RecompMenuItem
import de.rack.app.ui.theme.RecompMenuItemData
import de.rack.app.ui.theme.RecompNavDock
import de.rack.app.ui.theme.RecompNavTile
import de.rack.app.ui.theme.RecompOverflowSheet
import de.rack.app.ui.theme.RecompSheetSectionHeader
import de.rack.app.ui.theme.RecompTheme
import kotlinx.coroutines.launch

private val NavTileIconSize = 22.dp
private val OverflowIconSize = 20.dp
private const val SCRIM_ALPHA = 0.72f

/** A single quick-nav tile descriptor: its label, the route pattern it is active on, the concrete
 * route to navigate to, and the dock icon. */
private data class NavTile(
    val label: String,
    val routePattern: String,
    val target: String,
    val icon: ImageVector,
)

/** The four quick-nav tiles shared by the collapsed dock and the off-canvas first row. */
private val NAV_TILES =
    listOf(
        NavTile("Plan", RackDestinations.PLAN, RackDestinations.PLAN, RackNavIcons.Plan),
        NavTile("Verlauf", RackDestinations.CALENDAR, RackDestinations.calendarRoute(), RackNavIcons.Calendar),
        NavTile("Statistik", RackDestinations.HOME, RackDestinations.HOME, RackNavIcons.Stats),
        NavTile("Artifacts", RackDestinations.ARTIFACTS, RackDestinations.ARTIFACTS, RackNavIcons.Artifacts),
    )

/**
 * App navigation chrome (kit `menu.html`): wraps the NavHost [content] in a column with the pull-up
 * bottom-nav dock pinned beneath it. The dock is shown only for the top-level destinations
 * ([RackDestinations.isDockVisible]); detail/flow/session screens get the bare content. Tapping the
 * dock handle raises the draggable off-canvas overflow. All routing flows through [onNavigate] and
 * [onSignOut] — the scaffold holds no business logic.
 */
@Composable
fun RackNavScaffold(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    content: @Composable () -> Unit,
) {
    var overflowOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) { content() }
        if (RackDestinations.isDockVisible(currentRoute)) {
            RecompNavDock(onHandleClick = { overflowOpen = true }) {
                NavTiles(currentRoute = currentRoute, onNavigate = onNavigate)
            }
        }
    }
    if (overflowOpen) {
        NavOverflowSheet(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            onSignOut = onSignOut,
            onDismiss = { overflowOpen = false },
        )
    }
}

@Composable
private fun RowScope.NavTiles(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NAV_TILES.forEach { tile ->
        RecompNavTile(
            label = tile.label,
            selected = currentRoute == tile.routePattern,
            onClick = { onNavigate(tile.target) },
        ) {
            Icon(imageVector = tile.icon, contentDescription = null, modifier = Modifier.size(NavTileIconSize))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavOverflowSheet(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RecompTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { action ->
        action()
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    val onNavigateAndClose: (String) -> Unit = { route -> close { onNavigate(route) } }
    val onSignOutAndClose: () -> Unit = { close(onSignOut) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = colors.bg.copy(alpha = SCRIM_ALPHA),
        shape = RectangleShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        RecompOverflowSheet(
            tiles = { NavTiles(currentRoute = currentRoute, onNavigate = onNavigateAndClose) },
            content = { OverflowLinks(onNavigate = onNavigateAndClose, onSignOut = onSignOutAndClose) },
        )
    }
}

/** The off-canvas overflow rows below the quick-nav tiles (kit `menu.html`). */
@Composable
private fun OverflowLinks(
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    // "Übungen"/exercise-catalog overflow link deferred until a catalog screen exists
    // (out of UX-overhaul scope; future feature spec).
    RecompMenuItem(
        data = RecompMenuItemData("API-Keys", "MCP-Zugang für deinen Agent", chevron = true),
        onClick = { onNavigate(RackDestinations.KEYS) },
        leading = { OverflowIcon(RackNavIcons.Key) },
    )
    RecompMenuItem(
        data = RecompMenuItemData("Einstellungen", "Einheit, Theme, Pausen, Reminder", chevron = true),
        onClick = { onNavigate(RackDestinations.SETTINGS) },
        leading = { OverflowIcon(RackNavIcons.Settings) },
    )
    RecompSheetSectionHeader(text = "Account")
    RecompMenuItem(
        data = RecompMenuItemData("Abmelden"),
        onClick = onSignOut,
        leading = { OverflowIcon(RackNavIcons.Logout) },
    )
}

@Composable
private fun OverflowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = RecompTheme.colors.dim,
        modifier = Modifier.size(OverflowIconSize),
    )
}
