package com.viple.looby.core.network

import com.viple.looby.core.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MobileApi {
    @GET("api/mobile/v1/app/config")
    suspend fun appConfig(): MobileAppConfig

    @POST("api/mobile/v1/auth/exchange")
    suspend fun exchange(@Body body: MobileTokenExchangeRequest): MobileTokenResponse

    @POST("api/mobile/v1/auth/refresh")
    suspend fun refresh(@Body body: MobileRefreshRequest): MobileTokenResponse

    @POST("api/mobile/v1/auth/logout")
    suspend fun logout(@Body body: MobileLogoutRequest): Response<Unit>

    @POST("api/mobile/v1/auth/logout-all")
    suspend fun logoutAll(): Response<Unit>

    @GET("api/mobile/v1/auth/me")
    suspend fun me(): MobileUser

    @GET("api/mobile/v1/auth/sessions")
    suspend fun sessions(): List<MobileSession>

    @DELETE("api/mobile/v1/auth/sessions/{deviceId}")
    suspend fun revokeSession(@Path("deviceId") deviceId: String): Response<Unit>

    @GET("api/mobile/v1/devices")
    suspend fun devices(): List<MobileDevice>

    @PUT("api/mobile/v1/devices/push-token")
    suspend fun updatePushToken(@Body body: UpdatePushTokenRequest): Response<Unit>

    @DELETE("api/mobile/v1/devices/{deviceId}")
    suspend fun deleteDevice(@Path("deviceId") deviceId: String): Response<Unit>

    @GET("api/mobile/v1/home")
    suspend fun home(): MobileHomeFeed

    @GET("api/mobile/v1/listings/search")
    suspend fun searchListings(
        @Query("query") query: String? = null,
        @Query("categoryId") categoryId: String? = null,
        @Query("donationsOnly") donationsOnly: Boolean? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null,
        @Query("city") city: String? = null,
        @Query("postalCode") postalCode: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): PagedResult<Listing>
}

interface CatalogApi {
    @GET("api/categories")
    suspend fun categories(): List<Category>

    @GET("api/categories/{categoryId}/attributes")
    suspend fun categoryAttributes(@Path("categoryId") categoryId: String): List<CategoryAttributeDefinition>

    @GET("api/listings/{listingId}")
    suspend fun listing(@Path("listingId") listingId: String): Listing

    @GET("api/listings/mine")
    suspend fun myListings(): List<Listing>

    @POST("api/listings/step")
    suspend fun saveListingStep(@Body body: SaveListingStepRequest): Listing

    @GET("api/users/{userId}/public-profile")
    suspend fun publicProfile(@Path("userId") userId: String): PublicUserProfile

    @POST("api/media/upload-ticket")
    suspend fun uploadTicket(@Query("section") section: Int, @Query("fileName") fileName: String): UploadTicket

    @GET("api/legal/{slug}")
    suspend fun legal(@Path("slug") slug: String): LegalDocument

    @GET("api/changelog")
    suspend fun changelog(): List<ChangelogEntry>

    @GET("api/orders/mine")
    suspend fun myOrders(): List<Order>

    @POST("api/orders/pay")
    suspend fun payOrder(@Body body: PayOrderRequest): PayOrderResult
}

interface MessagingApi {
    @GET("api/messaging/conversations")
    suspend fun conversations(@Query("archived") archived: Boolean = false): List<Conversation>

    @GET("api/messaging/conversations/{id}")
    suspend fun conversation(@Path("id") id: String): Conversation

    @POST("api/messaging/conversations")
    suspend fun startConversation(@Body body: StartConversationRequest): Conversation

    @GET("api/messaging/conversations/{id}/messages")
    suspend fun messages(
        @Path("id") id: String,
        @Query("before") before: String? = null,
        @Query("take") take: Int = 50
    ): MessagePage

    @POST("api/messaging/conversations/{id}/read")
    suspend fun markRead(@Path("id") id: String): ResponseBody

    @POST("api/messaging/conversations/{id}/archive")
    suspend fun archive(@Path("id") id: String, @Query("value") value: Boolean): Response<Unit>

    @POST("api/messaging/conversations/{id}/mute")
    suspend fun mute(@Path("id") id: String, @Query("value") value: Boolean): Response<Unit>

    @GET("api/messaging/inbox")
    suspend fun inbox(): InboxSummary

    @GET("api/messaging/gifs")
    suspend fun gifs(@Query("q") query: String, @Query("pos") pos: String? = null, @Query("limit") limit: Int = 24): GifSearchResult
}

