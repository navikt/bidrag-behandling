package no.nav.bidrag.behandling.dto.v1.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.v1.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.v1.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.v1.inntekt.KontantstøtteDto
import no.nav.bidrag.behandling.dto.v1.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.SivilstandPeriodeseringsfeil
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import java.time.LocalDate

data class BehandlingDto(
    val id: Long,
    val vedtakstype: Vedtakstype,
    val stønadstype: Stønadstype? = null,
    val engangsbeløptype: Engangsbeløptype? = null,
    val erVedtakFattet: Boolean,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val søktFomDato: LocalDate,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottattdato: LocalDate,
    val søktAv: SøktAvType,
    val saksnummer: String,
    val søknadsid: Long,
    val søknadRefId: Long? = null,
    val behandlerenhet: String,
    val roller: Set<RolleDto>,
    val grunnlagspakkeid: Long? = null,
    val virkningstidspunkt: VirkningstidspunktDto,
    val inntekter: InntekterDto,
    val boforhold: BoforholdDto,
    val opplysninger: List<GrunnlagsdataDto>,
)

data class VirkningstidspunktDto(
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningstidspunkt: LocalDate? = null,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val opprinneligVirkningstidspunkt: LocalDate? = null,
    @Schema(name = "årsak", enumAsRef = true)
    val årsak: VirkningstidspunktÅrsakstype? = null,
    @Schema(enumAsRef = true)
    val avslag: Resultatkode? = null,
    val notat: BehandlingNotatDto,
)

data class BoforholdDto(
    val husstandsbarn: Set<HusstandsbarnDto>,
    val sivilstand: Set<SivilstandDto>,
    val notat: BehandlingNotatDto,
    val valideringsfeil: BoforholdValideringsfeil,
)

data class BoforholdValideringsfeil(
    val husstandsbarn: List<BoforholdPeriodeseringsfeil>? = emptyList(),
    val sivilstand: SivilstandPeriodeseringsfeil? = null,
)

data class InntekterDto(
    val inntekter: Set<InntektDto>,
    val barnetillegg: Set<BarnetilleggDto>,
    val utvidetbarnetrygd: Set<UtvidetBarnetrygdDto>,
    val kontantstøtte: Set<KontantstøtteDto>,
    val småbarnstillegg: Set<InntektDto>,
    val notat: BehandlingNotatDto,
)

data class BehandlingNotatDto(
    val kunINotat: String? = null,
    val medIVedtaket: String? = null,
)
