package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import java.time.LocalDate

// TODO: Flytt dette til bidrag-transport
data class BehandlingDto(
    val id: Long,
    // Union av Stønadstype og Engangsbeløptype
    val behandlingtype: Behandlingstype,
    // TODO Bruk Vedtakstype istedenfor
    val søknadType: Soknadstype,
    val erVedtakFattet: Boolean,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottattdato: LocalDate,
    val soknadFraType: SøktAvType,
    val saksnummer: String,
    val soknadsid: Long,
    val behandlerenhet: String,
    val roller: Set<RolleDto>,
    val husstandsbarn: Set<HusstandsbarnDto>,
    val sivilstand: Set<SivilstandDto>,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningsdato: LocalDate? = null,
    val soknadRefId: Long? = null,
    val grunnlagspakkeid: Long? = null,
    val årsak: ForskuddAarsakType? = null,
    val virkningstidspunktsbegrunnelseIVedtakOgNotat: String? = null,
    val virkningstidspunktsbegrunnelseKunINotat: String? = null,
    val boforholdsbegrunnelseIVedtakOgNotat: String? = null,
    val boforholdsbegrunnelseKunINotat: String? = null,
    val inntektsbegrunnelseIVedtakOgNotat: String? = null,
    val inntektsbegrunnelseKunINotat: String? = null,
)
