package no.nav.bidrag.behandling.service.forholdsmessigfordeling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.tilDato18årsBidrag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregningsperiode
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.felles.toYearMonth
import java.time.YearMonth

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
