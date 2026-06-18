package de.rack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val NavHandleWidth = 40.dp
private val NavHandleHeight = 4.dp
private val AvatarSize = 20.dp
private val SheetCorner = 18.dp
private val NavLabelSize = 9.5.sp

private val HandleShape = RoundedCornerShape(percent = 50)
private val SheetShape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)

/**
 * The collapsed bottom-nav dock (kit `.navdock`): an elevated bar with a pull handle over a
 * row of quick-nav tiles. Tapping the handle raises the off-canvas overflow ([onHandleClick]);
 * place [RecompNavTile]s in [content]. Detail/flow screens simply omit the dock.
 */
@Composable
fun RecompNavDock(
    modifier: Modifier = Modifier,
    onHandleClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val colors = RecompTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.panelElevated)) {
        RecompDivider()
        NavHandle(onClick = onHandleClick)
        Row(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun NavHandle(onClick: () -> Unit) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Box(
        modifier = Modifier.fillMaxWidth().recompClick(onClick = onClick).padding(vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(NavHandleWidth)
                    .height(NavHandleHeight)
                    .background(colors.line, HandleShape),
        )
    }
}

/**
 * A single quick-nav tile (kit `.bottomnav a`): a stacked [icon] + [label], volt when
 * [selected] and dim otherwise. The active tint is provided through [LocalContentColor], so an
 * `Icon` in the [icon] slot picks it up with its default tint. Must be placed in a [RowScope]
 * (the dock row); it claims an equal share of the width.
 */
@Composable
fun RowScope.RecompNavTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    val tint = if (selected) colors.volt else colors.dim
    Column(
        modifier =
            modifier
                .weight(1f)
                .recompClick(onClick = onClick)
                .padding(start = spacing.xxs, end = spacing.xxs, top = spacing.sm, bottom = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) { icon() }
        Text(
            text = label.uppercase(),
            style = RecompTheme.typography.caption.copy(fontSize = NavLabelSize, letterSpacing = 0.05.em),
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The pulled-up overflow sheet (kit `.sheet`): a top-rounded panel with a grip, the same
 * quick-nav tiles as the first row ([tiles]), then the overflow rows + account section in
 * [content]. Compose it from [RecompNavTile], [RecompMenuItem], [RecompSheetSectionHeader],
 * and [RecompAvatar]. The drag-to-open behavior lives in the navigation screen, not here.
 */
@Composable
fun RecompOverflowSheet(
    modifier: Modifier = Modifier,
    tiles: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RecompTheme.colors
    val spacing = RecompTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(colors.panel)
                .padding(bottom = spacing.md),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .width(NavHandleWidth)
                        .height(NavHandleHeight)
                        .background(colors.line, HandleShape),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), content = tiles)
        RecompDivider()
        content()
    }
}

/**
 * The data for an overflow-menu row ([RecompMenuItem]): a [title], an optional mono [subtitle],
 * and whether to show the navigable `›` [chevron] (kit `.menu-item .c`).
 */
data class RecompMenuItemData(
    val title: String,
    val subtitle: String? = null,
    val chevron: Boolean = false,
)

/**
 * One overflow-menu row (kit `.menu-item`): a [leading] icon/avatar slot beside the row's
 * [data] (title + optional subtitle + optional chevron). Separated from the row above by a 1px
 * divider; tappable via [onClick].
 */
@Composable
fun RecompMenuItem(
    data: RecompMenuItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
) {
    val colors = RecompTheme.colors
    val type = RecompTheme.typography
    val spacing = RecompTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        RecompDivider()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .recompClick(onClick = onClick)
                    .padding(horizontal = spacing.xl, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(text = data.title, style = type.intro, color = colors.txt)
                data.subtitle?.let { Text(text = it, style = type.history, color = colors.dim) }
            }
            if (data.chevron) {
                RecompChevron()
            }
        }
    }
}

/** The overflow-sheet section heading (kit `.sh-h`): a tracked, uppercased dim mono label. */
@Composable
fun RecompSheetSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = RecompTheme.spacing
    Text(
        text = text.uppercase(),
        style = RecompTheme.typography.history.copy(letterSpacing = 0.12.em),
        color = RecompTheme.colors.dim,
        modifier = modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
    )
}

/** The small account avatar (kit `.avatar`): a volt monogram on a bordered elevated circle. */
@Composable
fun RecompAvatar(
    initial: String,
    modifier: Modifier = Modifier,
) {
    val colors = RecompTheme.colors
    Box(
        modifier =
            modifier
                .size(AvatarSize)
                .background(colors.panelElevated, HandleShape)
                .border(RecompTheme.spacing.border, colors.line, HandleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial.uppercase(),
            style = RecompTheme.typography.caption.copy(fontSize = 9.sp),
            color = colors.volt
        )
    }
}

/** The kit's `›` row affordance — a volt-dim mono chevron for navigable menu/list rows. */
@Composable
fun RecompChevron(modifier: Modifier = Modifier) {
    Text(
        text = "›",
        style = RecompTheme.typography.exerciseName.copy(fontFamily = SplineSansMonoFamily),
        color = RecompTheme.colors.voltDim,
        modifier = modifier,
    )
}
