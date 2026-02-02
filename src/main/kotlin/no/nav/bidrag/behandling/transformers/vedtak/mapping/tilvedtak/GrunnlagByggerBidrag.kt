package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.StønadsendringPeriode
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.sak.Sakskategori
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BarnetilsynMedStønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtaleGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtaleGrunnlagV2
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtalePeriodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilleggsstønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.VedtakOrkestreringDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.erResultatEndringUnderGrense
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanser
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAldersjusteringDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilsynGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.resultatSluttberegning
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.felles.toCompactString
import no.nav.bidrag.transport.felles.toYearMonth
import java.time.LocalDate
import kotlin.collections.plus

// Lager PERSON_BARN_BIDRAGSMOTTAKER objekter for at beregningen skal kunne hente ut riktig antall barn til BM
// Kan hende barn til BM er husstandsmedlem
fun Behandling.opprettMidlertidligPersonobjekterBMsbarn(personobjekter: Set<GrunnlagDto>): MutableSet<GrunnlagDto> =
    grunnlagListe
        .filter { it.type == Grunnlagsdatatype.ANDRE_BARN }
        .filter { it.rolle.ident == bidragsmottaker?.ident }
        .flatMap { grunnlag ->
            val andreBarn =
                grunnlag.konvertereData<List<RelatertPersonGrunnlagDto>>()?.filter { it.erBarn } ?: emptyList()
            andreBarn.mapNotNull { barn ->
                val eksisterendeGrunnlag = personobjekter.find { it.personIdent == barn.gjelderPersonId }
                if (listOf(
                        Grunnlagstype.PERSON_SØKNADSBARN,
                        Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
                    ).contains(eksisterendeGrunnlag?.type)
                ) {
                    return@mapNotNull null
                }
                val bidragsmottakerReferanse = grunnlag.rolle.tilGrunnlagsreferanse()
                val referanse = opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse(bidragsmottakerReferanse)
                barn.tilPersonGrunnlagAndreBarnTilBidragsmottaker(referanse, bidragsmottakerReferanse)
            }
        }.toMutableSet()

fun List<GrunnlagDto>.fjernMidlertidligPersonobjekterBMsbarn() =
    mapNotNull { grunnlag ->
        if (grunnlag.type != Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER) return@mapNotNull grunnlag
        if (filtrerBasertPåEgenReferanse(referanse = grunnlag.referanse).size > 1) return@mapNotNull null
        return@mapNotNull grunnlag
    }

fun Behandling.tilGrunnlagBarnetilsyn(inkluderIkkeAngitt: Boolean = false): List<GrunnlagDto> =
    underholdskostnader
        .flatMap { u ->
            u.barnetilsyn
                .filter { inkluderIkkeAngitt || it.omfang != Tilsynstype.IKKE_ANGITT && it.under_skolealder != null }
                .flatMap {
                    val underholdRolle =
                        u.rolle
                            ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
                    val underholdRolleGrunnlagobjekt = underholdRolle.tilGrunnlagPerson()
                    val gjelderBarnReferanse = underholdRolleGrunnlagobjekt.referanse
                    val bidragsmottaker = underholdRolle.bidragsmottaker
                    listOf(
                        underholdRolleGrunnlagobjekt,
                        GrunnlagDto(
                            referanse = it.tilGrunnlagsreferanseBarnetilsyn(gjelderBarnReferanse),
                            type = Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE,
                            gjelderReferanse = underholdRolle.bidragsmottaker!!.tilGrunnlagsreferanse(),
                            gjelderBarnReferanse = gjelderBarnReferanse,
                            grunnlagsreferanseListe =
                                if (it.kilde == Kilde.OFFENTLIG) {
                                    listOf(
                                        opprettBarnetilsynGrunnlagsreferanse(bidragsmottaker!!.tilGrunnlagsreferanse()),
                                    )
                                } else {
                                    emptyList()
                                },
                            innhold =
                                POJONode(
                                    BarnetilsynMedStønadPeriode(
                                        periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                        tilsynstype = it.omfang,
                                        skolealder =
                                            it.under_skolealder?.let {
                                                if (it) Skolealder.UNDER else Skolealder.OVER
                                            } ?: Skolealder.IKKE_ANGITT,
                                        manueltRegistrert = if (it.kilde == Kilde.OFFENTLIG) false else true,
                                    ),
                                ),
                        ),
                    )
                }
        }.toSet()
        .toList()

