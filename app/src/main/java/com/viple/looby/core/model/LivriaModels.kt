package com.viple.looby.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LivriaProgress(
    val xp: Int = 0,
    val level: Int = 1,
    val levelTitle: String = "Apprenti lecteur",
    val xpIntoLevel: Int = 0,
    val xpForNextLevel: Int = 100,
    val levelPercent: Double = 0.0
)

@Serializable
data class LivriaBadge(
    val code: String,
    val name: String = "",
    val description: String = "",
    val icon: String = "🏅",
    val unlocked: Boolean = false,
    val unlockedAtUtc: String? = null
)

@Serializable
data class LivriaProfile(
    val userId: String = "",
    val readerPseudonym: String = "",
    val favoriteGenres: List<String> = emptyList(),
    val favoriteAuthors: List<String> = emptyList(),
    val hasCompletedOnboarding: Boolean = false,
    val bio: String? = null,
    val motto: String? = null,
    val avatarUrl: String? = null,
    val accentColor: String? = null,
    val readingGoal: Int = 12,
    val booksReadThisYear: Int = 0,
    val shelfCount: Int = 0,
    val exchangesCompleted: Int = 0,
    val progress: LivriaProgress = LivriaProgress(),
    val badges: List<LivriaBadge> = emptyList()
)

@Serializable
data class CompleteLivriaOnboardingRequest(
    val readerPseudonym: String,
    val favoriteGenres: List<String> = emptyList(),
    val favoriteAuthors: List<String> = emptyList(),
    val motto: String? = null,
    val accentColor: String? = null,
    val avatarUrl: String? = null,
    val readingGoal: Int = 12
)

@Serializable
data class UpdateLivriaProfileRequest(
    val readerPseudonym: String,
    val favoriteGenres: List<String> = emptyList(),
    val favoriteAuthors: List<String> = emptyList(),
    val bio: String? = null,
    val motto: String? = null,
    val avatarUrl: String? = null,
    val accentColor: String? = null,
    val readingGoal: Int = 12
)

@Serializable
data class LivriaReward(val xpGained: Int = 0, val progress: LivriaProgress = LivriaProgress(), val leveledUp: Boolean = false)

@Serializable
data class Book(
    val id: String,
    val isbn: String = "",
    val title: String = "",
    val author: String = "",
    val publisher: String? = null,
    val genre: String? = null,
    val description: String? = null,
    val coverImageBlobUrl: String? = null,
    val retailPrice: Double = 0.0,
    val livriaPrice: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val stockQuantity: Int = 0
)

@Serializable
data class RecommendedBook(val book: Book, val score: Double = 0.0, val reason: String = "")

@Serializable
data class ShelfEntry(
    val id: String,
    val book: Book,
    val status: String = "WantToRead",
    val rating: Int? = null,
    val finishedAtUtc: String? = null
)

@Serializable
data class UpsertShelfEntryRequest(val bookId: String, val status: String = "WantToRead", val rating: Int? = null)

@Serializable
data class ShelfMutationResult(val entry: ShelfEntry, val reward: LivriaReward = LivriaReward())

@Serializable
data class ExchangeOffer(
    val id: String,
    val ownerUserId: String = "",
    val ownerPseudonym: String = "",
    val ownerAvatarUrl: String? = null,
    val ownerAccentColor: String? = null,
    val ownerLevel: Int = 1,
    val bookId: String? = null,
    val title: String = "",
    val author: String = "",
    val genre: String? = null,
    val condition: String? = null,
    val notes: String? = null,
    val photoUrl: String? = null,
    val coverImageBlobUrl: String? = null,
    val wantedBookId: String? = null,
    val wantedBookTitle: String? = null,
    val wantedGenre: String? = null,
    val wantedDescription: String? = null,
    val status: String = "Open",
    val requestCount: Int = 0,
    val isMine: Boolean = false,
    val matchesMyWishlist: Boolean = false,
    val createdAtUtc: String? = null
)

@Serializable
data class CreateExchangeOfferRequest(
    val bookId: String? = null,
    val title: String,
    val author: String,
    val genre: String? = null,
    val condition: String? = null,
    val notes: String? = null,
    val photoUrl: String? = null,
    val wantedBookId: String? = null,
    val wantedGenre: String? = null,
    val wantedDescription: String? = null
)

@Serializable
data class ExchangeRequest(
    val id: String,
    val offerId: String = "",
    val offerTitle: String = "",
    val requesterUserId: String = "",
    val requesterPseudonym: String = "",
    val proposedTitle: String = "",
    val proposedAuthor: String = "",
    val message: String? = null,
    val status: String = "Pending",
    val conversationId: String? = null,
    val ownerConfirmed: Boolean = false,
    val requesterConfirmed: Boolean = false,
    val iAmOwner: Boolean = false,
    val createdAtUtc: String
)

@Serializable
data class CreateExchangeRequestRequest(
    val offerId: String,
    val proposedTitle: String,
    val proposedAuthor: String,
    val message: String? = null
)

@Serializable
data class ExchangeMutationResult(val request: ExchangeRequest, val reward: LivriaReward? = null)

@Serializable
data class ExchangeHub(
    val offers: List<ExchangeOffer> = emptyList(),
    val myOffers: List<ExchangeOffer> = emptyList(),
    val incomingRequests: List<ExchangeRequest> = emptyList(),
    val outgoingRequests: List<ExchangeRequest> = emptyList()
)

@Serializable
data class ProfileMutationResult(val profile: LivriaProfile, val reward: LivriaReward = LivriaReward())

@Serializable
data class OfferMutationResult(val offer: ExchangeOffer, val reward: LivriaReward = LivriaReward())

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val userId: String = "",
    val readerPseudonym: String = "",
    val avatarUrl: String? = null,
    val accentColor: String? = null,
    val xp: Int = 0,
    val level: Int = 1,
    val levelTitle: String = "",
    val badgeCount: Int = 0,
    val exchangesCompleted: Int = 0,
    val isMe: Boolean = false
)

@Serializable
data class LivriaHub(
    val profile: LivriaProfile = LivriaProfile(),
    val recommendations: List<RecommendedBook> = emptyList(),
    val trending: List<Book> = emptyList(),
    val newArrivals: List<Book> = emptyList(),
    val shelf: List<ShelfEntry> = emptyList(),
    val dailyQuests: List<String> = emptyList()
)

@Serializable
data class PlaceBookOrderRequest(val quantities: Map<String, Int>)

@Serializable
data class BookOrderLine(val bookId: String, val title: String = "", val quantity: Int = 0, val unitPrice: Double = 0.0)

@Serializable
data class BookOrder(val orderId: String, val totalAmount: Double = 0.0, val lines: List<BookOrderLine> = emptyList())
