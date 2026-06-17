package de.rack.app.ui.artifacts

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.rack.app.domain.Artifact
import de.rack.app.ui.theme.RecompTheme

/**
 * Read-only artifacts view: the signed-in user's agent-authored visualization
 * artifacts (name, type, date) newest first, each agent-written one marked with the
 * volt agent badge. Pull-to-refresh re-fetches the list. Purely presentational — it
 * renders [state] and emits refresh / retry / back events upward; no Supabase access
 * and no business logic live here. The viewer that renders an artifact's bytes is #44.
 */
@Composable
fun ArtifactScreen(
    state: ArtifactUiState,
    isRefreshing: Boolean,
    actions: ArtifactActions,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        ArtifactsTopBar(onBack = actions.onBack)
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is ArtifactUiState.Loading -> CenterSpinner()
                is ArtifactUiState.Error -> ErrorPane(message = state.message, onRetry = actions.onRetry)
                is ArtifactUiState.Content ->
                    ArtifactListPane(
                        artifacts = state.artifacts,
                        isRefreshing = isRefreshing,
                        onRefresh = actions.onRefresh,
                        onOpen = actions.onOpen,
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtifactListPane(
    artifacts: List<Artifact>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val spacing = RecompTheme.spacing
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item { Text(text = "ARTIFACTS", style = RecompTheme.typography.kicker, color = RecompTheme.colors.volt) }
            if (artifacts.isEmpty()) {
                item { EmptyState() }
            } else {
                items(artifacts, key = { it.id }) { artifact ->
                    ArtifactRow(artifact = artifact, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun ArtifactRow(
    artifact: Artifact,
    onOpen: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.panel, RecompTheme.shapes.xl)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl)
                .clickable { onOpen(artifact.id) }
                .padding(spacing.cardInsetH),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = artifact.name?.takeIf { it.isNotBlank() } ?: "Untitled artifact",
                style = type.exerciseName,
                color = colors.txt,
            )
            if (artifact.isAgentAuthored) {
                AgentBadge()
            }
        }
        Text(text = typeLabel(artifact.type), style = type.loadValue, color = colors.volt)
        Text(text = "CREATED ${shortDate(artifact.createdAt)}", style = type.caption, color = colors.dim)
    }
}

/** Volt #c8f23a agent badge, consistent with the app's agent-edit highlighting language. */
@Composable
private fun AgentBadge() {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Text(
        text = "AGENT",
        style = type.label,
        color = colors.bg,
        modifier =
            Modifier
                .background(colors.volt, RecompTheme.shapes.sm)
                .padding(horizontal = spacing.md, vertical = spacing.xxs),
    )
}

@Composable
private fun ArtifactsTopBar(onBack: () -> Unit) {
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
        Text(text = "ARTIFACTS", style = type.kicker, color = colors.volt)
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
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "No artifacts yet.", style = type.body, color = colors.mutedEmpty)
        Text(
            text = "Ask your agent to author a visualization, then pull to refresh.",
            style = type.body,
            color = colors.mutedEmpty,
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

/** Map the stored MIME type to a compact uppercase label for the row; fall back to the raw value. */
private fun typeLabel(type: String?): String =
    when (type) {
        "text/html" -> "HTML"
        "image/svg+xml" -> "SVG"
        "image/png" -> "PNG"
        else -> type?.takeIf { it.isNotBlank() }?.uppercase() ?: "UNKNOWN"
    }

/** Trim an ISO timestamp to its date portion for the compact list rows. */
private fun shortDate(iso: String): String = iso.substringBefore('T').ifBlank { iso }
