package de.rack.app.domain

/**
 * The signed-in user's persisted app preferences (`user_settings`, one row per
 * user) in the camelCase domain shape; see docs/specs/spec-settings.md.
 *
 * [weightUnit] is a display/entry preference only — weights stay canonically in
 * kilograms in storage. The four `rest*Seconds` values are the rest-timer
 * defaults the Phase-8 timer reads. [theme] is a `dark`/`system` choice that
 * resolves to the Recomp dark palette this phase. [displayName] is the editable
 * profile name (the account email is read from Supabase Auth, not stored here).
 */
data class UserSettings(
    val weightUnit: WeightUnit,
    val theme: String,
    val restCompoundSeconds: Int,
    val restIsolationSeconds: Int,
    val restSupersetSeconds: Int,
    val restCircuitSeconds: Int,
    val displayName: String,
) {
    companion object {
        /** The default row provisioned on first access; mirrors the DB column defaults. */
        val DEFAULT =
            UserSettings(
                weightUnit = WeightUnit.KG,
                theme = THEME_DARK,
                restCompoundSeconds = DEFAULT_REST_COMPOUND_SECONDS,
                restIsolationSeconds = DEFAULT_REST_ISOLATION_SECONDS,
                restSupersetSeconds = DEFAULT_REST_SUPERSET_SECONDS,
                restCircuitSeconds = DEFAULT_REST_CIRCUIT_SECONDS,
                displayName = "",
            )

        const val THEME_DARK = "dark"

        // Default rest-timer durations (seconds), matching the user_settings DB
        // column defaults: compound 150 / isolation 90 / superset 90 / circuit 45.
        const val DEFAULT_REST_COMPOUND_SECONDS = 150
        const val DEFAULT_REST_ISOLATION_SECONDS = 90
        const val DEFAULT_REST_SUPERSET_SECONDS = 90
        const val DEFAULT_REST_CIRCUIT_SECONDS = 45
    }
}
