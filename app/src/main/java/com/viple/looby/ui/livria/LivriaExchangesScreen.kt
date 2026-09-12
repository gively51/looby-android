package com.viple.looby.ui.livria

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.model.*
import com.viple.looby.data.LivriaRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.theme.LoobyTheme
import com.viple.looby.ui.util.relativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExchangesUiState(val loading: Boolean = true, val hub: ExchangeHub? = null, val error: String? = null, val busy: Boolean = false)

@HiltViewModel
class LivriaExchangesViewModel @Inject constructor(private val repo: LivriaRepository) : ViewModel() {
    private val _state = MutableStateFlow(ExchangesUiState())
    val state = _state.asStateFlow()
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast = _toast.asSharedFlow()

    init { load() }
    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = it.hub == null, error = null) }
        repo.exchanges().onSuccess { h -> _state.update { it.copy(loading = false, hub = h) } }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }
    private fun run(block: suspend () -> Result<*>, success: String? = null) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        block().onSuccess { success?.let(_toast::tryEmit); load() }.onFailure { e -> _toast.tryEmit(e.message ?: "Erreur") }
        _state.update { it.copy(busy = false) }
    }
    fun createOffer(r: CreateExchangeOfferRequest) = run({ repo.createOffer(r) }, "Offre publiée !")
    fun cancelOffer(id: String) = run({ repo.cancelOffer(id) }, "Offre retirée")
    fun request(r: CreateExchangeRequestRequest) = run({ repo.createRequest(r) }, "Demande envoyée !")
    fun accept(id: String) = run({ repo.accept(id) }, "Demande acceptée — une conversation a été ouverte")
    fun decline(id: String) = run({ repo.decline(id) })
    fun confirm(id: String) = run({ repo.confirm(id) }, "Échange confirmé 🎉")
}

@Composable
fun LivriaExchangesScreen(onBack: () -> Unit, onConversation: (String) -> Unit, vm: LivriaExchangesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf<ExchangeOffer?>(null) }
    LaunchedEffect(Unit) { vm.toast.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { LoobyTopBar("Échanges de livres", onBack) },
        floatingActionButton = { ExtendedFloatingActionButton(text = { Text("Proposer un livre") }, icon = { Icon(Icons.Rounded.Add, null) }, onClick = { showCreate = true }, containerColor = LoobyTheme.brand.livria, contentColor = Color.White) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            val h = s.hub
            PrimaryTabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Offres") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Mes offres (${h?.myOffers?.size ?: 0})") })
                Tab(tab == 2, { tab = 2 }, text = { BadgedBox(badge = { val n = h?.incomingRequests?.count { it.status == "Pending" } ?: 0; if (n > 0) Badge { Text("$n") } }) { Text("Demandes") } })
            }
            when {
                s.loading -> LoadingState()
                s.error != null && h == null -> ErrorState(s.error!!, onRetry = vm::load)
                h != null -> when (tab) {
                    0 -> if (h.offers.isEmpty()) EmptyState("Aucune offre disponible", "Soyez le premier à proposer un livre.", Icons.Rounded.SwapHoriz, Modifier.fillMaxSize())
                    else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(h.offers, key = { it.id }) { o -> OfferCard(o, onRequest = { requesting = o }) } }
                    1 -> if (h.myOffers.isEmpty()) EmptyState("Vous n'avez pas d'offre", "Proposez un livre que vous ne lisez plus.", Icons.Rounded.MenuBook, Modifier.fillMaxSize())
                    else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(h.myOffers, key = { it.id }) { o -> OfferCard(o, onCancel = { vm.cancelOffer(o.id) }) } }
                    else -> {
                        val all = h.incomingRequests.map { it to true } + h.outgoingRequests.map { it to false }
                        if (all.isEmpty()) EmptyState("Aucune demande", icon = Icons.Rounded.Inbox, modifier = Modifier.fillMaxSize())
                        else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(all, key = { it.first.id }) { (r, incoming) -> RequestCard(r, incoming, vm, onConversation) }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) CreateOfferSheet({ showCreate = false }) { vm.createOffer(it); showCreate = false }
    requesting?.let { o -> RequestSheet(o, { requesting = null }) { vm.request(it); requesting = null } }
}

