package com.viple.looby.ui.listings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viple.looby.core.model.Category
import com.viple.looby.core.model.CategoryAttributeDefinition
import com.viple.looby.core.model.CategoryAttributeType
import com.viple.looby.ui.components.*
import com.viple.looby.ui.home.categoryIcon
import com.viple.looby.ui.theme.LoobyTheme

@Composable
fun ListingWizardScreen(listingId: String?, onClose: () -> Unit, onPublished: (String) -> Unit, vm: ListingWizardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.error) { s.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
    val progress by animateFloatAsState((s.step.index + 1) / WizardStep.entries.size.toFloat(), label = "progress")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Column { Text(s.step.label); Text("Étape ${s.step.index + 1} sur ${WizardStep.entries.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    navigationIcon = { IconButton(onClick = { if (s.step == WizardStep.Category) onClose() else vm.back() }) { Icon(if (s.step == WizardStep.Category) Icons.Rounded.Close else Icons.Rounded.ArrowBack, "Retour") } },
                    actions = { TextButton(onClick = onClose) { Text("Quitter") } }
                )
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(4.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.navigationBarsPadding().imePadding().padding(20.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.step != WizardStep.Category) OutlinedButton(onClick = vm::back, Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.large) { Text("Précédent") }
                    Button(onClick = { vm.next(onPublished) }, enabled = s.canProceed && !s.saving, modifier = Modifier.weight(2f).height(52.dp), shape = MaterialTheme.shapes.large) {
                        if (s.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(if (s.step == WizardStep.Review) "Publier l'annonce 🚀" else "Continuer", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        if (s.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        AnimatedContent(
            targetState = s.step,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                val forward = targetState.index >= initialState.index
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "step"
        ) { step ->
            when (step) {
                WizardStep.Category -> CategoryStep(s, vm::selectCategory)
                WizardStep.Details -> DetailsStep(s, vm)
                WizardStep.Attributes -> AttributesStep(s, vm::setAttribute)
                WizardStep.Media -> MediaStep(s, vm)
                WizardStep.Price -> PriceStep(s, vm)
                WizardStep.Review -> ReviewStep(s)
            }
        }
    }
}

@Composable
private fun CategoryStep(s: WizardUiState, onSelect: (String) -> Unit) {
    var parent by remember { mutableStateOf<Category?>(null) }
    val shown = parent?.children ?: s.categories
    Column(Modifier.fillMaxSize()) {
        Text(
            if (parent == null) "Dans quelle catégorie ?" else parent!!.name,
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp)
        )
        if (parent != null) TextButton(onClick = { parent = null }, Modifier.padding(horizontal = 12.dp)) { Icon(Icons.Rounded.ArrowBack, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Toutes les catégories") }
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { c ->
                val selected = s.categoryId == c.id
                Card(
                    onClick = { if (c.children.isNotEmpty()) { parent = c; onSelect(c.id) } else onSelect(c.id) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(Modifier.padding(16.dp).height(84.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(categoryIcon(c.iconName), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            if (c.allowsDonation) Icon(Icons.Rounded.Favorite, "Dons possibles", Modifier.size(16.dp), tint = LoobyTheme.brand.gively)
                            if (c.children.isNotEmpty()) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsStep(s: WizardUiState, vm: ListingWizardViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Décrivez votre objet", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(s.title, vm::setTitle, label = { Text("Titre *") }, placeholder = { Text("Ex. Canapé 3 places en velours") }, supportingText = { Text("${s.title.length}/120") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.description, vm::setDescription, label = { Text("Description") }, placeholder = { Text("État, dimensions, raison de la vente…") }, minLines = 5, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${s.description.length}/4000") })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(s.postalCode, vm::setPostalCode, label = { Text("Code postal") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(s.city, vm::setCity, label = { Text("Ville") }, singleLine = true, modifier = Modifier.weight(2f), leadingIcon = { Icon(Icons.Rounded.Place, null) })
        }
    }
}

@Composable
private fun AttributesStep(s: WizardUiState, onSet: (String, String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Caractéristiques", style = MaterialTheme.typography.headlineSmall)
        if (s.attributes.isEmpty()) {
            EmptyState("Rien à préciser", "Cette catégorie ne demande pas d'information supplémentaire.", Icons.Rounded.Check)
        }
        s.attributes.forEach { def -> AttributeField(def, s.attributeValues[def.key] ?: "", onSet) }
    }
}

@Composable
private fun AttributeField(def: CategoryAttributeDefinition, value: String, onSet: (String, String) -> Unit) {
    val label = if (def.isRequired) "${def.name} *" else def.name
    when (def.type) {
        CategoryAttributeType.Text -> OutlinedTextField(value, { onSet(def.key, it) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CategoryAttributeType.Number -> OutlinedTextField(value, { onSet(def.key, it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
        CategoryAttributeType.Boolean -> Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onSet(def.key, if (value == "true") "false" else "true") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(value == "true", { onSet(def.key, it.toString()) })
        }
        CategoryAttributeType.SingleChoice -> Column {
            Text(label, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                def.options.forEach { o -> FilterChip(value == o, { onSet(def.key, o) }, label = { Text(o) }) }
            }
        }
        CategoryAttributeType.MultiChoice -> Column {
            val selected = value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
            Text(label, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                def.options.forEach { o ->
                    FilterChip(o in selected, { onSet(def.key, (if (o in selected) selected - o else selected + o).joinToString(",")) }, label = { Text(o) })
                }
            }
        }
    }
}

@Composable
private fun MediaStep(s: WizardUiState, vm: ListingWizardViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris -> if (uris.isNotEmpty()) vm.addMedia(uris) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ajoutez des photos", style = MaterialTheme.typography.headlineSmall)
        Text("Jusqu'à 10 photos. La première sera la couverture.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.mediaUrls.size + s.uploading < 10) item {
                Box(
                    Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium).border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text("Ajouter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(s.mediaUrls, key = { it }) { url ->
                Box(Modifier.aspectRatio(1f)) {
                    NetworkImage(url, Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium))
                    if (s.mediaUrls.first() == url) BrandBadge("Couverture", MaterialTheme.colorScheme.primary, Modifier.align(Alignment.BottomStart).padding(6.dp))
                    FilledIconButton(onClick = { vm.removeMedia(url) }, Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)) {
                        Icon(Icons.Rounded.Close, "Retirer", Modifier.size(16.dp))
                    }
                }
            }
            items(s.uploading) { Box(Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium).shimmer(), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
        }
    }
}

@Composable
private fun PriceStep(s: WizardUiState, vm: ListingWizardViewModel) {
    val brand = LoobyTheme.brand
    val allowsDonation = s.selectedCategory?.allowsDonation ?: true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Vendre ou donner ?", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceCard("Vendre", "Fixez votre prix sur Looby", Icons.Rounded.Sell, brand.looby, !s.isDonation, Modifier.weight(1f)) { vm.setDonation(false) }
            ChoiceCard("Donner", "Gratuit via Gively", Icons.Rounded.Favorite, brand.gively, s.isDonation, Modifier.weight(1f), enabled = allowsDonation) { vm.setDonation(true) }
        }
        if (!s.isDonation) {
            OutlinedTextField(
                s.price, vm::setPrice, label = { Text("Prix *") }, suffix = { Text("€") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = brand.gively.copy(alpha = 0.12f)), shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolunteerActivism, null, tint = brand.gively); Spacer(Modifier.width(12.dp))
                    Text("Merci ! Votre don sera mis en avant dans la section Gively.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, selected: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        onClick = onClick, enabled = enabled, modifier = modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(color), Alignment.Center) { Icon(icon, null, tint = Color.White) }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewStep(s: WizardUiState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tout est prêt ?", style = MaterialTheme.typography.headlineSmall)
        MediaCarousel(s.mediaUrls, Modifier.clip(MaterialTheme.shapes.large))
        Text(s.title, style = MaterialTheme.typography.titleLarge)
        Text(
            if (s.isDonation) "Don · Gratuit" else "${s.price.replace(',', '.').toDoubleOrNull() ?: 0.0} €",
            style = MaterialTheme.typography.headlineMedium, color = if (s.isDonation) LoobyTheme.brand.gively else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
        )
        ReviewRow("Catégorie", s.selectedCategory?.name ?: "—")
        ReviewRow("Localisation", listOf(s.postalCode, s.city).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "—" })
        if (s.description.isNotBlank()) ReviewRow("Description", s.description)
        s.attributes.forEach { d -> s.attributeValues[d.key]?.takeIf { it.isNotBlank() }?.let { ReviewRow(d.name, it) } }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge) }
}
