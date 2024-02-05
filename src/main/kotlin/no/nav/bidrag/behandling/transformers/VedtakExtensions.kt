package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse

val VedtakHendelse.stønadstype get() = this.stønadsendringListe?.firstOrNull()?.type
val VedtakHendelse.engangsbeløptype get() = this.engangsbeløpListe?.firstOrNull()?.type

val VedtakHendelse.saksnummer
    get(): String =
        stønadsendringListe?.firstOrNull()?.sak?.verdi
            ?: engangsbeløpListe?.firstOrNull()?.sak?.verdi
            ?: throw RuntimeException("Vedtak hendelse med id $id mangler saksnummer")
