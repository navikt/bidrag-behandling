package no.nav.bidrag.behandling.dto.v2.validering

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.gebyr.GebyrValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadValideringsfeil
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.erSøknadsbarn
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.nio.charset.Charset
import java.time.LocalDate

data class VirkningstidspunktFeilV2Dto(
    val gjelder: RolleDto,
    val manglerVirkningstidspunkt: Boolean = false,
    val manglerOpphørsdato: Boolean = false,
    val kanIkkeSetteOpphørsdatoEtterEtterfølgendeVedtak: Boolean = false,
    val manglerÅrsakEllerAvslag: Boolean = false,
    val måVelgeVedtakForBeregning: Boolean = false,
    val manglerBegrunnelse: Boolean = false,
    val manglerVurderingAvSkolegang: Boolean = false,
    val virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig: Boolean = false,
) {
    @get:JsonIgnore
    val harFeil
        get() =
            manglerBegrunnelse ||
                måVelgeVedtakForBeregning ||
                manglerOpphørsdato ||
                kanIkkeSetteOpphørsdatoEtterEtterfølgendeVedtak ||
                manglerVurderingAvSkolegang ||
                manglerVirkningstidspunkt ||
                manglerÅrsakEllerAvslag ||
                virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig
}

data class VirkningstidspunktFeilDto(
    val manglerVirkningstidspunkt: Boolean = false,
    val manglerOpphørsdato: List<RolleDto> = emptyList(),
    val kanIkkeSetteOpphørsdatoEtterEtterfølgendeVedtak: List<RolleDto> = emptyList(),
    val manglerÅrsakEllerAvslag: Boolean = false,
    val måVelgeVedtakForBeregning: List<RolleDto> = emptyList(),
    val manglerBegrunnelse: Boolean = false,
    val manglerVurderingAvSkolegang: Boolean = false,
    val virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig: Boolean = false,
) {
    @get:JsonIgnore
    val harFeil
        get() =
            manglerBegrunnelse ||
                måVelgeVedtakForBeregning.isNotEmpty() ||
                manglerOpphørsdato.isNotEmpty() ||
                kanIkkeSetteOpphørsdatoEtterEtterfølgendeVedtak.isNotEmpty() ||
                manglerVurderingAvSkolegang ||
                manglerVirkningstidspunkt ||
                manglerÅrsakEllerAvslag ||
                virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig
}

data class UtgiftValideringsfeilDto(
    val maksGodkjentBeløp: MaksGodkjentBeløpValideringsfeil? = null,
    val manglerUtgifter: Boolean,
    val ugyldigUtgiftspost: Boolean,
) {
    @get:JsonIgnore
    val harFeil
        get() = manglerUtgifter || ugyldigUtgiftspost || maksGodkjentBeløp != null
}

data class MaksGodkjentBeløpValideringsfeil(
    val manglerBeløp: Boolean,
    val manglerBegrunnelse: Boolean,
) {
    val harFeil get() = manglerBeløp || manglerBegrunnelse
}

data class InntektValideringsfeilV2Dto(
    val barnetillegg: Collection<InntektValideringsfeil>? = emptySet(),
    val utvidetBarnetrygd: InntektValideringsfeil? = InntektValideringsfeil(),
    val kontantstøtte: Collection<InntektValideringsfeil>? = emptySet(),
    val småbarnstillegg: InntektValideringsfeil? = InntektValideringsfeil(),
    @Schema(name = "årsinntekter")
    val årsinntekter: InntektValideringsfeil? = InntektValideringsfeil(),
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
                årsinntekter?.harFeil == true
}

