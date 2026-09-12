package com.viple.looby.ui.messaging

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viple.looby.core.auth.SessionManager
import com.viple.looby.core.model.*
import com.viple.looby.core.realtime.HubEvent
import com.viple.looby.data.ListingRepository
import com.viple.looby.data.MessagingRepository
import com.viple.looby.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ConversationUiState(
    val loading: Boolean = true,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val typingName: String? = null,
    val replyTo: Message? = null,
    val editing: Message? = null,
    val gifs: GifSearchResult? = null,
    val gifQuery: String = "",
    val uploading: Boolean = false
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val repo: MessagingRepository,
    private val listings: ListingRepository,
    private val session: SessionManager,
    savedState: SavedStateHandle
) : ViewModel() {
    val conversationId: String = savedState.toRoute<Route.Conversation>().conversationId
    val myUserId: String? get() = session.currentUser?.id

    private val _state = MutableStateFlow(ConversationUiState())
    val state = _state.asStateFlow()
    val hubStatus = repo.hubStatus

    private var typingJob: Job? = null
    private var typingSent = false

    init {
        load()
        viewModelScope.launch { repo.join(conversationId) }
        viewModelScope.launch { repo.hubEvents.collect(::onHubEvent) }
    }

    override fun onCleared() { viewModelScope.launch { repo.leave(conversationId) }; super.onCleared() }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        val conv = repo.conversation(conversationId).getOrElse { e -> _state.update { it.copy(loading = false, error = e.message) }; return@launch }
        repo.messages(conversationId)
            .onSuccess { page -> _state.update { it.copy(loading = false, conversation = conv, messages = page.items.sortedBy { m -> m.sentAtUtc }, hasMore = page.hasMore) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        repo.markRead(conversationId)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        val oldest = s.messages.firstOrNull()?.sentAtUtc ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            repo.messages(conversationId, before = oldest)
                .onSuccess { page -> _state.update { it.copy(loadingMore = false, messages = (page.items + it.messages).distinctBy { m -> m.id }.sortedBy { m -> m.sentAtUtc }, hasMore = page.hasMore) } }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    private fun onHubEvent(e: HubEvent) {
        when (e) {
            is HubEvent.MessageReceived -> if (e.message.conversationId == conversationId) {
                _state.update { s ->
                    val replaced = if (e.clientId != null && s.messages.any { it.clientId == e.clientId }) s.messages.map { if (it.clientId == e.clientId) e.message else it }
                    else if (s.messages.any { it.id == e.message.id }) s.messages.map { if (it.id == e.message.id) e.message else it }
                    else s.messages + e.message
                    s.copy(messages = replaced.sortedBy { it.sentAtUtc }, typingName = if (e.message.senderUserId != myUserId) null else s.typingName)
                }
                if (e.message.senderUserId != myUserId) viewModelScope.launch { repo.markRead(conversationId) }
            }
            is HubEvent.MessageUpdated -> if (e.message.conversationId == conversationId) _state.update { s -> s.copy(messages = s.messages.map { if (it.id == e.message.id) e.message else it }) }
            is HubEvent.MessageDeleted -> if (e.conversationId == conversationId) _state.update { s -> s.copy(messages = s.messages.map { if (it.id == e.messageId) it.copy(isDeleted = true, body = "", attachmentKind = MessageAttachmentKind.None) else it }) }
            is HubEvent.MessagesRead -> if (e.conversationId == conversationId && e.readerUserId != myUserId) _state.update { s -> s.copy(messages = s.messages.map { if (it.senderUserId == myUserId) it.copy(isRead = true, readAtUtc = e.readAtUtc) else it }) }
            is HubEvent.Typing -> if (e.conversationId == conversationId && e.userId != myUserId) {
                _state.update { it.copy(typingName = if (e.isTyping) e.displayName else null) }
                if (e.isTyping) viewModelScope.launch { delay(5000); _state.update { if (it.typingName == e.displayName) it.copy(typingName = null) else it } }
            }
            is HubEvent.InboxChanged -> Unit
        }
    }

    fun onTyping(text: String) {
        if (text.isNotBlank() && !typingSent) { typingSent = true; viewModelScope.launch { repo.typing(conversationId, true) } }
        typingJob?.cancel()
        typingJob = viewModelScope.launch { delay(2500); if (typingSent) { typingSent = false; repo.typing(conversationId, false) } }
    }

    fun send(text: String) {
        val body = text.trim()
        val editing = _state.value.editing
        if (editing != null) {
            if (body.isBlank()) return
            viewModelScope.launch {
                repo.edit(editing.id, body).onFailure { e -> _state.update { it.copy(error = e.message) } }
                _state.update { it.copy(editing = null) }
            }
            return
        }
        if (body.isBlank()) return
        sendInternal(SendMessageRequest(conversationId, body = body, replyToMessageId = _state.value.replyTo?.id))
    }

    fun sendGif(gif: Gif) = sendInternal(SendMessageRequest(conversationId, attachmentKind = MessageAttachmentKind.Gif, attachmentUrl = gif.url, attachmentPreviewUrl = gif.previewUrl, attachmentWidth = gif.width, attachmentHeight = gif.height, attachmentName = gif.title, replyToMessageId = _state.value.replyTo?.id))

    fun sendImage(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(uploading = true) }
        listings.uploadMedia(uri)
            .onSuccess { url -> sendInternal(SendMessageRequest(conversationId, attachmentKind = MessageAttachmentKind.Image, attachmentUrl = url, attachmentPreviewUrl = url, replyToMessageId = _state.value.replyTo?.id)) }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        _state.update { it.copy(uploading = false) }
    }

    private fun sendInternal(base: SendMessageRequest) {
        val clientId = UUID.randomUUID().toString()
        val req = base.copy(clientId = clientId)
        val me = session.currentUser
        val optimistic = Message(
            id = "local-$clientId", conversationId = conversationId, senderUserId = me?.id ?: "", senderDisplayName = me?.displayName ?: "",
            body = req.body ?: "", attachmentKind = req.attachmentKind, attachmentUrl = req.attachmentUrl, attachmentPreviewUrl = req.attachmentPreviewUrl,
            attachmentWidth = req.attachmentWidth, attachmentHeight = req.attachmentHeight, replyToMessageId = req.replyToMessageId,
            replyToPreview = _state.value.replyTo?.body, replyToSenderName = _state.value.replyTo?.senderDisplayName,
            sentAtUtc = Instant.now().toString(), clientId = clientId, isPending = true
        )
        _state.update { it.copy(messages = it.messages + optimistic, replyTo = null, gifs = null) }
        typingJob?.cancel(); typingSent = false
        viewModelScope.launch {
            repo.typing(conversationId, false)
            repo.send(req)
                .onSuccess { m -> _state.update { s -> s.copy(messages = s.messages.map { if (it.clientId == clientId) m.copy(clientId = clientId) else it }.distinctBy { it.id }.sortedBy { it.sentAtUtc }) } }
                .onFailure { e -> _state.update { s -> s.copy(messages = s.messages.map { if (it.clientId == clientId) it.copy(isPending = false, sendFailed = true) else it }, error = e.message) } }
        }
    }

    fun retry(m: Message) {
        _state.update { s -> s.copy(messages = s.messages.filterNot { it.clientId == m.clientId }) }
        sendInternal(SendMessageRequest(conversationId, body = m.body.ifBlank { null }, attachmentKind = m.attachmentKind, attachmentUrl = m.attachmentUrl, attachmentPreviewUrl = m.attachmentPreviewUrl, attachmentWidth = m.attachmentWidth, attachmentHeight = m.attachmentHeight, replyToMessageId = m.replyToMessageId))
    }

    fun delete(m: Message) = viewModelScope.launch { repo.delete(conversationId, m.id).onFailure { e -> _state.update { it.copy(error = e.message) } } }
    fun react(m: Message, emoji: String) = viewModelScope.launch { repo.react(m.id, emoji) }
    fun setReplyTo(m: Message?) = _state.update { it.copy(replyTo = m, editing = null) }
    fun setEditing(m: Message?) = _state.update { it.copy(editing = m, replyTo = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun searchGifs(q: String) {
        _state.update { it.copy(gifQuery = q) }
        viewModelScope.launch { repo.gifs(q.ifBlank { "trending" }).onSuccess { r -> _state.update { it.copy(gifs = r) } } }
    }
    fun closeGifs() = _state.update { it.copy(gifs = null) }
}
