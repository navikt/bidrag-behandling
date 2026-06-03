package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.dto.grunnlag.LøpendeBidragGrunnlagForholdsmessigFordeling
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.tilDato18årsBidrag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregningsperiode
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
import no.nav.bidrag.transport.felles.toYearMonth
import java.math.BigDecimal
import java.time.YearMonth

// ═══════════════════════════════════════════════════════════════════
// region Data classes
// ═══════════════════════════════════════════════════════════════════

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

// endregion

// ═══════════════════════════════════════════════════════════════════
// region Constants
// ═══════════════════════════════════════════════════════════════════

val behandlingstyperSomIkkeSkalInkluderesIFF =
    listOf(
        Behandlingstype.ALDERSJUSTERING,
        Behandlingstype.INNKREVINGSGRUNNLAG,
        Behandlingstype.PRIVAT_AVTALE,
        Behandlingstype.OPPHØR,
        Behandlingstype.INDEKSREGULERING,
    )

// endregion

// ═══════════════════════════════════════════════════════════════════
// region Extension functions — HentSøknad
// ═══════════════════════════════════════════════════════════════════

val HentSøknad.bidragsmottaker get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
val HentSøknad.bidragspliktig get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
val HentSøknad.barn get() =
    partISøknadListe.filter {
        it.rolletype == Rolletype.BARN &&
            it.behandlingstatus != Behandlingstatus.FEILREGISTRERT
    }

fun HentSøknad.parterForRolle(rolletype: Rolletype) = partISøknadListe.filter { it.rolletype == rolletype }

// endregion

// ═══════════════════════════════════════════════════════════════════
// region Extension functions — LøpendeBidrag
// ═══════════════════════════════════════════════════════════════════

fun LøpendeBidragPeriodeResponse.filtrerForPeriode(beregningsperiode: ÅrMånedsperiode): List<LøpendeBidrag> =
    bidragListe.mapNotNull { bidrag ->
        val beregningsperiodeTil = beregningsperiode.til
        val periodeListe =
            bidrag.periodeListe
                .filter {
                    it.periode.overlapper(beregningsperiode) &&
                        it.periode.fom != beregningsperiode.til &&
                        it.periode.til != beregningsperiode.fom
                }.map { periode ->
                    val periodeTil = periode.periode.til
                    val justerTil = beregningsperiodeTil != null && (periodeTil == null || periodeTil.isAfter(beregningsperiodeTil))
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

// endregion

// ═══════════════════════════════════════════════════════════════════
// region Helper functions
// ═══════════════════════════════════════════════════════════════════

fun Behandling.finnOpphørsdato(
    stønadstype: Stønadstype,
    barnFnr: String,
): YearMonth? {
    val fødselsdato = hentPersonFødselsdato(barnFnr)
    return if (fødselsdato != null && stønadstype == Stønadstype.BIDRAG) {
        fødselsdato.tilDato18årsBidrag().takeIf { it <= finnBeregnTilDato() }?.toYearMonth()
    } else {
        null
    }
}

fun finnBeregningsperiodeForKravhavere(
    eksisterendeRelevanteKravhavere: Set<SakKravhaver>?,
    behandling: Behandling,
): ÅrMånedsperiode {
    val beregningsperiode =
        if (eksisterendeRelevanteKravhavere.isNullOrEmpty()) {
            behandling.finnBeregningsperiode()
        } else {
            val senestBeregnTil =
                if (eksisterendeRelevanteKravhavere.any { it.løperBidragTil == null && it.løperBidragFra != null }) {
                    null
                } else {
                    eksisterendeRelevanteKravhavere
                        .filter {
                            it.løperBidragTil != null
                        }.maxOfOrNull { it.løperBidragTil!! }
                }
            val nyBeregnTil =
                behandling.søknadsbarn.maxOf {
                    behandling.finnBeregnTilDatoBehandling(
                        it,
                        opphørsdato = senestBeregnTil,
                    )
                }
            ÅrMånedsperiode(
                fom = behandling.eldsteVirkningstidspunkt,
                til = nyBeregnTil,
            )
        }
    return beregningsperiode
}

// endregion
