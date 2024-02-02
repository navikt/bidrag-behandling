package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse

val VedtakHendelse.stønadstype get() = this.stønadsendringListe?.firstOrNull()?.type
val VedtakHendelse.engangsbeløptype get() = this.engangsbeløpListe?.firstOrNull()?.type
val VedtakHendelse.søknadsid
    get() =
        this.behandlingsreferanseListe?.find {
            it.kilde == BehandlingsrefKilde.BISYS_SØKNAD.name
        }?.referanse?.toLong()
val VedtakHendelse.behandlingId
    get() =
        this.behandlingsreferanseListe?.find {
            it.kilde == BehandlingsrefKilde.BEHANDLING_ID.name
        }?.referanse?.toLong()

fun VedtakHendelse.erFattetFraBidragBehandling() = behandlingId != null

val VedtakHendelse.saksnummer
    get(): String =
        stønadsendringListe?.firstOrNull()?.sak?.verdi
            ?: engangsbeløpListe?.firstOrNull()?.sak?.verdi
            ?: throw RuntimeException("Vedtak hendelse med id $id mangler saksnummer")
