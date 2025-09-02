package org.openbase.bco.device.hass.util

import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.type.language.LabelType

fun LabelType.LabelOrBuilder.bestMatch(): String? = LabelProcessor.getBestMatch(this)