fun Behandling.tilGrunnlagTilleggsstønad(): List<GrunnlagDto> =
    underholdskostnader
        .flatMap { u ->
            u.tilleggsstønad.flatMap {
                val underholdRolle =
                    u.rolle
                        ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
                val bidragsmottaker = underholdRolle.bidragsmottaker
                val underholdRolleGrunnlagobjekt = underholdRolle.tilGrunnlagPerson()
                val gjelderBarnReferanse = underholdRolleGrunnlagobjekt.referanse
                listOf(
                    underholdRolleGrunnlagobjekt,
                    GrunnlagDto(
                        referanse = it.tilGrunnlagsreferanseTilleggsstønad(gjelderBarnReferanse),
                        type = Grunnlagstype.TILLEGGSSTØNAD_PERIODE,
                        gjelderReferanse = bidragsmottaker!!.tilGrunnlagsreferanse(),
                        gjelderBarnReferanse = gjelderBarnReferanse,
                        innhold =
                            POJONode(
                                TilleggsstønadPeriode(
                                    periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                    beløpDagsats = it.dagsats,
                                    beløpMåned = it.månedsbeløp,
                                    manueltRegistrert = true,
                                ),
                            ),
                    ),
                )
            }
        }.toSet()
        .toList()

fun BeregnetBarnebidragResultat.byggStønadsendringerForEndeligVedtak(
    behandling: Behandling,
    søknadsbarn: ResultatRolle,
    resultatDelvedtak: List<ResultatDelvedtak>,
): StønadsendringPeriode {
    val søknadsbarnRolle =
        behandling.søknadsbarn.find { it.ident == søknadsbarn.ident!!.verdi }
            ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)

    val grunnlagListe = mutableSetOf<GrunnlagDto>()

    fun opprettPeriode(resultatPeriode: ResultatPeriode): OpprettPeriodeRequestDto {
        val erOpphørsperiode =
            søknadsbarnRolle.opphørsdato != null && resultatPeriode.periode.fom == søknadsbarnRolle.opphørsdato?.toYearMonth()
        val vedtak =
            resultatDelvedtak.find { rv ->
                rv.resultat.beregnetBarnebidragPeriodeListe.any { vp ->
                    resultatPeriode.periode.fom == vp.periode.fom ||
                        erOpphørsperiode && vp.periode.til == søknadsbarnRolle.opphørsdato?.toYearMonth()
                }
            }!!
        val resultatkode =
            if (vedtak.request != null && erOpphørsperiode) {
                Resultatkode.OPPHØR.name
            } else if (vedtak.request != null) {
                vedtak.request.stønadsendringListe
                    .find { it.kravhaver.verdi == søknadsbarnRolle.ident!! }!!
                    .periodeListe
                    .find { vp -> resultatPeriode.periode.fom == vp.periode.fom }!!
                    .resultatkode
            } else {
                val periode = vedtak.resultat.beregnetBarnebidragPeriodeListe.find { vp -> resultatPeriode.periode.fom == vp.periode.fom }!!
                if (periode.resultat.beløp == null) Resultatkode.OPPHØR.name else Resultatkode.BEREGNET_BIDRAG.name
            }
        val referanse = "resultatFraVedtak_${vedtak.vedtaksid ?: resultatPeriode.periode.fom.toCompactString()}"
        val resultatFraGrunnlag =
            GrunnlagDto(
                referanse = referanse,
                type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                innhold =
                    POJONode(
                        ResultatFraVedtakGrunnlag(
                            vedtaksid = vedtak.vedtaksid,
                            omgjøringsvedtak = vedtak.omgjøringsvedtak,
                            beregnet = vedtak.beregnet,
                            vedtakstype = vedtak.type,
                            vedtakstidspunkt = vedtak.vedtakstidspunkt,
                            opprettParagraf35c =
                                behandling.omgjøringsdetaljer!!.paragraf35c.any {
                                    it.vedtaksid == vedtak.vedtaksid &&
                                        it.opprettParagraf35c
                                },
                        ),
                    ),
            )
        grunnlagListe.add(resultatFraGrunnlag)
        val klagevedtak =
            resultatDelvedtak
                .find { it.omgjøringsvedtak }!!
        val orkestrertVedtakGrunnlag =
            VedtakOrkestreringDetaljerGrunnlag(
                omgjøringsvedtakId = klagevedtak.vedtaksid!!,
                innkrevesFraDato = behandling.finnInnkrevesFraDato(søknadsbarnRolle),
                beregnTilDato =
                    behandling
                        .finnBeregnTilDatoBehandling(søknadsbarnRolle)
                        .toYearMonth(),
            )
        grunnlagListe.add(
            GrunnlagDto(
                referanse = "${Grunnlagstype.VEDTAK_ORKESTRERING_DETALJER}_${søknadsbarn.referanse}",
                type = Grunnlagstype.VEDTAK_ORKESTRERING_DETALJER,
                innhold = POJONode(orkestrertVedtakGrunnlag),
                gjelderBarnReferanse = søknadsbarn.referanse,
            ),
        )
        return OpprettPeriodeRequestDto(
            periode = resultatPeriode.periode,
            beløp = resultatPeriode.resultat.beløp,
            valutakode = if (resultatPeriode.resultat.beløp == null) null else "NOK",
            resultatkode = resultatkode,
            grunnlagReferanseListe = listOf(resultatFraGrunnlag.referanse),
        )
    }
    val periodeliste =
        beregnetBarnebidragPeriodeListe.map {
            opprettPeriode(it)
        }
    val finnesPerioderEtterOpphør =
        periodeliste.any { p ->
            søknadsbarnRolle.opphørsdato != null &&
                p.periode.fom >= søknadsbarnRolle.opphørsdato?.toYearMonth()
        }
    val opphørPeriode = if (finnesPerioderEtterOpphør) emptyList() else listOfNotNull(opprettPeriodeOpphør(søknadsbarnRolle, periodeliste))

    return StønadsendringPeriode(
        søknadsbarnRolle,
        periodeliste + opphørPeriode,
        grunnlagListe,
    )
}

