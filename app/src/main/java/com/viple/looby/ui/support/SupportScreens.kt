package com.viple.looby.ui.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viple.looby.core.model.*
import com.viple.looby.data.AccountRepository
import com.viple.looby.data.ListingRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.navigation.Route
import com.viple.looby.ui.theme.LoobyTheme
import com.viple.looby.ui.util.formatDate
import com.viple.looby.ui.util.formatDateTime
import com.viple.looby.ui.util.relativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupportUiState(val loading: Boolean = true, val tickets: List<SupportTicket> = emptyList(), val features: List<FeatureRequest> = emptyList(), val bugs: List<BugReport> = emptyList(), val error: String? = null)

@HiltViewModel
class SupportViewModel @Inject constructor(private val repo: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(SupportUiState())
    val state = _state.asStateFlow()
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 2); val toast = _toast.asSharedFlow()
    init { load() }
    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = it.tickets.isEmpty() && it.features.isEmpty(), error = null) }
        val t = repo.tickets(); val f = repo.featureRequests(); val b = repo.bugReports()
        _state.update { it.copy(loading = false, tickets = t.getOrDefault(emptyList()), features = f.getOrDefault(emptyList()), bugs = b.getOrDefault(emptyList()), error = t.exceptionOrNull()?.message) }
    }
    fun createTicket(subject: String, body: String, onCreated: (String) -> Unit) = viewModelScope.launch { repo.createTicket(subject, body).onSuccess { load(); onCreated(it.id) }.onFailure { _toast.tryEmit(it.message ?: "Erreur") } }
    fun createFeature(title: String, desc: String) = viewModelScope.launch { repo.createFeatureRequest(title, desc).onSuccess { _toast.tryEmit("Merci pour votre idée !"); load() }.onFailure { _toast.tryEmit(it.message ?: "Erreur") } }
    fun createBug(r: CreateBugReportRequest) = viewModelScope.launch { repo.createBugReport(r).onSuccess { _toast.tryEmit("Bug signalé, merci !"); load() }.onFailure { _toast.tryEmit(it.message ?: "Erreur") } }
}

fun ticketStatusLabel(s: SupportTicketStatus) = when (s) { SupportTicketStatus.Open -> "Ouvert"; SupportTicketStatus.AwaitingUser -> "Votre réponse attendue"; SupportTicketStatus.AwaitingSupport -> "En cours"; SupportTicketStatus.Resolved -> "Résolu"; SupportTicketStatus.Closed -> "Fermé" }
fun featureStatusLabel(s: FeatureRequestStatus) = when (s) { FeatureRequestStatus.Proposed -> "Proposée"; FeatureRequestStatus.UnderReview -> "À l'étude"; FeatureRequestStatus.Planned -> "Planifiée"; FeatureRequestStatus.InProgress -> "En cours"; FeatureRequestStatus.Shipped -> "Livrée"; FeatureRequestStatus.Declined -> "Refusée" }
fun bugStatusLabel(s: BugReportStatus) = when (s) { BugReportStatus.New -> "Nouveau"; BugReportStatus.Triaged -> "Qualifié"; BugReportStatus.InProgress -> "En cours"; BugReportStatus.Fixed -> "Corrigé"; BugReportStatus.WontFix -> "Non corrigé" }
fun severityLabel(s: BugReportSeverity) = when (s) { BugReportSeverity.Low -> "Faible"; BugReportSeverity.Medium -> "Moyenne"; BugReportSeverity.High -> "Élevée"; BugReportSeverity.Critical -> "Critique" }

