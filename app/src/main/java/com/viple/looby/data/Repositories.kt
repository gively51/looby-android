package com.viple.looby.data

import android.net.Uri
import com.viple.looby.core.model.*
import com.viple.looby.core.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListingRepository @Inject constructor(
    private val mobileApi: MobileApi,
    private val catalogApi: CatalogApi,
    private val uploader: MediaUploader
) {
    private var categoriesCache: List<Category>? = null

    suspend fun home(): Result<MobileHomeFeed> = apiCall { mobileApi.home() }

    suspend fun categories(force: Boolean = false): Result<List<Category>> = apiCall {
        if (!force) categoriesCache?.let { return@apiCall it }
        catalogApi.categories().also { categoriesCache = it }
    }

    fun flattenCategories(tree: List<Category>): List<Category> =
        tree.flatMap { listOf(it) + flattenCategories(it.children) }

    suspend fun attributes(categoryId: String): Result<List<CategoryAttributeDefinition>> =
        apiCall { catalogApi.categoryAttributes(categoryId) }

    suspend fun search(q: ListingSearchQuery): Result<PagedResult<Listing>> = apiCall {
        mobileApi.searchListings(
            query = q.query?.takeIf { it.isNotBlank() },
            categoryId = q.categoryId,
            donationsOnly = q.donationsOnly,
            minPrice = q.minPrice,
            maxPrice = q.maxPrice,
            city = q.city?.takeIf { it.isNotBlank() },
            postalCode = q.postalCode?.takeIf { it.isNotBlank() },
            sort = q.sort,
            page = q.page,
            pageSize = q.pageSize
        )
    }

    suspend fun listing(id: String): Result<Listing> = apiCall { catalogApi.listing(id) }
    suspend fun myListings(): Result<List<Listing>> = apiCall { catalogApi.myListings() }
    suspend fun saveStep(request: SaveListingStepRequest): Result<Listing> = apiCall { catalogApi.saveListingStep(request) }
    suspend fun publicProfile(userId: String): Result<PublicUserProfile> = apiCall { catalogApi.publicProfile(userId) }
    suspend fun uploadMedia(uri: Uri, section: SectionTheme = SectionTheme.Looby): Result<String> = uploader.upload(uri, section)
    suspend fun legal(slug: String): Result<LegalDocument> = apiCall { catalogApi.legal(slug) }
    suspend fun changelog(): Result<List<ChangelogEntry>> = apiCall { catalogApi.changelog() }
    suspend fun myOrders(): Result<List<Order>> = apiCall { catalogApi.myOrders() }
    suspend fun payOrder(orderId: String): Result<PayOrderResult> = apiCall { catalogApi.payOrder(PayOrderRequest(orderId)) }
}

@Singleton
class MessagingRepository @Inject constructor(
    private val api: MessagingApi,
    private val hub: com.viple.looby.core.realtime.MessagingHubClient
) {
    private val _inbox = MutableStateFlow(InboxSummary())
    val inbox: StateFlow<InboxSummary> = _inbox.asStateFlow()

    val hubEvents get() = hub.events
    val hubStatus get() = hub.status

    suspend fun refreshInbox(): Result<InboxSummary> = apiCall { api.inbox().also { _inbox.value = it } }
    fun clearInbox() { _inbox.value = InboxSummary() }

    suspend fun conversations(archived: Boolean = false): Result<List<Conversation>> = apiCall { api.conversations(archived) }
    suspend fun conversation(id: String): Result<Conversation> = apiCall { api.conversation(id) }
    suspend fun start(listingId: String): Result<Conversation> = apiCall { api.startConversation(StartConversationRequest(listingId)) }
    suspend fun messages(id: String, before: String? = null, take: Int = 50): Result<MessagePage> = apiCall { api.messages(id, before, take) }
    suspend fun markRead(id: String): Result<Unit> = apiCall { api.markRead(id); hub.markAsRead(id) }
    suspend fun archive(id: String, value: Boolean): Result<Unit> = apiCall { api.archive(id, value); Unit }
    suspend fun mute(id: String, value: Boolean): Result<Unit> = apiCall { api.mute(id, value); Unit }
    suspend fun gifs(q: String, pos: String? = null): Result<GifSearchResult> = apiCall { api.gifs(q, pos) }

    suspend fun join(id: String) = hub.joinConversation(id)
    suspend fun leave(id: String) = hub.leaveConversation(id)
    suspend fun send(request: SendMessageRequest): Result<Message> = apiCall { hub.sendMessage(request) }
    suspend fun edit(messageId: String, body: String): Result<Unit> = apiCall { hub.editMessage(messageId, body) }
    suspend fun delete(conversationId: String, messageId: String): Result<Unit> = apiCall { hub.deleteMessage(conversationId, messageId) }
    suspend fun react(messageId: String, emoji: String): Result<Unit> = apiCall { hub.toggleReaction(messageId, emoji) }
    suspend fun typing(conversationId: String, isTyping: Boolean) = hub.setTyping(conversationId, isTyping)
}