data class InntektValideringsfeilDto(
    val barnetillegg: Collection<InntektValideringsfeil>? = emptySet(),
    val utvidetBarnetrygd: InntektValideringsfeil? = InntektValideringsfeil(),
    val kontantstøtte: Collection<InntektValideringsfeil>? = emptySet(),
    val småbarnstillegg: InntektValideringsfeil? = InntektValideringsfeil(),
    @Schema(name = "årsinntekter")
    val årsinntekter: Set<InntektValideringsfeil>? = emptySet(),
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
    val overlappendePerioder: Set<OverlappendePeriode> = emptySet(),
    val fremtidigPeriode: Boolean = false,
    @Schema(description = "Liste med perioder hvor det mangler inntekter. Vil alltid være tom liste for ytelser")
    val hullIPerioder: List<Datoperiode> = emptyList(),
    @Schema(description = "Er sann hvis det ikke finnes noen valgte inntekter. Vil alltid være false hvis det er ytelse")
    val manglerPerioder: Boolean = false,
    @Schema(description = "Hvis det er inntekter som har periode som starter før virkningstidspunkt")
    val perioderFørVirkningstidspunkt: Boolean = false,
    val ugyldigSluttPeriode: Boolean = false,
    val gjelderBarnRolle: RolleDto? = null,
    @Schema(description = "Personident ytelsen gjelder for. Kan være null hvis det er en ytelse som ikke gjelder for et barn.")
    val gjelderBarn: String? = gjelderBarnRolle?.ident,
    @JsonIgnore
    val erYtelse: Boolean = false,
    val manglerSkatteprosent: Boolean = false,
    val rolle: RolleDto? = null,
    @Deprecated("Skal fjernes")
    val ident: String? = rolle?.ident,
    @Schema(
        description =
            "Er sann hvis det ikke finnes noe løpende periode. " +
                "Det vil si en periode hvor datoTom er null. Er bare relevant for årsinntekter",
    )
    val ingenLøpendePeriode: Boolean = if (erYtelse) false else hullIPerioder.any { it.til == null },
) {
    @get:JsonIgnore
    val harFeil
        get() =
            overlappendePerioder.isNotEmpty() ||
                hullIPerioder.isNotEmpty() ||
                ugyldigSluttPeriode ||
                fremtidigPeriode ||
                manglerSkatteprosent ||
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
    val ugyldigSluttperiode: Boolean = false,
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

data class FatteVedtakFeil(
    val feilmelding: String,
    val ugyldigPerioder: List<ÅrMånedsperiode> = emptyList(),
) {
    @JsonIgnore
    fun kastFeil(): Nothing {
        secureLogger.warn {
            "Feil ved fatting av vedtak " +
                commonObjectmapper.writeValueAsString(this)
        }
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Feil fatting av vedtak",
            commonObjectmapper.writeValueAsBytes(this),
            Charset.defaultCharset(),
        )
    }
}

data class BeregningValideringsfeil(
    val virkningstidspunkt: List<VirkningstidspunktFeilV2Dto>? = null,
    val utgift: UtgiftValideringsfeilDto? = null,
    val inntekter: InntektValideringsfeilDto? = null,
    val privatAvtale: List<PrivatAvtaleValideringsfeilDto>? = null,
    val husstandsmedlem: List<BoforholdPeriodeseringsfeil>? = null,
    val andreVoksneIHusstanden: AndreVoksneIHusstandenPeriodeseringsfeil? = null,
    val sivilstand: SivilstandPeriodeseringsfeil? = null,
    val samvær: Set<SamværValideringsfeilDto>? = null,
    val gebyr: Set<GebyrValideringsfeilDto>? = null,
    val underholdskostnad: Set<UnderholdskostnadValideringsfeil>? = null,
    val måBekrefteNyeOpplysninger: Set<MåBekrefteNyeOpplysninger> = emptySet(),
)

data class MåBekrefteNyeOpplysninger(
    val type: Grunnlagsdatatype,
    val rolle: RolleDto,
    @JsonIgnore
    val husstandsmedlem: Husstandsmedlem? = null,
    @JsonIgnore
    val underholdskostnad: Underholdskostnad? = null,
) {
    val underholdskostnadId get() = underholdskostnad?.id

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
            } ?: underholdskostnad?.person?.let {
                HusstandsmedlemDto(
                    navn = it.navn,
                    ident = it.ident,
                    fødselsdato = it.fødselsdato,
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
