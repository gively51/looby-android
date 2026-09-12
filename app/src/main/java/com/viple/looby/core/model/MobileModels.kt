package com.viple.looby.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MobileDeviceRegistration(
    val deviceId: String,
    val platform: MobilePlatform = MobilePlatform.Android,
    val model: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val pushToken: String? = null,
    val locale: String? = null
)

@Serializable
data class MobileTokenExchangeRequest(
    val vipleIdAccessToken: String,
    val device: MobileDeviceRegistration
)

@Serializable
data class MobileRefreshRequest(val refreshToken: String, val deviceId: String)

@Serializable
data class MobileLogoutRequest(val deviceId: String)

@Serializable
data class MobileTokenResponse(
    val tokenType: String = "Bearer",
    val accessToken: String,
    val accessTokenExpiresAtUtc: String,
    val refreshToken: String,
    val refreshTokenExpiresAtUtc: String,
    val user: MobileUser
)

@Serializable
data class MobileUser(
    val id: String,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val displayName: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val avatarUrl: String? = null,
    val role: String = "User",
    val isBanned: Boolean = false,
    val memberSinceUtc: String
) {
    val isAdmin get() = role == "Admin"
    val isLivriaAdmin get() = role == "LivriaAdmin" || isAdmin
    val shortName get() = displayName?.takeIf { it.isNotBlank() } ?: givenName ?: email?.substringBefore('@') ?: "Vous"
}

@Serializable
data class MobileDevice(
    val id: String,
    val deviceId: String,
    val platform: MobilePlatform,
    val model: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val hasPushToken: Boolean = false,
    val pushEnabled: Boolean = false,
    val isCurrent: Boolean = false,
    val createdAtUtc: String,
    val lastSeenAtUtc: String
)

@Serializable
data class MobileSession(
    val id: String,
    val deviceId: String,
    val platform: MobilePlatform,
    val model: String? = null,
    val createdAtUtc: String,
    val expiresAtUtc: String,
    val lastUsedAtUtc: String? = null,
    val isCurrent: Boolean = false
)

@Serializable
data class UpdatePushTokenRequest(val deviceId: String, val pushToken: String?, val pushEnabled: Boolean = true)

@Serializable
data class MobileAppConfig(
    val apiVersion: String = "1",
    val minimumSupportedAppVersion: String = "1.0.0",
    val latestAppVersion: String = "1.0.0",
    val forceUpdate: Boolean = false,
    val authority: String,
    val clientId: String,
    val scopes: List<String> = emptyList(),
    val messagingHubPath: String = "/hubs/messaging",
    val features: Map<String, Boolean> = emptyMap(),
    val legalDocumentSlugs: List<String> = emptyList()
)

@Serializable
data class PagedResult<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalCount: Int = 0,
    val hasMore: Boolean = false
)

@Serializable
data class MobileHomeFeed(
    val me: MobileUser? = null,
    val unreadMessages: Int = 0,
    val categories: List<Category> = emptyList(),
    val latestListings: List<Listing> = emptyList(),
    val latestDonations: List<Listing> = emptyList()
)

data class ListingSearchQuery(
    val query: String? = null,
    val categoryId: String? = null,
    val donationsOnly: Boolean? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val sort: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20
)