@Composable
private fun OfferCard(o: ExchangeOffer, onRequest: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
    val livria = LoobyTheme.brand.livria
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp)) {
            Row {
                NetworkImage(o.photoUrl ?: o.coverImageBlobUrl, Modifier.width(64.dp).height(92.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (o.matchesMyWishlist) Icon(Icons.Rounded.AutoAwesome, "Correspond à vos envies", tint = LoobyTheme.brand.looby)
                    }
                    Text(o.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        o.genre?.let { SuggestionChip({}, { Text(it) }) }
                        o.condition?.let { SuggestionChip({}, { Text(it) }) }
                    }
                }
            }
            if (!o.wantedDescription.isNullOrBlank() || o.wantedBookTitle != null || o.wantedGenre != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(16.dp), tint = livria); Spacer(Modifier.width(6.dp))
                    Text("Recherche : " + listOfNotNull(o.wantedBookTitle, o.wantedGenre, o.wantedDescription).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(o.ownerPseudonym, o.ownerAvatarUrl, 28.dp); Spacer(Modifier.width(8.dp))
                Text("${o.ownerPseudonym} · Niv. ${o.ownerLevel}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(o.createdAtUtc.relativeTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (onRequest != null && !o.isMine) { Spacer(Modifier.height(10.dp)); Button(onRequest, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.buttonColors(containerColor = livria)) { Text("Proposer un échange") } }
            if (onCancel != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${o.requestCount} demande${if (o.requestCount > 1) "s" else ""} · ${o.status}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    if (o.status == "Open") TextButton(onCancel) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(r: ExchangeRequest, incoming: Boolean, vm: LivriaExchangesViewModel, onConversation: (String) -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (incoming) Icons.Rounded.CallReceived else Icons.Rounded.CallMade, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                Text(if (incoming) "Demande de ${r.requesterPseudonym}" else "Votre demande", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(r.status)
            }
            Text("Pour : ${r.offerTitle}", style = MaterialTheme.typography.bodyMedium)
            Text("Propose : ${r.proposedTitle} — ${r.proposedAuthor}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            r.message?.takeIf { it.isNotBlank() }?.let { Text("« $it »", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (r.status) {
                    "Pending" -> if (incoming) {
                        OutlinedButton({ vm.decline(r.id) }, Modifier.weight(1f)) { Text("Refuser") }
                        Button({ vm.accept(r.id) }, Modifier.weight(1f)) { Text("Accepter") }
                    }
                    "Accepted" -> {
                        r.conversationId?.let { OutlinedButton({ onConversation(it) }, Modifier.weight(1f)) { Icon(Icons.Rounded.ChatBubble, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Discuter") } }
                        val meConfirmed = if (r.iAmOwner) r.ownerConfirmed else r.requesterConfirmed
                        Button({ vm.confirm(r.id) }, Modifier.weight(1f), enabled = !meConfirmed) { Text(if (meConfirmed) "En attente…" else "Confirmer l'échange") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "Pending" -> "En attente" to MaterialTheme.colorScheme.tertiary
        "Accepted" -> "Acceptée" to LoobyTheme.brand.gively
        "Completed" -> "Terminé" to LoobyTheme.brand.livria
        "Declined" -> "Refusée" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }
    Text(label, Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(8.dp, 3.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
}

private val Conditions = listOf("Neuf", "Très bon", "Bon", "Correct")

@Composable
private fun CreateOfferSheet(onDismiss: () -> Unit, onSubmit: (CreateExchangeOfferRequest) -> Unit) {
    var title by remember { mutableStateOf("") }; var author by remember { mutableStateOf("") }; var genre by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf<String?>(null) }; var notes by remember { mutableStateOf("") }; var wanted by remember { mutableStateOf("") }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Proposer un livre", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(title, { title = it }, label = { Text("Titre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Auteur *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("État", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Conditions.forEach { c -> FilterChip(condition == c, { condition = c }, label = { Text(c) }) } }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(wanted, { wanted = it }, label = { Text("Ce que je recherche en échange") }, modifier = Modifier.fillMaxWidth())
            Button({ onSubmit(CreateExchangeOfferRequest(title = title.trim(), author = author.trim(), genre = genre.ifBlank { null }, condition = condition, notes = notes.ifBlank { null }, wantedDescription = wanted.ifBlank { null })) },
                Modifier.fillMaxWidth().height(52.dp), enabled = title.isNotBlank() && author.isNotBlank(), shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(containerColor = LoobyTheme.brand.livria)) { Text("Publier l'offre") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RequestSheet(o: ExchangeOffer, onDismiss: () -> Unit, onSubmit: (CreateExchangeRequestRequest) -> Unit) {
    var title by remember { mutableStateOf("") }; var author by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Échanger contre « ${o.title} »", style = MaterialTheme.typography.titleLarge)
            Text("Décrivez le livre que vous proposez à ${o.ownerPseudonym}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(title, { title = it }, label = { Text("Titre proposé *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Auteur *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(message, { message = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button({ onSubmit(CreateExchangeRequestRequest(o.id, title.trim(), author.trim(), message.ifBlank { null })) }, Modifier.fillMaxWidth().height(52.dp), enabled = title.isNotBlank() && author.isNotBlank(), shape = MaterialTheme.shapes.large) { Text("Envoyer la demande") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ---------- Classement ---------- */

@HiltViewModel
class LivriaLeaderboardViewModel @Inject constructor(private val repo: LivriaRepository) : ViewModel() {
    private val _state = MutableStateFlow<Result<List<LeaderboardEntry>>?>(null)
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = repo.leaderboard() }
}

@Composable
fun LivriaLeaderboardScreen(onBack: () -> Unit, vm: LivriaLeaderboardViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { LoobyTopBar("Classement des lecteurs", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(result!!.getOrThrow(), key = { it.userId + it.rank }) { e ->
                        val medal = when (e.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = if (e.isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent),
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(medal ?: "#${e.rank}", Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Avatar(e.readerPseudonym, e.avatarUrl, 40.dp)
                                }
                            },
                            headlineContent = { Text(e.readerPseudonym + if (e.isMe) " (vous)" else "", fontWeight = if (e.isMe) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text("Niv. ${e.level} · ${e.levelTitle} · ${e.badgeCount} badges · ${e.exchangesCompleted} échanges") },
                            trailingContent = { Text("${e.xp} XP", style = MaterialTheme.typography.titleSmall, color = LoobyTheme.brand.livria, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
    }
}
