package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import no.nav.bidrag.domain.enums.Rolletype
import java.io.IOException
import java.util.Date

data class RolleDto(
    val id: Long,
    @JsonSerialize(using = RolletypeSerializer::class)
    val rolleType: Rolletype,
    val ident: String,
    val fodtDato: Date?,
    val opprettetDato: Date?,
)

class RolletypeSerializer : StdSerializer<Rolletype>(Rolletype::class.java) {
    @Throws(IOException::class)
    override fun serialize(value: Rolletype, g: JsonGenerator, provider: SerializerProvider) {
        g.writeString(value.name.toString())
    }
}
