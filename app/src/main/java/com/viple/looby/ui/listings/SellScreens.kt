package com.viple.looby.ui.listings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.model.Listing
import com.viple.looby.core.model.ListingStatus
import com.viple.looby.data.ListingRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.theme.LoobyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyListingsViewModel @Inject constructor(private val repo: ListingRepository) : ViewModel() {
    private val _state = MutableStateFlow<Result<List<Listing>>?>(null)
    val state = _state.asStateFlow()
    fun load() = viewModelScope.launch { _state.value = repo.myListings() }
}

@Composable
fun SellHubScreen(
    isAuthenticated: Boolean,
    onSignIn: () -> Unit,
    onNew: () -> Unit,
    onMyListings: () -> Unit,
    onListing: (String) -> Unit,
    onResume: (String) -> Unit,
    vm: MyListingsViewModel = hiltViewModel()
) {
    val brand = LoobyTheme.brand
    val result by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(isAuthenticated) { if (isAuthenticated) vm.load() }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                GradientHeader(brand.heroGradient, Modifier.statusBarsPadding()) {
                    Text("Vendre ou donner", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("Publiez une annonce en quelques étapes", color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { if (isAuthenticated) onNew() else onSignIn() },
                        Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = brand.looby)
                    ) { Icon(Icons.Rounded.AddCircle, null); Spacer(Modifier.width(8.dp)); Text("Déposer une annonce", fontWeight = FontWeight.Bold) }
                }
            }
            item {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoTile(Icons.Rounded.Storefront, "Looby", "Fixez un prix et vendez", brand.looby, Modifier.weight(1f))
                    InfoTile(Icons.Rounded.Favorite, "Gively", "Donnez gratuitement", brand.gively, Modifier.weight(1f))
                }
            }
            if (!isAuthenticated) {
                item { SignInRequired(onSignIn, Modifier.height(320.dp)) }
            } else {
                item { SectionHeader("Mes annonces", actionLabel = "Tout voir", onAction = onMyListings); Spacer(Modifier.height(8.dp)) }
                when {
                    result == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() } }
                    result!!.isFailure -> item { ErrorState(result!!.exceptionOrNull()?.message ?: "", Modifier.height(200.dp), vm::load) }
                    else -> {
                        val list = result!!.getOrThrow()
                        if (list.isEmpty()) item { EmptyState("Aucune annonce", "Votre première annonce n'attend que vous.", Icons.Rounded.Sell) }
                        items(list.take(5), key = { it.id }) { MyListingRow(it, onListing, onResume) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(color.copy(alpha = 0.15f)), Alignment.Center) { Icon(icon, null, tint = color) }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MyListingRow(listing: Listing, onListing: (String) -> Unit, onResume: (String) -> Unit) {
    val isDraft = listing.status == ListingStatus.Draft || listing.status == ListingStatus.PublishInProgress
    ListingRow(listing, onClick = { if (isDraft) onResume(listing.id) else onListing(listing.id) }, Modifier.padding(horizontal = 12.dp)) {
        AssistChip(
            onClick = { if (isDraft) onResume(listing.id) else onListing(listing.id) },
            label = { Text(if (isDraft) "Reprendre" else statusLabel(listing.status)) },
            leadingIcon = { Icon(if (isDraft) Icons.Rounded.EditNote else Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) }
        )
    }
}

@Composable
fun MyListingsScreen(onBack: () -> Unit, onListing: (String) -> Unit, onResume: (String) -> Unit, vm: MyListingsViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { LoobyTopBar("Mes annonces", onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            val list = result?.getOrNull() ?: emptyList()
            val drafts = list.filter { it.status == ListingStatus.Draft || it.status == ListingStatus.PublishInProgress }
            val online = list.filter { it.status == ListingStatus.Published || it.status == ListingStatus.Reserved }
            val done = list - drafts.toSet() - online.toSet()
            PrimaryTabRow(tab) {
                listOf("En ligne (${online.size})", "Brouillons (${drafts.size})", "Terminées (${done.size})").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            val shown = when (tab) { 0 -> online; 1 -> drafts; else -> done }
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                shown.isEmpty() -> EmptyState("Rien ici", icon = Icons.Rounded.Inventory2, modifier = Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) { items(shown, key = { it.id }) { MyListingRow(it, onListing, onResume) } }
            }
        }
    }
}