interface AccountApi {
    @GET("api/account/profile")
    suspend fun profile(): MyAccountProfile

    @GET("api/account/dashboard")
    suspend fun dashboard(): AccountDashboard

    @PUT("api/account/preferences")
    suspend fun updatePreferences(@Body body: UpdateAccountPreferencesRequest): Response<Unit>

    @POST("api/account/notifications")
    suspend fun updateNotifications(@Body body: UpdateNotificationSettingsRequest): Response<Unit>

    @GET("api/account/addresses")
    suspend fun addresses(): List<UserAddress>

    @POST("api/account/addresses")
    suspend fun saveAddress(@Body body: SaveUserAddressRequest): UserAddress

    @DELETE("api/account/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: String): Response<Unit>

    @GET("api/account/payment-methods")
    suspend fun paymentMethods(): List<UserPaymentMethod>

    @DELETE("api/account/payment-methods/{id}")
    suspend fun deletePaymentMethod(@Path("id") id: String): Response<Unit>

    @GET("api/account/gdpr/export")
    suspend fun gdprExport(): ResponseBody

    @POST("api/account/gdpr/delete")
    suspend fun gdprDelete(): Response<Unit>
}

interface SupportApi {
    @GET("api/support/tickets")
    suspend fun tickets(): List<SupportTicket>

    @POST("api/support/tickets")
    suspend fun createTicket(@Body body: CreateSupportTicketRequest): SupportTicket

    @POST("api/support/tickets/messages")
    suspend fun addTicketMessage(@Body body: AddSupportTicketMessageRequest): SupportTicket

    @GET("api/support/feature-requests")
    suspend fun featureRequests(): List<FeatureRequest>

    @POST("api/support/feature-requests")
    suspend fun createFeatureRequest(@Body body: CreateFeatureRequestRequest): FeatureRequest

    @GET("api/support/bug-reports")
    suspend fun bugReports(): List<BugReport>

    @POST("api/support/bug-reports")
    suspend fun createBugReport(@Body body: CreateBugReportRequest): BugReport
}

interface LivriaApi {
    @GET("api/livria/books")
    suspend fun books(@Query("isbn") isbn: String? = null, @Query("genre") genre: String? = null, @Query("query") query: String? = null): List<Book>

    @GET("api/livria/books/{bookId}")
    suspend fun book(@Path("bookId") bookId: String): Book

    @GET("api/livria/profile")
    suspend fun profile(): Response<LivriaProfile>

    @POST("api/livria/onboarding")
    suspend fun onboarding(@Body body: CompleteLivriaOnboardingRequest): LivriaProfile

    @PUT("api/livria/profile")
    suspend fun updateProfile(@Body body: UpdateLivriaProfileRequest): ProfileMutationResult

    @GET("api/livria/hub")
    suspend fun hub(): LivriaHub

    @GET("api/livria/recommendations")
    suspend fun recommendations(): List<RecommendedBook>

    @GET("api/livria/shelf")
    suspend fun shelf(): List<ShelfEntry>

    @POST("api/livria/shelf")
    suspend fun upsertShelf(@Body body: UpsertShelfEntryRequest): ShelfMutationResult

    @DELETE("api/livria/shelf/{bookId}")
    suspend fun removeFromShelf(@Path("bookId") bookId: String): Response<Unit>

    @GET("api/livria/exchanges")
    suspend fun exchanges(@Query("genre") genre: String? = null, @Query("query") query: String? = null): ExchangeHub

    @POST("api/livria/exchanges/offers")
    suspend fun createOffer(@Body body: CreateExchangeOfferRequest): OfferMutationResult

    @DELETE("api/livria/exchanges/offers/{offerId}")
    suspend fun cancelOffer(@Path("offerId") offerId: String): Response<Unit>

    @POST("api/livria/exchanges/requests")
    suspend fun createRequest(@Body body: CreateExchangeRequestRequest): ExchangeMutationResult

    @POST("api/livria/exchanges/requests/{id}/accept")
    suspend fun acceptRequest(@Path("id") id: String): ExchangeMutationResult

    @POST("api/livria/exchanges/requests/{id}/decline")
    suspend fun declineRequest(@Path("id") id: String): ExchangeMutationResult

    @POST("api/livria/exchanges/requests/{id}/confirm")
    suspend fun confirmRequest(@Path("id") id: String): ExchangeMutationResult

    @GET("api/livria/leaderboard")
    suspend fun leaderboard(): List<LeaderboardEntry>

    @POST("api/livria/orders")
    suspend fun placeOrder(@Body body: PlaceBookOrderRequest): BookOrder
}
