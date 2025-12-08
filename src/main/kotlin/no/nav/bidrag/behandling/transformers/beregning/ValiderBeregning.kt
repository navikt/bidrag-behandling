package no.nav.bidrag.behandling.transformers.beregning

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.gebyr.validerGebyr
import no.nav.bidrag.behandling.dto.v2.samvær.mapValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.BoforholdPeriodeseringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.MåBekrefteNyeOpplysninger
import no.nav.bidrag.behandling.dto.v2.validering.VirkningstidspunktFeilDto
import no.nav.bidrag.behandling.dto.v2.validering.VirkningstidspunktFeilV2Dto
import no.nav.bidrag.behandling.transformers.behandling.hentInntekterValideringsfeil
import no.nav.bidrag.behandling.transformers.behandling.hentVirkningstidspunktValideringsfeil
import no.nav.bidrag.behandling.transformers.behandling.hentVirkningstidspunktValideringsfeilV2
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.erDatoForUtgiftForeldet
import no.nav.bidrag.behandling.transformers.underhold.valider
import no.nav.bidrag.behandling.transformers.utgift.hentValideringsfeil
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validereAndreVoksneIHusstanden
import no.nav.bidrag.behandling.transformers.validerePrivatAvtale
import no.nav.bidrag.behandling.transformers.validereSivilstand
import no.nav.bidrag.behandling.transformers.vedtak.hentAlleSomMåBekreftes
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilGrunnlagUtgift
import no.nav.bidrag.behandling.transformers.vedtak.særbidragDirekteAvslagskoderSomKreverBeregning
import no.nav.bidrag.beregn.særbidrag.ValiderSærbidragForBeregningService
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import java.nio.charset.Charset
import java.time.LocalDate

