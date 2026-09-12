package com.viple.looby.ui.navigation

import kotlinx.serialization.Serializable

/** Destinations typées (Navigation Compose 2.8+). */
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data class Explore(val query: String? = null, val categoryId: String? = null, val donationsOnly: Boolean = false) : Route
    @Serializable data object Sell : Route
    @Serializable data object Messages : Route
    @Serializable data object Livria : Route
    @Serializable data object Account : Route

    @Serializable data class ListingDetail(val listingId: String) : Route
    @Serializable data class SellerProfile(val userId: String) : Route
    @Serializable data class ListingWizard(val listingId: String? = null) : Route
    @Serializable data object MyListings : Route

    @Serializable data class Conversation(val conversationId: String) : Route

    @Serializable data object LivriaCatalog : Route
    @Serializable data class BookDetail(val bookId: String) : Route
    @Serializable data object LivriaShelf : Route
    @Serializable data object LivriaExchanges : Route
    @Serializable data object LivriaLeaderboard : Route
    @Serializable data object LivriaOnboarding : Route
    @Serializable data object LivriaProfileEdit : Route

    @Serializable data object SignIn : Route
    @Serializable data object Settings : Route
    @Serializable data object Addresses : Route
    @Serializable data object PaymentMethods : Route
    @Serializable data object Orders : Route
    @Serializable data object Sessions : Route
    @Serializable data object Privacy : Route
    @Serializable data object Support : Route
    @Serializable data class SupportTicket(val ticketId: String) : Route
    @Serializable data object Changelog : Route
    @Serializable data class Legal(val slug: String) : Route
}
