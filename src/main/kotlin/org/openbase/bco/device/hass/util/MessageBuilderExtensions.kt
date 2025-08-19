package org.openbase.bco.device.hass.util

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Message
import org.openbase.jul.extension.protobuf.processing.MessageProcessor
import org.openbase.jul.extension.protobuf.processing.ProtoBufFieldProcessor
import org.openbase.jul.extension.protobuf.processing.SimpleMessageProcessor

fun <MB : Message.Builder> MB.mergeFromWithRepeatedFields(message: Message): MB = run {
    clone().mergeFrom(message).eliminateAllDuplicates() as MB
}

fun <MB : Message.Builder> MB.eliminateAllDuplicates(): MB = apply {
    allFields.keys.forEach { descriptor ->
        descriptor
            .takeIf { it.isKeyValueField }
            ?.also { eliminateDuplicatesOfMapField(descriptor) ; return@forEach }
        descriptor
            .takeIf { it.isRepeated }
            ?.also { eliminateDuplicatesOfRepeatedField(descriptor) ; return@forEach }
        descriptor
            .takeIf { it.isMessage }
            ?.also { getFieldBuilder(descriptor).eliminateAllDuplicates() }
    }
}

val FieldDescriptor.isMessage: Boolean get() = javaType == FieldDescriptor.JavaType.MESSAGE
val FieldDescriptor.isKeyValueField: Boolean get() = isRepeated
        && isMessage
        && messageType.fields.any { it.name == "key"}
        && messageType.fields.any { it.name == "value"}

fun <MB : Message.Builder> MB.eliminateDuplicatesOfRepeatedField(descriptor: FieldDescriptor): MB = also {
    val values = getField(descriptor) as List<*>
    val uniqueValues = values.distinct()
    this.setField(descriptor, uniqueValues)
}

fun <MB : Message.Builder> MB.eliminateDuplicatesOfMapField(descriptor: FieldDescriptor): MB = also {
    (0..<getRepeatedFieldCount(descriptor))
        .map { getRepeatedFieldBuilder(descriptor, it) }
        .groupBy { ProtoBufFieldProcessor.getFieldValue(it, "key") }
        .values
        .mapNotNull { it.lastOrNull() }
        .also { clearField(descriptor) }
        .forEach { addRepeatedField(descriptor, it.build()) }
//        .also { uniqueValues -> setField(descriptor, uniqueValues) }
}
