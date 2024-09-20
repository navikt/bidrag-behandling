package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.ingenTilgang
import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import org.springframework.stereotype.Service

@Service
class TilgangskontrollService(
    private val tIlgangskontrollConsumer: BidragTilgangskontrollConsumer,
) {
    fun sjekkTilgangPersonISak(personident: Personident, saksnummer: Saksnummer) {
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        if (!tIlgangskontrollConsumer.sjekkTilgangPersonISak(personident, saksnummer)) ingenTilgang("Ingen tilgang til saksnummer $saksnummer")
    }

    fun sjekkTilgangBehandling(behandling: Behandling) {
        behandling.roller.filter { it.ident != null }.forEach {
            sjekkTilgangPersonISak(Personident(it.ident!!), Saksnummer(behandling.saksnummer))
        }
    }
}
