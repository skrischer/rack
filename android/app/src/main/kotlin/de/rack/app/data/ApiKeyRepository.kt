package de.rack.app.data

import de.rack.app.BuildConfig
import de.rack.app.domain.ApiKey
import de.rack.app.domain.CreatedApiKey
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The single access point for rack-MCP API-key management.
 *
 * Create, list, and revoke all go through the JWT-authenticated MCP admin
 * endpoints ([baseUrl] from [BuildConfig.MCP_BASE_URL]) — never direct Supabase
 * Postgrest access to `api_keys` and never the service-role key. Each call sends
 * the signed-in user's Supabase JWT as a bearer token; the MCP resolves identity
 * from the token alone (no `user_id` is ever sent). The create endpoint returns
 * the plaintext exactly once; this layer hands it straight to the caller without
 * persisting or logging it. ViewModels consume this; Composables never do.
 *
 * [baseUrl] is also surfaced ([mcpBaseUrl]) so onboarding can build the client
 * connection details from the very same endpoint constant the admin calls use.
 */
class ApiKeyRepository(
    private val client: SupabaseClient,
    private val baseUrl: String = BuildConfig.MCP_BASE_URL,
    private val http: HttpClient = defaultHttpClient(),
) {
    /** The rack-MCP base URL backing all admin calls; onboarding derives its config from it. */
    val mcpBaseUrl: String
        get() = baseUrl

    /** Lists the signed-in user's keys, newest first (non-secret fields only). */
    suspend fun list(): List<ApiKey> {
        val response = http.get(keysUrl) { authorize() }
        requireSuccess(response)
        return response.body<ListResponse>().keys.map(ApiKeyDto::toDomain)
    }

    /** Mints a named key and returns its plaintext exactly once. */
    suspend fun create(name: String): CreatedApiKey {
        val response =
            http.post(keysUrl) {
                authorize()
                contentType(ContentType.Application.Json)
                setBody(CreateRequest(name = name.trim()))
            }
        requireSuccess(response)
        return CreatedApiKey(plaintext = response.body<CreateResponse>().key)
    }

    /** Soft-deletes the caller's key; returns false when no owned key matched. */
    suspend fun revoke(keyId: String): Boolean {
        val response = http.post("$keysUrl/$keyId/revoke") { authorize() }
        if (response.status == HttpStatusCode.NotFound) return false
        requireSuccess(response)
        return true
    }

    private val keysUrl: String
        get() = "${baseUrl.trimEnd('/')}$ADMIN_KEYS_PATH"

    private fun HttpRequestBuilder.authorize() {
        val token = checkNotNull(client.auth.currentAccessTokenOrNull()) { NOT_SIGNED_IN }
        bearerAuth(token)
    }

    private fun requireSuccess(response: HttpResponse) {
        check(response.status.isSuccess()) { "rack-MCP request failed: ${response.status}" }
    }

    private companion object {
        const val ADMIN_KEYS_PATH = "/admin/keys"
        const val NOT_SIGNED_IN = "Not signed in."

        fun defaultHttpClient(): HttpClient =
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
    }
}

/** Mirrors the MCP list payload `{ keys: [...] }`; non-secret fields only. */
@Serializable
private data class ListResponse(
    val keys: List<ApiKeyDto>,
)

/** A single `api_keys` summary row from the MCP list endpoint. */
@Serializable
private data class ApiKeyDto(
    val id: String,
    val name: String? = null,
    val keyPrefix: String? = null,
    val createdAt: String,
    val lastUsedAt: String? = null,
    val revoked: Boolean,
) {
    fun toDomain(): ApiKey =
        ApiKey(
            id = id,
            name = name,
            keyPrefix = keyPrefix,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            revoked = revoked,
        )
}

/** Mint request body; carries only the human-readable name (no `user_id`). */
@Serializable
private data class CreateRequest(
    val name: String,
)

/** Mint response `{ key: "rack_..." }`: the plaintext returned exactly once. */
@Serializable
private data class CreateResponse(
    val key: String,
)
