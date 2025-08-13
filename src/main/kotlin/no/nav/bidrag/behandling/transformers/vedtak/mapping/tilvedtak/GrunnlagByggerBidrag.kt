package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.dto.v1.beregning.finnSluttberegningIReferanser
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.service.hentSisteBeløpshistorikk
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.StønadsendringPeriode
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BarnetilsynMedStønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilleggsstønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.erResultatEndringUnderGrense
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilsynGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.felles.toCompactString
import java.time.LocalDate
import java.time.YearMonth

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
                val bidragsmottakerReferanse = bidragsmottaker!!.tilGrunnlagsreferanse()
                val referanse = opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse(bidragsmottakerReferanse)
                barn.tilPersonGrunnlagAndreBarnTilBidragsmottaker(referanse, eksisterendeGrunnlag?.referanse)
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
                        u.barnetsRolleIBehandlingen
                            ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
                    val underholdRolleGrunnlagobjekt = underholdRolle.tilGrunnlagPerson()
                    val gjelderBarnReferanse = underholdRolleGrunnlagobjekt.referanse
                    listOf(
                        underholdRolleGrunnlagobjekt,
                        GrunnlagDto(
                            referanse = it.tilGrunnlagsreferanseBarnetilsyn(gjelderBarnReferanse),
                            type = Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE,
                            gjelderReferanse = bidragsmottaker!!.tilGrunnlagsreferanse(),
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
                    u.barnetsRolleIBehandlingen
                        ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
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
        val vedtak =
            resultatDelvedtak.find { rv ->
                rv.resultat.beregnetBarnebidragPeriodeListe.any { vp -> resultatPeriode.periode.fom == vp.periode.fom }
            }!!
        val resultatkode =
            if (vedtak.request != null) {
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
                            klagevedtak = vedtak.klagevedtak,
                            beregnet = vedtak.beregnet,
                            opprettParagraf35c = behandling.klagedetaljer!!.paragraf35c.any { it.vedtaksid == vedtak.vedtaksid },
                        ),
                    ),
            )
        grunnlagListe.add(resultatFraGrunnlag)
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

    return StønadsendringPeriode(
        søknadsbarnRolle,
        periodeliste,
        grunnlagListe,
    )
}

fun BeregnetBarnebidragResultat.byggStønadsendringerForVedtak(
    behandling: Behandling,
    søknadsbarn: ResultatRolle,
): StønadsendringPeriode {
    val søknadsbarn =
        behandling.søknadsbarn.find { it.ident == søknadsbarn.ident!!.verdi }
            ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)

    val grunnlagListe = grunnlagListe.toSet()
    val periodeliste =
        beregnetBarnebidragPeriodeListe.map {
            val sluttberegningGrunnlag =
                grunnlagListe
                    .toList()
                    .finnSluttberegningIReferanser(
                        it.grunnlagsreferanseListe,
                    )
            val ikkeOmsorgForBarnet =
                if (sluttberegningGrunnlag?.type == Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG) {
                    sluttberegningGrunnlag.innholdTilObjekt<SluttberegningBarnebidrag>().ikkeOmsorgForBarnet
                } else {
                    false
                }
            val erResultatIngenEndringUnderGrense = grunnlagListe.toList().erResultatEndringUnderGrense(søknadsbarn.tilGrunnlagsreferanse())
            OpprettPeriodeRequestDto(
                periode = it.periode,
                beløp = it.resultat.beløp,
                valutakode = if (ikkeOmsorgForBarnet) null else "NOK",
                resultatkode =
                    if (ikkeOmsorgForBarnet) {
                        Resultatkode.IKKE_OMSORG_FOR_BARNET.name
                    } else if (erResultatIngenEndringUnderGrense) {
                        Resultatkode.INGEN_ENDRING_UNDER_GRENSE.name
                    } else {
                        Resultatkode.BEREGNET_BIDRAG.name
                    },
                grunnlagReferanseListe = it.grunnlagsreferanseListe,
            )
        }

    val opphørPeriode =
        listOfNotNull(opprettPeriodeOpphør(søknadsbarn, periodeliste))

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste + opphørPeriode,
        grunnlagListe,
    )
}
