package com.viple.looby.ui.livria

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viple.looby.core.model.Book
import com.viple.looby.core.model.ShelfEntry
import com.viple.looby.data.LivriaRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.home.SearchBarField
import com.viple.looby.ui.navigation.Route
import com.viple.looby.ui.theme.LoobyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

val ShelfStatuses = listOf("WantToRead" to "À lire", "Reading" to "En cours", "Read" to "Lu")
fun shelfStatusLabel(s: String) = ShelfStatuses.firstOrNull { it.first == s }?.second ?: s

/* ---------- Catalogue ---------- */

@OptIn(FlowPreview::class)
@HiltViewModel
class LivriaCatalogViewModel @Inject constructor(private val repo: LivriaRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val genre = MutableStateFlow<String?>(null)
    private val _books = MutableStateFlow<Result<List<Book>>?>(null)
    val books = _books.asStateFlow()
    val genreFlow = genre.asStateFlow()

    init { viewModelScope.launch { combine(query.debounce(300), genre) { q, g -> q to g }.collectLatest { (q, g) -> _books.value = null; _books.value = repo.books(q.ifBlank { null }, g) } } }
    fun setQuery(q: String) { query.value = q }
    fun setGenre(g: String?) { genre.value = if (genre.value == g) null else g }
    fun reload() { val q = query.value; query.value = "$q "; query.value = q }
}

private val Genres = listOf("Roman", "Policier", "Science-fiction", "Fantasy", "Jeunesse", "BD", "Biographie", "Histoire", "Développement personnel", "Cuisine")

@Composable
fun LivriaCatalogScreen(onBack: () -> Unit, onBook: (String) -> Unit, vm: LivriaCatalogViewModel = hiltViewModel()) {
    val result by vm.books.collectAsStateWithLifecycle()
    val genre by vm.genreFlow.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.padding(8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                SearchBarField(text, { text = it; vm.setQuery(it) }, onSubmit = {}, Modifier.weight(1f), placeholder = "Titre, auteur, ISBN…")
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Genres) { g -> FilterChip(genre == g, { vm.setGenre(g) }, label = { Text(g) }) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }, contentWindowInsets = WindowInsets(0)) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                result == null -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { items(9) { ShimmerBox(Modifier.aspectRatio(0.68f), RoundedCornerShape(12.dp)) } }
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::reload)
                result!!.getOrThrow().isEmpty() -> EmptyState("Aucun livre", "Essayez un autre titre ou genre.", Icons.Rounded.MenuBook, Modifier.fillMaxSize())
                else -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(result!!.getOrThrow(), key = { it.id }) { b -> BookCard(b, { onBook(b.id) }, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

/* ---------- Détail livre ---------- */

data class BookDetailUiState(val book: Book? = null, val entry: ShelfEntry? = null, val error: String? = null, val busy: Boolean = false, val ordered: Boolean = false)

@HiltViewModel
class BookDetailViewModel @Inject constructor(private val repo: LivriaRepository, savedState: SavedStateHandle) : ViewModel() {
    private val bookId = savedState.toRoute<Route.BookDetail>().bookId
    private val _state = MutableStateFlow(BookDetailUiState())
    val state = _state.asStateFlow()
    private val _reward = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val reward = _reward.asSharedFlow()

    init { load() }
    fun load() = viewModelScope.launch {
        repo.book(bookId).onSuccess { b -> _state.update { it.copy(book = b) } }.onFailure { e -> _state.update { it.copy(error = e.message) } }
        repo.shelf().onSuccess { s -> _state.update { it.copy(entry = s.firstOrNull { e -> e.book.id == bookId }) } }
    }
    fun setStatus(status: String, rating: Int? = _state.value.entry?.rating) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        repo.upsertShelf(bookId, status, rating).onSuccess { r ->
            _state.update { it.copy(entry = r.entry, busy = false) }
            if (r.reward.xpGained > 0) _reward.tryEmit("+${r.reward.xpGained} XP" + if (r.reward.leveledUp) " · Niveau ${r.reward.progress.level} !" else "")
        }.onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
    }
    fun remove() = viewModelScope.launch { repo.removeFromShelf(bookId).onSuccess { _state.update { it.copy(entry = null) } } }
    fun order() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        repo.order(mapOf(bookId to 1)).onSuccess { _state.update { it.copy(busy = false, ordered = true) }; _reward.tryEmit("Commande créée ! Retrouvez‑la dans Mes commandes.") }
            .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
    }
    fun dismissError() = _state.update { it.copy(error = null) }
}

