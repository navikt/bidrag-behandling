package no.nav.bidrag.behandling.dto.v2.boforhold

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.transformers.erForskudd
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBoforhold
import java.time.LocalDate

val Behandling.egetBarnErEnesteVoksenIHusstanden get() =
    if (!erForskudd()) {
        husstandsmedlem.voksneIHusstanden?.perioder?.none { avh -> avh.bostatus == Bostatuskode.BOR_MED_ANDRE_VOKSNE } == true &&
            husstandsmedlem.barn.any { hm -> hm.perioder.any { p -> p.bostatus == Bostatuskode.REGNES_IKKE_SOM_BARN } }
    } else {
        null
    }

data class BoforholdDtoV2(
    val husstandsmedlem: Set<HusstandsmedlemDtoV2>,
    val andreVoksneIHusstanden: Set<BostatusperiodeDto> = emptySet(),
    val sivilstand: Set<SivilstandDto>,
    @Schema(description = "Saksbehandlers begrunnelse", deprecated = false)
    val begrunnelse: BegrunnelseDto,
    val begrunnelseFraOpprinneligVedtak: BegrunnelseDto? = null,
    val valideringsfeil: BoforholdValideringsfeil,
    @Schema(
        description =
            "Er sann hvis status på andre voksne i husstanden er 'BOR_IKKE_MED_ANDRE_VOKSNE'," +
                " men det er 18 åring i husstanden som regnes som voksen i husstanden",
    )
    val egetBarnErEnesteVoksenIHusstanden: Boolean? = false,
    val beregnetBoforhold: List<DelberegningBoforhold> = emptyList(),
) {
    @Deprecated("Erstattes av husstandsmedlem")
    @Schema(description = "Erstattes av husstandsmedlem", deprecated = true)
    val husstandsbarn = husstandsmedlem

    @Deprecated("Erstattes av begrunnelse")
    @Schema(description = "Saksbehandlers begrunnelse", deprecated = true)
    val notat: BegrunnelseDto = begrunnelse
}

data class HusstandsmedlemDtoV2(
    val id: Long?,
    @Schema(required = true)
    val kilde: Kilde,
    @Schema(required = true)
    val medIBehandling: Boolean,
    val perioder: Set<BostatusperiodeDto>,
    val ident: String? = null,
    val navn: String? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val fødselsdato: LocalDate?,
)
