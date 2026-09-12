package com.viple.looby.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationListing(
    val id: String,
    val title: String = "",
    val price: Double? = null,
    val isDonation: Boolean = false,
    val coverUrl: String? = null,
    val status: ListingStatus = ListingStatus.Published
)

@Serializable
data class ConversationParticipant(
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val memberSinceUtc: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    val role: ConversationRole = ConversationRole.Buyer,
    val listing: ConversationListing? = null,
    val other: ConversationParticipant = ConversationParticipant(),
    val title: String = "",
    val subtitle: String = "",
    val lastMessagePreview: String? = null,
    val lastMessageAtUtc: String? = null,
    val lastMessageIsMine: Boolean = false,
    val unreadCount: Int = 0,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val createdAtUtc: String
)

@Serializable
data class MessageReaction(
    val emoji: String,
    val count: Int = 0,
    val mine: Boolean = false,
    val users: List<String> = emptyList()
)

@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderUserId: String,
    val senderDisplayName: String = "",
    val senderAvatarUrl: String? = null,
    val body: String = "",
    val attachmentKind: MessageAttachmentKind = MessageAttachmentKind.None,
    val attachmentUrl: String? = null,
    val attachmentPreviewUrl: String? = null,
    val attachmentName: String? = null,
    val attachmentWidth: Int? = null,
    val attachmentHeight: Int? = null,
    val replyToMessageId: String? = null,
    val replyToPreview: String? = null,
    val replyToSenderName: String? = null,
    val isRead: Boolean = false,
    val readAtUtc: String? = null,
    val editedAtUtc: String? = null,
    val isDeleted: Boolean = false,
    val isSystem: Boolean = false,
    val sentAtUtc: String,
    val reactions: List<MessageReaction> = emptyList(),
    /** Local uniquement : identifiant optimiste et état d'envoi. */
    val clientId: String? = null,
    val isPending: Boolean = false,
    val sendFailed: Boolean = false
)

@Serializable
data class MessagePage(val items: List<Message> = emptyList(), val hasMore: Boolean = false)

@Serializable
data class InboxSummary(val unreadConversations: Int = 0, val unreadMessages: Int = 0)

@Serializable
data class StartConversationRequest(val listingId: String, val otherUserId: String? = null)

@Serializable
data class SendMessageRequest(
    val conversationId: String,
    val body: String? = null,
    val attachmentKind: MessageAttachmentKind = MessageAttachmentKind.None,
    val attachmentUrl: String? = null,
    val attachmentPreviewUrl: String? = null,
    val attachmentName: String? = null,
    val attachmentWidth: Int? = null,
    val attachmentHeight: Int? = null,
    val replyToMessageId: String? = null,
    val clientId: String? = null
)

@Serializable
data class TypingNotification(val conversationId: String, val userId: String, val displayName: String, val isTyping: Boolean)

@Serializable
data class MessagesReadNotification(val conversationId: String, val readerUserId: String, val readAtUtc: String)

@Serializable
data class MessageDeletedNotification(val conversationId: String, val messageId: String)

@Serializable
data class Gif(
    val id: String,
    val title: String = "",
    val url: String,
    val previewUrl: String,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class GifSearchResult(val items: List<Gif> = emptyList(), val next: String? = null, val available: Boolean = true)
