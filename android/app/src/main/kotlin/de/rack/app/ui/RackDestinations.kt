package de.rack.app.ui

/**
 * Navigation routes for the single-Activity Compose graph. Login is the signed-out
 * entry; Plan is the signed-in home. Signed-in vs signed-out routing is wired in
 * the auth issue (#21).
 */
object RackDestinations {
    const val LOGIN = "login"
    const val PLAN = "plan"
}