@Composable
fun SupportScreen(onBack: () -> Unit, onTicket: (String) -> Unit, onChangelog: () -> Unit, vm: SupportViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { vm.toast.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { LoobyTopBar("Support & retours", onBack, actions = { IconButton(onChangelog) { Icon(Icons.Rounded.NewReleases, "Nouveautés") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(text = { Text(when (tab) { 0 -> "Nouveau ticket"; 1 -> "Proposer une idée"; else -> "Signaler un bug" }) }, icon = { Icon(Icons.Rounded.Add, null) }, onClick = { sheet = tab }) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Tickets") }, icon = { Icon(Icons.Rounded.SupportAgent, null) })
                Tab(tab == 1, { tab = 1 }, text = { Text("Idées") }, icon = { Icon(Icons.Rounded.Lightbulb, null) })
                Tab(tab == 2, { tab = 2 }, text = { Text("Bugs") }, icon = { Icon(Icons.Rounded.BugReport, null) })
            }
            if (s.loading) { LoadingState(); return@Column }
            when (tab) {
                0 -> if (s.tickets.isEmpty()) EmptyState("Aucun ticket", "Une question ? Notre équipe vous répond.", Icons.Rounded.SupportAgent, Modifier.fillMaxSize())
                else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.tickets, key = { it.id }) { t ->
                        Card(onClick = { onTicket(t.id) }, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.subject, style = MaterialTheme.typography.titleSmall)
                                    Text("${t.messages.size} message${if (t.messages.size > 1) "s" else ""} · ${(t.messages.lastOrNull()?.createdAtUtc ?: t.createdAtUtc).relativeTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                BrandBadge(ticketStatusLabel(t.status), when (t.status) { SupportTicketStatus.Resolved, SupportTicketStatus.Closed -> MaterialTheme.colorScheme.outline; SupportTicketStatus.AwaitingUser -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary })
                            }
                        }
                    }
                }
                1 -> if (s.features.isEmpty()) EmptyState("Aucune idée", "Proposez des améliorations pour Looby.", Icons.Rounded.Lightbulb, Modifier.fillMaxSize())
                else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.features, key = { it.id }) { f ->
                        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.ThumbUp, null, tint = MaterialTheme.colorScheme.primary); Text("${f.upvotes}", fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text(f.title, style = MaterialTheme.typography.titleSmall); Text(f.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3) }
                                Spacer(Modifier.width(8.dp))
                                BrandBadge(featureStatusLabel(f.status), MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
                else -> if (s.bugs.isEmpty()) EmptyState("Aucun bug signalé", "Un problème ? Dites‑le nous.", Icons.Rounded.BugReport, Modifier.fillMaxSize())
                else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.bugs, key = { it.id }) { b ->
                        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text(b.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); BrandBadge(bugStatusLabel(b.status), MaterialTheme.colorScheme.primary) }
                                Text(b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                                Text("Sévérité : ${severityLabel(b.severity)}", style = MaterialTheme.typography.labelSmall, color = if (b.severity >= BugReportSeverity.High) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        0 -> TwoFieldSheet("Nouveau ticket", "Sujet", "Décrivez votre demande", "Envoyer", { sheet = null }) { a, b -> vm.createTicket(a, b) { id -> sheet = null; onTicket(id) } }
        1 -> TwoFieldSheet("Proposer une idée", "Titre", "Décrivez la fonctionnalité souhaitée", "Proposer", { sheet = null }) { a, b -> vm.createFeature(a, b); sheet = null }
        2 -> BugSheet({ sheet = null }) { vm.createBug(it); sheet = null }
    }
}

@Composable
private fun TwoFieldSheet(title: String, l1: String, l2: String, cta: String, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(a, { a = it }, label = { Text(l1) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(b, { b = it }, label = { Text(l2) }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Button({ onSubmit(a.trim(), b.trim()) }, Modifier.fillMaxWidth().height(52.dp), enabled = a.isNotBlank() && b.trim().length >= 10, shape = MaterialTheme.shapes.large) { Text(cta) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BugSheet(onDismiss: () -> Unit, onSubmit: (CreateBugReportRequest) -> Unit) {
    var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var steps by remember { mutableStateOf("") }; var sev by remember { mutableStateOf(BugReportSeverity.Medium) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Signaler un bug", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(title, { title = it }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Que s'est‑il passé ?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(steps, { steps = it }, label = { Text("Étapes pour reproduire") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text("Sévérité", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { BugReportSeverity.entries.forEachIndexed { i, s -> SegmentedButton(sev == s, { sev = s }, SegmentedButtonDefaults.itemShape(i, 4)) { Text(severityLabel(s)) } } }
            Button({ onSubmit(CreateBugReportRequest(title.trim(), desc.trim(), steps.ifBlank { null }, sev)) }, Modifier.fillMaxWidth().height(52.dp), enabled = title.isNotBlank() && desc.trim().length >= 10, shape = MaterialTheme.shapes.large) { Text("Envoyer") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ---------- Détail ticket ---------- */

@HiltViewModel
class SupportTicketViewModel @Inject constructor(private val repo: AccountRepository, savedState: SavedStateHandle) : ViewModel() {
    private val ticketId = savedState.toRoute<Route.SupportTicket>().ticketId
    private val _ticket = MutableStateFlow<Result<SupportTicket>?>(null); val ticket = _ticket.asStateFlow()
    private val _sending = MutableStateFlow(false); val sending = _sending.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _ticket.value = repo.tickets().map { l -> l.first { it.id == ticketId } } }
    fun reply(body: String) = viewModelScope.launch { _sending.value = true; repo.replyTicket(ticketId, body).onSuccess { _ticket.value = Result.success(it) }; _sending.value = false }
}

@Composable
fun SupportTicketScreen(ticketId: String, onBack: () -> Unit, vm: SupportTicketViewModel = hiltViewModel()) {
    val result by vm.ticket.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val t = result?.getOrNull()
    val closed = t?.status == SupportTicketStatus.Closed || t?.status == SupportTicketStatus.Resolved
    Scaffold(
        topBar = { LoobyTopBar(t?.subject ?: "Ticket", onBack) },
        bottomBar = {
            if (t != null && !closed) Surface(tonalElevation = 3.dp) {
                Row(Modifier.navigationBarsPadding().imePadding().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    TextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Votre réponse…") }, maxLines = 5, shape = RoundedCornerShape(24.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton({ vm.reply(input.trim()); input = "" }, enabled = input.isNotBlank() && !sending) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer") }
                }
            }
        }
    ) { padding ->
        when {
            result == null -> LoadingState(Modifier.padding(padding))
            result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", Modifier.padding(padding), vm::load)
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { BrandBadge(ticketStatusLabel(t!!.status), MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Ouvert le ${t.createdAtUtc.formatDateTime()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) } }
                items(t!!.messages) { m ->
                    val staff = m.isFromSupportStaff
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (staff) Alignment.Start else Alignment.End) {
                        Text(if (staff) "Support Looby" else m.authorDisplayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(16.dp), color = if (staff) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary) {
                            Text(m.body, Modifier.padding(12.dp), color = if (staff) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(m.createdAtUtc.relativeTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (closed) item { Text("Ce ticket est clos.", Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/* ---------- Changelog & légal ---------- */

@HiltViewModel
class ChangelogViewModel @Inject constructor(private val repo: ListingRepository) : ViewModel() {
    private val _state = MutableStateFlow<Result<List<ChangelogEntry>>?>(null); val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = repo.changelog() }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit, vm: ChangelogViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { LoobyTopBar("Nouveautés", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(result!!.getOrThrow(), key = { it.id }) { e ->
                        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { BrandBadge("v${e.version}", LoobyTheme.brand.looby); Spacer(Modifier.width(8.dp)); Text(e.publishedAtUtc.formatDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                MarkdownText(e.contentMarkdown)
                            }
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class LegalViewModel @Inject constructor(private val repo: ListingRepository, savedState: SavedStateHandle) : ViewModel() {
    private val slug = savedState.toRoute<Route.Legal>().slug
    private val _state = MutableStateFlow<Result<LegalDocument>?>(null); val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = repo.legal(slug) }
}

@Composable
fun LegalScreen(slug: String, onBack: () -> Unit, vm: LegalViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { LoobyTopBar(result?.getOrNull()?.title ?: "Document", onBack) }) { padding ->
        when {
            result == null -> LoadingState(Modifier.padding(padding))
            result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", Modifier.padding(padding), vm::load)
            else -> {
                val d = result!!.getOrThrow()
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Version ${d.version} · ${d.publishedAtUtc.formatDate()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    MarkdownText(d.contentMarkdown)
                }
            }
        }
    }
}

/** Rendu Markdown minimaliste (titres, listes, gras) suffisant pour CGU/changelog. */
@Composable
fun MarkdownText(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(2.dp))
                line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleSmall)
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium)
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.titleLarge)
                line.startsWith("- ") || line.startsWith("* ") -> Row { Text("•  ", style = MaterialTheme.typography.bodyMedium); Text(stripInline(line.drop(2)), style = MaterialTheme.typography.bodyMedium) }
                else -> Text(stripInline(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun stripInline(s: String) = s.replace("**", "").replace("__", "").replace("`", "")
