package com.viple.looby.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.BuildConfig
import com.viple.looby.core.auth.AuthState
import com.viple.looby.core.model.AccountDashboard
import com.viple.looby.data.AccountRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.navigation.Route
import com.viple.looby.ui.theme.LoobyTheme
import com.viple.looby.ui.util.formatDate
import com.viple.looby.ui.util.relativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(private val repo: AccountRepository) : ViewModel() {
    private val _dashboard = MutableStateFlow<Result<AccountDashboard>?>(null)
    val dashboard = _dashboard.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    fun load() = viewModelScope.launch { _refreshing.value = _dashboard.value != null; _dashboard.value = repo.dashboard(); _refreshing.value = false }
}

@Composable
fun AccountScreen(authState: AuthState, onSignIn: () -> Unit, onSignOut: () -> Unit, onNavigate: (Route) -> Unit, vm: AccountViewModel = hiltViewModel()) {
    val brand = LoobyTheme.brand
    val result by vm.dashboard.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(authState) { if (authState is AuthState.Authenticated) vm.load() }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        PullToRefreshBox(refreshing, vm::load, Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    GradientHeader(brand.heroGradient, Modifier.statusBarsPadding()) {
                        val user = (authState as? AuthState.Authenticated)?.user
                        if (user == null) {
                            Text("Mon compte", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                            Text("Connectez‑vous pour gérer vos annonces, messages et commandes.", color = Color.White.copy(alpha = 0.85f))
                            Spacer(Modifier.height(16.dp))
                            Button(onSignIn, Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = brand.looby)) { Text("Se connecter avec Viple ID", fontWeight = FontWeight.Bold) }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(user.shortName, user.avatarUrl, 64.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user.displayName ?: user.shortName, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                    user.email?.let { Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
                                    Text("Membre depuis ${user.memberSinceUtc.formatDate()}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            val d = result?.getOrNull()
                            if (d != null && d.completion.percent < 100) {
                                Spacer(Modifier.height(16.dp))
                                Surface(shape = MaterialTheme.shapes.large, color = Color.White.copy(alpha = 0.15f)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row { Text("Profil complété", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)); Text("${d.completion.percent}%", color = Color.White, fontWeight = FontWeight.Bold) }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator({ d.completion.percent / 100f }, Modifier.fillMaxWidth().height(6.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }

                if (authState is AuthState.Authenticated) {
                    val d = result?.getOrNull()
                    if (d != null) item {
                        Row(Modifier.padding(20.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatPill("${d.stats.activeListings}", "annonces", Modifier.weight(1f))
                            StatPill("${d.stats.donationListings}", "dons", Modifier.weight(1f), brand.gively.copy(alpha = 0.15f))
                            StatPill("${d.stats.ordersPlaced}", "commandes", Modifier.weight(1f))
                            StatPill("${d.stats.unreadConversations}", "non lus", Modifier.weight(1f), brand.looby.copy(alpha = 0.15f))
                        }
                    }
                    item { SectionHeader("Mon activité"); Spacer(Modifier.height(4.dp)) }
                    item { SettingsRow(Icons.Rounded.Storefront, "Mes annonces", "Brouillons, en ligne, vendues", { onNavigate(Route.MyListings) }) }
                    item { SettingsRow(Icons.Rounded.ShoppingBag, "Mes commandes", "Achats et paiements", { onNavigate(Route.Orders) }) }
                    item { SettingsRow(Icons.Rounded.AutoStories, "Livria", "Profil lecteur, étagère, échanges", { onNavigate(Route.Livria) }) }

                    item { Spacer(Modifier.height(8.dp)); SectionHeader("Paramètres"); Spacer(Modifier.height(4.dp)) }
                    item { SettingsRow(Icons.Rounded.Tune, "Préférences & notifications", null, { onNavigate(Route.Settings) }) }
                    item { SettingsRow(Icons.Rounded.Home, "Adresses", null, { onNavigate(Route.Addresses) }) }
                    item { SettingsRow(Icons.Rounded.CreditCard, "Moyens de paiement", null, { onNavigate(Route.PaymentMethods) }) }
                    item { SettingsRow(Icons.Rounded.Devices, "Appareils & sessions", "Gérer les connexions actives", { onNavigate(Route.Sessions) }) }
                    item { SettingsRow(Icons.Rounded.Shield, "Confidentialité & données", "Export RGPD, suppression", { onNavigate(Route.Privacy) }) }

                    if (d != null && d.recentActivity.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)); SectionHeader("Activité récente"); Spacer(Modifier.height(4.dp)) }
                        items(d.recentActivity.take(6)) { a ->
                            ListItem(
                                leadingContent = { Icon(activityIcon(a.kind), null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text(a.title) },
                                supportingContent = { a.subtitle?.let { Text(it) } },
                                trailingContent = { Text(a.occurredAtUtc.relativeTime(), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)); SectionHeader("Aide"); Spacer(Modifier.height(4.dp)) }
                item { SettingsRow(Icons.Rounded.SupportAgent, "Support & retours", "Tickets, idées, bugs", { onNavigate(Route.Support) }) }
                item { SettingsRow(Icons.Rounded.NewReleases, "Nouveautés", "Journal des versions", { onNavigate(Route.Changelog) }) }
                item { SettingsRow(Icons.Rounded.Gavel, "Conditions d'utilisation", null, { onNavigate(Route.Legal("cgu")) }) }
                item { SettingsRow(Icons.Rounded.PrivacyTip, "Politique de confidentialité", null, { onNavigate(Route.Legal("confidentialite")) }) }

                if (authState is AuthState.Authenticated) item {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton({ confirmLogout = true }, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null); Spacer(Modifier.width(8.dp)); Text("Se déconnecter")
                    }
                }
                item { Spacer(Modifier.height(12.dp)); Text("Looby v${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Se déconnecter ?") },
        text = { Text("Vous devrez vous reconnecter avec Viple ID pour accéder à votre compte.") },
        confirmButton = { TextButton({ confirmLogout = false; onSignOut() }) { Text("Déconnexion", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Annuler") } }
    )
}

private fun activityIcon(kind: String) = when (kind.lowercase()) {
    "listing" -> Icons.Rounded.Storefront; "order" -> Icons.Rounded.ShoppingBag; "message" -> Icons.Rounded.ChatBubble
    "support" -> Icons.Rounded.SupportAgent; "livria" -> Icons.Rounded.AutoStories; else -> Icons.Rounded.History
}
