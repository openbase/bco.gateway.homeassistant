package org.openbase.bco.device.hass.util

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Message
import org.openbase.jul.extension.protobuf.processing.ProtoBufFieldProcessor
import org.openbase.jul.iface.Identifiable

fun <MB : Message.Builder> MB.mergeFromWithRepeatedFields(message: Message): MB = run {
    clone().mergeFrom(message).eliminateAllDuplicates() as MB
}

private fun <MB : Message.Builder> MB.eliminateAllDuplicates(): MB = apply {
    allFields.keys.forEach { descriptor ->
        when {
            descriptor.isKeyValueField -> eliminateDuplicatesOfMapField(descriptor)
            descriptor.isRepeated -> eliminateDuplicatesOfRepeatedField(descriptor)
            descriptor.isMessage -> getFieldBuilder(descriptor).eliminateAllDuplicates()
        }
    }
}

val FieldDescriptor.isMessage: Boolean get() = javaType == FieldDescriptor.JavaType.MESSAGE
val FieldDescriptor.isKeyValueField: Boolean get() = isRepeated
        && isMessage
        && messageType.fields.any { it.name == "key"}
        && messageType.fields.any { it.name == "value"}
val FieldDescriptor.isIdentifiable: Boolean get() = isRepeated
        && isMessage
        && messageType.fields.any { it.name == Identifiable.TYPE_FIELD_ID}

private fun <MB : Message.Builder> MB.eliminateDuplicatesOfRepeatedField(descriptor: FieldDescriptor): MB = also {
    val values = getField(descriptor) as List<Message>
    val uniqueValues = if(descriptor.isIdentifiable) {
        val idFieldDescriptor = descriptor.messageType.fields.first { it.name == Identifiable.TYPE_FIELD_ID }
        // select the latest occurrence of each id
        values.reversed().distinctBy { repeatedEntry ->
            repeatedEntry.getField(idFieldDescriptor)
        }
    } else {
        values.distinct()
    }
    setField(descriptor, uniqueValues)
    elimitateDublicatedOfRepeatedMessages(descriptor)
}

private fun <MB : Message.Builder> MB.eliminateDuplicatesOfMapField(descriptor: FieldDescriptor): MB = also {
    (0..<getRepeatedFieldCount(descriptor))
        .map { getRepeatedFieldBuilder(descriptor, it) }
        .groupBy { ProtoBufFieldProcessor.getFieldValue(it, "key") }
        .values
        .mapNotNull { it.lastOrNull() }
        .also { clearField(descriptor) }
        .forEach { addRepeatedField(descriptor, it.build()) }
    elimitateDublicatedOfRepeatedMessages(descriptor)
}

private fun <MB : Message.Builder> MB.elimitateDublicatedOfRepeatedMessages(descriptor: FieldDescriptor) = this
    .takeIf { descriptor.isMessage }
    ?.let { (0..<getRepeatedFieldCount(descriptor)) }
            ?.map { getRepeatedFieldBuilder(descriptor, it) }
            ?.forEach { it.eliminateAllDuplicates() }
