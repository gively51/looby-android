package com.viple.looby.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String,
    val name: String,
    val slug: String,
    val iconName: String? = null,
    val parentCategoryId: String? = null,
    val allowsDonation: Boolean = false,
    val children: List<Category> = emptyList()
)

@Serializable
data class CategoryAttributeDefinition(
    val id: String,
    val name: String,
    val key: String,
    val type: CategoryAttributeType = CategoryAttributeType.Text,
    val isRequired: Boolean = false,
    val sortOrder: Int = 0,
    val options: List<String> = emptyList()
)

@Serializable
data class Listing(
    val id: String,
    val sellerUserId: String,
    val sellerDisplayName: String? = null,
    val categoryId: String,
    val categoryName: String? = null,
    val title: String = "",
    val description: String? = null,
    val price: Double? = null,
    val isDonation: Boolean = false,
    val status: ListingStatus = ListingStatus.Draft,
    val currentWizardStep: ListingWizardStep = ListingWizardStep.Category,
    val city: String? = null,
    val postalCode: String? = null,
    val floorPlan3DUrl: String? = null,
    val attributeValues: Map<String, String> = emptyMap(),
    val mediaUrls: List<String> = emptyList(),
    val createdAtUtc: String? = null,
    val publishedAtUtc: String? = null
) {
    val coverUrl get() = mediaUrls.firstOrNull()
    val location get() = listOfNotNull(city, postalCode).joinToString(" ").ifBlank { null }
}

@Serializable
data class SaveListingStepRequest(
    val listingId: String? = null,
    val step: ListingWizardStep,
    val categoryId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val floorPlan3DUrl: String? = null,
    val attributeValues: Map<String, String>? = null,
    val mediaUrls: List<String>? = null,
    val publish: Boolean = false
)

@Serializable
data class PublicUserProfile(
    val userId: String,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val memberSinceUtc: String,
    val activeListingsCount: Int = 0,
    val recentListings: List<Listing> = emptyList()
)

@Serializable
data class UploadTicket(val blobUrl: String, val uploadUrl: String, val contentType: String)

@Serializable
data class LegalDocument(
    val slug: String,
    val title: String,
    val contentMarkdown: String,
    val version: Int = 1,
    val publishedAtUtc: String
)

@Serializable
data class Order(
    val id: String,
    val status: OrderStatus,
    val totalAmount: Double,
    val createdAtUtc: String,
    val items: List<OrderItem> = emptyList()
)

@Serializable
data class OrderItem(val productName: String, val unitPrice: Double, val quantity: Int)

@Serializable
data class PayOrderRequest(val orderId: String)

@Serializable
data class PayOrderResult(
    val orderId: String,
    val success: Boolean,
    val status: OrderStatus,
    val providerReference: String? = null
)
