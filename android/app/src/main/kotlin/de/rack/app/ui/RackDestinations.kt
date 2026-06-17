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
}
