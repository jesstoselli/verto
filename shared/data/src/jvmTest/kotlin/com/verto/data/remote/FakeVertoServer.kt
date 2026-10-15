package com.verto.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * In-memory stand-in for the Verto API with the same rules that matter to the client:
 * refresh tokens rotate and a reused one is refused, push upserts by (type, id) with a growing
 * version, pull pages by version.
 */
class FakeVertoServer {

    data class Stored(val entityType: String, val id: String, val payload: JsonElement, val deletedAt: String?, val version: Long)

    val email = "jess@verto.app"
    val password = "senha-certa"

    val validAccess = mutableSetOf<String>()
    private val validRefresh = mutableSetOf<String>()
    private var tokenSeq = 0
    private var version = 0L

    val records = linkedMapOf<Pair<String, String>, Stored>()
    var refreshCalls = 0
    var pushCalls = 0
    var offline = false
    val rejectIds = mutableSetOf<String>()

    /** Runs inside a push, before it is stored: lets a test edit the phone while the push is in flight. */
    var onPush: (suspend () -> Unit)? = null

    /** When set, refresh waits for it: lets a test line up two 401s before the first refresh answers. */
    var refreshGate: CompletableDeferred<Unit>? = null

    val engine = MockEngine { request -> handle(request) }
    val api = VertoApi(vertoHttpClient(engine), "https://fake.verto")

    /** A session the server already accepts, as if she had logged in earlier. */
    fun signedInSession(): Session {
        val auth = issue()
        return Session(auth.accessToken, auth.refreshToken, email)
    }

    fun expireAccessTokens() = validAccess.clear()

    fun revokeRefreshTokens() = validRefresh.clear()

    /** A change made on another device. */
    fun seed(entityType: String, id: String, payload: JsonObject, deletedAt: String? = null) {
        records[entityType to id] = Stored(entityType, id, payload, deletedAt, ++version)
    }

    private fun issue(): AuthBody {
        tokenSeq++
        val access = "access-$tokenSeq"
        val refresh = "refresh-$tokenSeq"
        validAccess += access
        validRefresh += refresh
        return AuthBody(access, refresh, UserBody("00000000-0000-0000-0000-000000000001", email, "Jess"))
    }

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        if (offline) throw IOException("sem rede")
        val path = request.url.encodedPath
        val body = if (request.method == HttpMethod.Post) request.body.toByteArray().decodeToString() else ""
        return when (path) {
            "/v1/auth/login" -> {
                val login = ApiJson.decodeFromString(LoginBody.serializer(), body)
                if (login.email == email && login.password == password) {
                    ok(ApiJson.encodeToString(AuthBody.serializer(), issue()))
                } else {
                    failure(HttpStatusCode.Unauthorized, "invalid_credentials")
                }
            }
            "/v1/auth/refresh" -> {
                refreshCalls++
                refreshGate?.await()
                val refresh = ApiJson.decodeFromString(RefreshBody.serializer(), body).refreshToken
                if (validRefresh.remove(refresh)) {
                    ok(ApiJson.encodeToString(AuthBody.serializer(), issue()))
                } else {
                    // Reuse of a rotated token: the real server revokes the family.
                    validRefresh.clear()
                    failure(HttpStatusCode.Unauthorized, "invalid_refresh_token")
                }
            }
            "/v1/auth/logout" -> respond("", HttpStatusCode.NoContent)
            "/v1/sync/push" -> authorized(request) {
                pushCalls++
                onPush?.invoke()
                val push = ApiJson.decodeFromString(PushBody.serializer(), body)
                val accepted = mutableListOf<Accepted>()
                val rejected = mutableListOf<Rejected>()
                push.changes.forEach { change ->
                    if (change.id in rejectIds) {
                        rejected += Rejected(change.entityType, change.id, "missing_field:title")
                    } else {
                        version++
                        records[change.entityType to change.id] =
                            Stored(change.entityType, change.id, change.payload, change.deletedAt, version)
                        accepted += Accepted(change.entityType, change.id, version)
                    }
                }
                ok(ApiJson.encodeToString(PushResult.serializer(), PushResult(accepted, rejected)))
            }
            "/v1/sync/pull" -> authorized(request) {
                val since = request.url.parameters["since"]!!.toLong()
                val limit = request.url.parameters["limit"]!!.toInt()
                val rows = records.values.filter { it.version > since }.sortedBy { it.version }.take(limit + 1)
                val page = rows.take(limit)
                val result = PullResult(
                    changes = page.map { ChangeOut(it.entityType, it.id, it.payload, it.deletedAt, it.version) },
                    cursor = page.lastOrNull()?.version ?: since,
                    hasMore = rows.size > limit,
                )
                ok(ApiJson.encodeToString(PullResult.serializer(), result))
            }
            else -> failure(HttpStatusCode.NotFound, "not_found")
        }
    }

    private suspend fun MockRequestHandleScope.authorized(
        request: HttpRequestData,
        block: suspend MockRequestHandleScope.() -> HttpResponseData,
    ): HttpResponseData {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        return if (token != null && token in validAccess) block() else failure(HttpStatusCode.Unauthorized, null)
    }

    private fun MockRequestHandleScope.ok(json: String) =
        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.failure(status: HttpStatusCode, code: String?) = respond(
        if (code == null) "" else """{"error":"$code","message":"..."}""",
        status,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
