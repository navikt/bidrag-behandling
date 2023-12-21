package no.nav.bidrag.behandling.dto.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.dto.opplysninger.OpplysningerDto
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import java.time.LocalDate

// TODO: Flytt dette til bidrag-transport
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
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningsdato: LocalDate? = null,
    val grunnlagspakkeid: Long? = null,
    @Schema(name = "årsak")
    val årsak: ForskuddAarsakType? = null,
    val inntekter: InntekterDto,
    val boforhold: BoforholdDto,
    val opplysninger: List<OpplysningerDto>,
    val notatVirkningstidspunkt: BehandlingNotatInnholdDto,
    val notat: BehandlingNotatDto,
)

data class BoforholdDto(
    val husstandsbarn: Set<HusstandsbarnDto>,
    val sivilstand: Set<SivilstandDto>,
    val notat: BehandlingNotatInnholdDto,
)

data class InntekterDto(
    val inntekter: Set<InntektDto>,
    val barnetillegg: Set<BarnetilleggDto>,
    val utvidetbarnetrygd: Set<UtvidetBarnetrygdDto>,
    val kontantstøtte: Set<InntektDto>,
    val småbarnstillegg: Set<InntektDto>,
    val notat: BehandlingNotatInnholdDto,
)

data class BehandlingNotatDto(
    val virkningstidspunkt: BehandlingNotatInnholdDto,
    val boforhold: BehandlingNotatInnholdDto,
    val inntekt: BehandlingNotatInnholdDto,
)

data class BehandlingNotatInnholdDto(
    val kunINotat: String? = null,
    val medIVedtaket: String? = null,
)
