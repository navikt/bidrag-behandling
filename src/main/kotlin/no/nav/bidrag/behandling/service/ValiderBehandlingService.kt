package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.tilType
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.stonad.request.LøpendeBidragssakerRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

private val log = KotlinLogging.logger {}

@Service
class ValiderBehandlingService(
    private val bidragStonadConsumer: BidragStønadConsumer,
) {
    fun kanBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): Boolean {
        if (request.tilType() == TypeBehandling.SÆRBIDRAG) {
            val bp = request.roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }!!
            val løpendeBidrag = bidragStonadConsumer.hentLøpendeBidrag(LøpendeBidragssakerRequest(skyldner = bp.ident!!))
            return løpendeBidrag.bidragssakerListe.all { it.valutakode == "NOK" || it.valutakode.isEmpty() }
        }
        return true
    }

    fun validerKanBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest) {
        if (!kanBehandlesINyLøsning(request)) {
            log.info {
                "Behandling engangsbeløpstype=${request.engangsbeløpstype}, stønadstype=${request.stønadstype} kan ikke behandles i ny løsning"
            }
            secureLogger.info { "Behandling kan ikke behandles i ny løsning: $request" }
            throw HttpClientErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "Behandling kan ikke behandles i ny løsning",
            )
        }
    }
}
