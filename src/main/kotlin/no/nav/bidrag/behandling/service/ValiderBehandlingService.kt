package no.nav.bidrag.behandling.service

import io.getunleash.Unleash
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
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.stonad.request.HentStønadHistoriskRequest
import no.nav.bidrag.transport.behandling.stonad.request.LøpendeBidragssakerRequest
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

val bidragStønadstyperSomKanBehandles = listOf(Stønadstype.BIDRAG, Stønadstype.BIDRAG18AAR)

@Service
class ValiderBehandlingService(
    private val bidragStonadConsumer: BidragStønadConsumer,
    private val unleash: Unleash,
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
        if (!bidragStønadstyperSomKanBehandles.contains(request.stønadstype)) {
            return "Kan ikke behandle ${request.stønadstype?.tilVisningsnavn()} gjennom ny løsning"
        }
        if (request.søknadsbarn.size > 1) return "Behandlingen har flere enn ett søknadsbarn"
        if (request.vedtakstype == Vedtakstype.KLAGE || request.harReferanseTilAnnenBehandling) {
            return "Kan ikke behandle klage eller omgjøring"
        }
        if (!kanBehandleBegrensetRevurdering(request)) {
            return "Kan ikke behandle begrenset revurdering. Minst en løpende forskudd eller bidrag periode har utenlandsk valuta"
        }
        val bp = request.bidragspliktig
        if (bp == null || bp.erUkjent == true || bp.ident == null) return "Behandlingen mangler bidragspliktig"
        if (!unleash.isEnabled("behandling.v2_endring", false)) {
            val harBPMinstEnBidragsstønad =
                bidragStonadConsumer
                    .hentAlleStønaderForBidragspliktig(bp.ident)
                    .stønader
                    .any { it.type != Stønadstype.FORSKUDD }
            if (harBPMinstEnBidragsstønad &&
                !request.erBegrensetRevurdering()
            ) {
                return "Bidragspliktig har en eller flere historiske eller løpende bidrag"
            }
        }

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

    fun kanBehandleBegrensetRevurdering(request: KanBehandlesINyLøsningRequest): Boolean =
        if (request.erBegrensetRevurdering()) {
            harIngenHistoriskePerioderMedUtenlandskValuta(request, Stønadstype.BIDRAG) &&
                harIngenHistoriskePerioderMedUtenlandskValuta(request, Stønadstype.FORSKUDD)
        } else {
            true
        }

    private fun KanBehandlesINyLøsningRequest.erBegrensetRevurdering() =
        this.søknadstype == BisysSøknadstype.BEGRENSET_REVURDERING ||
            this.søknadstype == BisysSøknadstype.REVURDERING

    private fun harIngenHistoriskePerioderMedUtenlandskValuta(
        request: KanBehandlesINyLøsningRequest,
        stønadstype: Stønadstype,
    ): Boolean =
        request.søknadsbarn.filter { it.ident != null }.all {
            bidragStonadConsumer
                .hentHistoriskeStønader(
                    HentStønadHistoriskRequest(
                        type = stønadstype,
                        sak = Saksnummer(request.saksnummer),
                        skyldner = request.bidragspliktig!!.ident!!,
                        kravhaver = it.ident!!,
                        gyldigTidspunkt = LocalDateTime.now(),
                    ),
                )?.let {
                    it.periodeListe.all {
                        it.valutakode == "NOK" || it.valutakode.isNullOrEmpty()
                    }
                } != false
        }
}

private fun Stønadstype.tilVisningsnavn() =
    when (this) {
        Stønadstype.BIDRAG -> "Barnebidrag"
        Stønadstype.BIDRAG18AAR -> "18 års bidrag"
        Stønadstype.EKTEFELLEBIDRAG -> "Ektefellebidrag"
        Stønadstype.MOTREGNING -> "Motregning"
        Stønadstype.OPPFOSTRINGSBIDRAG -> "Oppfostringbidrag"
        Stønadstype.FORSKUDD -> "Forskudd"
    }
