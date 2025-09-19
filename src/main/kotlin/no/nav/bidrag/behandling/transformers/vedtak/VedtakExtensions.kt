package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto

val VedtakHendelse.stønadstype get() = this.stønadsendringListe?.firstOrNull()?.type
val VedtakHendelse.engangsbeløptype get() = this.engangsbeløpListe?.firstOrNull()?.type
val VedtakDto.innkrevingstype get() =
    this.stønadsendringListe.firstOrNull()?.innkreving
        ?: this.engangsbeløpListe.firstOrNull()?.innkreving
        ?: Innkrevingstype.MED_INNKREVING

// val VedtakDto.mottatDato get() = hent