fun BeregnetBarnebidragResultat.byggStønadsendringerForVedtak(
    behandling: Behandling,
    søknadsbarn: ResultatRolle,
    erEndeligVedtak: Boolean = true,
): StønadsendringPeriode {
    val søknadsbarn =
        behandling.søknadsbarn.find { it.ident == søknadsbarn.ident!!.verdi }
            ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)

    val grunnlagListe = grunnlagListe.toSet()
    val periodeliste =
        beregnetBarnebidragPeriodeListe.map {
            val resultatSluttberegning = grunnlagListe.toList().resultatSluttberegning(it.grunnlagsreferanseListe)
            val ikkeOmsorgForBarnet = resultatSluttberegning == Resultatkode.IKKE_OMSORG
            val barnetErSelvforsørget = resultatSluttberegning == Resultatkode.BARNET_ER_SELVFORSØRGET
            val erResultatIngenEndringUnderGrense = grunnlagListe.toList().erResultatEndringUnderGrense(søknadsbarn.tilGrunnlagsreferanse())
            val erIndeksregulering =
                grunnlagListe
                    .toList()
                    .filtrerBasertPåEgenReferanser(
                        Grunnlagstype.SLUTTBEREGNING_INDEKSREGULERING,
                        it.grunnlagsreferanseListe,
                    ).isNotEmpty()
            val erDirekteAvslag = søknadsbarn.avslag != null
            OpprettPeriodeRequestDto(
                periode = it.periode,
                beløp = if (erDirekteAvslag || ikkeOmsorgForBarnet || barnetErSelvforsørget) null else it.resultat.beløp,
                valutakode = if (ikkeOmsorgForBarnet) null else "NOK",
                resultatkode =
                    if (ikkeOmsorgForBarnet) {
                        Resultatkode.IKKE_OMSORG_FOR_BARNET.name
                    } else if (erResultatIngenEndringUnderGrense) {
                        Resultatkode.INGEN_ENDRING_UNDER_GRENSE.name
                    } else if (erIndeksregulering) {
                        Resultatkode.INDEKSREGULERING.name
                    } else if (erDirekteAvslag) {
                        søknadsbarn.avslag!!.name
                    } else {
                        Resultatkode.BEREGNET_BIDRAG.name
                    },
                grunnlagReferanseListe = it.grunnlagsreferanseListe,
            )
        }

    val opphørPeriode =
        if (erEndeligVedtak) listOfNotNull(opprettPeriodeOpphør(søknadsbarn, periodeliste)) else emptyList()

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste + opphørPeriode,
        grunnlagListe,
    )
}