@Component
class ValiderBeregning(
    val særbidragValidering: ValiderSærbidragForBeregningService = ValiderSærbidragForBeregningService(),
) {
    fun Behandling.validerForBeregningForskudd() {
        val virkningstidspunktFeil = hentVirkningstidspunktValideringsfeil().takeIf { it.harFeil }
        val virkningstidspunktFeilV2 = hentVirkningstidspunktValideringsfeilV2()

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
                        virkningstidspunktFeilV2.isNotEmpty()
                måBekrefteOpplysninger.isNotEmpty()
                harFeil.ifTrue {
                    BeregningValideringsfeil(
                        virkningstidspunkt = virkningstidspunktFeilV2.takeIf { it.isNotEmpty() },
                        inntekter = inntekterFeil,
                        husstandsmedlem = husstandsmedlemsfeil,
                        sivilstand = sivilstandFeil,
                        måBekrefteNyeOpplysninger = måBekrefteOpplysninger,
                    )
                }
            } else if (virkningstidspunktFeil != null) {
                BeregningValideringsfeil(virkningstidspunktFeilV2)
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

    fun Behandling.erDirekteAvslagUtenBeregning(): Boolean {
        if (avslag != null) return true
        return tilSærbidragAvslagskode() != null && !særbidragDirekteAvslagskoderSomKreverBeregning.contains(tilSærbidragAvslagskode())
    }

    fun Behandling.tilSærbidragAvslagskode(): Resultatkode? {
        if (avslag != null || utgift == null || utgift?.utgiftsposter?.isEmpty() == true) return avslag
        val delberegningUtgift = tilGrunnlagUtgift().innholdTilObjekt<DelberegningUtgift>()
        return when {
            utgift?.utgiftsposter?.all { erDatoForUtgiftForeldet(it.dato) } == true -> Resultatkode.ALLE_UTGIFTER_ER_FORELDET
            else -> særbidragValidering.validerForBeregning(vedtakstype, delberegningUtgift)
        }
    }

    fun Behandling.validerForBeregningBidrag() {
        val feil =
            if (erInnkreving) {
                validerForBeregningInnkrevingBidrag()
            } else if (vedtakstype == Vedtakstype.ALDERSJUSTERING) {
                validerForBeregningAldersjusteringBidrag()
            } else if (avslag == null) {
                validerForBeregningBidragIkkeAvslag()
            } else {
                val gebyrValideringsfeil = validerGebyr()
                val virkningstidspunktFeil = hentVirkningstidspunktValideringsfeil().takeIf { it.harFeil }
                val virkningstidspunktFeilV2 = hentVirkningstidspunktValideringsfeilV2()
                val harFeil = virkningstidspunktFeil != null || gebyrValideringsfeil.isNotEmpty() || virkningstidspunktFeilV2.isNotEmpty()
                harFeil.ifTrue {
                    BeregningValideringsfeil(
                        virkningstidspunkt = virkningstidspunktFeilV2.takeIf { it.isNotEmpty() },
                        gebyr = gebyrValideringsfeil.takeIf { it.isNotEmpty() }?.toSet(),
                    )
                }
            }

        if (feil != null) {
            secureLogger.warn {
                "Feil ved validering av behandling for beregning av bidrag" +
                    commonObjectmapper.writeValueAsString(feil)
            }
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Feil ved validering av behandling for beregning av bidrag",
                commonObjectmapper.writeValueAsBytes(feil),
                Charset.defaultCharset(),
            )
        }
    }

    fun Behandling.validerForBeregningBidragIkkeAvslag(): BeregningValideringsfeil? {
        val gebyrValideringsfeil = validerGebyr()
        val virkningstidspunktFeil = hentVirkningstidspunktValideringsfeil()
        val virkningstidspunktFeilV2 = hentVirkningstidspunktValideringsfeilV2()
        val inntekterFeil = hentInntekterValideringsfeil().takeIf { it.harFeil }
        val andreVoksneIHusstandenFeil =
            (husstandsmedlem.voksneIHusstanden ?: Husstandsmedlem(this, kilde = Kilde.OFFENTLIG, rolle = bidragspliktig))
                .validereAndreVoksneIHusstanden(
                    virkningstidspunktEllerSøktFomDato,
                ).takeIf {
                    it.harFeil
                }
        val privatAvtaleValideringsfeil = privatAvtale.map { it.validerePrivatAvtale() }.filter { it.harFeil }
        val husstandsmedlemsfeil =
            husstandsmedlem.barn
                .toSet()
                .validerBoforhold(
                    virkningstidspunktEllerSøktFomDato,
                ).filter { it.harFeil }
                .toMutableList()

        if (søknadsbarn.none { sb -> husstandsmedlem.any { it.ident == sb.ident } }) {
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
                        underholdskostnad =
                            (grunnlagSomMåBekreftes.type == Grunnlagsdatatype.BARNETILSYN).ifTrue {
                                underholdskostnader.find { u -> u.rolle != null }
                            },
                        husstandsmedlem =
                            (grunnlagSomMåBekreftes.type == Grunnlagsdatatype.BOFORHOLD).ifTrue {
                                husstandsmedlem.find { it.ident != null && it.ident == grunnlagSomMåBekreftes.gjelder }
                            },
                    )
                }.toSet()
        val samværValideringsfeil = samvær.mapValideringsfeil()
        val underholdValideringsfeil = underholdskostnader.valider()
        val harFeil =
            inntekterFeil != null ||
                husstandsmedlemsfeil.isNotEmpty() ||
                privatAvtaleValideringsfeil.isNotEmpty() ||
                andreVoksneIHusstandenFeil != null ||
                samværValideringsfeil.isNotEmpty() ||
                gebyrValideringsfeil.isNotEmpty() ||
                virkningstidspunktFeilV2.isNotEmpty() ||
                underholdValideringsfeil.isNotEmpty() ||
                måBekrefteOpplysninger.isNotEmpty()
        return harFeil.ifTrue {
            BeregningValideringsfeil(
                inntekter = inntekterFeil,
                privatAvtale = privatAvtaleValideringsfeil.takeIf { it.isNotEmpty() },
                husstandsmedlem = husstandsmedlemsfeil.takeIf { it.isNotEmpty() },
                andreVoksneIHusstanden = andreVoksneIHusstandenFeil,
                måBekrefteNyeOpplysninger = måBekrefteOpplysninger,
                virkningstidspunkt = virkningstidspunktFeilV2.takeIf { it.isNotEmpty() },
                gebyr = gebyrValideringsfeil.takeIf { it.isNotEmpty() }?.toSet(),
                samvær = samværValideringsfeil.takeIf { it.isNotEmpty() },
                underholdskostnad = underholdValideringsfeil.takeIf { it.isNotEmpty() },
            )
        }
    }

    fun Behandling.validerForBeregningInnkrevingBidrag(): BeregningValideringsfeil? {
        val virkningstidspunktFeil = hentVirkningstidspunktValideringsfeil().takeIf { it.harFeil }
        val virkningstidspunktFeilV2 = hentVirkningstidspunktValideringsfeilV2()
        val privatAvtaleValideringsfeil = privatAvtale.map { it.validerePrivatAvtale() }.filter { it.harFeil }
        val harFeil = virkningstidspunktFeil != null || privatAvtaleValideringsfeil.isNotEmpty() || virkningstidspunktFeilV2.isNotEmpty()
        return harFeil.ifTrue {
            BeregningValideringsfeil(
                virkningstidspunkt = virkningstidspunktFeilV2.takeIf { it.isNotEmpty() },
                privatAvtale = privatAvtaleValideringsfeil,
            )
        }
    }

    fun Behandling.validerForBeregningAldersjusteringBidrag(): BeregningValideringsfeil? =
        BeregningValideringsfeil(
            virkningstidspunkt =
                søknadsbarn
                    .mapNotNull {
                        VirkningstidspunktFeilV2Dto(
                            gjelder = it.tilDto(),
                            måVelgeVedtakForBeregning = it.grunnlagFraVedtak == null,
                        ).takeIf { it.harFeil }
                    }.takeIf { it.isNotEmpty() },
        ).takeIf { it.virkningstidspunkt != null }

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
                        utgift = utgiftFeil,
                        inntekter = inntekterFeil,
                        husstandsmedlem = husstandsmedlemsfeil.takeIf { it.isNotEmpty() },
                        andreVoksneIHusstanden = andreVoksneIHusstandenFeil,
                        måBekrefteNyeOpplysninger = måBekrefteOpplysninger,
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
}
