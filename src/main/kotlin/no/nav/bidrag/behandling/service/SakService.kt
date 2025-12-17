package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.sak.BidragssakDto

fun hentSak(saksnummer: String): BidragssakDto? =
    try {
        AppContext.getBean(BidragSakConsumer::class.java).hentSak(saksnummer)
    } catch (e: Exception) {
        secureLogger.debug(e) { "Feil ved henting av sak $saksnummer" }
        null
    }

fun hentAlleSaker(bp: String): List<BidragssakDto> =
    try {
        AppContext.getBean(BidragSakConsumer::class.java).hentSakerPerson(bp)
    } catch (e: Exception) {
        secureLogger.debug(e) { "Feil ved henting av saker for person $bp" }
        emptyList()
    }
