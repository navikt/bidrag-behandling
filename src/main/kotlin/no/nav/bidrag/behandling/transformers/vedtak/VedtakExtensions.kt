package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse

val VedtakHendelse.stønadstype get() = this.stønadsendringListe?.firstOrNull()?.type
val VedtakHendelse.engangsbeløptype get() = this.engangsbeløpListe?.firstOrNull()?.type