@Composable
fun BookDetailScreen(bookId: String, onBack: () -> Unit, requireAuth: (() -> Unit) -> Unit, vm: BookDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val livria = LoobyTheme.brand.livria
    LaunchedEffect(Unit) { vm.reward.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(s.error) { s.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { LoobyTopBar("Livre", onBack) }) { padding ->
        val b = s.book
        if (b == null) { if (s.error == null) LoadingState(Modifier.padding(padding)) else ErrorState(s.error!!, Modifier.padding(padding), vm::load); return@Scaffold }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row {
                NetworkImage(b.coverImageBlobUrl, Modifier.width(130.dp).aspectRatio(0.68f).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    b.genre?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = livria, fontWeight = FontWeight.Bold) }
                    Text(b.title, style = MaterialTheme.typography.headlineSmall)
                    Text(b.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    b.publisher?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("%.2f €".format(b.livriaPrice), style = MaterialTheme.typography.headlineMedium, color = livria, fontWeight = FontWeight.Bold)
                        if (b.discountPercentage > 0) Text("%.2f €".format(b.retailPrice), style = MaterialTheme.typography.bodyMedium, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(if (b.stockQuantity > 0) "En stock" else "Rupture", style = MaterialTheme.typography.labelMedium, color = if (b.stockQuantity > 0) LoobyTheme.brand.gively else MaterialTheme.colorScheme.error)
                }
            }

            Text("Mon étagère", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ShelfStatuses.forEachIndexed { i, (k, label) ->
                    SegmentedButton(s.entry?.status == k, { requireAuth { vm.setStatus(k) } }, SegmentedButtonDefaults.itemShape(i, ShelfStatuses.size), enabled = !s.busy) { Text(label) }
                }
            }
            if (s.entry?.status == "Read") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ma note", Modifier.weight(1f)); (1..5).forEach { n -> IconButton({ vm.setStatus("Read", n) }) { Icon(if ((s.entry?.rating ?: 0) >= n) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = LoobyTheme.brand.looby) } }
                }
            }
            if (s.entry != null) TextButton(vm::remove) { Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Retirer de l'étagère") }

            Button({ requireAuth { vm.order() } }, Modifier.fillMaxWidth().height(52.dp), enabled = b.stockQuantity > 0 && !s.busy && !s.ordered, shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(containerColor = livria)) {
                Icon(Icons.Rounded.ShoppingBag, null); Spacer(Modifier.width(8.dp)); Text(if (s.ordered) "Commandé ✓" else "Commander · %.2f €".format(b.livriaPrice))
            }

            if (!b.description.isNullOrBlank()) Column { Text("Résumé", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text(b.description, style = MaterialTheme.typography.bodyLarge) }
            if (b.isbn.isNotBlank()) Text("ISBN ${b.isbn}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/* ---------- Étagère ---------- */

@HiltViewModel
class LivriaShelfViewModel @Inject constructor(private val repo: LivriaRepository) : ViewModel() {
    private val _state = MutableStateFlow<Result<List<ShelfEntry>>?>(null)
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = repo.shelf() }
    fun setStatus(e: ShelfEntry, status: String) = viewModelScope.launch { repo.upsertShelf(e.book.id, status, e.rating); load() }
    fun remove(e: ShelfEntry) = viewModelScope.launch { repo.removeFromShelf(e.book.id); load() }
}

@Composable
fun LivriaShelfScreen(onBack: () -> Unit, onBook: (String) -> Unit, vm: LivriaShelfViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { LoobyTopBar("Mon étagère", onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            val all = result?.getOrNull() ?: emptyList()
            PrimaryTabRow(tab) { ShelfStatuses.forEachIndexed { i, (k, l) -> Tab(tab == i, { tab = i }, text = { Text("$l (${all.count { it.status == k }})") }) } }
            val shown = all.filter { it.status == ShelfStatuses[tab].first }
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                shown.isEmpty() -> EmptyState("Rien ici pour l'instant", "Ajoutez des livres depuis le catalogue.", Icons.Rounded.CollectionsBookmark, Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(shown, key = { it.id }) { e ->
                        var menu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier = Modifier.clickable { onBook(e.book.id) },
                            leadingContent = { NetworkImage(e.book.coverImageBlobUrl, Modifier.width(44.dp).height(64.dp).clip(RoundedCornerShape(6.dp))) },
                            headlineContent = { Text(e.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(e.book.author + (e.rating?.let { " · ${"★".repeat(it)}" } ?: "")) },
                            trailingContent = {
                                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                                DropdownMenu(menu, { menu = false }) {
                                    ShelfStatuses.filter { it.first != e.status }.forEach { (k, l) -> DropdownMenuItem({ Text("Marquer « $l »") }, { menu = false; vm.setStatus(e, k) }) }
                                    DropdownMenuItem({ Text("Retirer", color = MaterialTheme.colorScheme.error) }, { menu = false; vm.remove(e) })
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
