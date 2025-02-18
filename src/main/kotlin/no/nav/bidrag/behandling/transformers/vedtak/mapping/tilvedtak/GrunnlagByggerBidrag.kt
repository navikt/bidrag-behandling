package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
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
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BarnetilsynMedStønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilleggsstønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilsynGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto

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

fun ResultatBidragsberegningBarn.byggStønadsendringerForVedtak(behandling: Behandling): StønadsendringPeriode {
    val søknadsbarn =
        behandling.søknadsbarn.find { it.ident == barn.ident?.verdi }
            ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)

    val grunnlagListe = resultat.grunnlagListe.toSet()
    val periodeliste =
        resultat.beregnetBarnebidragPeriodeListe.map {
            OpprettPeriodeRequestDto(
                periode = it.periode,
                beløp = it.resultat.beløp,
                valutakode = "NOK",
                resultatkode = Resultatkode.BEREGNET_BIDRAG.name,
                grunnlagReferanseListe = it.grunnlagsreferanseListe,
            )
        }

    val opphørPeriode =
        listOfNotNull(
            søknadsbarn.opphørsdato?.let {
//                val sistePeriode = periodeliste.maxBy { it.periode.fom }
//                if (sistePeriode.periode.fom.plusMonths(1) !=
//                    YearMonth.from(it)
//                ) {
//                    ugyldigForespørsel("Siste periode i beregningen $sistePeriode er ikke lik opphørsdato $it")
//                }
                OpprettPeriodeRequestDto(
                    periode = ÅrMånedsperiode(it, null),
                    resultatkode = Resultatkode.OPPHØR.name,
                    beløp = null,
                    grunnlagReferanseListe =
                        listOf(
                            opprettGrunnlagsreferanseVirkningstidspunkt(søknadsbarn),
                        ),
                )
            },
        )

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste + opphørPeriode,
        grunnlagListe,
    )
}
