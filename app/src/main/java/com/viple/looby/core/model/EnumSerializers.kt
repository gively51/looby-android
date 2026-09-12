package com.viple.looby.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Sérialiseur générique pour les enums exposés en entiers par l'API .NET. */
abstract class IntEnumSerializer<T : Enum<T>>(
    name: String,
    private val toInt: (T) -> Int,
    private val fromInt: (Int) -> T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeInt(toInt(value))
    override fun deserialize(decoder: Decoder): T = fromInt(decoder.decodeInt())
}

object ListingStatusSerializer : IntEnumSerializer<ListingStatus>("ListingStatus", { it.value }, ListingStatus::from)
object ListingWizardStepSerializer : IntEnumSerializer<ListingWizardStep>("ListingWizardStep", { it.value }, ListingWizardStep::from)
object CategoryAttributeTypeSerializer : IntEnumSerializer<CategoryAttributeType>("CategoryAttributeType", { it.value }, CategoryAttributeType::from)
object OrderStatusSerializer : IntEnumSerializer<OrderStatus>("OrderStatus", { it.value }, OrderStatus::from)
object SupportTicketStatusSerializer : IntEnumSerializer<SupportTicketStatus>("SupportTicketStatus", { it.value }, SupportTicketStatus::from)
object FeatureRequestStatusSerializer : IntEnumSerializer<FeatureRequestStatus>("FeatureRequestStatus", { it.value }, FeatureRequestStatus::from)
object BugReportSeveritySerializer : IntEnumSerializer<BugReportSeverity>("BugReportSeverity", { it.value }, BugReportSeverity::from)
object BugReportStatusSerializer : IntEnumSerializer<BugReportStatus>("BugReportStatus", { it.value }, BugReportStatus::from)
object ConversationRoleSerializer : IntEnumSerializer<ConversationRole>("ConversationRole", { it.value }, ConversationRole::from)
object MessageAttachmentKindSerializer : IntEnumSerializer<MessageAttachmentKind>("MessageAttachmentKind", { it.value }, MessageAttachmentKind::from)
object MobilePlatformSerializer : IntEnumSerializer<MobilePlatform>("MobilePlatform", { it.value }, MobilePlatform::from)
