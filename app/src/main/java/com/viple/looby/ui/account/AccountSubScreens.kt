package com.viple.looby.ui.account

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.auth.SessionManager
import com.viple.looby.core.model.*
import com.viple.looby.data.AccountRepository
import com.viple.looby.data.ListingRepository
import com.viple.looby.ui.components.*
import com.viple.looby.ui.theme.LoobyTheme
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

/* ---------- Préférences ---------- */

data class SettingsUiState(val profile: MyAccountProfile? = null, val displayName: String = "", val email: Boolean = true, val messaging: Boolean = true, val saving: Boolean = false, val error: String? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: AccountRepository, private val session: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toast = _toast.asSharedFlow()
    init { viewModelScope.launch { repo.profile().onSuccess { p -> _state.update { it.copy(profile = p, displayName = p.displayName ?: "", email = p.emailNotificationsEnabled, messaging = p.messagingNotificationsEnabled) } }.onFailure { e -> _state.update { it.copy(error = e.message) } } } }
    fun setDisplayName(v: String) = _state.update { it.copy(displayName = v.take(60)) }
    fun setEmail(v: Boolean) { _state.update { it.copy(email = v) }; saveNotifications() }
    fun setMessaging(v: Boolean) { _state.update { it.copy(messaging = v) }; saveNotifications() }
    private fun saveNotifications() = viewModelScope.launch { repo.updateNotifications(_state.value.email, _state.value.messaging).onFailure { _toast.tryEmit(it.message ?: "Erreur") } }
    fun save() = viewModelScope.launch {
        val s = _state.value; _state.update { it.copy(saving = true) }
        repo.updatePreferences(UpdateAccountPreferencesRequest(s.displayName.trim().ifBlank { null }, s.profile?.avatarUrl, s.email, s.messaging))
            .onSuccess { session.refreshMe(); _toast.tryEmit("Préférences enregistrées") }.onFailure { _toast.tryEmit(it.message ?: "Erreur") }
        _state.update { it.copy(saving = false) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOutAll: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.toast.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { LoobyTopBar("Préférences", onBack) }) { padding ->
        if (s.profile == null && s.error == null) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text("Profil public", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                OutlinedTextField(s.displayName, vm::setDisplayName, label = { Text("Nom affiché") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                s.profile?.email?.let { OutlinedTextField(it, {}, label = { Text("E‑mail (Viple ID)") }, enabled = false, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                Spacer(Modifier.height(10.dp))
                Button(vm::save, enabled = !s.saving, shape = MaterialTheme.shapes.medium) { Text("Enregistrer") }
            }
            Column {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                SettingsRow(Icons.Rounded.Email, "E‑mails", "Nouveautés, récapitulatifs", { vm.setEmail(!s.email) }) { Switch(s.email, vm::setEmail) }
                SettingsRow(Icons.Rounded.ChatBubble, "Messagerie", "Nouveaux messages", { vm.setMessaging(!s.messaging) }) { Switch(s.messaging, vm::setMessaging) }
            }
            Column {
                Text("Sécurité", style = MaterialTheme.typography.titleMedium)
                SettingsRow(Icons.Rounded.PhonelinkErase, "Déconnecter tous les appareils", "Révoque toutes les sessions mobiles", onSignOutAll)
            }
        }
    }
}

/* ---------- Adresses ---------- */

@HiltViewModel
class AddressesViewModel @Inject constructor(private val repo: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow<Result<List<UserAddress>>?>(null)
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = repo.addresses() }
    fun save(r: SaveUserAddressRequest, done: () -> Unit) = viewModelScope.launch { repo.saveAddress(r).onSuccess { load(); done() } }
    fun delete(id: String) = viewModelScope.launch { repo.deleteAddress(id); load() }
}

@Composable
fun AddressesScreen(onBack: () -> Unit, vm: AddressesViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<UserAddress?>(null) }
    var showForm by remember { mutableStateOf(false) }
    Scaffold(topBar = { LoobyTopBar("Mes adresses", onBack) }, floatingActionButton = { FloatingActionButton({ editing = null; showForm = true }) { Icon(Icons.Rounded.Add, "Ajouter") } }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                result!!.getOrThrow().isEmpty() -> EmptyState("Aucune adresse", "Ajoutez une adresse pour vos livraisons.", Icons.Rounded.Home, Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(result!!.getOrThrow(), key = { it.id }) { a ->
                        Card(onClick = { editing = a; showForm = true }, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text(a.label, style = MaterialTheme.typography.titleMedium); if (a.isDefault) { Spacer(Modifier.width(8.dp)); BrandBadge("Par défaut", MaterialTheme.colorScheme.primary) } }
                                    Text(a.recipientName, fontWeight = FontWeight.Medium)
                                    Text(listOfNotNull(a.line1, a.line2, "${a.postalCode} ${a.city}", a.country).joinToString("\n"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton({ vm.delete(a.id) }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showForm) AddressForm(editing, { showForm = false }) { vm.save(it) { showForm = false } }
}

@Composable
private fun AddressForm(existing: UserAddress?, onDismiss: () -> Unit, onSave: (SaveUserAddressRequest) -> Unit) {
    var label by remember { mutableStateOf(existing?.label ?: "") }; var name by remember { mutableStateOf(existing?.recipientName ?: "") }
    var line1 by remember { mutableStateOf(existing?.line1 ?: "") }; var line2 by remember { mutableStateOf(existing?.line2 ?: "") }
    var cp by remember { mutableStateOf(existing?.postalCode ?: "") }; var city by remember { mutableStateOf(existing?.city ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }; var default by remember { mutableStateOf(existing?.isDefault ?: false) }
    val valid = label.isNotBlank() && name.isNotBlank() && line1.isNotBlank() && cp.isNotBlank() && city.isNotBlank()
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (existing == null) "Nouvelle adresse" else "Modifier l'adresse", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(label, { label = it }, label = { Text("Libellé (Maison, Bureau…) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Destinataire *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(line1, { line1 = it }, label = { Text("Adresse *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(line2, { line2 = it }, label = { Text("Complément") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(cp, { cp = it.filter(Char::isDigit).take(5) }, label = { Text("CP *") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(city, { city = it }, label = { Text("Ville *") }, singleLine = true, modifier = Modifier.weight(2f))
            }
            OutlinedTextField(phone, { phone = it }, label = { Text("Téléphone") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(default, { default = it }); Text("Adresse par défaut") }
            Button({ onSave(SaveUserAddressRequest(existing?.id, label.trim(), name.trim(), line1.trim(), line2.ifBlank { null }, cp, city.trim(), "France", phone.ifBlank { null }, default)) }, Modifier.fillMaxWidth().height(52.dp), enabled = valid, shape = MaterialTheme.shapes.large) { Text("Enregistrer") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ---------- Paiement ---------- */

@HiltViewModel
class PaymentMethodsViewModel @Inject constructor(private val repo: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow<Result<List<UserPaymentMethod>>?>(null)
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = repo.paymentMethods() }
    fun delete(id: String) = viewModelScope.launch { repo.deletePaymentMethod(id); load() }
}

@Composable
fun PaymentMethodsScreen(onBack: () -> Unit, vm: PaymentMethodsViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { LoobyTopBar("Moyens de paiement", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                result!!.getOrThrow().isEmpty() -> EmptyState("Aucune carte enregistrée", "Les cartes sont ajoutées lors d'un paiement.", Icons.Rounded.CreditCard, Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(result!!.getOrThrow(), key = { it.id }) { pm ->
                        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CreditCard, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("${pm.brand.replaceFirstChar { it.uppercase() }} •••• ${pm.last4}", style = MaterialTheme.typography.titleMedium); if (pm.isDefault) { Spacer(Modifier.width(8.dp)); BrandBadge("Par défaut", MaterialTheme.colorScheme.primary) } }
                                    Text("Expire ${pm.expiryMonth.toString().padStart(2, '0')}/${pm.expiryYear}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton({ vm.delete(pm.id) }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Commandes ---------- */

@HiltViewModel
class OrdersViewModel @Inject constructor(private val repo: ListingRepository) : ViewModel() {
    private val _state = MutableStateFlow<Result<List<Order>>?>(null)
    val state = _state.asStateFlow()
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toast = _toast.asSharedFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = repo.myOrders() }
    fun pay(id: String) = viewModelScope.launch { repo.payOrder(id).onSuccess { _toast.tryEmit(if (it.success) "Paiement confirmé ✓" else "Paiement refusé"); load() }.onFailure { _toast.tryEmit(it.message ?: "Erreur") } }
}

fun orderStatusLabel(s: OrderStatus) = when (s) {
    OrderStatus.PendingPayment -> "En attente de paiement"; OrderStatus.Paid -> "Payée"; OrderStatus.Preparing -> "En préparation"; OrderStatus.Shipped -> "Expédiée"
    OrderStatus.Delivered -> "Livrée"; OrderStatus.Cancelled -> "Annulée"; OrderStatus.Refunded -> "Remboursée"
}

@Composable
fun OrdersScreen(onBack: () -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    val result by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.toast.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { LoobyTopBar("Mes commandes", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                result == null -> LoadingState()
                result!!.isFailure -> ErrorState(result!!.exceptionOrNull()?.message ?: "", onRetry = vm::load)
                result!!.getOrThrow().isEmpty() -> EmptyState("Aucune commande", "Vos achats Livria apparaîtront ici.", Icons.Rounded.ShoppingBag, Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(result!!.getOrThrow(), key = { it.id }) { o ->
                        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Commande ${o.id.take(8).uppercase()}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    BrandBadge(orderStatusLabel(o.status), when (o.status) { OrderStatus.Paid, OrderStatus.Delivered -> LoobyTheme.brand.gively; OrderStatus.Cancelled, OrderStatus.Refunded -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.tertiary })
                                }
                                Text(o.createdAtUtc.formatDateTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                o.items.forEach { i -> Row { Text("${i.quantity} × ${i.productName}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("%.2f €".format(i.unitPrice * i.quantity), style = MaterialTheme.typography.bodyMedium) } }
                                HorizontalDivider()
                                Row { Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("%.2f €".format(o.totalAmount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                                if (o.status == OrderStatus.PendingPayment) Button(
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Sessions & appareils ---------- */

data class SessionsUiState(val sessions: List<MobileSession> = emptyList(), val devices: List<MobileDevice> = emptyList(), val loading: Boolean = true, val error: String? = null)

@HiltViewModel
class SessionsViewModel @Inject constructor(private val repo: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        val s = repo.sessions(); val d = repo.devices()
        _state.update { it.copy(loading = false, sessions = s.getOrDefault(emptyList()), devices = d.getOrDefault(emptyList()), error = s.exceptionOrNull()?.message) }
    }
    fun revoke(deviceId: String) = viewModelScope.launch { repo.revokeSession(deviceId); load() }
}

@Composable
fun SessionsScreen(onBack: () -> Unit, vm: SessionsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { LoobyTopBar("Appareils & sessions", onBack) }) { padding ->
        when {
            s.loading -> LoadingState(Modifier.padding(padding))
            s.error != null && s.sessions.isEmpty() -> ErrorState(s.error!!, Modifier.padding(padding), vm::load)
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Sessions actives", style = MaterialTheme.typography.titleMedium) }
                if (s.sessions.isEmpty()) item { Text("Aucune session active.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(s.sessions, key = { it.id }) { se ->
                    val device = s.devices.firstOrNull { it.deviceId == se.deviceId }
                    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = if (se.isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (se.platform == MobilePlatform.Ios) Icons.Rounded.PhoneIphone else Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text(se.model ?: device?.model ?: se.platform.name, style = MaterialTheme.typography.titleSmall); if (se.isCurrent) { Spacer(Modifier.width(8.dp)); BrandBadge("Cet appareil", MaterialTheme.colorScheme.primary) } }
                                Text("Dernière activité ${(se.lastUsedAtUtc ?: se.createdAtUtc).relativeTime()} · expire le ${se.expiresAtUtc.formatDateTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                device?.appVersion?.let { Text("Looby $it · ${device.osVersion ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                            }
                            if (!se.isCurrent) IconButton({ vm.revoke(se.deviceId) }) { Icon(Icons.Rounded.Block, "Révoquer", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Confidentialité / RGPD ---------- */

@HiltViewModel
class PrivacyViewModel @Inject constructor(private val repo: AccountRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _export = MutableStateFlow<String?>(null); val export = _export.asStateFlow()
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 2); val toast = _toast.asSharedFlow()
    fun export() = viewModelScope.launch { _busy.value = true; repo.gdprExport().onSuccess { _export.value = it }.onFailure { _toast.tryEmit(it.message ?: "Erreur") }; _busy.value = false }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { _busy.value = true; repo.gdprDelete().onSuccess { onDone() }.onFailure { _toast.tryEmit(it.message ?: "Erreur") }; _busy.value = false }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, onDeleted: () -> Unit, vm: PrivacyViewModel = hiltViewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.toast.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(export) { export?.let { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, it); putExtra(Intent.EXTRA_SUBJECT, "Export de mes données Looby") }, "Exporter mes données")) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { LoobyTopBar("Confidentialité & données", onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Exporter mes données", style = MaterialTheme.typography.titleMedium)
                    Text("Recevez une copie complète (JSON) de vos données personnelles, annonces, messages et commandes, conformément au RGPD.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(vm::export, enabled = !busy) { Text("Générer l'export") }
                }
            }
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                    Text("Supprimer mon compte", style = MaterialTheme.typography.titleMedium)
                    Text("Cette action est irréversible : vos annonces, conversations et données Livria seront définitivement effacées.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button({ confirm = true }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Supprimer définitivement") }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Confirmer la suppression") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Tapez SUPPRIMER pour confirmer."); OutlinedTextField(confirmText, { confirmText = it }, singleLine = true) } },
        confirmButton = { TextButton({ confirm = false; vm.delete(onDeleted) }, enabled = confirmText == "SUPPRIMER") { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Annuler") } }
    )
}
