package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.tid.Periode
import no.nav.bidrag.transport.felles.commonObjectmapper
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class Jsonoperasjoner {
    companion object {
        fun <T> tilJson(objekt: T): String = commonObjectmapper.writeValueAsString(objekt)

        inline fun <reified T> tilJson(sett: Set<T>): String = commonObjectmapper.writeValueAsString(sett)

        inline fun <reified T> jsonListeTilObjekt(json: String) = commonObjectmapper.readValue<Set<T>>(json)

        inline fun <reified T> jsonTilObjekt(json: String) = commonObjectmapper.readValue<T>(json)
    }
}

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
