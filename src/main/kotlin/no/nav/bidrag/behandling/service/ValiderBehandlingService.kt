package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningResponse
import no.nav.bidrag.behandling.dto.v2.behandling.tilType
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.stonad.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@Service
class ValiderBehandlingService(
    private val bidragStonadConsumer: BidragStønadConsumer,
) {
    fun kanBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? =
        when (request.tilType()) {
            TypeBehandling.SÆRBIDRAG -> kanSærbidragBehandlesINyLøsning(request)
            TypeBehandling.BIDRAG -> kanBidragBehandlesINyLøsning(request)
            else -> null
        }

    private fun kanSærbidragBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? {
        val bp = request.roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }!!
        val løpendeBidrag = bidragStonadConsumer.hentLøpendeBidrag(LøpendeBidragssakerRequest(skyldner = bp.ident!!))
        val harLøpendeBidragUtenlandskValuta =
            løpendeBidrag.bidragssakerListe.all {
                it.valutakode == "NOK" || it.valutakode.isEmpty()
            }
        return if (!harLøpendeBidragUtenlandskValuta) "Bidragspliktig har løpende bidrag i utenlandsk valuta" else null
    }

    private fun kanBidragBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? = kanBidragV1BehandlesINyLøsning(request)

    private fun kanBidragV1BehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? {
        if (!request.skruddAvManuelt.isNullOrEmpty()) return request.skruddAvManuelt
        if (request.søknadsbarn.size > 1) return "Behandlingen har flere enn ett søknadsbarn"
        if (request.vedtakstype == Vedtakstype.KLAGE || request.harReferanseTilAnnenBehandling) {
            return "Kan ikke behandle klage eller omgjøring"
        }
        if (request.vedtakstype == Vedtakstype.REVURDERING || request.søknadstype == BisysSøknadstype.BEGRENSET_REVURDERING) {
            return "Kan ikke behandle begrenset revurdering"
        }
        val bp = request.bidragspliktig
        if (bp == null || bp.erUkjent == true || bp.ident == null) return "Behandlingen mangler bidragspliktig"
        val harBPMinstEnBidragsstønad =
            bidragStonadConsumer
                .hentAlleStønaderForBidragspliktig(bp.ident)
                .stønader
                .any { it.type != Stønadstype.FORSKUDD }
        if (harBPMinstEnBidragsstønad) "Bidragspliktig har en eller flere historiske eller løpende bidrag"

        if (request.søktFomDato != null && request.søktFomDato.isBefore(LocalDate.parse("2023-03-01"))) {
            return "Behandlingen er registrert med søkt fra dato før mars 2023"
        }
        return null
    }

    fun validerKanBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest) {
        val resultat = kanBehandlesINyLøsning(request)
        if (resultat != null) {
            log.info {
                "Behandling engangsbeløpstype=${request.engangsbeløpstype}, stønadstype=${request.stønadstype} kan ikke behandles i ny løsning: $resultat"
            }
            secureLogger.info { "Behandling kan ikke behandles i ny løsning: $request med begrunnelse $resultat" }
            throw HttpClientErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "Behandling kan ikke behandles i ny løsning",
                commonObjectmapper.writeValueAsBytes(KanBehandlesINyLøsningResponse(listOf(resultat))),
                Charsets.UTF_8,
            )
        }
    }
}
