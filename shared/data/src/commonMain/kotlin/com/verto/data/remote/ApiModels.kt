package com.verto.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// Mirrors Verto.Application.Dtos (camelCase JSON). Only the fields the app reads are declared.

internal val ApiJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class LoginBody(val email: String, val password: String)

@Serializable
internal data class RefreshBody(val refreshToken: String)

@Serializable
internal data class UserBody(val id: String, val email: String, val displayName: String)

@Serializable
internal data class AuthBody(val accessToken: String, val refreshToken: String, val user: UserBody)

@Serializable
internal data class ErrorBody(val error: String? = null)

@Serializable
internal data class ChangeIn(
    val entityType: String,
    val id: String,
    val payload: JsonElement,
    val deletedAt: String?,
    val clientUpdatedAt: String,
)

@Serializable
internal data class PushBody(val changes: List<ChangeIn>)

@Serializable
internal data class Accepted(val entityType: String, val id: String, val serverVersion: Long)

@Serializable
internal data class Rejected(val entityType: String, val id: String, val error: String)

@Serializable
internal data class PushResult(val accepted: List<Accepted>, val rejected: List<Rejected>)

@Serializable
internal data class ChangeOut(
    val entityType: String,
    val id: String,
    val payload: JsonElement,
    val deletedAt: String? = null,
    val serverVersion: Long,
)

@Serializable
internal data class PullResult(val changes: List<ChangeOut>, val cursor: Long, val hasMore: Boolean)
