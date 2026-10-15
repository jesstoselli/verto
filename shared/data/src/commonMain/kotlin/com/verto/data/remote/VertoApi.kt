package com.verto.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/** The server said the credentials or tokens are not, or no longer, good. */
class UnauthorizedException(val code: String?) : Exception(code ?: "unauthorized")

/** Any other answer outside 2xx. */
class ServerException(val status: Int, val code: String?) : Exception("HTTP $status ${code.orEmpty()}")

fun vertoHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(ApiJson) }
    expectSuccess = false
}

class VertoApi(private val http: HttpClient, private val baseUrl: String = DEFAULT_BASE_URL) {

    internal suspend fun login(email: String, password: String): AuthBody =
        http.post("$baseUrl/v1/auth/login") { json(LoginBody(email, password)) }.checked().body()

    internal suspend fun refresh(refreshToken: String): AuthBody =
        http.post("$baseUrl/v1/auth/refresh") { json(RefreshBody(refreshToken)) }.checked().body()

    internal suspend fun logout(refreshToken: String) {
        http.post("$baseUrl/v1/auth/logout") { json(RefreshBody(refreshToken)) }.checked()
    }

    internal suspend fun push(accessToken: String, changes: List<ChangeIn>): PushResult =
        http.post("$baseUrl/v1/sync/push") {
            bearerAuth(accessToken)
            json(PushBody(changes))
        }.checked().body()

    internal suspend fun pull(accessToken: String, since: Long, limit: Int): PullResult =
        http.get("$baseUrl/v1/sync/pull") {
            bearerAuth(accessToken)
            parameter("since", since)
            parameter("limit", limit)
        }.checked().body()

    private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.checked(): HttpResponse {
        if (status.isSuccess()) return this
        val code = try {
            body<ErrorBody>().error
        } catch (_: Exception) {
            null
        }
        if (status == HttpStatusCode.Unauthorized) throw UnauthorizedException(code)
        throw ServerException(status.value, code)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://verto-api.billfolder.app"
    }
}
