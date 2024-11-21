package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
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
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto

fun Behandling.tilGrunnlagBarnetilsyn(inkluderIkkeAngitt: Boolean = false): List<GrunnlagDto> =
    underholdskostnader
        .flatMap { u ->
            u.barnetilsyn
                .filter { inkluderIkkeAngitt || it.omfang != Tilsynstype.IKKE_ANGITT && it.under_skolealder != null }
                .map {
                    val underholdRolle = u.person.rolle.find { it.behandling.id == id }
                    val gjelderBarn =
                        underholdRolle?.tilGrunnlagPerson()
                            ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
                    val gjelderBarnReferanse = gjelderBarn.referanse
                    GrunnlagDto(
                        referanse = it.tilGrunnlagsreferanseBarnetilsyn(gjelderBarnReferanse),
                        type = Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE,
                        gjelderReferanse = bidragsmottaker!!.tilGrunnlagsreferanse(),
                        innhold =
                            POJONode(
                                BarnetilsynMedStønadPeriode(
                                    periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                    gjelderBarn = gjelderBarnReferanse,
                                    tilsynstype = it.omfang,
                                    skolealder =
                                        it.under_skolealder?.let {
                                            if (it) Skolealder.UNDER else Skolealder.OVER
                                        } ?: Skolealder.IKKE_ANGITT,
                                    manueltRegistrert = if (it.kilde == Kilde.OFFENTLIG) false else true,
                                ),
                            ),
                    )
                }
        }

fun Behandling.tilGrunnlagTilleggsstønad(): List<GrunnlagDto> =
    underholdskostnader
        .flatMap { u ->
            u.tilleggsstønad.map {
                val underholdRolle = u.person.rolle.find { it.behandling.id == id }
                val gjelderBarnReferanse =
                    underholdRolle?.tilGrunnlagsreferanse()
                        ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
                GrunnlagDto(
                    referanse = it.tilGrunnlagsreferanseTilleggsstønad(gjelderBarnReferanse),
                    type = Grunnlagstype.TILLEGGSSTØNAD_PERIODE,
                    gjelderReferanse = bidragsmottaker!!.tilGrunnlagsreferanse(),
                    innhold =
                        POJONode(
                            TilleggsstønadPeriode(
                                periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                beløpDagsats = it.dagsats,
                                gjelderBarn = gjelderBarnReferanse,
                                manueltRegistrert = true,
                            ),
                        ),
                )
            }
        }

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

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste,
        grunnlagListe,
    )
}
