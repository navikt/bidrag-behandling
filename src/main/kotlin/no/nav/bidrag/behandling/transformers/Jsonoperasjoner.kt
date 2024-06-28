package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.tid.Periode
import no.nav.bidrag.transport.felles.commonObjectmapper
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

private val log = KotlinLogging.logger {}

class Jsonoperasjoner {
    companion object {
        fun <T> tilJson(objekt: T): String =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter())
                .create()
                .toJson(
                    objekt,
                )

        inline fun <reified T> tilJson(sett: Set<T>): String = commonObjectmapper.writeValueAsString(sett)

        inline fun <reified T> jsonListeTilObjekt(json: String) =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter())
                .create()
                .fromJsonList<T>(json)
                .toSet()

        inline fun <reified T> jsonTilObjekt(json: String) =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter())
                .create()
                .fromJson<T>(json, genericType<T>())
    }
}

inline fun <reified T> Gson.fromJsonList(json: String) = fromJson<List<T>>(json, object : TypeToken<List<T>>() {}.type)

inline fun <reified T> genericType(): Type = object : TypeToken<T?>() {}.type

inline fun <reified T> kolleksjon() = object : TypeToken<ArrayList<T?>?>() {}.type

object PeriodeDeserialiserer : JsonDeserializer<Periode<LocalDate>>() {
    override fun deserialize(
        p0: JsonParser?,
        p1: DeserializationContext?,
    ): Periode<LocalDate> {
        val node = p0?.readValueAsTree<JsonNode>()

        val fom = node?.get("fom")?.asText()?.let { LocalDate.parse(it) }
        val tom = node?.get("til")?.asText()?.let { LocalDate.parse(it) }

        if (fom == null) {
            log.warn { "Fra-og-med-dato settes til 25 år tilbake i tid." }
        }

        return Datoperiode(fom ?: LocalDate.now().minusYears(25), tom)
    }
}
