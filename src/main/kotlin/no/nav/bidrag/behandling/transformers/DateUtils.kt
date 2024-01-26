package no.nav.bidrag.behandling.transformers

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val formatterCommpact = DateTimeFormatter.ofPattern("yyyyMMdd")

fun Date.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(this.toInstant(), ZoneId.systemDefault())
}

fun LocalDate.toCompactString(): String = this.format(formatterCommpact)

class LocalDateTypeAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
    override fun serialize(
        objekt: LocalDate?,
        type: Type?,
        kontekst: JsonSerializationContext?,
    ): JsonElement {
        return JsonPrimitive(objekt.toString())
    }

    override fun deserialize(
        jsonelement: JsonElement?,
        type: Type?,
        kontekst: JsonDeserializationContext?,
    ): LocalDate {
        return LocalDate.parse(jsonelement?.asString, formatter)
    }
}

class LocalDateTimeTypeAdapter : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override fun serialize(
        objekt: LocalDateTime?,
        type: Type?,
        kontekst: JsonSerializationContext?,
    ): JsonElement {
        return JsonPrimitive(objekt.toString())
    }

    override fun deserialize(
        jsonelement: JsonElement?,
        type: Type?,
        kontekst: JsonDeserializationContext?,
    ): LocalDateTime {
        return LocalDateTime.parse(jsonelement?.asString, formatter)
    }
}

class YearMonthTypeAdapter : JsonSerializer<YearMonth>, JsonDeserializer<YearMonth> {
    override fun serialize(
        objekt: YearMonth?,
        type: Type?,
        kontekst: JsonSerializationContext?,
    ): JsonElement {
        return JsonPrimitive(objekt.toString())
    }

    override fun deserialize(
        jsonelement: JsonElement?,
        type: Type?,
        kontekst: JsonDeserializationContext?,
    ): YearMonth {
        return YearMonth.parse(jsonelement?.asString, formatter)
    }
}