@Singleton
class AccountRepository @Inject constructor(
    private val api: AccountApi,
    private val mobileApi: MobileApi,
    private val supportApi: SupportApi
) {
    suspend fun profile(): Result<MyAccountProfile> = apiCall { api.profile() }
    suspend fun dashboard(): Result<AccountDashboard> = apiCall { api.dashboard() }
    suspend fun updatePreferences(r: UpdateAccountPreferencesRequest): Result<Unit> = apiCall { api.updatePreferences(r); Unit }
    suspend fun updateNotifications(email: Boolean, messaging: Boolean): Result<Unit> =
        apiCall { api.updateNotifications(UpdateNotificationSettingsRequest(email, messaging)); Unit }
    suspend fun addresses(): Result<List<UserAddress>> = apiCall { api.addresses() }
    suspend fun saveAddress(r: SaveUserAddressRequest): Result<UserAddress> = apiCall { api.saveAddress(r) }
    suspend fun deleteAddress(id: String): Result<Unit> = apiCall { api.deleteAddress(id); Unit }
    suspend fun paymentMethods(): Result<List<UserPaymentMethod>> = apiCall { api.paymentMethods() }
    suspend fun deletePaymentMethod(id: String): Result<Unit> = apiCall { api.deletePaymentMethod(id); Unit }
    suspend fun gdprExport(): Result<String> = apiCall { api.gdprExport().string() }
    suspend fun gdprDelete(): Result<Unit> = apiCall { api.gdprDelete(); Unit }
    suspend fun sessions(): Result<List<MobileSession>> = apiCall { mobileApi.sessions() }
    suspend fun revokeSession(deviceId: String): Result<Unit> = apiCall { mobileApi.revokeSession(deviceId); Unit }
    suspend fun devices(): Result<List<MobileDevice>> = apiCall { mobileApi.devices() }

    suspend fun tickets(): Result<List<SupportTicket>> = apiCall { supportApi.tickets() }
    suspend fun createTicket(subject: String, body: String): Result<SupportTicket> = apiCall { supportApi.createTicket(CreateSupportTicketRequest(subject, body)) }
    suspend fun replyTicket(ticketId: String, body: String): Result<SupportTicket> = apiCall { supportApi.addTicketMessage(AddSupportTicketMessageRequest(ticketId, body)) }
    suspend fun featureRequests(): Result<List<FeatureRequest>> = apiCall { supportApi.featureRequests() }
    suspend fun createFeatureRequest(title: String, description: String): Result<FeatureRequest> = apiCall { supportApi.createFeatureRequest(CreateFeatureRequestRequest(title, description)) }
    suspend fun bugReports(): Result<List<BugReport>> = apiCall { supportApi.bugReports() }
    suspend fun createBugReport(r: CreateBugReportRequest): Result<BugReport> = apiCall { supportApi.createBugReport(r) }
}

@Singleton
class LivriaRepository @Inject constructor(private val api: LivriaApi) {
    suspend fun books(query: String? = null, genre: String? = null, isbn: String? = null): Result<List<Book>> = apiCall { api.books(isbn, genre, query) }
    suspend fun book(id: String): Result<Book> = apiCall { api.book(id) }

    /** null = onboarding non réalisé (404). */
    suspend fun profile(): Result<LivriaProfile?> = apiCall {
        val r = api.profile()
        if (r.code() == 404) null else if (r.isSuccessful) r.body() else throw retrofit2.HttpException(r)
    }
    suspend fun onboarding(r: CompleteLivriaOnboardingRequest): Result<LivriaProfile> = apiCall { api.onboarding(r) }
    suspend fun updateProfile(r: UpdateLivriaProfileRequest): Result<ProfileMutationResult> = apiCall { api.updateProfile(r) }
    suspend fun hub(): Result<LivriaHub> = apiCall { api.hub() }
    suspend fun recommendations(): Result<List<RecommendedBook>> = apiCall { api.recommendations() }
    suspend fun shelf(): Result<List<ShelfEntry>> = apiCall { api.shelf() }
    suspend fun upsertShelf(bookId: String, status: String, rating: Int? = null): Result<ShelfMutationResult> =
        apiCall { api.upsertShelf(UpsertShelfEntryRequest(bookId, status, rating)) }
    suspend fun removeFromShelf(bookId: String): Result<Unit> = apiCall { api.removeFromShelf(bookId); Unit }
    suspend fun exchanges(genre: String? = null, query: String? = null): Result<ExchangeHub> = apiCall { api.exchanges(genre, query) }
    suspend fun createOffer(r: CreateExchangeOfferRequest): Result<OfferMutationResult> = apiCall { api.createOffer(r) }
    suspend fun cancelOffer(id: String): Result<Unit> = apiCall { api.cancelOffer(id); Unit }
    suspend fun createRequest(r: CreateExchangeRequestRequest): Result<ExchangeMutationResult> = apiCall { api.createRequest(r) }
    suspend fun accept(id: String): Result<ExchangeMutationResult> = apiCall { api.acceptRequest(id) }
    suspend fun decline(id: String): Result<ExchangeMutationResult> = apiCall { api.declineRequest(id) }
    suspend fun confirm(id: String): Result<ExchangeMutationResult> = apiCall { api.confirmRequest(id) }
    suspend fun leaderboard(): Result<List<LeaderboardEntry>> = apiCall { api.leaderboard() }
    suspend fun order(quantities: Map<String, Int>): Result<BookOrder> = apiCall { api.placeOrder(PlaceBookOrderRequest(quantities)) }
}
