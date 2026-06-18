package de.rack.app.data

import de.rack.app.domain.UserSettings
import de.rack.app.domain.WeightUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the `user_settings` Postgrest row, mapping its `snake_case`
 * columns to serializable Kotlin types. An internal detail of the repository
 * layer; callers receive the camelCase [UserSettings] domain model.
 */
@Serializable
internal data class UserSettingsDto(
    @SerialName("weight_unit") val weightUnit: String,
    val theme: String,
    @SerialName("rest_compound_seconds") val restCompoundSeconds: Int,
    @SerialName("rest_isolation_seconds") val restIsolationSeconds: Int,
    @SerialName("rest_superset_seconds") val restSupersetSeconds: Int,
    @SerialName("rest_circuit_seconds") val restCircuitSeconds: Int,
    @SerialName("display_name") val displayName: String,
) {
    fun toDomain(): UserSettings =
        UserSettings(
            weightUnit = WeightUnit.fromWire(weightUnit),
            theme = theme,
            restCompoundSeconds = restCompoundSeconds,
            restIsolationSeconds = restIsolationSeconds,
            restSupersetSeconds = restSupersetSeconds,
            restCircuitSeconds = restCircuitSeconds,
            displayName = displayName,
        )
}

/**
 * The row written on provisioning and on update. The client supplies `user_id`
 * so the upsert lands on the PK row (idempotent) under the RLS
 * `with check (user_id = auth.uid())` policy; `source` is always `'app'`.
 * `updated_at` is stamped by the DB trigger on update, so it is not sent.
 */
@Serializable
internal data class UserSettingsUpsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("weight_unit") val weightUnit: String,
    val theme: String,
    @SerialName("rest_compound_seconds") val restCompoundSeconds: Int,
    @SerialName("rest_isolation_seconds") val restIsolationSeconds: Int,
    @SerialName("rest_superset_seconds") val restSupersetSeconds: Int,
    @SerialName("rest_circuit_seconds") val restCircuitSeconds: Int,
    @SerialName("display_name") val displayName: String,
    val source: String = "app",
) {
    companion object {
        fun from(
            userId: String,
            settings: UserSettings,
        ): UserSettingsUpsertDto =
            UserSettingsUpsertDto(
                userId = userId,
                weightUnit = settings.weightUnit.wire,
                theme = settings.theme,
                restCompoundSeconds = settings.restCompoundSeconds,
                restIsolationSeconds = settings.restIsolationSeconds,
                restSupersetSeconds = settings.restSupersetSeconds,
                restCircuitSeconds = settings.restCircuitSeconds,
                displayName = settings.displayName,
            )
    }
}
