package no.nav.bidrag.behandling.dto.v2.validering

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.erSøknadsbarn
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.tid.Datoperiode
import java.time.LocalDate

data class VirkningstidspunktFeilDto(
    val manglerVirkningstidspunkt: Boolean,
    val manglerÅrsakEllerAvslag: Boolean,
    val virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig: Boolean,
) {
    @get:JsonIgnore
    val harFeil
        get() =
            manglerVirkningstidspunkt ||
                manglerÅrsakEllerAvslag ||
                virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig
}

data class UtgiftValideringsfeilDto(
    val maksGodkjentBeløp: MaksGodkjentBeløpValiderignsfeil? = null,
    val manglerUtgifter: Boolean,
    val ugyldigUtgiftspost: Boolean,
) {
    @get:JsonIgnore
    val harFeil
        get() = manglerUtgifter || ugyldigUtgiftspost || maksGodkjentBeløp != null
}

data class MaksGodkjentBeløpValiderignsfeil(
    val manglerBeløp: Boolean,
    val manglerKommentar: Boolean,
)

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
            barnetillegg?.any { it.harFeil } == true ||
                utvidetBarnetrygd?.harFeil == true ||
                kontantstøtte?.any {
                    it.harFeil
                } == true ||
                småbarnstillegg?.harFeil == true ||
                årsinntekter?.any { it.harFeil } == true
}

data class InntektValideringsfeil(
    val overlappendePerioder: Set<OverlappendePeriode>,
    val fremtidigPeriode: Boolean,
    @Schema(description = "Liste med perioder hvor det mangler inntekter. Vil alltid være tom liste for ytelser")
    val hullIPerioder: List<Datoperiode> = emptyList(),
    @Schema(description = "Er sann hvis det ikke finnes noen valgte inntekter. Vil alltid være false hvis det er ytelse")
    val manglerPerioder: Boolean = false,
    @Schema(description = "Hvis det er inntekter som har periode som starter før virkningstidspunkt")
    val perioderFørVirkningstidspunkt: Boolean = false,
    @Schema(description = "Personident ytelsen gjelder for. Kan være null hvis det er en ytelse som ikke gjelder for et barn.")
    val gjelderBarn: String? = null,
    @JsonIgnore
    val erYtelse: Boolean = false,
    val rolle: RolleDto? = null,
    val ident: String? = rolle?.ident,
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
            overlappendePerioder.isNotEmpty() ||
                hullIPerioder.isNotEmpty() ||
                fremtidigPeriode ||
                manglerPerioder ||
                perioderFørVirkningstidspunkt ||
                ingenLøpendePeriode
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
    val husstandsmedlem: Husstandsmedlem?,
    val hullIPerioder: List<Datoperiode> = emptyList(),
    val overlappendePerioder: List<OverlappendeBostatusperiode> = emptyList(),
    @Schema(description = "Er sann hvis husstandsmedlem har en periode som starter senere enn starten av dagens måned.")
    val fremtidigPeriode: Boolean = false,
    @Schema(
        description = """Er sann hvis husstandsmedlem mangler perioder. 
        Dette vil si at husstandsmedlem ikke har noen perioder i det hele tatt."""",
    )
    val manglerPerioder: Boolean,
) {
    @Schema(description = "Er sann hvis husstandsmedlem ikke har noen løpende periode. Det vil si en periode hvor datoTom er null")
    val ingenLøpendePeriode: Boolean = hullIPerioder.any { it.til == null }

    @get:JsonIgnore
    val harFeil
        get() =
            hullIPerioder.isNotEmpty() ||
                overlappendePerioder.isNotEmpty() ||
                fremtidigPeriode ||
                manglerPerioder ||
                ingenLøpendePeriode
    val barn
        get(): HusstandsmedlemPeriodiseringsfeilDto =
            husstandsmedlem?.let {
                HusstandsmedlemPeriodiseringsfeilDto(
                    hentPersonVisningsnavn(husstandsmedlem.ident) ?: husstandsmedlem.navn ?: husstandsmedlem.rolle?.navn,
                    husstandsmedlem.ident ?: husstandsmedlem.rolle?.ident,
                    husstandsmedlem.fødselsdato ?: husstandsmedlem.rolle?.fødselsdato ?: LocalDate.now(),
                    husstandsmedlem.id ?: -1,
                    husstandsmedlem.erSøknadsbarn(),
                )
            } ?: HusstandsmedlemPeriodiseringsfeilDto("", "", LocalDate.now(), -1, false)

    data class HusstandsmedlemPeriodiseringsfeilDto(
        val navn: String?,
        val ident: String?,
        val fødselsdato: LocalDate,
        @Schema(description = "Teknisk id på husstandsmedlem som har periodiseringsfeil")
        val husstandsmedlemId: Long,
        val erSøknadsbarn: Boolean,
    )
}

