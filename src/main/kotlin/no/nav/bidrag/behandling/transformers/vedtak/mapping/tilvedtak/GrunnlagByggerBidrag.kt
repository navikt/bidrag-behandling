package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.StønadsendringPeriode
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.beregn.barnebidrag.bo.FaktiskUtgiftPeriode
import no.nav.bidrag.beregn.barnebidrag.bo.TilleggsstønadPeriode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DeltBostedPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.felles.toCompactString
import java.math.BigDecimal

fun Behandling.tilGrunnlagFaktiskeTilsynsutgifter(søknadsbarn: GrunnlagDto? = null): List<GrunnlagDto> {
    val søknadsbarnIdent = søknadsbarn?.personIdent
    return underholdskostnader
        .filter { søknadsbarn == null || it.person.ident == søknadsbarnIdent }
        .flatMap { u ->
            val gjelderBarn =
                søknadsbarn
                    ?: u.person.rolle
                        .find { it.behandling.id == id }
                        ?.tilGrunnlagPerson()
                    ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
            val gjelderBarnReferanse = gjelderBarn.referanse

            u.faktiskeTilsynsutgifter.map {
                GrunnlagDto(
                    referanse =
                        "${Grunnlagstype.TILLEGGSSTØNAD}_${u.person.rolle.first().tilGrunnlagsreferanse()}_" +
                            "_${it.fom.toCompactString()}${it.tom?.let { "_${it.toCompactString()}" }}",
                    type = Grunnlagstype.TILLEGGSSTØNAD,
                    innhold =
                        POJONode(
                            FaktiskUtgiftPeriode(
                                periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                fødselsdatoBarn = gjelderBarn.personObjekt.fødselsdato,
                                gjelderBarn = gjelderBarnReferanse,
                                kostpengerBeløp = it.kostpenger ?: BigDecimal.ZERO,
                                faktiskUtgiftBeløp = it.tilsynsutgift,
                                manueltRegistrert = true,
                            ),
                        ),
                )
            }
        }
}

fun Behandling.tilGrunnlagTilleggsstønad(søknadsbarn: GrunnlagDto? = null): List<GrunnlagDto> {
    val søknadsbarnIdent = søknadsbarn?.personIdent
    return underholdskostnader
        .filter { søknadsbarn == null || it.person.ident == søknadsbarnIdent }
        .flatMap { u ->
            val gjelderBarn =
                søknadsbarn?.referanse ?: u.person.rolle
                    .find { it.behandling.id == id }
                    ?.tilGrunnlagsreferanse()
                    ?: ugyldigForespørsel("Fant ikke person for underholdskostnad i behandlingen")
            u.tilleggsstønad.map {
                GrunnlagDto(
                    referanse =
                        "${Grunnlagstype.TILLEGGSSTØNAD}_${u.person.rolle.first().tilGrunnlagsreferanse()}_" +
                            "_${it.fom.toCompactString()}${it.tom?.let { "_${it.toCompactString()}" }}",
                    type = Grunnlagstype.TILLEGGSSTØNAD,
                    innhold =
                        POJONode(
                            TilleggsstønadPeriode(
                                periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                beløpDagsats = it.dagsats,
                                gjelderBarn = gjelderBarn,
                                manueltRegistrert = true,
                            ),
                        ),
                )
            }
        }
}

fun Behandling.tilGrunnlagDeltBossted(søknadsbarn: GrunnlagDto? = null): List<GrunnlagDto> {
    val søknadsbarnIdent = søknadsbarn?.personIdent
    return listOf(
        GrunnlagDto(
            referanse = "DeltBossted_$id",
            type = Grunnlagstype.DELT_BOSTED,
            innhold =
                POJONode(
                    DeltBostedPeriode(
                        periode = ÅrMånedsperiode(virkningstidspunkt!!, null),
                        deltBosted = false,
                        manueltRegistrert = true,
                    ),
                ),
        ),
    )
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
                resultatkode = it.resultat.kode.name,
                grunnlagReferanseListe = it.grunnlagsreferanseListe,
            )
        }

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste,
        grunnlagListe,
    )
}
