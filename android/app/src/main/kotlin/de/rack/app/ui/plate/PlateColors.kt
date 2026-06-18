package de.rack.app.ui.plate

import androidx.compose.ui.graphics.Color
import de.rack.app.domain.formatPlateKg
import de.rack.app.ui.theme.RecompColors
import java.math.BigDecimal

/**
 * Resolves a kg plate denomination to one of the Recomp accent hues for the barbell diagram
 * and the per-side dots. The mapping is fixed per denomination so a plate always reads in the
 * same color; weights outside the canonical set fall back to volt. These are the category
 * tokens (push/pull/legs/superset) reused for visual distinction only — no new hues are
 * invented (docs/design-tokens.md). The 20 / 10 / 2.5 kg colors match the design reference.
 */
fun RecompColors.plateColor(weightKg: BigDecimal): Color =
    when (formatPlateKg(weightKg)) {
        "25" -> superset
        "20" -> legs
        "15" -> volt
        "10" -> pull
        "5" -> superset
        "2.5" -> volt
        "1.25" -> pull
        else -> volt
    }
