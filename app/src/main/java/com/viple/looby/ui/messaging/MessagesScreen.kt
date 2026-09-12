package com.viple.looby.ui.messaging

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.model.Conversation
import com.viple.looby.core.realtime.HubEvent
import com.viple.looby.core.realtime.HubStatus
import com.viple.looby.data.MessagingRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.util.relativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val archived: Boolean = false,
    val items: List<Conversation> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class MessagesViewModel @Inject constructor(private val repo: MessagingRepository) : ViewModel() {
    private val _state = MutableStateFlow(MessagesUiState())
    val state = _state.asStateFlow()
    val hubStatus = repo.hubStatus

    init {
        viewModelScope.launch {
            repo.hubEvents.collect { e ->
                if (e is HubEvent.InboxChanged || e is HubEvent.MessageReceived || e is HubEvent.MessagesRead) load(silent = true)
            }
        }
    }

    fun setArchived(v: Boolean) { _state.update { it.copy(archived = v) }; load() }

    fun load(silent: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(loading = !silent && it.items.isEmpty(), refreshing = silent, error = null) }
        repo.conversations(_state.value.archived)
            .onSuccess { list -> _state.update { it.copy(loading = false, refreshing = false, items = list) } }
            .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message) } }
        repo.refreshInbox()
    }

    fun archive(id: String, value: Boolean) = viewModelScope.launch { repo.archive(id, value); load(silent = true) }
    fun mute(id: String, value: Boolean) = viewModelScope.launch { repo.mute(id, value); load(silent = true) }
}

@Composable
fun MessagesScreen(isAuthenticated: Boolean, onSignIn: () -> Unit, onOpen: (String) -> Unit, vm: MessagesViewModel = hiltViewModel()) {
    if (!isAuthenticated) { SignInRequired(onSignIn, Modifier.fillMaxSize()); return }
    val s by vm.state.collectAsStateWithLifecycle()
    val hub by vm.hubStatus.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = { Text("Messages") },
                    actions = {
                        if (hub != HubStatus.Connected) {
                            AssistChip(onClick = {}, label = { Text(if (hub == HubStatus.Disconnected) "Hors ligne" else "Connexion…") }, leadingIcon = { Icon(Icons.Rounded.CloudOff, null, Modifier.size(16.dp)) })
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                )
                SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    SegmentedButton(!s.archived, { vm.setArchived(false) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Boîte de réception") }
                    SegmentedButton(s.archived, { vm.setArchived(true) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Archivées") }
                }
                Spacer(Modifier.height(8.dp))
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        PullToRefreshBox(s.refreshing, { vm.load(silent = true) }, Modifier.padding(padding).fillMaxSize()) {
            when {
                s.loading -> LoadingState()
                s.error != null && s.items.isEmpty() -> ErrorState(s.error!!, onRetry = { vm.load() })
                s.items.isEmpty() -> EmptyState(if (s.archived) "Aucune conversation archivée" else "Pas encore de messages", "Contactez un vendeur depuis une annonce pour démarrer.", Icons.Rounded.Forum, Modifier.fillMaxSize())
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(s.items, key = { it.id }) { c ->
                        ConversationRow(c, onClick = { onOpen(c.id) }, onArchive = { vm.archive(c.id, !c.isArchived) }, onMute = { vm.mute(c.id, !c.isMuted) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit, onArchive: () -> Unit, onMute: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val bold = c.unreadCount > 0
    ListItem(
        modifier = Modifier.combinedClickableCompat(onClick = onClick, onLongClick = { menu = true }),
        leadingContent = {
            Box {
                Avatar(c.other.displayName, c.other.avatarUrl, 52.dp)
                c.listing?.coverUrl?.let {
                    NetworkImage(it, Modifier.size(24.dp).align(Alignment.BottomEnd).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(1.dp).clip(CircleShape))
                }
            }
        },
        headlineContent = { Text(c.other.displayName.ifBlank { c.title }, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                c.listing?.let { Text(it.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(
                    (if (c.lastMessageIsMine) "Vous : " else "") + (c.lastMessagePreview ?: "Nouvelle conversation"),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.lastMessageAtUtc.relativeTime(), style = MaterialTheme.typography.labelSmall, color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (c.isMuted) Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    if (bold) Badge { Text(c.unreadCount.toString()) }
                }
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(if (c.isArchived) "Désarchiver" else "Archiver") }, onClick = { menu = false; onArchive() }, leadingIcon = { Icon(if (c.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) })
                DropdownMenuItem(text = { Text(if (c.isMuted) "Réactiver les notifications" else "Mettre en sourdine") }, onClick = { menu = false; onMute() }, leadingIcon = { Icon(if (c.isMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, null) })
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)
