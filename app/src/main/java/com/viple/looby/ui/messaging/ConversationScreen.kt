package com.viple.looby.ui.messaging

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viple.looby.core.model.Message
import com.viple.looby.core.model.MessageAttachmentKind
import com.viple.looby.core.realtime.HubStatus
import com.viple.looby.ui.components.*
import com.viple.looby.ui.util.formatTime
import com.viple.looby.ui.util.formatDate

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(conversationId: String, onBack: () -> Unit, onListing: (String) -> Unit, vm: ConversationViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val hub by vm.hubStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(s.error) { s.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
    LaunchedEffect(s.editing) { s.editing?.let { input = it.body } }
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size - 1) }
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    LaunchedEffect(atTop) { if (atTop && !s.loading) vm.loadMore() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::sendImage) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                title = {
                    val c = s.conversation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c?.other?.displayName, c?.other?.avatarUrl, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(c?.other?.displayName ?: "Conversation", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                s.typingName?.let { "écrit…" } ?: when (hub) { HubStatus.Connected -> "En ligne"; HubStatus.Disconnected -> "Hors ligne"; else -> "Connexion…" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.typingName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.imePadding().navigationBarsPadding()) {
                AnimatedVisibility(s.replyTo != null || s.editing != null) {
                    val target = s.replyTo ?: s.editing
                    Surface(tonalElevation = 2.dp) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (s.editing != null) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.Reply, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (s.editing != null) "Modifier le message" else "Répondre à ${target?.senderDisplayName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(target?.body?.ifBlank { "Pièce jointe" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ vm.setReplyTo(null); vm.setEditing(null); if (s.editing != null) input = "" }) { Icon(Icons.Rounded.Close, "Annuler") }
                        }
                    }
                }
                AnimatedVisibility(s.gifs != null) { GifPicker(s, vm) }
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                        IconButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !s.uploading) {
                            if (s.uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Image, "Photo")
                        }
                        IconButton({ if (s.gifs == null) vm.searchGifs("") else vm.closeGifs() }) { Icon(Icons.Rounded.Gif, "GIF", tint = if (s.gifs != null) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                        TextField(
                            input, { input = it; vm.onTyping(it) },
                            Modifier.weight(1f), placeholder = { Text("Votre message…") }, maxLines = 5, shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                        )
                        Spacer(Modifier.width(6.dp))
                        FilledIconButton({ vm.send(input); input = "" }, enabled = input.isNotBlank()) { Icon(if (s.editing != null) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send, "Envoyer") }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            s.conversation?.listing?.let { l ->
                Surface(Modifier.fillMaxWidth().clickable { onListing(l.id) }, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetworkImage(l.coverUrl, Modifier.size(48.dp).clip(MaterialTheme.shapes.small))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (l.isDonation) "Don · Gively" else "%.2f €".format(l.price ?: 0.0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
            when {
                s.loading -> LoadingState(Modifier.weight(1f))
                s.error != null && s.messages.isEmpty() -> ErrorState(s.error!!, Modifier.weight(1f), vm::load)
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.loadingMore) item { Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                    s.messages.forEachIndexed { i, m ->
                        val prev = s.messages.getOrNull(i - 1)
                        if (prev == null || prev.sentAtUtc.formatDate() != m.sentAtUtc.formatDate()) {
                            item(key = "day-${m.sentAtUtc}") { Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) { Text(m.sentAtUtc.formatDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        }
                        item(key = m.clientId ?: m.id) {
                            MessageBubble(
                                m, mine = m.senderUserId == vm.myUserId,
                                grouped = prev?.senderUserId == m.senderUserId && !prev.isSystem,
                                onLongPress = { selected = m }, onRetry = { vm.retry(m) }, onReact = { vm.react(m, it) }
                            )
                        }
                    }
                    if (s.typingName != null) item { TypingIndicator() }
                }
            }
        }
    }

    selected?.let { m ->
        MessageActionsSheet(m, mine = m.senderUserId == vm.myUserId, onDismiss = { selected = null },
            onReply = { vm.setReplyTo(m) }, onEdit = { vm.setEditing(m) }, onDelete = { vm.delete(m) }, onReact = { vm.react(m, it) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(m: Message, mine: Boolean, grouped: Boolean, onLongPress: () -> Unit, onRetry: () -> Unit, onReact: (String) -> Unit) {
    if (m.isSystem) {
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), Alignment.Center) {
            Text(m.body, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50)).padding(10.dp, 4.dp))
        }
        return
    }
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(
        topStart = if (!mine && grouped) 6.dp else 20.dp, topEnd = if (mine && grouped) 6.dp else 20.dp,
        bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp
    )
    Column(Modifier.fillMaxWidth().padding(top = if (grouped) 0.dp else 8.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(bg).combinedClickable(onClick = {}, onLongClick = onLongPress).padding(if (m.attachmentKind != MessageAttachmentKind.None && m.body.isBlank()) 4.dp else 12.dp, 8.dp)
        ) {
            m.replyToPreview?.let {
                Row(Modifier.padding(bottom = 6.dp).background(fg.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Box(Modifier.width(3.dp).height(30.dp).background(fg, RoundedCornerShape(2.dp))); Spacer(Modifier.width(8.dp))
                    Column { Text(m.replyToSenderName ?: "", style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold); Text(it, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            when {
                m.isDeleted -> Text("Message supprimé", color = fg.copy(alpha = 0.7f), fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
                m.attachmentKind == MessageAttachmentKind.Image || m.attachmentKind == MessageAttachmentKind.Gif -> {
                    val ratio = if ((m.attachmentWidth ?: 0) > 0 && (m.attachmentHeight ?: 0) > 0) m.attachmentWidth!!.toFloat() / m.attachmentHeight!! else 1f
                    NetworkImage(m.attachmentPreviewUrl ?: m.attachmentUrl, Modifier.width(240.dp).aspectRatio(ratio.coerceIn(0.5f, 2f)).clip(RoundedCornerShape(16.dp)))
                    if (m.body.isNotBlank()) Text(m.body, color = fg, modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp))
                }
                m.attachmentKind == MessageAttachmentKind.File -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.AttachFile, null, tint = fg); Spacer(Modifier.width(6.dp)); Text(m.attachmentName ?: "Fichier", color = fg) }
                else -> Text(m.body, color = fg, style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.editedAtUtc != null) Text("modifié", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                Text(m.sentAtUtc.formatTime(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                if (mine) Icon(
                    when { m.sendFailed -> Icons.Rounded.ErrorOutline; m.isPending -> Icons.Rounded.Schedule; m.isRead -> Icons.Rounded.DoneAll; else -> Icons.Rounded.Done },
                    null, Modifier.size(14.dp), tint = if (m.sendFailed) MaterialTheme.colorScheme.error else fg.copy(alpha = 0.8f)
                )
            }
        }
        if (m.reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                m.reactions.forEach { r ->
                    Surface(onClick = { onReact(r.emoji) }, shape = RoundedCornerShape(50), color = if (r.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text("${r.emoji} ${r.count}", Modifier.padding(8.dp, 3.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (m.sendFailed) TextButton(onRetry, contentPadding = PaddingValues(4.dp)) { Text("Échec · Réessayer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun TypingIndicator() {
    Row(Modifier.padding(8.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("• • •", Modifier.padding(14.dp, 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageActionsSheet(m: Message, mine: Boolean, onDismiss: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReact: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickReactions.forEach { e -> Text(e, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onReact(e); onDismiss() }.padding(6.dp)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (!m.isDeleted) ListItem(headlineContent = { Text("Répondre") }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.Reply, null) }, modifier = Modifier.clickable { onReply(); onDismiss() })
            if (mine && !m.isDeleted && m.attachmentKind == MessageAttachmentKind.None) ListItem(headlineContent = { Text("Modifier") }, leadingContent = { Icon(Icons.Rounded.Edit, null) }, modifier = Modifier.clickable { onEdit(); onDismiss() })
            if (mine && !m.isDeleted) ListItem(headlineContent = { Text("Supprimer", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { onDelete(); onDismiss() })
        }
    }
}

@Composable
private fun GifPicker(s: ConversationUiState, vm: ConversationViewModel) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.height(280.dp)) {
            OutlinedTextField(s.gifQuery, vm::searchGifs, Modifier.fillMaxWidth().padding(12.dp, 8.dp), placeholder = { Text("Rechercher un GIF") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(50))
            val gifs = s.gifs
            if (gifs == null || !gifs.available) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("GIFs indisponibles", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(gifs.items, key = { it.id }) { g -> NetworkImage(g.previewUrl, Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { vm.sendGif(g) }) }
            }
        }
    }
}
