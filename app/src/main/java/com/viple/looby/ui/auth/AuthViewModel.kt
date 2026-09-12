package com.viple.looby.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.auth.AuthState
import com.viple.looby.core.auth.SessionManager
import com.viple.looby.core.model.MobileAppConfig
import com.viple.looby.data.MessagingRepository
import com.viple.looby.core.realtime.HubEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SignInUi {
    data object Idle : SignInUi
    data object Launching : SignInUi
    data class LaunchBrowser(val intent: Intent) : SignInUi
    data object Exchanging : SignInUi
    data class Error(val message: String) : SignInUi
}

/** ViewModel partagé au niveau de l'Activity : état d'auth global, connexion, inbox non lus. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val messagingRepository: MessagingRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = sessionManager.authState
    val appConfig: StateFlow<MobileAppConfig?> = sessionManager.appConfig
    val unreadMessages: StateFlow<Int> = messagingRepository.inbox.map { it.unreadMessages }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _signIn = MutableStateFlow<SignInUi>(SignInUi.Idle)
    val signIn: StateFlow<SignInUi> = _signIn.asStateFlow()

    init {
        sessionManager.bootstrap()
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Authenticated) messagingRepository.refreshInbox() else messagingRepository.clearInbox()
            }
        }
        viewModelScope.launch {
            messagingRepository.hubEvents.collect { e ->
                if (e is HubEvent.InboxChanged || e is HubEvent.MessageReceived) messagingRepository.refreshInbox()
            }
        }
    }

    fun startSignIn() {
        if (_signIn.value is SignInUi.Launching || _signIn.value is SignInUi.Exchanging) return
        _signIn.value = SignInUi.Launching
        viewModelScope.launch {
            sessionManager.buildSignInIntent()
                .onSuccess { _signIn.value = SignInUi.LaunchBrowser(it) }
                .onFailure { _signIn.value = SignInUi.Error(it.message ?: "Connexion impossible") }
        }
    }

    fun onBrowserLaunched() { if (_signIn.value is SignInUi.LaunchBrowser) _signIn.value = SignInUi.Idle }

    fun onAuthResult(data: Intent?) {
        if (data == null) { _signIn.value = SignInUi.Idle; return }
        _signIn.value = SignInUi.Exchanging
        viewModelScope.launch {
            sessionManager.completeSignIn(data)
                .onSuccess { _signIn.value = SignInUi.Idle }
                .onFailure { _signIn.value = SignInUi.Error(it.message ?: "Connexion refusée") }
        }
    }

    fun dismissError() { _signIn.value = SignInUi.Idle }

    fun signOut(allDevices: Boolean = false) = viewModelScope.launch { sessionManager.signOut(allDevices) }

    val isAuthenticated get() = sessionManager.isAuthenticated
}
