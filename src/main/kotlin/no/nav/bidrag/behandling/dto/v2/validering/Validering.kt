package no.nav.bidrag.behandling.dto.v2.validering

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.Datoperiode
import java.time.LocalDate

data class VirkningstidspunktFeilDto(
    val manglerVirkningstidspunkt: Boolean,
    val manglerÅrsakEllerAvslag: Boolean,
) {
    @get:JsonIgnore
    val harFeil
        get() = manglerVirkningstidspunkt || manglerÅrsakEllerAvslag
}

data class InntektValideringsfeilDto(
    val barnetillegg: Set<InntektValideringsfeil>?,
    val utvidetBarnetrygd: InntektValideringsfeil?,
    val kontantstøtte: Set<InntektValideringsfeil>?,
    val småbarnstillegg: InntektValideringsfeil?,
    @Schema(name = "årsinntekter")
    val årsinntekter: Set<InntektValideringsfeil>?,
) {
    @get:JsonIgnore
    val harFeil
        get() =
            barnetillegg?.any { it.harFeil } == true || utvidetBarnetrygd?.harFeil == true ||
                kontantstøtte?.any { it.harFeil } == true || småbarnstillegg?.harFeil == true || årsinntekter?.any { it.harFeil } == true
}

data class InntektValideringsfeil(
    val overlappendePerioder: Set<OverlappendePeriode>,
    val fremtidigPeriode: Boolean,
    @Schema(description = "Liste med perioder hvor det mangler inntekter. Vil alltid være tom liste for ytelser")
    val hullIPerioder: List<Datoperiode> = emptyList(),
    @Schema(description = "Er sann hvis det ikke finnes noen valgte inntekter. Vil alltid være false hvis det er ytelse")
    val manglerPerioder: Boolean = false,
    val ident: String,
    @Schema(description = "Personident ytelsen gjelder for. Kan være null hvis det er en ytelse som ikke gjelder for et barn.")
    val gjelderBarn: String? = null,
    @JsonIgnore
    val rolle: Rolletype?,
    @JsonIgnore
    val erYtelse: Boolean = false,
) {
    @Schema(
        description =
            "Er sann hvis det ikke finnes noe løpende periode. " +
                "Det vil si en periode hvor datoTom er null. Er bare relevant for årsinntekter",
    )
    val ingenLøpendePeriode: Boolean = if (erYtelse) false else hullIPerioder.any { it.til == null }

    @get:JsonIgnore
    val harFeil
        get() =
            overlappendePerioder.isNotEmpty() || hullIPerioder.isNotEmpty() ||
                fremtidigPeriode || manglerPerioder || ingenLøpendePeriode
}

data class OverlappendePeriode(
    val periode: Datoperiode,
    @Schema(description = "Teknisk id på inntekter som overlapper")
    val idListe: MutableSet<Long>,
    @Schema(description = "Inntektsrapportering typer på inntekter som overlapper")
    val rapporteringTyper: MutableSet<Inntektsrapportering>,
    @Schema(description = "Inntektstyper som inntektene har felles. Det der dette som bestemmer hvilken inntekter som overlapper.")
    val inntektstyper: MutableSet<Inntektstype>,
)

data class BoforholdPeriodeseringsfeil(
    @JsonIgnore
    val husstandsbarn: Husstandsbarn?,
    val hullIPerioder: List<Datoperiode>,
    val overlappendePerioder: List<HusstandsbarnOverlappendePeriode>,
    @Schema(description = "Er sann hvis husstandsbarn har en periode som starter senere enn starten av dagens måned.")
    val fremtidigPeriode: Boolean,
    @Schema(
        description = """Er sann hvis husstandsbarn mangler perioder. 
        Dette vil si at husstandsbarn ikke har noen perioder i det hele tatt."""",
    )
    val manglerPerioder: Boolean,
) {
    @Schema(description = "Er sann hvis husstandsbarn ikke har noen løpende periode. Det vil si en periode hvor datoTom er null")
    val ingenLøpendePeriode: Boolean = hullIPerioder.any { it.til == null }

    @get:JsonIgnore
    val harFeil
        get() =
            hullIPerioder.isNotEmpty() || overlappendePerioder.isNotEmpty() ||
                fremtidigPeriode || manglerPerioder || ingenLøpendePeriode
    val barn
        get() =
            husstandsbarn?.let {
                HusstandsbarnPeriodiseringsfeilDto(
                    hentPersonVisningsnavn(husstandsbarn.ident) ?: husstandsbarn.navn,
                    husstandsbarn.ident,
                    husstandsbarn.foedselsdato,
                    husstandsbarn.id ?: -1,
                )
            }

    data class HusstandsbarnPeriodiseringsfeilDto(
        val navn: String?,
        val ident: String?,
        val fødselsdato: LocalDate,
        @Schema(description = "Teknisk id på husstandsbarn som har periodiseringsfeil")
        val tekniskId: Long,
    )
}

data class HusstandsbarnOverlappendePeriode(
    val periode: Datoperiode,
    val bosstatus: Set<Bostatuskode>,
)

data class SivilstandPeriodeseringsfeil(
    val hullIPerioder: List<Datoperiode>,
    val overlappendePerioder: List<SivilstandOverlappendePeriode>,
    @Schema(description = "Er sann hvis det finnes en eller flere perioder som starter senere enn starten av dagens måned.")
    val fremtidigPeriode: Boolean,
    @Schema(description = """Er sann hvis det mangler sivilstand perioder."""")
    val manglerPerioder: Boolean,
) {
    @Schema(description = "Er sann hvis det ikke finnes noe løpende periode. Det vil si en periode hvor datoTom er null")
    val ingenLøpendePeriode: Boolean = hullIPerioder.any { it.til == null }

    @get:JsonIgnore
    val harFeil
        get() =
            hullIPerioder.isNotEmpty() || overlappendePerioder.isNotEmpty() ||
                fremtidigPeriode || manglerPerioder || ingenLøpendePeriode
}

data class SivilstandOverlappendePeriode(
    val periode: Datoperiode,
    val sivilstandskode: Set<Sivilstandskode>,
)

data class BeregningValideringsfeil2(
    val virkningstidspunkt: VirkningstidspunktFeilDto?,
    val inntekter: InntektValideringsfeilDto? = null,
    val husstandsbarn: List<BoforholdPeriodeseringsfeil>? = null,
    val sivilstand: SivilstandPeriodeseringsfeil?,
)
