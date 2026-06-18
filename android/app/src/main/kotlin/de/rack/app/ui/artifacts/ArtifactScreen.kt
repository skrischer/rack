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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import de.rack.app.domain.Artifact
import de.rack.app.ui.theme.RecompBadge
import de.rack.app.ui.theme.RecompBadgeStyle
import de.rack.app.ui.theme.RecompChevron
import de.rack.app.ui.theme.RecompDivider
import de.rack.app.ui.theme.RecompEmpty
import de.rack.app.ui.theme.RecompError
import de.rack.app.ui.theme.RecompLoading
import de.rack.app.ui.theme.RecompTheme

/**
 * Read-only artifacts view: the signed-in user's agent-authored visualization
 * artifacts (name, type, date) newest first, each agent-written one marked with the
 * violet agent badge. Pull-to-refresh re-fetches the list. Purely presentational — it
 * renders [state] and emits refresh / retry / open / back events upward; no Supabase
 * access and no business logic live here. The viewer that renders an artifact's bytes
 * is the [ArtifactViewerScreen].
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
                is ArtifactUiState.Loading -> RecompLoading()
                is ArtifactUiState.Error -> RecompError(message = state.message, onRetry = actions.onRetry)
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
            item {
                Text(
                    text = "VOM AGENT ERSTELLT",
                    style = RecompTheme.typography.kicker,
                    color = RecompTheme.colors.volt,
                )
            }
            if (artifacts.isEmpty()) {
                item {
                    RecompEmpty(
                        text =
                            "Noch keine Artifacts.\n" +
                                "Bitte deinen Agent um eine Visualisierung, dann nach unten ziehen.",
                    )
                }
            } else {
                item { ArtifactCard(artifacts = artifacts, onOpen = onOpen) }
            }
            item { FooterNote() }
        }
    }
}

/**
 * The single artifacts card (kit `.card`): one navigable `.row` per artifact, each
 * separated by the pervasive 1px hairline, newest first.
 */
@Composable
private fun ArtifactCard(
    artifacts: List<Artifact>,
    onOpen: (String) -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RecompTheme.shapes.xl)
                .background(colors.panel)
                .border(spacing.border, colors.line, RecompTheme.shapes.xl),
    ) {
        artifacts.forEachIndexed { index, artifact ->
            if (index > 0) RecompDivider()
            ArtifactRow(artifact = artifact, onOpen = onOpen)
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(artifact.id) }
                .padding(horizontal = spacing.cardInsetH, vertical = spacing.rowInsetV),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = (artifact.name?.takeIf { it.isNotBlank() } ?: "Unbenanntes Artifact").uppercase(),
                style = type.label,
                color = colors.txt,
            )
            Text(text = metaLine(artifact), style = type.history, color = colors.dim)
        }
        if (artifact.isAgentAuthored) {
            RecompBadge(text = "Agent", style = RecompBadgeStyle.Agent)
        }
        RecompChevron()
    }
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

/** The kit `.foot` line: the pull-to-refresh hint, centered in the muted mono voice. */
@Composable
private fun FooterNote() {
    Text(
        text = "Zum Aktualisieren nach unten ziehen",
        style = RecompTheme.typography.caption,
        color = RecompTheme.colors.dim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "HTML · 17.06.2026" — the compact type + creation-date metadata line for a row. */
private fun metaLine(artifact: Artifact): String = "${typeLabel(artifact.type)} · ${germanDate(artifact.createdAt)}"

/** Map the stored MIME type to a compact uppercase label for the row; fall back to the raw value. */
private fun typeLabel(type: String?): String =
    when (type) {
        "text/html" -> "HTML"
        "image/svg+xml" -> "SVG"
        "image/png" -> "PNG"
        else -> type?.takeIf { it.isNotBlank() }?.uppercase() ?: "UNBEKANNT"
    }

/** The `yyyy-MM-dd` segment count an ISO date splits into before German reordering. */
private const val ISO_DATE_PART_COUNT = 3

/** Reformat an ISO timestamp's date part to the designs' German `dd.MM.yyyy`; fall back to the raw value. */
private fun germanDate(iso: String): String {
    val date = iso.substringBefore('T')
    val parts = date.split('-')
    return if (parts.size == ISO_DATE_PART_COUNT) "${parts[2]}.${parts[1]}.${parts[0]}" else date.ifBlank { iso }
}
