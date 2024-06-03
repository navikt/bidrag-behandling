package no.nav.bidrag.behandling.dto.v2.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal
import java.time.LocalDate

data class OppdaterBehandlingRequestV2(
    val virkningstidspunkt: OppdatereVirkningstidspunkt? = null,
    val boforhold: OppdaterBoforholdRequest? = null,
    val inntekter: OppdatereInntekterRequestV2? = null,
    val aktivereGrunnlagForPerson: AktivereGrunnlagRequest? = null,
)

data class OppdatereUtgiftRequest(
    @Schema(
        description =
            "Oppdater avslag. Hvis verdien er satt til null så vil det ikke bli gjort noe endringer. " +
                "Hvis verdien er satt så vil avslag settes til samme verdi fra forespørsel",
        enumAsRef = true,
    )
    val avslag: Resultatkode? = null,
    val beløpBetaltAvBp: BigDecimal = BigDecimal.ZERO,
    @Schema(
        description =
            "Legg til eller endre en utgift. Utgift kan ikke endres eller oppdateres hvis avslag er satt",
    )
    val nyEllerEndretUtgift: OppdatereUtgift? = null,
    @Schema(
        description =
            "Slette en utgift. Utgift kan ikke endres eller oppdateres hvis avslag er satt",
    )
    val sletteUtgift: Long? = null,
    @Schema(
        type = "Boolean",
        description = "Angre siste endring som ble gjort. Siste endring kan ikke angres hvis avslag er satt",
    )
    val angreSisteEndring: Boolean = false,
    val notat: OppdaterNotat? = null,
)

data class OppdatereUtgift(
    @Schema(description = "Når utgifter gjelder. Kan være feks dato på kvittering")
    val dato: LocalDate,
    @Schema(description = "Beskrivelse av utgiften. Kan feks være hva som ble kjøpt for kravbeløp (bugnad, klær, sko, etc)")
    val beskrivelse: String,
    @Schema(description = "Beløp som er betalt for utgiften det gjelder")
    val kravbeløp: BigDecimal,
    @Schema(description = "Beløp som er godkjent for beregningen")
    val godkjentBeløp: BigDecimal = kravbeløp,
    @Schema(description = "Begrunnelse for hvorfor godkjent beløp avviker fra kravbeløp. Må settes hvis godkjent beløp er ulik kravbeløp")
    val begrunnelse: String,
    val id: Long? = null,
)

@Deprecated("Bruk AktivereGrunnlagRequestV2 via eget endepunkt /behandling/{behandlingsid}/aktivere.")
data class AktivereGrunnlagRequest(
    @Schema(description = "Personident tilhørende rolle i behandling grunnlag skal aktiveres for")
    val personident: Personident,
    @Schema(description = "Grunnlagstyper som skal aktiveres")
    val grunnlagsdatatyper: Set<Grunnlagsdatatype> = emptySet(),
)

fun AktivereGrunnlagRequest.toV2() =
    AktivereGrunnlagRequestV2(
        personident,
        grunnlagstype = grunnlagsdatatyper.first(),
    )

data class AktivereGrunnlagResponseV2(
    val inntekter: InntekterDtoV2,
    val boforhold: BoforholdDtoV2,
    val aktiveGrunnlagsdata: AktiveGrunnlagsdata,
    val ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
)

data class AktivereGrunnlagRequestV2(
    @Schema(description = "Personident tilhørende rolle i behandling grunnlag skal aktiveres for")
    val personident: Personident,
    @Schema(description = "Grunnlagstype som skal aktiveres")
    val grunnlagstype: Grunnlagsdatatype,
    @Schema(description = "Angi om manuelle opplysninger skal overskrives")
    val overskriveManuelleOpplysninger: Boolean = true,
)
