package com.viple.looby.ui.listings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viple.looby.core.auth.SessionManager
import com.viple.looby.core.model.Listing
import com.viple.looby.core.model.ListingStatus
import com.viple.looby.core.model.PublicUserProfile
import com.viple.looby.data.ListingRepository
import com.viple.looby.data.MessagingRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.navigation.Route
import com.viple.looby.ui.theme.LoobyTheme
import com.viple.looby.ui.util.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListingDetailUiState(
    val loading: Boolean = true,
    val listing: Listing? = null,
    val error: String? = null,
    val contacting: Boolean = false,
    val isMine: Boolean = false
)

@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val repo: ListingRepository,
    private val messaging: MessagingRepository,
    private val session: SessionManager,
    savedState: SavedStateHandle
) : ViewModel() {
    private val listingId = savedState.toRoute<Route.ListingDetail>().listingId
    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        repo.listing(listingId)
            .onSuccess { l -> _state.update { it.copy(loading = false, listing = l, isMine = session.currentUser?.id == l.sellerUserId) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    fun contactSeller(onOpen: (String) -> Unit) = viewModelScope.launch {
        _state.update { it.copy(contacting = true) }
        messaging.start(listingId)
            .onSuccess { onOpen(it.id) }
            .onFailure { _toast.value = it.message }
        _state.update { it.copy(contacting = false) }
    }

    fun consumeToast() { _toast.value = null }
}

@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onSeller: (String) -> Unit,
    onConversation: (String) -> Unit,
    onEdit: (String) -> Unit,
    requireAuth: (() -> Unit) -> Unit,
    vm: ListingDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val brand = LoobyTheme.brand

    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.consumeToast() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            val l = state.listing
            if (l != null && !state.isMine && l.status == ListingStatus.Published) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (l.isDonation) "Gively" else "Prix", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PriceTag(l, large = true)
                        }
                        Button(
                            onClick = { requireAuth { vm.contactSeller(onConversation) } },
                            enabled = !state.contacting,
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                            colors = if (l.isDonation) ButtonDefaults.buttonColors(containerColor = brand.gively) else ButtonDefaults.buttonColors()
                        ) {
                            if (state.contacting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            else { Icon(Icons.Rounded.ChatBubble, null); Spacer(Modifier.width(8.dp)); Text(if (l.isDonation) "Je le veux !" else "Contacter") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.listing != null -> {
                val l = state.listing!!
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = padding.calculateBottomPadding())) {
                    Box {
                        MediaCarousel(l.mediaUrls)
                        Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth()) {
                            FloatingIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack)
                            Spacer(Modifier.weight(1f))
                            FloatingIcon(Icons.Rounded.Share, "Partager") {
                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${l.title} — https://looby.viplenetwork.com/annonces/${l.id}") }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                            if (state.isMine) { Spacer(Modifier.width(8.dp)); FloatingIcon(Icons.Rounded.Edit, "Modifier") { onEdit(l.id) } }
                        }
                        if (l.isDonation) BrandBadge("Gively · Don", brand.gively, Modifier.align(Alignment.BottomStart).padding(16.dp), Icons.Rounded.Favorite)
                        if (l.status != ListingStatus.Published) BrandBadge(statusLabel(l.status), MaterialTheme.colorScheme.outline, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                    }

                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            l.categoryName?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                            Text(l.title, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PriceTag(l, large = true)
                                l.location?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        // Vendeur
                        Card(onClick = { onSeller(l.sellerUserId) }, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(l.sellerDisplayName, null, 48.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(l.sellerDisplayName ?: "Vendeur", style = MaterialTheme.typography.titleMedium)
                                    Text("Voir le profil et les annonces", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (!l.description.isNullOrBlank()) {
                            Column {
                                Text("Description", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(l.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                            }
                        }

                        if (l.attributeValues.isNotEmpty()) {
                            Column {
                                Text("Caractéristiques", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        l.attributeValues.entries.forEachIndexed { i, (k, v) ->
                                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                                Text(k.replaceFirstChar { it.uppercase() }.replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            }
                                            if (i < l.attributeValues.size - 1) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        l.floorPlan3DUrl?.let {
                            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it))) }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.ViewInAr, null); Spacer(Modifier.width(8.dp)); Text("Voir le plan 3D")
                            }
                        }

                        Text("Réf. ${l.id.take(8).uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

fun statusLabel(s: ListingStatus) = when (s) {
    ListingStatus.Draft -> "Brouillon"; ListingStatus.PublishInProgress -> "Publication…"; ListingStatus.Published -> "En ligne"
    ListingStatus.Reserved -> "Réservé"; ListingStatus.Sold -> "Vendu"; ListingStatus.Expired -> "Expiré"; ListingStatus.Removed -> "Retiré"
}

@Composable
private fun FloatingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White)) { Icon(icon, desc) }
}

@Composable
fun MediaCarousel(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) {
        NetworkImage(null, modifier.fillMaxWidth().aspectRatio(1f))
        return
    }
    val pager = rememberPagerState { urls.size }
    Box(modifier.fillMaxWidth().aspectRatio(1f)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { NetworkImage(urls[it], Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
        if (urls.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(urls.size) { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 10.dp else 6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.5f)))
                }
            }
        }
    }
}

/* ---------- Profil public vendeur ---------- */

@HiltViewModel
class SellerProfileViewModel @Inject constructor(private val repo: ListingRepository, savedState: SavedStateHandle) : ViewModel() {
    private val userId = savedState.toRoute<Route.SellerProfile>().userId
    private val _state = MutableStateFlow<Result<PublicUserProfile>?>(null)
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = null; _state.value = repo.publicProfile(userId) }
}

@Composable
fun SellerProfileScreen(userId: String, onBack: () -> Unit, onListing: (String) -> Unit, vm: SellerProfileViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { LoobyTopBar("Profil", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                else -> {
                    val p = result!!.getOrThrow()
                    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Avatar(p.displayName, p.avatarUrl, 88.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(p.displayName, style = MaterialTheme.typography.headlineSmall)
                                Text("Membre depuis ${p.memberSinceUtc.formatDate()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                StatPill(p.activeListingsCount.toString(), "annonces actives")
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        items(p.recentListings, key = { it.id }) { ListingCard(it, { onListing(it.id) }) }
                    }
                }
            }
        }
    }
}
