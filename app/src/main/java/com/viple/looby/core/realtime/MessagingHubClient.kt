package com.viple.looby.core.realtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.microsoft.signalr.TransportEnum
import com.viple.looby.core.auth.SessionManager
import com.viple.looby.core.model.*
import com.viple.looby.core.network.TokenRefresher
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.rx3.await
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface HubEvent {
    data class MessageReceived(val message: Message, val clientId: String?) : HubEvent
    data class MessageUpdated(val message: Message) : HubEvent
    data class MessageDeleted(val conversationId: String, val messageId: String) : HubEvent
    data class MessagesRead(val conversationId: String, val readerUserId: String, val readAtUtc: String) : HubEvent
    data class Typing(val conversationId: String, val userId: String, val displayName: String, val isTyping: Boolean) : HubEvent
    data class InboxChanged(val conversationId: String) : HubEvent
}

enum class HubStatus { Disconnected, Connecting, Connected, Reconnecting }

/**
 * Client SignalR pour /hubs/messaging (JWT en query string `access_token`, reconnexion automatique
 * avec backoff, rafraîchissement du jeton avant chaque (re)connexion).
 */
@Singleton
class MessagingHubClient @Inject constructor(
    private val sessionManager: SessionManager,
    private val refresher: TokenRefresher,
    private val json: Json,
    @Named("baseUrl") private val baseUrl: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder().create()

    private var connection: HubConnection? = null
    private var connectJob: Job? = null
    private val joined = mutableSetOf<String>()

    private val _status = MutableStateFlow(HubStatus.Disconnected)
    val status: StateFlow<HubStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<HubEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    init {
        // Suivre l'état d'auth : connecter si authentifié, couper sinon.
        scope.launch {
            sessionManager.authState.collect { state ->
                if (state is com.viple.looby.core.auth.AuthState.Authenticated) connect() else disconnect()
            }
        }
    }

    fun connect() {
        if (connectJob?.isActive == true) return
        if (connection?.connectionState == HubConnectionState.CONNECTED) return
        connectJob = scope.launch {
            var attempt = 0
            while (isActive && sessionManager.isAuthenticated) {
                try {
                    _status.value = if (attempt == 0) HubStatus.Connecting else HubStatus.Reconnecting
                    startInternal()
                    _status.value = HubStatus.Connected
                    attempt = 0
                    joined.toList().forEach { runCatching { connection?.invoke("JoinConversation", it)?.await() } }
                    awaitClosed()
                    _status.value = HubStatus.Reconnecting
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _status.value = HubStatus.Reconnecting
                }
                attempt++
                delay(minOf(30_000L, 1_000L * (1L shl minOf(attempt, 5))))
            }
            _status.value = HubStatus.Disconnected
        }
    }

    private suspend fun startInternal() {
        val session = refresher.ensureFresh() ?: throw IllegalStateException("Non authentifié")
        val hubPath = sessionManager.appConfig.value?.messagingHubPath ?: "/hubs/messaging"
        val hub = HubConnectionBuilder.create(baseUrl.trimEnd('/') + hubPath)
            .withAccessTokenProvider(Single.defer { Single.just(sessionManager.accessToken ?: session.accessToken) })
            .withTransport(TransportEnum.WEBSOCKETS)
            .shouldSkipNegotiate(false)
            .build()
        registerHandlers(hub)
        connection?.let { runCatching { it.stop() } }
        connection = hub
        hub.start().await()
    }

    private suspend fun awaitClosed() = suspendCancellableCoroutine<Unit> { cont ->
        connection?.onClosed { if (cont.isActive) cont.resume(Unit) { } }
    }

    private fun registerHandlers(hub: HubConnection) {
        hub.on("MessageReceived", { raw: Any, clientId: String? ->
            _events.tryEmit(HubEvent.MessageReceived(decode(raw), clientId))
        }, Any::class.java, String::class.java)
        hub.on("MessageUpdated", { raw: Any -> _events.tryEmit(HubEvent.MessageUpdated(decode(raw))) }, Any::class.java)
        hub.on("MessageDeleted", { raw: Any ->
            val n: MessageDeletedNotification = decode(raw)
            _events.tryEmit(HubEvent.MessageDeleted(n.conversationId, n.messageId))
        }, Any::class.java)
        hub.on("MessagesRead", { raw: Any ->
            val n: MessagesReadNotification = decode(raw)
            _events.tryEmit(HubEvent.MessagesRead(n.conversationId, n.readerUserId, n.readAtUtc))
        }, Any::class.java)
        hub.on("Typing", { raw: Any ->
            val n: TypingNotification = decode(raw)
            _events.tryEmit(HubEvent.Typing(n.conversationId, n.userId, n.displayName, n.isTyping))
        }, Any::class.java)
        hub.on("InboxChanged", { id: String -> _events.tryEmit(HubEvent.InboxChanged(id)) }, String::class.java)
    }

    /** Le client SignalR désérialise en Gson (LinkedTreeMap) ; on repasse par JSON pour kotlinx.serialization. */
    private inline fun <reified T> decode(raw: Any): T = json.decodeFromString(gson.toJson(raw))
    private inline fun <reified T> encode(value: T): Any = gson.fromJson(json.encodeToString(kotlinx.serialization.serializer<T>(), value), Any::class.java)

    fun disconnect() {
        connectJob?.cancel(); connectJob = null
        connection?.let { runCatching { it.stop() } }
        connection = null
        joined.clear()
        _status.value = HubStatus.Disconnected
    }

    private fun requireHub(): HubConnection =
        connection?.takeIf { it.connectionState == HubConnectionState.CONNECTED } ?: throw IllegalStateException("Temps réel indisponible")

    suspend fun joinConversation(id: String) {
        joined += id
        runCatching { requireHub().invoke("JoinConversation", id).await() }
    }

    suspend fun leaveConversation(id: String) {
        joined -= id
        runCatching { requireHub().invoke("LeaveConversation", id).await() }
    }

    suspend fun sendMessage(request: SendMessageRequest): Message {
        val result = requireHub().invoke(Any::class.java, "SendMessage", encode(request)).await()
        return decode(result)
    }

    suspend fun editMessage(messageId: String, body: String) = requireHub().invoke("EditMessage", messageId, body).await()
    suspend fun deleteMessage(conversationId: String, messageId: String) = requireHub().invoke("DeleteMessage", conversationId, messageId).await()
    suspend fun toggleReaction(messageId: String, emoji: String) = requireHub().invoke("ToggleReaction", messageId, emoji).await()
    suspend fun markAsRead(conversationId: String) { runCatching { requireHub().invoke("MarkAsRead", conversationId).await() } }
    suspend fun setTyping(conversationId: String, isTyping: Boolean) { runCatching { requireHub().invoke("SetTyping", conversationId, isTyping).await() } }
}