data class OverlappendeBostatusperiode(
    val periode: Datoperiode,
    val bosstatus: Set<Bostatuskode>,
)

data class AndreVoksneIHusstandenPeriodeseringsfeil(
    val hullIPerioder: List<Datoperiode> = emptyList(),
    val overlappendePerioder: List<OverlappendeBostatusperiode> = emptyList(),
    @Schema(description = "Er sann hvis det finnes en eller flere perioder som starter senere enn starten av dagens måned.")
    val fremtidigPeriode: Boolean = false,
    @Schema(description = """Er sann hvis det mangler sivilstand perioder."""")
    val manglerPerioder: Boolean = false,
) {
    @Schema(description = "Er sann hvis det ikke finnes noe løpende periode. Det vil si en periode hvor datoTom er null")
    val ingenLøpendePeriode: Boolean = hullIPerioder.any { it.til == null }

    val harFeil
        get() =
            hullIPerioder.isNotEmpty() ||
                overlappendePerioder.isNotEmpty() ||
                fremtidigPeriode ||
                manglerPerioder ||
                ingenLøpendePeriode
}

data class SivilstandPeriodeseringsfeil(
    val hullIPerioder: List<Datoperiode>,
    val overlappendePerioder: List<SivilstandOverlappendePeriode>,
    @Schema(description = "Er sann hvis det finnes en eller flere perioder som starter senere enn starten av dagens måned.")
    val fremtidigPeriode: Boolean,
    @Schema(description = """Er sann hvis det mangler sivilstand perioder."""")
    val manglerPerioder: Boolean,
    @Schema(description = """Er sann hvis en eller flere perioder har status UKJENT."""")
    val ugyldigStatus: Boolean,
) {
    @Schema(description = "Er sann hvis det ikke finnes noe løpende periode. Det vil si en periode hvor datoTom er null")
    val ingenLøpendePeriode: Boolean = hullIPerioder.any { it.til == null }

    val harFeil
        get() =
            hullIPerioder.isNotEmpty() ||
                overlappendePerioder.isNotEmpty() ||
                fremtidigPeriode ||
                manglerPerioder ||
                ingenLøpendePeriode ||
                ugyldigStatus
}

data class SivilstandOverlappendePeriode(
    val periode: Datoperiode,
    val sivilstandskode: Set<Sivilstandskode>,
)

data class BeregningValideringsfeil(
    val virkningstidspunkt: VirkningstidspunktFeilDto?,
    val utgift: UtgiftValideringsfeilDto?,
    val inntekter: InntektValideringsfeilDto? = null,
    val husstandsmedlem: List<BoforholdPeriodeseringsfeil>? = null,
    val andreVoksneIHusstanden: AndreVoksneIHusstandenPeriodeseringsfeil? = null,
    val sivilstand: SivilstandPeriodeseringsfeil?,
    val måBekrefteNyeOpplysninger: Set<MåBekrefteNyeOpplysninger> = emptySet(),
)

data class MåBekrefteNyeOpplysninger(
    val type: Grunnlagsdatatype,
    val rolle: RolleDto,
    @JsonIgnore
    val husstandsmedlem: Husstandsmedlem? = null,
) {
    @get:Schema(
        description = "Barn som det må bekreftes nye opplysninger for. Vil bare være satt hvis type = BOFORHOLD",
    )
    val gjelderBarn: HusstandsmedlemDto?
        get() =
            husstandsmedlem?.let {
                HusstandsmedlemDto(
                    navn = it.navn ?: hentPersonVisningsnavn(it.ident),
                    ident = it.ident,
                    fødselsdato = it.fødselsdato ?: it.rolle!!.fødselsdato,
                    husstandsmedlemId = it.id ?: -1,
                )
            }

    data class HusstandsmedlemDto(
        val navn: String?,
        val ident: String?,
        val fødselsdato: LocalDate,
        @Schema(description = "Teknisk id på husstandsmedlem som har periodiseringsfeil")
        val husstandsmedlemId: Long,
    )
}
