package li.klass.walkingpad2hc.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object StepMessageParser {
    fun parse(payload: String, stepsKey: String): Long? = try {
        if (payload.length > 10_000) {
            null
        } else {
            val element = Json.parseToJsonElement(payload).jsonObject
            val value = element[stepsKey]?.jsonPrimitive
            value?.longOrNull ?: value?.doubleOrNull?.let { Math.round(it) }
        }
    } catch (_: Throwable) {
        null
    }
}