fun PrivatAvtale.mapTilGrunnlag(
    gjelderBarnReferanse: String,
    bpReferanse: String,
    virkningstidspunkt: LocalDate,
): List<GrunnlagDto> =
    perioderInnkreving.map {
        GrunnlagDto(
            type = Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG,
            referanse = it.tilGrunnlagsreferansPrivatAvtalePeriode(gjelderBarnReferanse),
            gjelderReferanse = bpReferanse,
            gjelderBarnReferanse = gjelderBarnReferanse,
            innhold =
                POJONode(
                    PrivatAvtalePeriodeGrunnlag(
                        periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                        beløp = it.beløp,
                        valutakode = it.valutakode,
                        samværsklasse = it.samværsklasse,
                    ),
                ),
        )
    } +
        GrunnlagDto(
            referanse = tilGrunnlagsreferansPrivatAvtale(gjelderBarnReferanse),
            gjelderReferanse = bpReferanse,
            gjelderBarnReferanse = gjelderBarnReferanse,
            type = Grunnlagstype.PRIVAT_AVTALE_GRUNNLAG,
            innhold =
                POJONode(
                    PrivatAvtaleGrunnlagV2(
                        avtaleInngåttDato = utledetAvtaledato ?: virkningstidspunkt!!,
                        avtaleType = avtaleType ?: PrivatAvtaleType.PRIVAT_AVTALE,
                        skalIndeksreguleres = skalIndeksreguleres,
                        sakskategori = if (utenlandsk) Sakskategori.U else Sakskategori.N,
                    ),
                ),
        )

/*
Legg til vedtaksid hvis aldersjustering er utført basert på klagevedtaket i klageorkestreringen
Hvis aldersjustering er gjort basert på etterfølgende vedtak så legges det til i klageorkestreringen. Derfor det sjekkes om det er null eller ikke
 */
fun OpprettVedtakRequestDto.leggTilVedtaksidPåAldersjusteringGrunnlag(vedtaksidKlage: Int): OpprettVedtakRequestDto {
    if (type != Vedtakstype.ALDERSJUSTERING) return this
    val oppdatertGrunnlagsliste = grunnlagListe.toMutableList()
    stønadsendringListe.forEach {
        val aldersjusteringGrunnlag = grunnlagListe.hentAldersjusteringDetaljerGrunnlag(it.grunnlagReferanseListe)
        if (aldersjusteringGrunnlag != null && aldersjusteringGrunnlag.innhold.grunnlagFraVedtak == null) {
            val oppdatertInnhold =
                aldersjusteringGrunnlag.innhold.copy(
                    grunnlagFraVedtak = vedtaksidKlage,
                )
            val eksisterendeGrunnlag = grunnlagListe.find { it.referanse == aldersjusteringGrunnlag.referanse }!!
            oppdatertGrunnlagsliste.remove(eksisterendeGrunnlag)
            oppdatertGrunnlagsliste.add(
                eksisterendeGrunnlag.copy(
                    innhold = POJONode(oppdatertInnhold),
                ),
            )
        }
    }
    return this.copy(grunnlagListe = oppdatertGrunnlagsliste)
}
