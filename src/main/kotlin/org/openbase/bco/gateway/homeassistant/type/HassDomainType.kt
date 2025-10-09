package org.openbase.bco.gateway.homeassistant.type

enum class HassDomainType(
    val id: String,
) {
    LIGHT("light"),
    SWITCH("switch"),
    SENSOR("sensor"),
    BINARY_SENSOR("binary_sensor"),
    COVER("cover"),
    FAN("fan"),
    CLIMATE("climate"),
    MEDIA_PLAYER("media_player"),
    SCENE("scene"),
    SCRIPT("script"),
    AUTOMATION("automation"),
    GROUP("group"),
    INPUT_BOOLEAN("input_boolean"),
    INPUT_NUMBER("input_number"),
    INPUT_SELECT("input_select"),
    INPUT_TEXT("input_text"),
    CAMERA("camera"),
    ALARM_CONTROL_PANEL("alarm_control_panel"),
    LOCK("lock"),
    VACUUM("vacuum"),
    WATER_HEATER("water_heater"),
    DEVICE_TRACKER("device_tracker"),
    PERSON("person"),
    ZONE("zone"),
    BUTTON("button"),
    NUMBER("number"),
    SELECT("select"),
    SIREN("siren"),
    UPDATE("update"),
    TEXT("text"),
    TIMER("timer"),
    COUNTER("counter"),
    WEATHER("weather"),
    ENERGY("energy"),
    GRID("grid"),
    MOISTURE("moisture"),
    TEMPERATURE("temperature"),
    AIR_QUALITY("air_quality"),
    HUMIDITY("humidity"),
    VOLUME("volume"),
    UNKNOWN("unknown"),
    EVENT("event"),
    ;

    companion object {
        private const val ENTITY_ID_DELIMITER: String = "."

        private fun fromId(id: String): HassDomainType? = HassDomainType.entries.find { it.id == id }

        fun fromEntityId(entityId: String): HassDomainType = HassDomainType.fromId(entityId.substringBefore(ENTITY_ID_DELIMITER)) ?: UNKNOWN
    }
}

fun String.toHassDomainType(): HassDomainType = HassDomainType.fromEntityId(this)
