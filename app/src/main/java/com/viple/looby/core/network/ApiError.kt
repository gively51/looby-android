package com.viple.looby.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : ApiError("Connexion impossible. Vérifiez votre réseau.", cause)
    class Unauthorized : ApiError("Session expirée, reconnectez‑vous.")
    class Forbidden : ApiError("Accès refusé.")
    class NotFound : ApiError("Introuvable.")
    class RateLimited : ApiError("Trop de requêtes, réessayez dans un instant.")
    class Validation(message: String, val errors: Map<String, List<String>> = emptyMap()) : ApiError(message)
    class Server(val code: Int, message: String) : ApiError(message)
    class Unknown(cause: Throwable) : ApiError(cause.message ?: "Erreur inattendue", cause)
}

@Serializable
private data class ProblemDetails(
    val title: String? = null,
    val detail: String? = null,
    val error: String? = null,
    val errors: Map<String, List<String>>? = null
)

fun Throwable.toApiError(json: Json = Json { ignoreUnknownKeys = true }): ApiError = when (this) {
    is ApiError -> this
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val problem = body?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<ProblemDetails>(it) }.getOrNull() }
        val message = problem?.detail ?: problem?.error ?: problem?.title ?: body?.takeIf { it.length < 200 && !it.trimStart().startsWith("<") }
        when (code()) {
            400, 422 -> ApiError.Validation(message ?: "Requête invalide.", problem?.errors ?: emptyMap())
            401 -> ApiError.Unauthorized()
            403 -> ApiError.Forbidden()
            404 -> ApiError.NotFound()
            429 -> ApiError.RateLimited()
            else -> ApiError.Server(code(), message ?: "Erreur serveur ($code()).")
        }
    }
    is IOException -> ApiError.Network(this)
    else -> ApiError.Unknown(this)
}

/** Exécute un appel API et encapsule le résultat. */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): Result<T> =
    try { Result.success(block()) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { Result.failure(e.toApiError()) }
