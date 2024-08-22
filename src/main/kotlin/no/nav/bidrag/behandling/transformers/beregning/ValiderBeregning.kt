package no.nav.bidrag.behandling.transformers.beregning

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.MåBekrefteNyeOpplysninger
import no.nav.bidrag.behandling.dto.v2.validering.VirkningstidspunktFeilDto
import no.nav.bidrag.behandling.transformers.behandling.hentInntekterValideringsfeil
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.erDatoForUtgiftForeldet
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagUtgift
import no.nav.bidrag.behandling.transformers.utgift.hentValideringsfeil
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validereAndreVoksneIHusstanden
import no.nav.bidrag.behandling.transformers.validereSivilstand
import no.nav.bidrag.behandling.transformers.vedtak.hentAlleSomMåBekreftes
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.behandling.transformers.vedtak.særbidragDirekteAvslagskoderSomInneholderUtgifter
import no.nav.bidrag.behandling.transformers.vedtak.særbidragDirekteAvslagskoderSomKreverBeregning
import no.nav.bidrag.beregn.særbidrag.ValiderSærbidragForBeregningService
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.nio.charset.Charset
import java.time.LocalDate

fun Behandling.validerForBeregning() {
    val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
        erKlageEllerOmgjøring &&
            opprinneligVirkningstidspunkt != null &&
            virkningstidspunkt?.isAfter(opprinneligVirkningstidspunkt) == true
    val virkningstidspunktFeil =
        VirkningstidspunktFeilDto(
            manglerÅrsakEllerAvslag = avslag == null && årsak == null,
            manglerVirkningstidspunkt = virkningstidspunkt == null,
            virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig = erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt,
        ).takeIf { it.harFeil }
    val feil =
        if (avslag == null) {
            val inntekterFeil = hentInntekterValideringsfeil().takeIf { it.harFeil }
            val sivilstandFeil = sivilstand.validereSivilstand(virkningstidspunktEllerSøktFomDato).takeIf { it.harFeil }
            val husstandsmedlemsfeil =
                husstandsmedlem
                    .validerBoforhold(
                        virkningstidspunktEllerSøktFomDato,
                    ).filter { it.harFeil }
                    .takeIf { it.isNotEmpty() }
            val måBekrefteOpplysninger =
                grunnlag
                    .hentAlleSomMåBekreftes()
                    .map { grunnlagSomMåBekreftes ->
                        MåBekrefteNyeOpplysninger(
                            grunnlagSomMåBekreftes.type,
                            rolle = grunnlagSomMåBekreftes.rolle.tilDto(),
                            husstandsmedlem =
                                (grunnlagSomMåBekreftes.type == Grunnlagsdatatype.BOFORHOLD).ifTrue {
                                    husstandsmedlem.find { it.ident != null && it.ident == grunnlagSomMåBekreftes.gjelder }
                                },
                        )
                    }.toSet()
            val harFeil =
                inntekterFeil != null ||
                    sivilstandFeil != null ||
                    husstandsmedlemsfeil != null ||
                    virkningstidspunktFeil != null ||
                    måBekrefteOpplysninger.isNotEmpty()
            harFeil.ifTrue {
                BeregningValideringsfeil(
                    virkningstidspunktFeil,
                    null,
                    inntekterFeil,
                    husstandsmedlemsfeil,
                    null,
                    sivilstandFeil,
                    måBekrefteOpplysninger,
                )
            }
        } else if (virkningstidspunktFeil != null) {
            BeregningValideringsfeil(virkningstidspunktFeil, null, null, null, null, null)
        } else {
            null
        }

    if (feil != null) {
        secureLogger.warn {
            "Feil ved validering av behandling for beregning " +
                commonObjectmapper.writeValueAsString(feil)
        }
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Feil ved validering av behandling for beregning",
            commonObjectmapper.writeValueAsBytes(feil),
            Charset.defaultCharset(),
        )
    }
}

/**
 * Teknisk validering av behandling for særbidrag. Dette er caser som ikke bør oppstå og er ikke noe saksbehandler kan rette på.
 * Dette er for å sikre at vi ikke får inn data som ikke er gyldig for beregning av særbidrag og fatte vedtak
 */
fun Behandling.validerTekniskForBeregningAvSærbidrag() {
    val feilListe = mutableListOf<String>()
    if (kategori.isNullOrEmpty()) {
        feilListe.add("Kategori er null eller tom.")
    }
    if (kategori == Særbidragskategori.ANNET.name && kategoriBeskrivelse.isNullOrEmpty()) {
        feilListe.add("Kategori beskrivelse må settes når kategori er satt til ${Særbidragskategori.ANNET}.")
    }
    if (engangsbeloptype != Engangsbeløptype.SÆRBIDRAG) {
        feilListe.add("Engangsbeløptype $engangsbeloptype er ikke ${Engangsbeløptype.SÆRBIDRAG}. ")
    }
    if (stonadstype != null) {
        feilListe.add("Stønadstype $stonadstype er ikke null.")
    }
    if (!erKlageEllerOmgjøring && virkningstidspunkt != LocalDate.now().withDayOfMonth(1)) {
        feilListe.add(
            "Virkningstidspunkt $virkningstidspunkt er ikke første dag i inneværende måned. " +
                "Dette er ikke gyldig for beregning av særbidrag.",
        )
    }
    if (søknadsbarn.size > 1) {
        feilListe.add("Det er flere enn ett søknadsbarn. Dette er ikke gyldig for beregning av særbidrag.")
    }
    if (feilListe.isNotEmpty()) {
        secureLogger.warn {
            "Feil ved validering av behandling for beregning av særbidrag" +
                commonObjectmapper.writeValueAsString(feilListe)
        }
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Feil ved validering av behandling for beregning av særbidrag",
            commonObjectmapper.writeValueAsBytes(feilListe),
            Charset.defaultCharset(),
        )
    }
}

