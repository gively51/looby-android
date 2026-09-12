package com.viple.looby.core.network

import com.viple.looby.core.auth.TokenStore
import com.viple.looby.core.model.MobileRefreshRequest
import com.viple.looby.core.model.MobileTokenResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/** Ajoute le Bearer aux requêtes API (sauf routes anonymes d'auth). */
@Singleton
class AuthInterceptor @Inject constructor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null || request.url.encodedPath.isAnonymousAuthRoute()) {
            return chain.proceed(request)
        }
        val token = tokenStore.session.value?.accessToken ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/** Sur 401 : rafraîchit le JWT (refresh rotatif) une seule fois, sérialisé entre requêtes concurrentes. */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.isAnonymousAuthRoute()) return null
        if (responseCount(response) >= 2) return null

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = runBlocking { refresher.refreshIfNeeded(failedToken) } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer ${fresh.accessToken}").build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}

@Singleton
class TokenRefresher @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: dagger.Lazy<MobileApi>
) {
    private val mutex = Mutex()

    /** Retourne la session valide (existante si déjà rafraîchie par un autre appel, sinon rafraîchie). */
    suspend fun refreshIfNeeded(failedAccessToken: String?): com.viple.looby.core.auth.StoredSession? = mutex.withLock {
        val current = tokenStore.session.value ?: return null
        if (failedAccessToken != null && current.accessToken != failedAccessToken && current.isAccessTokenFresh()) {
            return current
        }
        if (java.time.Instant.now().isAfter(current.refreshTokenExpiresAt)) {
            tokenStore.clear(); return null
        }
        return try {
            val response: MobileTokenResponse = refreshApi.get().refresh(MobileRefreshRequest(current.refreshToken, tokenStore.deviceId))
            tokenStore.save(response)
            tokenStore.session.value
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401 || e.code() == 400) tokenStore.clear()
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun ensureFresh(): com.viple.looby.core.auth.StoredSession? {
        val current = tokenStore.session.value ?: return null
        return if (current.isAccessTokenFresh()) current else refreshIfNeeded(current.accessToken)
    }
}

private fun String.isAnonymousAuthRoute() =
    endsWith("/auth/exchange") || endsWith("/auth/refresh") || endsWith("/app/config") || endsWith("/app/health")
