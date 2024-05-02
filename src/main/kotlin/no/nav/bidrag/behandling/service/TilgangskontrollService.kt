package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.ingenTilgang
import no.nav.bidrag.commons.security.SikkerhetsKontekst
import org.springframework.stereotype.Service

@Service
class TilgangskontrollService(private val tIlgangskontrollConsumer: BidragTilgangskontrollConsumer) {
    fun sjekkTilgangSak(saksnummer: String) {
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        if (!tIlgangskontrollConsumer.sjekkTilgangSak(saksnummer)) ingenTilgang("Ingen tilgang til saksnummer $saksnummer")
    }

    fun sjekkTilgangBehandling(behandling: Behandling) {
        sjekkTilgangSak(behandling.saksnummer)
        behandling.roller.filter { it.ident != null }.forEach {
            sjekkTilgangPerson(it.ident!!)
        }
    }

    fun sjekkTilgangPerson(personnummer: String) {
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        if (!tIlgangskontrollConsumer.sjekkTilgangPerson(personnummer)) ingenTilgang("Ingen tilgang til person $personnummer")
    }
}
