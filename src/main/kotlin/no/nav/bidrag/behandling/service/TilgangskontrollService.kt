package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.ingenTilgang
import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
class TilgangskontrollService(
    private val tilgangskontrollConsumer: BidragTilgangskontrollConsumer,
) {
    fun sjekkTilgangPersonISak(
        personident: Personident,
        saksnummer: Saksnummer,
    ) {
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        if (!tilgangskontrollConsumer.sjekkTilgangPersonISak(
                personident,
                saksnummer,
            )
        ) {
            ingenTilgang("Ingen tilgang til saksnummer $saksnummer")
        }
    }

    fun sjekkTilgangBehandling(behandling: Behandling) {
        behandling.roller.filter { it.ident != null }.forEach {
            sjekkTilgangPersonISak(Personident(it.ident!!), Saksnummer(behandling.saksnummer))
        }
    }

    fun harTilgang(
        personident: Personident,
        saksnummer: Saksnummer,
    ): Boolean =
        try {
            sjekkTilgangPersonISak(personident, saksnummer)
            true
        } catch (hcee: HttpClientErrorException) {
            false
        }
}
