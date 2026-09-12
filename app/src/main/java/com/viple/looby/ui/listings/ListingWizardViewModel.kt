package com.viple.looby.ui.listings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viple.looby.core.model.*
import com.viple.looby.data.ListingRepository
import com.viple.looby.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WizardStep(val index: Int, val label: String) {
    Category(0, "Catégorie"), Details(1, "Détails"), Attributes(2, "Caractéristiques"), Media(3, "Photos"), Price(4, "Prix"), Review(5, "Récapitulatif");
    fun toApi() = ListingWizardStep.from(index)
    companion object { fun fromApi(s: ListingWizardStep) = entries.firstOrNull { it.index == s.value } ?: Category }
}

data class WizardUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val listingId: String? = null,
    val step: WizardStep = WizardStep.Category,
    val categories: List<Category> = emptyList(),
    val attributes: List<CategoryAttributeDefinition> = emptyList(),
    // Champs
    val categoryId: String? = null,
    val title: String = "",
    val description: String = "",
    val city: String = "",
    val postalCode: String = "",
    val attributeValues: Map<String, String> = emptyMap(),
    val mediaUrls: List<String> = emptyList(),
    val uploading: Int = 0,
    val isDonation: Boolean = false,
    val price: String = "",
    val published: Listing? = null
) {
    val selectedCategory get() = flatten(categories).firstOrNull { it.id == categoryId }
    val canProceed: Boolean get() = when (step) {
        WizardStep.Category -> categoryId != null
        WizardStep.Details -> title.trim().length >= 3
        WizardStep.Attributes -> attributes.filter { it.isRequired }.all { !attributeValues[it.key].isNullOrBlank() }
        WizardStep.Media -> uploading == 0
        WizardStep.Price -> isDonation || (price.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0
        WizardStep.Review -> true
    }
    private fun flatten(c: List<Category>): List<Category> = c.flatMap { listOf(it) + flatten(it.children) }
}

@HiltViewModel
class ListingWizardViewModel @Inject constructor(
    private val repo: ListingRepository,
    savedState: SavedStateHandle
) : ViewModel() {
    private val initialId = savedState.toRoute<Route.ListingWizard>().listingId
    private val _state = MutableStateFlow(WizardUiState(listingId = initialId))
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cats = repo.categories().getOrElse { e -> _state.update { it.copy(loading = false, error = e.message) }; return@launch }
            _state.update { it.copy(categories = cats) }
            if (initialId != null) {
                repo.listing(initialId).onSuccess { l ->
                    _state.update {
                        it.copy(
                            categoryId = l.categoryId, title = l.title, description = l.description ?: "",
                            city = l.city ?: "", postalCode = l.postalCode ?: "", attributeValues = l.attributeValues,
                            mediaUrls = l.mediaUrls, isDonation = l.isDonation, price = l.price?.takeIf { p -> p > 0 }?.toString() ?: "",
                            step = WizardStep.fromApi(l.currentWizardStep).let { s -> if (s == WizardStep.Review && l.status == ListingStatus.Published) WizardStep.Review else s }
                        )
                    }
                    loadAttributes(l.categoryId)
                }.onFailure { e -> _state.update { it.copy(error = e.message) } }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun loadAttributes(categoryId: String) = viewModelScope.launch {
        repo.attributes(categoryId).onSuccess { a -> _state.update { it.copy(attributes = a.sortedBy { d -> d.sortOrder }) } }
    }

    fun selectCategory(id: String) {
        _state.update { it.copy(categoryId = id, attributeValues = if (it.categoryId == id) it.attributeValues else emptyMap()) }
        loadAttributes(id)
    }
    fun setTitle(v: String) = _state.update { it.copy(title = v.take(120)) }
    fun setDescription(v: String) = _state.update { it.copy(description = v.take(4000)) }
    fun setCity(v: String) = _state.update { it.copy(city = v) }
    fun setPostalCode(v: String) = _state.update { it.copy(postalCode = v.filter(Char::isDigit).take(5)) }
    fun setAttribute(key: String, value: String) = _state.update { it.copy(attributeValues = it.attributeValues + (key to value)) }
    fun setDonation(v: Boolean) = _state.update { it.copy(isDonation = v, price = if (v) "" else it.price) }
    fun setPrice(v: String) = _state.update { it.copy(price = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(9)) }
    fun removeMedia(url: String) = _state.update { it.copy(mediaUrls = it.mediaUrls - url) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun addMedia(uris: List<Uri>) {
        val section = if (_state.value.isDonation) SectionTheme.Gively else SectionTheme.Looby
        uris.take(10 - _state.value.mediaUrls.size).forEach { uri ->
            viewModelScope.launch {
                _state.update { it.copy(uploading = it.uploading + 1) }
                repo.uploadMedia(uri, section)
                    .onSuccess { url -> _state.update { it.copy(mediaUrls = it.mediaUrls + url) } }
                    .onFailure { e -> _state.update { it.copy(error = e.message) } }
                _state.update { it.copy(uploading = it.uploading - 1) }
            }
        }
    }

    fun back() { _state.update { it.copy(step = WizardStep.entries.getOrElse(it.step.index - 1) { WizardStep.Category }) } }

    /** Sauvegarde l'étape courante côté serveur puis avance. */
    fun next(onPublished: (String) -> Unit) {
        val s = _state.value
        if (!s.canProceed || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val publish = s.step == WizardStep.Review
            val priceValue = if (s.isDonation) 0.0 else s.price.replace(',', '.').toDoubleOrNull()
            val req = SaveListingStepRequest(
                listingId = s.listingId,
                step = s.step.toApi(),
                categoryId = s.categoryId,
                title = s.title.trim().ifBlank { null },
                description = s.description.trim().ifBlank { null },
                price = if (s.step >= WizardStep.Price) priceValue else null,
                city = s.city.trim().ifBlank { null },
                postalCode = s.postalCode.trim().ifBlank { null },
                attributeValues = if (s.step >= WizardStep.Attributes) s.attributeValues else null,
                mediaUrls = if (s.step >= WizardStep.Media) s.mediaUrls else null,
                publish = publish
            )
            repo.saveStep(req)
                .onSuccess { l ->
                    if (publish) { _state.update { it.copy(saving = false, published = l) }; onPublished(l.id) }
                    else _state.update { it.copy(saving = false, listingId = l.id, step = WizardStep.entries[it.step.index + 1]) }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }
}
