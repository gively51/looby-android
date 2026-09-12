package com.viple.looby.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MyAccountProfile(
    val userId: String,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val memberSinceUtc: String,
    val emailNotificationsEnabled: Boolean = true,
    val messagingNotificationsEnabled: Boolean = true
)

@Serializable
data class UpdateNotificationSettingsRequest(val emailNotificationsEnabled: Boolean, val messagingNotificationsEnabled: Boolean)

@Serializable
data class UpdateAccountPreferencesRequest(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val messagingNotificationsEnabled: Boolean = true
)

@Serializable
data class UserAddress(
    val id: String,
    val label: String = "",
    val recipientName: String = "",
    val line1: String = "",
    val line2: String? = null,
    val postalCode: String = "",
    val city: String = "",
    val country: String = "France",
    val phone: String? = null,
    val isDefault: Boolean = false
)

@Serializable
data class SaveUserAddressRequest(
    val id: String? = null,
    val label: String,
    val recipientName: String,
    val line1: String,
    val line2: String? = null,
    val postalCode: String,
    val city: String,
    val country: String = "France",
    val phone: String? = null,
    val isDefault: Boolean = false
)

@Serializable
data class UserPaymentMethod(
    val id: String,
    val brand: String = "",
    val last4: String = "",
    val expiryMonth: Int = 0,
    val expiryYear: Int = 0,
    val isDefault: Boolean = false
)

@Serializable
data class AccountStats(
    val activeListings: Int = 0,
    val donationListings: Int = 0,
    val ordersPlaced: Int = 0,
    val ordersReceived: Int = 0,
    val unreadConversations: Int = 0,
    val addresses: Int = 0,
    val paymentMethods: Int = 0,
    val openSupportTickets: Int = 0
)

@Serializable
data class AccountCompletionItem(val key: String, val label: String, val href: String, val done: Boolean = false)

@Serializable
data class AccountCompletion(val percent: Int = 0, val items: List<AccountCompletionItem> = emptyList())

@Serializable
data class AccountActivity(
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val href: String? = null,
    val occurredAtUtc: String
)

@Serializable
data class AccountDashboard(
    val profile: MyAccountProfile,
    val stats: AccountStats = AccountStats(),
    val completion: AccountCompletion = AccountCompletion(),
    val recentActivity: List<AccountActivity> = emptyList(),
    val livriaProgress: LivriaProgress? = null
)

@Serializable
data class SupportTicketMessage(
    val authorUserId: String,
    val authorDisplayName: String = "",
    val isFromSupportStaff: Boolean = false,
    val body: String = "",
    val createdAtUtc: String
)

@Serializable
data class SupportTicket(
    val id: String,
    val subject: String = "",
    val status: SupportTicketStatus = SupportTicketStatus.Open,
    val createdAtUtc: String,
    val messages: List<SupportTicketMessage> = emptyList()
)

@Serializable
data class CreateSupportTicketRequest(val subject: String, val body: String)

@Serializable
data class AddSupportTicketMessageRequest(val ticketId: String, val body: String)

@Serializable
data class FeatureRequest(
    val id: String,
    val title: String = "",
    val description: String = "",
    val status: FeatureRequestStatus = FeatureRequestStatus.Proposed,
    val upvotes: Int = 0
)

@Serializable
data class CreateFeatureRequestRequest(val title: String, val description: String)

@Serializable
data class BugReport(
    val id: String,
    val title: String = "",
    val description: String = "",
    val stepsToReproduce: String? = null,
    val severity: BugReportSeverity = BugReportSeverity.Medium,
    val status: BugReportStatus = BugReportStatus.New
)

@Serializable
data class CreateBugReportRequest(
    val title: String,
    val description: String,
    val stepsToReproduce: String? = null,
    val severity: BugReportSeverity = BugReportSeverity.Medium
)

@Serializable
data class ChangelogEntry(
    val id: String,
    val version: String = "",
    val title: String = "",
    val contentMarkdown: String = "",
    val publishedAtUtc: String
)
