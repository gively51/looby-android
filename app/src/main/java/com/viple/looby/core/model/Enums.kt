package com.viple.looby.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Les enums de l'API sont sérialisés en entiers (System.Text.Json par défaut). */

@Serializable(with = ListingStatusSerializer::class)
enum class ListingStatus(val value: Int) {
    Draft(0), PublishInProgress(1), Published(2), Reserved(3), Sold(4), Expired(5), Removed(6);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Draft }
}

@Serializable(with = ListingWizardStepSerializer::class)
enum class ListingWizardStep(val value: Int) {
    Category(0), Details(1), DynamicAttributes(2), Media(3), PriceOrDonation(4), Review(5), Published(6);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Category }
}

@Serializable(with = CategoryAttributeTypeSerializer::class)
enum class CategoryAttributeType(val value: Int) {
    Text(0), Number(1), SingleChoice(2), MultiChoice(3), Boolean(4);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Text }
}

@Serializable(with = OrderStatusSerializer::class)
enum class OrderStatus(val value: Int) {
    PendingPayment(0), Paid(1), Preparing(2), Shipped(3), Delivered(4), Cancelled(5), Refunded(6);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: PendingPayment }
}

@Serializable(with = SupportTicketStatusSerializer::class)
enum class SupportTicketStatus(val value: Int) {
    Open(0), AwaitingUser(1), AwaitingSupport(2), Resolved(3), Closed(4);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Open }
}

@Serializable(with = FeatureRequestStatusSerializer::class)
enum class FeatureRequestStatus(val value: Int) {
    Proposed(0), UnderReview(1), Planned(2), InProgress(3), Shipped(4), Declined(5);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Proposed }
}

@Serializable(with = BugReportSeveritySerializer::class)
enum class BugReportSeverity(val value: Int) {
    Low(0), Medium(1), High(2), Critical(3);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Medium }
}

@Serializable(with = BugReportStatusSerializer::class)
enum class BugReportStatus(val value: Int) {
    New(0), Triaged(1), InProgress(2), Fixed(3), WontFix(4);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: New }
}

@Serializable(with = ConversationRoleSerializer::class)
enum class ConversationRole(val value: Int) {
    Buyer(0), Seller(1);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Buyer }
}

@Serializable(with = MessageAttachmentKindSerializer::class)
enum class MessageAttachmentKind(val value: Int) {
    None(0), Image(1), Gif(2), File(3);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: None }
}

@Serializable(with = MobilePlatformSerializer::class)
enum class MobilePlatform(val value: Int) {
    Unknown(0), Ios(1), Android(2);
    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Unknown }
}

/** SectionTheme utilisé pour les tickets d'upload média. */
enum class SectionTheme(val value: Int) { Looby(0), Gively(1), Livria(2) }
