package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.dto.grunnlag.LøpendeBidragGrunnlagForholdsmessigFordeling
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidrag
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragPeriodeResponse
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import java.math.BigDecimal
import java.time.YearMonth

val HentSøknad.bidragsmottaker get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
val HentSøknad.bidragspliktig get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
val HentSøknad.barn get() =
    partISøknadListe.filter {
        it.rolletype == Rolletype.BARN &&
            it.behandlingstatus != Behandlingstatus.FEILREGISTRERT
    }

fun HentSøknad.parterForRolle(rolletype: Rolletype) = partISøknadListe.filter { it.rolletype == rolletype }

val behandlingstyperSomIkkeSkalInkluderesIFF =
    listOf(
        Behandlingstype.ALDERSJUSTERING,
        Behandlingstype.INNKREVINGSGRUNNLAG,
        Behandlingstype.PRIVAT_AVTALE,
        Behandlingstype.OPPHØR,
        Behandlingstype.INDEKSREGULERING,
    )

data class FFBeregningResultat(
    val harSlåttUtTilFF: Boolean,
    val beregningManglerGrunnlag: Boolean,
    val simulertGrunnlag: List<SimulertInntektGrunnlag> = emptyList(),
    val løpendeBidragBarn: List<LøpendeBidragGrunnlagForholdsmessigFordeling> = emptyList(),
)

data class SimulertInntektGrunnlag(
    val type: Grunnlagstype,
    val gjelder: String,
    val beløp: BigDecimal,
    val inntektstype: Inntektsrapportering,
)

data class SakKravhaver(
    val saksnummer: String?,
    val kravhaver: String,
    val bidragsmottaker: String? = null,
    val eierfogd: String? = null,
    val løperBidragFra: YearMonth? = null,
    val løperBidragTil: YearMonth? = null,
    val stønadstype: Stønadstype? = null,
    val opphørsdato: YearMonth? = null,
    val åpneSøknader: MutableSet<HentSøknad> = mutableSetOf(),
    val åpneBehandlinger: MutableSet<Behandling> = mutableSetOf(),
    val privatAvtale: PrivatAvtale? = null,
    val perioderLøperBidrag: List<ÅrMånedsperiode> = emptyList(),
) {
    val delAvNåværendeBehandling get() = privatAvtale?.rolle != null

    fun erSammePerson(
        ident: String,
        stønadstype1: Stønadstype?,
    ) = no.nav.bidrag.behandling.transformers.behandling
        .erSammePerson(ident, stønadstype1, kravhaver, stønadstype)

    val distinctKey get() = "${kravhaver}_${stønadstype ?: "null"}"
}

data class LøpendeBidragSakPeriode(
    val sak: Saksnummer,
    val type: Stønadstype,
    val kravhaver: Personident,
    val valutakode: String,
    val periodeFra: YearMonth,
    val periodeTil: YearMonth?,
    val perioderLøperBidrag: List<ÅrMånedsperiode> = emptyList(),
)

fun LøpendeBidragPeriodeResponse.filtrerForPeriode(beregningsperiode: ÅrMånedsperiode): List<LøpendeBidrag> =
    // Fjerner perioder som ikke overlapper med beregningsperioden
    bidragListe.mapNotNull { bidrag ->
        val beregningsperiodeTil = beregningsperiode.til
        val periodeListe =
            bidrag.periodeListe
                .filter {
                    it.periode.overlapper(beregningsperiode) &&
                        it.periode.fom != beregningsperiode.til &&
                        it.periode.til != beregningsperiode.fom
                }.map { periode ->
                    // Justerer periode.til til beregningsperiode.til hvis til er null eller etter beregningsperiode.til
                    val periodeTil = periode.periode.til
                    val justerTil = beregningsperiodeTil != null && (periodeTil == null || periodeTil.isAfter(beregningsperiodeTil))

                    // Justerer periode.fom til beregningsperiode.fom hvis fom er før beregningsperiode.fom
                    val justerFom = periode.periode.fom.isBefore(beregningsperiode.fom)

                    if (justerFom || justerTil) {
                        val nyFom = if (justerFom) beregningsperiode.fom else periode.periode.fom
                        val nyTil = if (justerTil) beregningsperiodeTil else periodeTil
                        periode.copy(periode = periode.periode.copy(fom = nyFom, til = nyTil))
                    } else {
                        periode
                    }
                }
        if (periodeListe.isNotEmpty()) {
            LøpendeBidrag(
                sak = bidrag.sak,
                type = bidrag.type,
                kravhaver = bidrag.kravhaver,
                mottaker = bidrag.mottaker,
                periodeListe = periodeListe,
            )
        } else {
            null
        }
    }
