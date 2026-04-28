package no.nav.bidrag.behandling.dto.v1.grunnlag

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadDto
import java.time.LocalDate
import java.time.LocalDateTime

data class GrunnlagsdataDto(
    val id: Long,
    val behandlingsid: Long,
    val gjelder: Personident,
    val grunnlagsdatatype: Grunnlagstype,
    val data: String,
    @Schema(type = "string", format = "timestamp", example = "01.12.2025 12:00:00.000")
    val innhentet: LocalDateTime,
)

data class GrunnlagsdataEndretDto(
    val nyeData: GrunnlagsdataDto,
    val endringerINyeData: Set<Grunnlagsdatatype>,
)

data class BpsBarnUtenLøpendeBidragDto(
    val ident: String? = null,
    val navn: String? = null,
    val fødselsdato: LocalDate? = null,
    val enhet: String? = null,
    val saksnummer: String? = null,
    val beløpshistorikkBidrag: StønadDto?,
    val beløpshistorikkBidrag18År: StønadDto?,
) {
    fun finnBeløpshistorikk(stønadstype: Stønadstype?) =
        when (stønadstype) {
            Stønadstype.BIDRAG -> beløpshistorikkBidrag
            Stønadstype.BIDRAG18AAR -> beløpshistorikkBidrag18År
            else -> null
        }
}