fun BeregnetSærbidragResultat.validerForSærbidrag() {
    val feilListe = mutableListOf<String>()
    val sluttberegninger =
        grunnlagListe
            .toList()
            .filtrerBasertPåEgenReferanse(
                no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
            )

    if (sluttberegninger.size != 1) {
        feilListe.add("Det er flere enn 1 eller ingen sluttberegninger i beregningsgrunnlaget.")
    }
    if (feilListe.isNotEmpty()) {
        secureLogger.warn {
            "Feil ved validering beregning av særbidrag" +
                commonObjectmapper.writeValueAsString(feilListe)
        }
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Feil ved validering av beregning av særbidrag",
            commonObjectmapper.writeValueAsBytes(feilListe),
            Charset.defaultCharset(),
        )
    }
}

fun Resultatkode?.erAvslagSomInneholderUtgifter(): Boolean {
    if (this == null) return false
    return særbidragDirekteAvslagskoderSomInneholderUtgifter.contains(this)
}

fun Behandling.erDirekteAvslagUtenBeregning(): Boolean {
    if (avslag != null) return true
    return tilSærbidragAvslagskode() != null && !særbidragDirekteAvslagskoderSomKreverBeregning.contains(tilSærbidragAvslagskode())
}

fun Behandling.tilSærbidragAvslagskode(): Resultatkode? {
    if (avslag != null || utgift == null || utgift?.utgiftsposter?.isEmpty() == true) return avslag
    val service = ValiderSærbidragForBeregningService()
    val delberegningUtgift = tilGrunnlagUtgift().innholdTilObjekt<DelberegningUtgift>()
    return when {
        utgift?.utgiftsposter?.all { erDatoForUtgiftForeldet(it.dato) } == true -> Resultatkode.ALLE_UTGIFTER_ER_FORELDET
        else -> service.validerForBeregning(vedtakstype, delberegningUtgift)
    }
}

fun Behandling.validerForBeregningSærbidrag() {
    val feil =
        if (tilSærbidragAvslagskode() == null) {
            val utgiftFeil = utgift.hentValideringsfeil()
            val inntekterFeil = hentInntekterValideringsfeil().takeIf { it.harFeil }
            val andreVoksneIHusstandenFeil =
                (husstandsmedlem.voksneIHusstanden ?: Husstandsmedlem(this, kilde = Kilde.OFFENTLIG, rolle = bidragspliktig))
                    .validereAndreVoksneIHusstanden(
                        virkningstidspunktEllerSøktFomDato,
                    ).takeIf {
                        it.harFeil
                    }
            val husstandsmedlemsfeil =
                husstandsmedlem.barn
                    .toSet()
                    .validerBoforhold(
                        virkningstidspunktEllerSøktFomDato,
                    ).filter { it.harFeil }
                    .toMutableList()

            if (husstandsmedlem.none { it.ident == søknadsbarn.first().ident }) {
                husstandsmedlemsfeil.add(
                    BoforholdPeriodeseringsfeil(
                        manglerPerioder = true,
                        husstandsmedlem =
                            Husstandsmedlem(
                                this,
                                ident = søknadsbarn.first().ident,
                                kilde = Kilde.OFFENTLIG,
                                navn = søknadsbarn.first().navn ?: "",
                                fødselsdato = søknadsbarn.first().fødselsdato,
                            ),
                    ),
                )
            }
            val måBekrefteOpplysninger =
                grunnlag
                    .hentAlleSomMåBekreftes()
                    .map { grunnlagSomMåBekreftes ->
                        MåBekrefteNyeOpplysninger(
                            grunnlagSomMåBekreftes.type,
                            rolle = grunnlagSomMåBekreftes.rolle.tilDto(),
                            husstandsmedlem =
                                (grunnlagSomMåBekreftes.type == Grunnlagsdatatype.BOFORHOLD).ifTrue {
                                    husstandsmedlem.find { it.ident != null && it.ident == grunnlagSomMåBekreftes.gjelder }
                                },
                        )
                    }.toSet()
            val harFeil =
                inntekterFeil != null ||
                    husstandsmedlemsfeil.isNotEmpty() ||
                    andreVoksneIHusstandenFeil != null ||
                    utgiftFeil != null ||
                    måBekrefteOpplysninger.isNotEmpty()
            harFeil.ifTrue {
                BeregningValideringsfeil(
                    null,
                    utgiftFeil,
                    inntekterFeil,
                    husstandsmedlemsfeil.takeIf { it.isNotEmpty() },
                    andreVoksneIHusstandenFeil,
                    null,
                    måBekrefteOpplysninger,
                )
            }
        } else {
            null
        }

    if (feil != null) {
        secureLogger.warn {
            "Feil ved validering av behandling for beregning av særbidrag" +
                commonObjectmapper.writeValueAsString(feil)
        }
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Feil ved validering av behandling for beregning av særbidrag",
            commonObjectmapper.writeValueAsBytes(feil),
            Charset.defaultCharset(),
        )
    }
}
