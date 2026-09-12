package com.viple.looby.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.viple.looby.BuildConfig
import com.viple.looby.core.model.MobileAppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.*
import net.openid.appauth.browser.BrowserAllowList
import net.openid.appauth.browser.VersionedBrowserMatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Étape 1 du flux natif : OIDC + PKCE auprès de Viple ID via Custom Tabs (AppAuth).
 * Retourne l'access token Viple ID à échanger contre un JWT Looby.
 */
@Singleton
class AuthLauncher @Inject constructor(@ApplicationContext private val context: Context) {

    private val service: AuthorizationService by lazy {
        AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setBrowserMatcher(
                    BrowserAllowList(
                        VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
                        VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB,
                        VersionedBrowserMatcher.FIREFOX_CUSTOM_TAB,
                        VersionedBrowserMatcher.CHROME_BROWSER,
                        VersionedBrowserMatcher.FIREFOX_BROWSER
                    )
                )
                .build()
        )
    }

    suspend fun discover(config: MobileAppConfig): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(config.authority.trimEnd('/'))) { cfg, ex ->
                when {
                    cfg != null -> cont.resume(cfg)
                    else -> cont.resumeWithException(ex ?: IllegalStateException("Découverte OIDC impossible"))
                }
            }
        }

    fun buildAuthorizationIntent(serviceConfig: AuthorizationServiceConfiguration, config: MobileAppConfig): Intent {
        val scopes = config.scopes.ifEmpty { listOf("openid", "profile", "email", "offline_access") }
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            config.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OIDC_REDIRECT_URI)
        )
            .setScopes(scopes)
            .setPrompt("select_account")
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Traite l'intent de retour et échange le code contre les jetons Viple ID. */
    suspend fun handleResult(data: Intent): TokenResponse {
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) throw error ?: IllegalStateException("Connexion annulée")
        return suspendCancellableCoroutine { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { token, ex ->
                when {
                    token != null -> cont.resume(token)
                    else -> cont.resumeWithException(ex ?: IllegalStateException("Échange de code impossible"))
                }
            }
        }
    }

    fun endSessionIntent(serviceConfig: AuthorizationServiceConfiguration, idToken: String?): Intent? {
        if (serviceConfig.endSessionEndpoint == null) return null
        val req = EndSessionRequest.Builder(serviceConfig)
            .setIdTokenHint(idToken)
            .setPostLogoutRedirectUri(Uri.parse(BuildConfig.OIDC_REDIRECT_URI))
            .build()
        return service.getEndSessionRequestIntent(req)
    }

    fun dispose() = service.dispose()
}
