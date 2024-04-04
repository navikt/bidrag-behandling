package no.nav.bidrag.behandling.dto.v2.validering

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.Datoperiode
import java.time.LocalDate

data class InntektValideringsfeilDto(
    val barnetillegg: Set<YtelseInntektValideringsfeil>,
    val utvidetBarnetrygd: YtelseInntektValideringsfeil?,
    val kontantstøtte: Set<YtelseInntektValideringsfeil>,
    val småbarnstillegg: YtelseInntektValideringsfeil?,
    @Schema(name = "årsinntekter")
    val årsinntekter: Set<ÅrsinntektValideringsfeil>,
)

interface InntektValideringsfeil {
    val overlappendePerioder: Set<OverlappendePeriode>

    @get:Schema(description = "Personident valideringen gjelder for")
    val ident: String?

    @get:Schema(description = "Rolle valideringen gjelder for")
    val rolle: Rolletype?

    @get:Schema(description = "Er sann hvis inntekt har en periode som starter senere enn starten av virkningstidspunkt")
    val fremtidigPeriode: Boolean

    @get:JsonIgnore
    val identifikator get() = "$ident/${rolle?.name}"
}

data class YtelseInntektValideringsfeil(
    override val overlappendePerioder: Set<OverlappendePeriode>,
    override val fremtidigPeriode: Boolean,
    @Schema(description = "Personident ytelsen gjelder for. Kan være null hvis det er en ytelse som ikke gjelder for et barn.")
    val gjelderBarn: String? = null,
    override val ident: String? = null,
    override val rolle: Rolletype? = null,
) : InntektValideringsfeil {
    @get:JsonIgnore
    val harFeil
        get() = overlappendePerioder.isNotEmpty() || fremtidigPeriode
}

data class ÅrsinntektValideringsfeil(
    override val overlappendePerioder: Set<OverlappendePeriode>,
    override val fremtidigPeriode: Boolean,
    val hullIPerioder: List<Datoperiode>,
    @Schema(description = "Er sann hvis det ikke finnes noen valgte inntekter")
    val manglerPerioder: Boolean,
    override val ident: String,
    override val rolle: Rolletype?,
) : InntektValideringsfeil {
    @Schema(description = "Er sann hvis det ikke finnes noe løpende periode. Det vil si en periode hvor datoTom er null")
    val ingenLøpendePeriode: Boolean = hullIPerioder.any { it.til == null }

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
                    husstandsbarn.ident,
                    husstandsbarn.foedselsdato,
                    husstandsbarn.id ?: -1,
                )
            }

    data class HusstandsbarnPeriodiseringsfeilDto(
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

enum class BeregningValideringsfeilType {
    BOFORHOLD,
    SIVILSTAND,
    INNTEKT,
    VIRKNINGSTIDSPUNKT,
    ANDRE,
}

class BeregningValideringsfeilList : ArrayList<BeregningValideringsfeil>()

data class BeregningValideringsfeil(
    val type: BeregningValideringsfeilType,
    val feilListe: MutableList<String>,
)

enum class ValideringsfeilType {
    INNTEKT,
    BOFORHOLD,
    SIVILSTAND,
    VIRKNINGSTIDSPUNKT,
    ANDRE,
}
