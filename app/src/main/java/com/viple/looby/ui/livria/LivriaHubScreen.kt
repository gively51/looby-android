package com.viple.looby.ui.livria

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.model.Book
import com.viple.looby.core.model.LivriaHub
import com.viple.looby.core.model.LivriaProgress
import com.viple.looby.data.LivriaRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.theme.LoobyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LivriaHubUiState(val loading: Boolean = true, val refreshing: Boolean = false, val hub: LivriaHub? = null, val needsOnboarding: Boolean = false, val error: String? = null)

@HiltViewModel
class LivriaHubViewModel @Inject constructor(private val repo: LivriaRepository) : ViewModel() {
    private val _state = MutableStateFlow(LivriaHubUiState())
    val state = _state.asStateFlow()

    fun load(silent: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(loading = !silent && it.hub == null, refreshing = silent, error = null) }
        val profile = repo.profile().getOrElse { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message) }; return@launch }
        if (profile == null || !profile.hasCompletedOnboarding) { _state.update { it.copy(loading = false, refreshing = false, needsOnboarding = true) }; return@launch }
        repo.hub()
            .onSuccess { h -> _state.update { it.copy(loading = false, refreshing = false, hub = h, needsOnboarding = false) } }
            .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message) } }
    }
}

@Composable
fun LivriaHubScreen(
    isAuthenticated: Boolean, onSignIn: () -> Unit, onCatalog: () -> Unit, onBook: (String) -> Unit, onShelf: () -> Unit,
    onExchanges: () -> Unit, onLeaderboard: () -> Unit, onOnboarding: () -> Unit, onEditProfile: () -> Unit,
    vm: LivriaHubViewModel = hiltViewModel()
) {
    val brand = LoobyTheme.brand
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(isAuthenticated) { if (isAuthenticated) vm.load() }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        PullToRefreshBox(s.refreshing, { vm.load(silent = true) }, Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    GradientHeader(brand.livriaGradient, Modifier.statusBarsPadding()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Livria 📚", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                                Text("Lisez, échangez, progressez", color = Color.White.copy(alpha = 0.85f))
                            }
                            FilledTonalIconButton(onCatalog, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)) { Icon(Icons.Rounded.Search, "Catalogue") }
                        }
                        val p = s.hub?.profile
                        if (p != null) {
                            Spacer(Modifier.height(18.dp))
                            Surface(shape = MaterialTheme.shapes.large, color = Color.White.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth().clickable(onClick = onEditProfile)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(p.readerPseudonym, p.avatarUrl, 52.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.readerPseudonym, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Niv. ${p.progress.level} · ${p.progress.levelTitle}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                                        Spacer(Modifier.height(6.dp))
                                        XpBar(p.progress, Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
                when {
                    !isAuthenticated -> item { SignInRequired(onSignIn, Modifier.height(360.dp)) }
                    s.loading -> item { LoadingState(Modifier.height(300.dp)) }
                    s.needsOnboarding -> item { OnboardingInvite(onOnboarding) }
                    s.error != null && s.hub == null -> item { ErrorState(s.error!!, Modifier.height(300.dp)) { vm.load() } }
                    s.hub != null -> {
                        val h = s.hub!!
                        item {
                            Row(Modifier.padding(20.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LivriaTile(Icons.Rounded.CollectionsBookmark, "Étagère", "${h.profile.shelfCount}", brand.livria, Modifier.weight(1f), onShelf)
                                LivriaTile(Icons.Rounded.SwapHoriz, "Échanges", "${h.profile.exchangesCompleted}", brand.gively, Modifier.weight(1f), onExchanges)
                                LivriaTile(Icons.Rounded.EmojiEvents, "Classement", "Top", brand.looby, Modifier.weight(1f), onLeaderboard)
                            }
                        }
                        item {
                            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Objectif ${java.time.Year.now()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        Text("${h.profile.booksReadThisYear}/${h.profile.readingGoal}", style = MaterialTheme.typography.titleMedium, color = brand.livria, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator({ (h.profile.booksReadThisYear.toFloat() / h.profile.readingGoal.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp), color = brand.livria, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                }
                            }
                        }
                        if (h.dailyQuests.isNotEmpty()) item {
                            Column(Modifier.padding(top = 16.dp)) {
                                SectionHeader("Quêtes du jour ✨")
                                Spacer(Modifier.height(8.dp))
                                h.dailyQuests.forEach { q ->
                                    Row(Modifier.padding(20.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Bolt, null, tint = brand.livria); Spacer(Modifier.width(10.dp)); Text(q, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        if (h.recommendations.isNotEmpty()) item { BookShelfRow("Pour vous", h.recommendations.map { it.book }, onBook, h.recommendations.associate { it.book.id to it.reason }) }
                        if (h.trending.isNotEmpty()) item { BookShelfRow("Tendances 🔥", h.trending, onBook) }
                        if (h.newArrivals.isNotEmpty()) item { BookShelfRow("Nouveautés", h.newArrivals, onBook) }
                        if (h.profile.badges.isNotEmpty()) item {
                            Column(Modifier.padding(top = 16.dp)) {
                                SectionHeader("Badges")
                                Spacer(Modifier.height(8.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(h.profile.badges, key = { it.code }) { b ->
                                        Column(Modifier.width(84.dp).alpha(if (b.unlocked) 1f else 0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), Alignment.Center) { Text(b.icon, style = MaterialTheme.typography.headlineSmall) }
                                            Spacer(Modifier.height(4.dp))
                                            Text(b.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XpBar(
    Column {
        LinearProgressIndicator({ (p.levelPercent / 100.0).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp), color = color, trackColor = color.copy(alpha = 0.25f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        Text("${p.xpIntoLevel} / ${p.xpForNextLevel} XP", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.9f))
    }
}

@Composable
private fun LivriaTile(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick, modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OnboardingInvite(onOnboarding: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📖", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text("Bienvenue dans Livria", style = MaterialTheme.typography.headlineSmall)
        Text("Créez votre profil lecteur pour recevoir des recommandations, gagner de l'XP et échanger vos livres.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onOnboarding, shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(containerColor = LoobyTheme.brand.livria)) { Text("Créer mon profil lecteur") }
    }
}

@Composable
fun BookShelfRow(title: String, books: List<Book>, onBook: (String) -> Unit, reasons: Map<String, String> = emptyMap(), actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.padding(top = 16.dp)) {
        SectionHeader(title, actionLabel = actionLabel, onAction = onAction)
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(books, key = { it.id }) { b -> BookCard(b, { onBook(b.id) }, reason = reasons[b.id]) }
        }
    }
}

@Composable
fun BookCard(b: Book, onClick: () -> Unit, modifier: Modifier = Modifier.width(130.dp), reason: String? = null) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            NetworkImage(b.coverImageBlobUrl, Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(12.dp)))
            if (b.discountPercentage > 0) BrandBadge("-${b.discountPercentage.toInt()}%", LoobyTheme.brand.gively, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("%.2f €".format(b.livriaPrice), style = MaterialTheme.typography.labelLarge, color = LoobyTheme.brand.livria, fontWeight = FontWeight.Bold)
        reason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
