package org.openbase.bco.device.hass.util

import org.openbase.jul.exception.tryOrNull
import org.openbase.jul.extension.type.processing.MetaConfigProcessor
import org.openbase.type.configuration.EntryType
import org.openbase.type.configuration.MetaConfigType

operator fun MetaConfigType.MetaConfig.get(key: String): String? = tryOrNull {
    MetaConfigProcessor.getValue(this, key)
}

operator fun MetaConfigType.MetaConfig.Builder.get(key: String): String? = tryOrNull {
    MetaConfigProcessor.getValue(this.build(), key)
}

operator fun MetaConfigType.MetaConfig.set(key: String, value: String): MetaConfigType.MetaConfig =
    toBuilder().set(key, value).build()

operator fun MetaConfigType.MetaConfig.Builder.set(key: String, value: String): MetaConfigType.MetaConfig.Builder = apply {
    EntryType.Entry.newBuilder().setKey(key).setValue(value).build().let { newEntry ->
        entryList
            .indexOfFirst { it.key == key }
            .takeIf { it != -1 }
            ?.also { index -> setEntry(index, newEntry) }
            ?: addEntry(newEntry)
    }
}

operator fun MetaConfigType.MetaConfig.contains(key: String): Boolean =
    entryList.any { entry -> entry.key == key }

operator fun MetaConfigType.MetaConfig.Builder.contains(key: String): Boolean =
    entryList.any { entry -> entry.key == key }
