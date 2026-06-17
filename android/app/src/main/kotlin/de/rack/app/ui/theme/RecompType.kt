package de.rack.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.rack.app.R

/**
 * Archivo (display/body) and Spline Sans Mono (labels/metrics) bundled as app font
 * resources — no runtime font fetch (docs/design-tokens.md). Weights match the
 * token source: Archivo 400/600/800/900, Spline Sans Mono 400/500/600.
 */
val ArchivoFamily: FontFamily =
    FontFamily(
        Font(R.font.archivo_regular, FontWeight.Normal),
        Font(R.font.archivo_semibold, FontWeight.SemiBold),
        Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
        Font(R.font.archivo_black, FontWeight.Black),
    )

val SplineSansMonoFamily: FontFamily =
    FontFamily(
        Font(R.font.spline_sans_mono_regular, FontWeight.Normal),
        Font(R.font.spline_sans_mono_medium, FontWeight.Medium),
        Font(R.font.spline_sans_mono_semibold, FontWeight.SemiBold),
    )

/**
 * The Recomp type scale, encoding the px→role table in docs/design-tokens.md. Each
 * style fixes family, weight, size, line height, and tracking; uppercase casing is
 * applied at the call site (`String.uppercase()`), not baked into the style.
 *
 * Rule of thumb from the tokens: uppercase mono labels are positively tracked;
 * large Archivo headings are negatively tracked.
 */
@Immutable
data class RecompTypography(
    // Archivo display/body.
    val hero: TextStyle,
    val dayTitle: TextStyle,
    val exerciseName: TextStyle,
    val intro: TextStyle,
    val body: TextStyle,
    val cue: TextStyle,
    // Spline Sans Mono labels/metrics.
    val loadValue: TextStyle,
    val noteHeading: TextStyle,
    val kicker: TextStyle,
    val label: TextStyle,
    val lastTime: TextStyle,
    val history: TextStyle,
    val supersetHeader: TextStyle,
    val caption: TextStyle,
)

val recompTypography: RecompTypography =
    RecompTypography(
        hero =
            TextStyle(
                fontFamily = ArchivoFamily,
                fontWeight = FontWeight.Black,
                fontSize = 58.sp,
                lineHeight = 55.sp,
                letterSpacing = (-0.02).em,
            ),
        dayTitle =
            TextStyle(
                fontFamily = ArchivoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.01).em,
            ),
        exerciseName =
            TextStyle(
                fontFamily = ArchivoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        intro =
            TextStyle(
                fontFamily = ArchivoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        body =
            TextStyle(
                fontFamily = ArchivoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
        cue =
            TextStyle(
                fontFamily = ArchivoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
            ),
        loadValue =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
        noteHeading =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.12.em,
            ),
        kicker =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.22.em,
            ),
        label =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.06.em,
            ),
        lastTime =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            ),
        history =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
        supersetHeader =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.07.em,
            ),
        caption =
            TextStyle(
                fontFamily = SplineSansMonoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.08.em,
            ),
    )
