package de.rack.app.ui

/**
 * Navigation routes for the single-Activity Compose graph. Login is the signed-out
 * entry; Plan is the signed-in home. Signed-in vs signed-out routing is wired in
 * the auth issue (#21).
 */
object RackDestinations {
    const val LOGIN = "login"
    const val PLAN = "plan"
    const val KEYS = "keys"
    const val ARTIFACTS = "artifacts"

    /** Settings: weight unit, theme, rest-timer defaults, and profile (Phase 12). */
    const val SETTINGS = "settings"

    /** Home/overview dashboard: weekly volume, streak, and recent sessions (Phase 11). */
    const val HOME = "home"

    /** Optional navigation argument deep-linking the calendar to a specific logged date. */
    const val CALENDAR_DATE_ARG = "date"

    /**
     * Calendar/history route template (Phase 11). The [CALENDAR_DATE_ARG] query argument is
     * optional: absent it opens on the latest logged month; present (an ISO date) it deep
     * links from a Home recent-session row to that day, selected and expanded.
     */
    const val CALENDAR = "calendar?$CALENDAR_DATE_ARG={$CALENDAR_DATE_ARG}"

    /** Builds a concrete calendar route, optionally deep-linking to [date] (ISO-8601). */
    fun calendarRoute(date: String? = null): String =
        if (date == null) "calendar" else "calendar?$CALENDAR_DATE_ARG=$date"

    /** Navigation argument carrying the opened artifact's id into the viewer route. */
    const val ARTIFACT_ID_ARG = "artifactId"

    /** Viewer route template; [artifactViewerRoute] fills in a concrete id. */
    const val ARTIFACT_VIEWER = "artifacts/{$ARTIFACT_ID_ARG}"

    /** Builds a concrete viewer route for the given artifact id. */
    fun artifactViewerRoute(artifactId: String): String = "artifacts/$artifactId"

    /** Navigation argument carrying the tapped catalog exercise id into the detail route. */
    const val EXERCISE_ID_ARG = "exerciseId"

    /** Detail route template; [exerciseDetailRoute] fills in a concrete catalog id. */
    const val EXERCISE_DETAIL = "exercise/{$EXERCISE_ID_ARG}"

    /** Builds a concrete detail route for the given catalog exercise id. */
    fun exerciseDetailRoute(exerciseId: String): String = "exercise/$exerciseId"

    /** Per-exercise progress route template; [exerciseProgressRoute] fills in a concrete id. */
    const val EXERCISE_PROGRESS = "exercise/{$EXERCISE_ID_ARG}/progress"

    /** Builds a concrete progress route for the given catalog exercise id. */
    fun exerciseProgressRoute(exerciseId: String): String = "exercise/$exerciseId/progress"

    /** Optional navigation argument pre-filling the plate calculator's target weight (kg). */
    const val PLATE_CALC_WEIGHT_ARG = "weight"

    /**
     * Plate-calculator route template (Phase 13). The [PLATE_CALC_WEIGHT_ARG] query argument
     * is optional: absent it opens with an empty target; present it pre-fills the target from
     * the logging surface's current working weight (kg).
     */
    const val PLATE_CALC = "plate-calc?$PLATE_CALC_WEIGHT_ARG={$PLATE_CALC_WEIGHT_ARG}"

    /** Builds a concrete plate-calculator route, optionally pre-filling [weight] (kg). */
    fun plateCalcRoute(weight: String? = null): String =
        if (weight.isNullOrBlank()) "plate-calc" else "plate-calc?$PLATE_CALC_WEIGHT_ARG=$weight"

    /** Navigation argument carrying the plan day id into the guided session player. */
    const val SESSION_DAY_ID_ARG = "dayId"

    /** Session-player route template; [sessionRoute] fills in a concrete plan day id. */
    const val SESSION = "session/{$SESSION_DAY_ID_ARG}"

    /** Builds a concrete session-player route for the given plan day id. */
    fun sessionRoute(dayId: String): String = "session/$dayId"
}
