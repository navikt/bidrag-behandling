package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.ingenTilgang
import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.saksnummer
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

    fun sjekkTilgangVedtak(vedtak: VedtakDto) {
        vedtak.grunnlagListe
            .hentAllePersoner()
            .filter {
                listOf(
                    Grunnlagstype.PERSON_SØKNADSBARN,
                    Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
                    Grunnlagstype.PERSON_BIDRAGSMOTTAKER,
                    Grunnlagstype.PERSON_REELL_MOTTAKER,
                ).contains(it.type)
            }.map { it.personObjekt }
            .forEach {
                sjekkTilgangPersonISak(Personident(it.ident!!.verdi), Saksnummer(vedtak.saksnummer!!))
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

    fun harBeskyttelse(personident: Personident): Boolean = tilgangskontrollConsumer.personHarBeskyttelse(personident)
}
