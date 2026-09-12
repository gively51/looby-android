package com.viple.looby.ui.livria

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viple.looby.core.model.CompleteLivriaOnboardingRequest
import com.viple.looby.core.model.LivriaProfile
import com.viple.looby.core.model.UpdateLivriaProfileRequest
import com.viple.looby.data.LivriaRepository
import com.viple.looby.ui.components.LoadingState
import com.viple.looby.ui.components.LoobyTopBar
import com.viple.looby.ui.theme.LoobyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val GenreChoices = listOf("Roman", "Policier", "Thriller", "Science-fiction", "Fantasy", "Romance", "Jeunesse", "BD / Manga", "Biographie", "Histoire", "Essai", "Poésie", "Développement personnel", "Cuisine", "Voyage")
private val AccentColors = listOf("#6750A4", "#E11D48", "#0EA5E9", "#16A34A", "#F59E0B", "#7C3AED", "#0F766E", "#DB2777")

data class LivriaProfileFormState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val pseudonym: String = "",
    val genres: Set<String> = emptySet(),
    val authors: String = "",
    val motto: String = "",
    val bio: String = "",
    val accentColor: String = AccentColors.first(),
    val readingGoal: Int = 12,
    val done: Boolean = false
) { val valid get() = pseudonym.trim().length >= 3 && genres.isNotEmpty() }

@HiltViewModel
class LivriaProfileFormViewModel @Inject constructor(private val repo: LivriaRepository) : ViewModel() {
    private val _state = MutableStateFlow(LivriaProfileFormState())
    val state = _state.asStateFlow()

    fun loadExisting() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        repo.profile().onSuccess { p -> p?.let(::apply) }
        _state.update { it.copy(loading = false) }
    }
    private fun apply(p: LivriaProfile) = _state.update {
        it.copy(pseudonym = p.readerPseudonym, genres = p.favoriteGenres.toSet(), authors = p.favoriteAuthors.joinToString(", "), motto = p.motto ?: "", bio = p.bio ?: "", accentColor = p.accentColor ?: AccentColors.first(), readingGoal = p.readingGoal)
    }

    fun setPseudonym(v: String) = _state.update { it.copy(pseudonym = v.take(30)) }
    fun toggleGenre(g: String) = _state.update { it.copy(genres = if (g in it.genres) it.genres - g else if (it.genres.size < 5) it.genres + g else it.genres) }
    fun setAuthors(v: String) = _state.update { it.copy(authors = v) }
    fun setMotto(v: String) = _state.update { it.copy(motto = v.take(80)) }
    fun setBio(v: String) = _state.update { it.copy(bio = v.take(500)) }
    fun setAccent(v: String) = _state.update { it.copy(accentColor = v) }
    fun setGoal(v: Int) = _state.update { it.copy(readingGoal = v.coerceIn(1, 200)) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private fun authorsList() = _state.value.authors.split(',').map(String::trim).filter(String::isNotEmpty)

    fun submitOnboarding() = viewModelScope.launch {
        val s = _state.value; _state.update { it.copy(saving = true) }
        repo.onboarding(CompleteLivriaOnboardingRequest(s.pseudonym.trim(), s.genres.toList(), authorsList(), s.motto.ifBlank { null }, s.accentColor, null, s.readingGoal))
            .onSuccess { _state.update { it.copy(saving = false, done = true) } }.onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
    }
    fun submitUpdate() = viewModelScope.launch {
        val s = _state.value; _state.update { it.copy(saving = true) }
        repo.updateProfile(UpdateLivriaProfileRequest(s.pseudonym.trim(), s.genres.toList(), authorsList(), s.bio.ifBlank { null }, s.motto.ifBlank { null }, null, s.accentColor, s.readingGoal))
            .onSuccess { _state.update { it.copy(saving = false, done = true) } }.onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
    }
}

@Composable
fun LivriaOnboardingScreen(onBack: () -> Unit, onDone: () -> Unit, vm: LivriaProfileFormViewModel = hiltViewModel()) {
    ProfileForm("Votre profil lecteur", "Créer mon profil", onBack, onDone, vm, isOnboarding = true)
}

@Composable
fun LivriaProfileEditScreen(onBack: () -> Unit, vm: LivriaProfileFormViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.loadExisting() }
    ProfileForm("Modifier mon profil", "Enregistrer", onBack, onBack, vm, isOnboarding = false)
}

@Composable
private fun ProfileForm(title: String, cta: String, onBack: () -> Unit, onDone: () -> Unit, vm: LivriaProfileFormViewModel, isOnboarding: Boolean) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.error) { s.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
    LaunchedEffect(s.done) { if (s.done) onDone() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { LoobyTopBar(title, onBack) }) { padding ->
        if (s.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (isOnboarding) Text("Quelques questions pour personnaliser vos recommandations et votre profil public.", color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(s.pseudonym, vm::setPseudonym, label = { Text("Pseudonyme de lecteur *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("3 à 30 caractères") })

            Column {
                Text("Genres favoris * (max 5)", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenreChoices.forEach { g -> FilterChip(g in s.genres, { vm.toggleGenre(g) }, label = { Text(g) }, leadingIcon = if (g in s.genres) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null) }
                }
            }

            OutlinedTextField(s.authors, vm::setAuthors, label = { Text("Auteurs favoris") }, placeholder = { Text("Séparés par des virgules") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.motto, vm::setMotto, label = { Text("Devise") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!isOnboarding) OutlinedTextField(s.bio, vm::setBio, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            Column {
                Text("Couleur d'accent", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentColors.forEach { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c).then(if (s.accentColor == hex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier).clickable { vm.setAccent(hex) }, Alignment.Center) {
                            if (s.accentColor == hex) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }
            }

            Column {
                Row { Text("Objectif de lecture annuel", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("${s.readingGoal} livres", color = LoobyTheme.brand.livria, style = MaterialTheme.typography.titleSmall) }
                Slider(s.readingGoal.toFloat(), { vm.setGoal(it.toInt()) }, valueRange = 1f..60f, steps = 58, colors = SliderDefaults.colors(thumbColor = LoobyTheme.brand.livria, activeTrackColor = LoobyTheme.brand.livria))
            }

            Button({ if (isOnboarding) vm.submitOnboarding() else vm.submitUpdate() }, Modifier.fillMaxWidth().height(54.dp), enabled = s.valid && !s.saving, shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(containerColor = LoobyTheme.brand.livria)) {
                if (s.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) else Text(cta)
            }
        }
    }
}
