package de.rack.app.ui.artifacts

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import de.rack.app.ui.theme.RecompTheme

/**
 * Full-screen viewer for one opened artifact. HTML and SVG render in a sandboxed
 * WebView (JavaScript on for interactive charts, but no file/content access and no
 * JS-to-native bridge); PNG renders in an image view. Loading shows a spinner; an
 * unrenderable or failed load shows a retry fallback. Purely presentational — it
 * renders [state] and emits retry / back events upward; the signed-URL fetch and
 * render-strategy choice live in the [ArtifactViewerViewModel].
 */
@Composable
fun ArtifactViewerScreen(
    state: ArtifactViewerUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        ViewerTopBar(title = titleOf(state), onBack = onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ArtifactViewerUiState.Loading -> CenterSpinner()
                is ArtifactViewerUiState.Error -> ViewerErrorPane(message = state.message, onRetry = onRetry)
                is ArtifactViewerUiState.Ready -> ArtifactContent(content = state.content)
            }
        }
    }
}

@Composable
private fun ArtifactContent(content: ArtifactViewerContent) {
    when (content) {
        is ArtifactViewerContent.WebContent -> SandboxedWebView(url = content.signedUrl)
        is ArtifactViewerContent.ImageContent ->
            Image(
                bitmap = content.image,
                contentDescription = "Artifact image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(RecompTheme.spacing.gutter),
            )
    }
}

/**
 * A locked-down [WebView]: JavaScript is enabled so agent-authored charts render,
 * but file and content URL access is disabled and no `addJavascriptInterface` bridge
 * is added, so the page cannot reach the device filesystem or call into the app. The
 * content is loaded over https from a signed URL, never a `file://` origin.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedWebView(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
            }
        },
        update = { webView -> webView.loadUrl(url) },
    )
}

@Composable
private fun ViewerTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = type.kicker, color = colors.volt)
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
private fun ViewerErrorPane(
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

private fun titleOf(state: ArtifactViewerUiState): String =
    when (state) {
        is ArtifactViewerUiState.Ready -> state.title
        is ArtifactViewerUiState.Error -> state.title
        ArtifactViewerUiState.Loading -> "ARTIFACT"
    }
