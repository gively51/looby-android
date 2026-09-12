package com.viple.looby.ui.listings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viple.looby.core.model.Category
import com.viple.looby.core.model.Listing
import com.viple.looby.core.model.ListingSearchQuery
import com.viple.looby.data.ListingRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.home.CategoryChip
import com.viple.looby.ui.home.SearchBarField
import com.viple.looby.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val query: ListingSearchQuery = ListingSearchQuery(),
    val items: List<Listing> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val categories: List<Category> = emptyList()
) {
    val activeFilters: Int get() = listOfNotNull(query.categoryId, query.donationsOnly?.takeIf { it }, query.minPrice, query.maxPrice, query.city?.takeIf { it.isNotBlank() }).size
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repo: ListingRepository,
    savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private val searchText = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        val initial = runCatching { savedState.toRoute<Route.Explore>() }.getOrNull()
        _state.update {
            it.copy(query = ListingSearchQuery(query = initial?.query, categoryId = initial?.categoryId, donationsOnly = initial?.donationsOnly?.takeIf { d -> d }))
        }
        searchText.value = initial?.query ?: ""
        viewModelScope.launch { repo.categories().onSuccess { c -> _state.update { it.copy(categories = repo.flattenCategories(c)) } } }
        viewModelScope.launch {
            searchText.drop(1).debounce(350).distinctUntilChanged().collect { q -> update { it.copy(query = q.ifBlank { null }) } }
        }
        load()
    }

    fun onSearchText(text: String) { searchText.value = text }
    val currentText get() = searchText.value

    fun update(transform: (ListingSearchQuery) -> ListingSearchQuery) {
        _state.update { it.copy(query = transform(it.query).copy(page = 1)) }
        load()
    }

    fun reset() { searchText.value = ""; update { ListingSearchQuery() } }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.search(_state.value.query)
                .onSuccess { page -> _state.update { it.copy(loading = false, items = page.items, totalCount = page.totalCount, hasMore = page.hasMore) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore || s.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val next = s.query.copy(page = s.query.page + 1)
            repo.search(next)
                .onSuccess { page -> _state.update { it.copy(loadingMore = false, query = next, items = it.items + page.items, hasMore = page.hasMore) } }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}

@Composable
fun ExploreScreen(initial: Route.Explore, onListing: (String) -> Unit, vm: ExploreViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(vm.currentText) }
    var showFilters by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchBarField(text, { text = it; vm.onSearchText(it) }, onSubmit = { vm.update { q -> q.copy(query = text) } }, Modifier.weight(1f))
                    BadgedBox(badge = { if (state.activeFilters > 0) Badge { Text(state.activeFilters.toString()) } }) {
                        FilledTonalIconButton(onClick = { showFilters = true }) { Icon(Icons.Rounded.Tune, "Filtres") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.query.donationsOnly == true,
                            onClick = { vm.update { it.copy(donationsOnly = if (it.donationsOnly == true) null else true) } },
                            label = { Text("Dons Gively") },
                            leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp)) }
                        )
                    }
                    items(state.categories.filter { it.parentCategoryId == null }, key = { it.id }) { c ->
                        CategoryChip(c, selected = state.query.categoryId == c.id) {
                            vm.update { it.copy(categoryId = if (it.categoryId == c.id) null else c.id) }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading && state.items.isEmpty() -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(6) { ListingCardSkeleton() }
                }
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = vm::load)
                state.items.isEmpty() -> EmptyState("Aucun résultat", "Essayez d'autres mots‑clés ou retirez des filtres.", Icons.Rounded.SearchOff) {
                    TextButton(onClick = vm::reset) { Text("Réinitialiser") }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.totalCount} annonce${if (state.totalCount > 1) "s" else ""}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            SortMenu(state.query.sort) { vm.update { q -> q.copy(sort = it) } }
                        }
                    }
                    items(state.items, key = { it.id }) { ListingCard(it, { onListing(it.id) }) }
                    if (state.loadingMore) item(span = { GridItemSpan(2) }) { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }

    if (showFilters) {
        FiltersSheet(state.query, state.categories, onDismiss = { showFilters = false }, onApply = { q -> vm.update { q }; showFilters = false })
    }
}

@Composable
private fun SortMenu(current: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(null to "Plus récentes", "price_asc" to "Prix croissant", "price_desc" to "Prix décroissant")
    Box {
        TextButton(onClick = { open = true }) { Icon(Icons.Rounded.SwapVert, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(options.first { it.first == current }.second) }
        DropdownMenu(open, { open = false }) {
            options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { onSelect(k); open = false }, trailingIcon = { if (k == current) Icon(Icons.Rounded.Check, null) }) }
        }
    }
}

@Composable
private fun FiltersSheet(query: ListingSearchQuery, categories: List<Category>, onDismiss: () -> Unit, onApply: (ListingSearchQuery) -> Unit) {
    var draft by remember { mutableStateOf(query) }
    var min by remember { mutableStateOf(query.minPrice?.toInt()?.toString() ?: "") }
    var max by remember { mutableStateOf(query.maxPrice?.toInt()?.toString() ?: "") }
    var city by remember { mutableStateOf(query.city ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Filtres", style = MaterialTheme.typography.headlineSmall)

            Text("Type", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = draft.donationsOnly != true, onClick = { draft = draft.copy(donationsOnly = null) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Tout") }
                SegmentedButton(selected = draft.donationsOnly == true, onClick = { draft = draft.copy(donationsOnly = true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Dons uniquement") }
            }

            Text("Prix (€)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(min, { min = it.filter(Char::isDigit) }, label = { Text("Min") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(max, { max = it.filter(Char::isDigit) }, label = { Text("Max") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
            }

            Text("Localisation", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(city, { city = it }, label = { Text("Ville") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Rounded.Place, null) })

            Text("Catégorie", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    FilterChip(selected = draft.categoryId == c.id, onClick = { draft = draft.copy(categoryId = if (draft.categoryId == c.id) null else c.id) }, label = { Text(c.name) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onApply(ListingSearchQuery(query = query.query)) }, Modifier.weight(1f)) { Text("Effacer") }
                Button(
                    onClick = { onApply(draft.copy(minPrice = min.toDoubleOrNull(), maxPrice = max.toDoubleOrNull(), city = city.ifBlank { null })) },
                    Modifier.weight(1f)
                ) { Text("Appliquer") }
            }
        }
    }
}
