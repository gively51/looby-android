package com.viple.looby.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.auth.SessionManager
import com.viple.looby.core.model.Category
import com.viple.looby.core.model.MobileHomeFeed
import com.viple.looby.data.ListingRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.theme.LoobyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val feed: MobileHomeFeed? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ListingRepository,
    private val session: SessionManager
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { session.authState.collect { if (_state.value.feed != null) load(silent = true) } }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = !silent && it.feed == null, refreshing = silent, error = null) }
            repo.home()
                .onSuccess { feed -> _state.update { it.copy(loading = false, refreshing = false, feed = feed) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message) } }
        }
    }
}

@Composable
fun HomeScreen(
    onSearch: (String) -> Unit,
    onCategory: (String) -> Unit,
    onDonations: () -> Unit,
    onListing: (String) -> Unit,
    onMessages: () -> Unit,
    onSignIn: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val brand = LoobyTheme.brand
    var query by rememberSaveable { mutableStateOf("") }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(silent = true) }, modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> HomeSkeleton()
            state.error != null && state.feed == null -> ErrorState(state.error!!, onRetry = { vm.load() })
            else -> {
                val feed = state.feed ?: return@PullToRefreshBox
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(2) }) {
                        GradientHeader(brand.heroGradient, Modifier.statusBarsPadding()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (feed.me != null) "Bonjour, ${feed.me.shortName} 👋" else "Bonjour 👋",
                                        style = MaterialTheme.typography.headlineMedium, color = Color.White
                                    )
                                    Text("Que cherchez‑vous aujourd'hui ?", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                                }
                                if (feed.me != null) {
                                    BadgedBox(badge = { if (feed.unreadMessages > 0) Badge { Text(feed.unreadMessages.toString()) } }) {
                                        FilledIconButton(onClick = onMessages, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)) {
                                            Icon(Icons.Rounded.ChatBubble, "Messages")
                                        }
                                    }
                                } else {
                                    FilledTonalButton(onClick = onSignIn, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = brand.looby)) {
                                        Text("Connexion")
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            SearchBarField(query, { query = it }, onSubmit = { if (query.isNotBlank()) onSearch(query.trim()) })
                        }
                    }

                    item(span = { GridItemSpan(2) }) {
                        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickAction("Gively", "Objets à donner", Icons.Rounded.Favorite, brand.givelyGradient, Modifier.weight(1f), onDonations)
                            QuickAction("Explorer", "Toutes les annonces", Icons.Rounded.Explore, brand.heroGradient, Modifier.weight(1f)) { onSearch("") }
                        }
                    }

                    if (feed.categories.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column {
                                SectionHeader("Catégories")
                                Spacer(Modifier.height(10.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(feed.categories, key = { it.id }) { CategoryChip(it) { onCategory(it.id) } }
                                }
                            }
                        }
                    }

                    if (feed.latestDonations.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column {
                                SectionHeader("Dons Gively 💝", actionLabel = "Tout voir", onAction = onDonations)
                                Spacer(Modifier.height(10.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(feed.latestDonations, key = { it.id }) { ListingCard(it, { onListing(it.id) }, Modifier.width(160.dp)) }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(2) }) { SectionHeader("Dernières annonces", actionLabel = "Tout voir") { onSearch("") } }
                    if (feed.latestListings.isEmpty()) {
                        item(span = { GridItemSpan(2) }) { EmptyState("Aucune annonce", "Soyez le premier à publier !", Icons.Rounded.Storefront) }
                    }
                    items(feed.latestListings, key = { it.id }) { listing ->
                        ListingCard(listing, { onListing(listing.id) }, Modifier.padding(horizontal = 0.dp).then(gridItemPadding(feed.latestListings.indexOf(listing))))
                    }
                }
            }
        }
    }
}

private fun gridItemPadding(index: Int) = if (index % 2 == 0) Modifier.padding(start = 20.dp) else Modifier.padding(end = 20.dp)

@Composable
fun SearchBarField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier, placeholder: String = "Rechercher un objet, une marque…") {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Rounded.Close, "Effacer") } },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit() })
    )
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: ImageVector, brush: androidx.compose.ui.graphics.Brush, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(brush).clickable(onClick = onClick).padding(16.dp).height(84.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, null, tint = Color.White)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
fun CategoryChip(category: Category, selected: Boolean = false, onClick: () -> Unit) {
    val icon = categoryIcon(category.iconName)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.name) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(50)
    )
}

fun categoryIcon(name: String?): ImageVector = when (name?.lowercase()) {
    "home", "house", "immobilier", "maison" -> Icons.Rounded.Home
    "car", "vehicle", "auto", "vehicules" -> Icons.Rounded.DirectionsCar
    "phone", "tech", "electronics", "electronique", "multimedia" -> Icons.Rounded.Devices
    "fashion", "mode", "clothes", "vetements" -> Icons.Rounded.Checkroom
    "furniture", "meubles", "maison-deco" -> Icons.Rounded.Chair
    "book", "books", "livres" -> Icons.Rounded.MenuBook
    "sport", "sports", "loisirs" -> Icons.Rounded.SportsSoccer
    "baby", "enfant", "kids" -> Icons.Rounded.ChildCare
    "garden", "jardin" -> Icons.Rounded.Yard
    "game", "gaming", "jeux" -> Icons.Rounded.SportsEsports
    "pet", "animaux" -> Icons.Rounded.Pets
    "job", "emploi", "services" -> Icons.Rounded.Work
    else -> Icons.Rounded.Category
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize()) {
        ShimmerBox(Modifier.fillMaxWidth().height(220.dp), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.weight(1f).height(116.dp), MaterialTheme.shapes.large)
            ShimmerBox(Modifier.weight(1f).height(116.dp), MaterialTheme.shapes.large)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ListingCardSkeleton(Modifier.weight(1f)); ListingCardSkeleton(Modifier.weight(1f))
        }
    }
}
