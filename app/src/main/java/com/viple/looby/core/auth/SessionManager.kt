package com.viple.looby.core.auth

import android.content.Intent
import com.viple.looby.BuildConfig
import com.viple.looby.core.model.*
import com.viple.looby.core.network.MobileApi
import com.viple.looby.core.network.TokenRefresher
import com.viple.looby.core.network.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationServiceConfiguration
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Loading : AuthState
    data object Anonymous : AuthState
    data class Authenticated(val user: MobileUser) : AuthState
}

/** Orchestration de la session : config app, connexion PKCE → exchange, refresh, déconnexion. */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val mobileApi: MobileApi,
    private val authLauncher: AuthLauncher,
    private val refresher: TokenRefresher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _appConfig = MutableStateFlow<MobileAppConfig?>(null)
    val appConfig: StateFlow<MobileAppConfig?> = _appConfig.asStateFlow()

    private val _bootstrapped = MutableStateFlow(false)

    val authState: StateFlow<AuthState> = combine(tokenStore.session, _bootstrapped) { session, ready ->
        when {
            !ready -> AuthState.Loading
            session == null -> AuthState.Anonymous
            else -> AuthState.Authenticated(session.user)
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    val currentUser: MobileUser? get() = tokenStore.session.value?.user
    val isAuthenticated: Boolean get() = tokenStore.session.value != null
    val accessToken: String? get() = tokenStore.session.value?.accessToken
    val deviceId: String get() = tokenStore.deviceId

    private var oidcConfig: AuthorizationServiceConfiguration? = null

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Appelé au démarrage : charge la config, rafraîchit la session si besoin. */
    fun bootstrap() {
        scope.launch {
            runCatching { mobileApi.appConfig() }.onSuccess { _appConfig.value = it }
            val session = tokenStore.session.value
            if (session != null) {
                val fresh = refresher.ensureFresh()
                if (fresh != null) {
                    runCatching { mobileApi.me() }.onSuccess { tokenStore.updateUser(it) }
                }
            }
            _bootstrapped.value = true
        }
    }

    suspend fun ensureAppConfig(): MobileAppConfig =
        _appConfig.value ?: mobileApi.appConfig().also { _appConfig.value = it }

    suspend fun buildSignInIntent(): Result<Intent> = apiCall {
        val config = ensureAppConfig()
        val oidc = oidcConfig ?: authLauncher.discover(config).also { oidcConfig = it }
        authLauncher.buildAuthorizationIntent(oidc, config)
    }

    /** Étape 2 : code → jetons Viple ID → exchange Looby. */
    suspend fun completeSignIn(data: Intent): Result<MobileUser> = apiCall {
        val vipleTokens = authLauncher.handleResult(data)
        val vipleAccessToken = vipleTokens.accessToken ?: error("Aucun access token Viple ID")
        tokenStore.vipleIdAccessToken = vipleAccessToken
        val response = mobileApi.exchange(
            MobileTokenExchangeRequest(
                vipleIdAccessToken = vipleAccessToken,
                device = deviceRegistration()
            )
        )
        tokenStore.save(response)
        _events.tryEmit(SessionEvent.SignedIn)
        response.user
    }

    suspend fun signOut(allDevices: Boolean = false) {
        runCatching {
            if (allDevices) mobileApi.logoutAll() else mobileApi.logout(MobileLogoutRequest(tokenStore.deviceId))
        }
        tokenStore.clear()
        _events.tryEmit(SessionEvent.SignedOut)
    }

    suspend fun refreshMe() {
        runCatching { mobileApi.me() }.onSuccess { tokenStore.updateUser(it) }
    }

    suspend fun updatePushToken(token: String?, enabled: Boolean = true) {
        tokenStore.pushToken = token
        if (!isAuthenticated) return
        runCatching { mobileApi.updatePushToken(UpdatePushTokenRequest(tokenStore.deviceId, token, enabled)) }
    }

    private fun deviceRegistration() = MobileDeviceRegistration(
        deviceId = tokenStore.deviceId,
        platform = MobilePlatform.Android,
        model = tokenStore.deviceModel,
        osVersion = tokenStore.osVersion,
        appVersion = BuildConfig.VERSION_NAME,
        pushToken = tokenStore.pushToken,
        locale = Locale.getDefault().toLanguageTag()
    )
}

sealed interface SessionEvent {
    data object SignedIn : SessionEvent
    data object SignedOut : SessionEvent
}
