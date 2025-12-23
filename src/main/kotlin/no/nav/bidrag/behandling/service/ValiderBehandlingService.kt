package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.minified.BehandlingSimple
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningResponse
import no.nav.bidrag.behandling.dto.v2.behandling.tilType
import no.nav.bidrag.behandling.transformers.behandling.kanFatteVedtak
import no.nav.bidrag.behandling.transformers.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.transport.behandling.belopshistorikk.request.HentStønadHistoriskRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.request.LøpendeBidragssakerRequest
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
    private val bidragBeløpshistorikkConsumer: BidragBeløpshistorikkConsumer,
    private val bidragSakConsumer: BidragSakConsumer,
    private val behandlingRepository: BehandlingRepository,
) {
    fun kanBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? {
        val sak = bidragSakConsumer.hentSak(request.saksnummer)
        if (sak.vedtakssperre || UnleashFeatures.VEDTAKSSPERRE.isEnabled) {
            return "Denne saken er midlertidig stengt for vedtak"
        }
        if (request.engangsbeløpstype != null && request.engangsbeløpstype != Engangsbeløptype.SÆRBIDRAG) {
            return "Kan ikke behandle ${request.engangsbeløpstype?.visningsnavn?.intern} i ny løsning"
        }
        return when (request.tilType()) {
            TypeBehandling.SÆRBIDRAG -> kanSærbidragBehandlesINyLøsning(request)
            TypeBehandling.BIDRAG -> kanBidragBehandlesINyLøsning(request)
            else -> null
        }
    }

    private fun kanSærbidragBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? {
        val bpIdent = request.roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }?.ident ?: return "Saken mangler bidragspliktig"
        val løpendeBidrag = bidragBeløpshistorikkConsumer.hentLøpendeBidrag(LøpendeBidragssakerRequest(skyldner = bpIdent))
        val harLøpendeBidragUtenlandskValuta =
            løpendeBidrag.bidragssakerListe.all {
                it.valutakode == "NOK" || it.valutakode.isEmpty()
            }
        return if (!harLøpendeBidragUtenlandskValuta) "Bidragspliktig har løpende bidrag i utenlandsk valuta" else null
    }

    private fun kanBidragBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? = kanBidragV1BehandlesINyLøsning(request)

    private fun kanBidragV1BehandlesINyLøsning(request: KanBehandlesINyLøsningRequest): String? {
        secureLogger.debug { "Sjekker om bidrag kan behandles i ny løsning for request: $request" }
        if (!request.skruddAvManuelt.isNullOrEmpty()) return request.skruddAvManuelt
        if (!bidragStønadstyperSomKanBehandles.contains(request.stønadstype)) {
            return "Kan ikke behandle ${request.stønadstype?.tilVisningsnavn()} gjennom ny løsning"
        }
        val erInnkreving =
            listOf(Behandlingstype.INNKREVINGSGRUNNLAG, Behandlingstype.PRIVAT_AVTALE).contains(request.søknadstype) ||
                request.vedtakstype == Vedtakstype.INNKREVING
        if (request.søknadsbarn.size > 1 && !erInnkreving && !UnleashFeatures.TILGANG_BEHANDLE_BIDRAG_FLERE_BARN.isEnabled) {
            return "Behandlingen har flere enn ett søknadsbarn"
        }

        if (request.søknadstype == Behandlingstype.PRIVAT_AVTALE && !UnleashFeatures.TILGANG_BEHANDLE_INNKREVINGSGRUNNLAG.isEnabled) {
            return "Kan ikke behandle privat avtale"
        }
        if ((request.søknadstype == Behandlingstype.INNKREVINGSGRUNNLAG || request.vedtakstype == Vedtakstype.INNKREVING) &&
            !UnleashFeatures.TILGANG_BEHANDLE_INNKREVINGSGRUNNLAG.isEnabled
        ) {
            return "Kan ikke behandle innkrevingsgrunnlag"
        }
        if ((request.vedtakstype == Vedtakstype.KLAGE || request.harReferanseTilAnnenBehandling) &&
            !UnleashFeatures.BIDRAG_KLAGE.isEnabled
        ) {
            return "Kan ikke behandle klage eller omgjøring"
        }
        if (request.erBegrensetRevurdering() && !UnleashFeatures.BEGRENSET_REVURDERING.isEnabled) {
            return "Kan ikke behandle begrenset revurdering"
        }
        if (!kanBehandleBegrensetRevurdering(request)) {
            return "Kan ikke behandle begrenset revurdering. Minst en løpende forskudd eller bidrag periode har utenlandsk valuta"
        }
        if (request.vedtakstype == Vedtakstype.ALDERSJUSTERING) return null
        val bp = request.bidragspliktig
        if (bp == null || bp.erUkjent == true || bp.ident == null) return "Behandlingen mangler bidragspliktig"
        val bm = request.bidragsmottaker
        if (bm == null || bm.erUkjent == true || bm.ident == null) return "Behandlingen mangler bidragsmottaker"

        val søknadsbarn = request.søknadsbarn.firstOrNull() ?: return "Behandlingen mangler søknadsbarn"
        val harBPStønadForFlereBarn =
            bidragBeløpshistorikkConsumer
                .hentAlleStønaderForBidragspliktig(bp.ident)
                .stønader
                .filter { it.kravhaver.verdi != søknadsbarn.ident?.verdi }
                .any { it.type != Stønadstype.FORSKUDD }
        val kanBehandleInnkreving = erInnkreving && UnleashFeatures.TILGANG_BEHANDLE_INNKREVINGSGRUNNLAG.isEnabled
        val kanBehandleFlereBarn =
            UnleashFeatures.TILGANG_BEHANDLE_BIDRAG_FLERE_BARN.isEnabled && request.vedtakstype != Vedtakstype.KLAGE ||
                request.vedtakstype == Vedtakstype.KLAGE && UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN.isEnabled &&
                UnleashFeatures.TILGANG_BEHANDLE_BIDRAG_FLERE_BARN.isEnabled
        if (harBPStønadForFlereBarn &&
            !(kanBehandleInnkreving || kanBehandleFlereBarn)
        ) {
            return "Bidragspliktig har historiske eller løpende bidrag for flere barn"
        }

        if (request.søktFomDato != null && request.søktFomDato.isBefore(LocalDate.parse("2023-03-01")) &&
            !UnleashFeatures.GRUNNLAGSINNHENTING_FUNKSJONELL_FEIL_TEKNISK.isEnabled
        ) {
            return "Behandlingen er registrert med søkt fra dato før mars 2023"
        }
        return null
    }

    fun validerKanBehandlesIBisys(behandling: BehandlingSimple) {
        if (!behandling.erBidrag()) return

        if (behandling.forholdsmessigFordeling != null) {
            log.debug {
                "Behandling ${behandling.id} kan ikke behandles i Bisys fordi det har blitt opprettet forholdsmessig fordeling"
            }
            throw HttpClientErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "Behandling kan ikke behandles i Bisys",
                commonObjectmapper.writeValueAsBytes(
                    KanBehandlesINyLøsningResponse(listOf("Forholdsmessig fordeling er opprettet i ny løsning")),
                ),
                Charsets.UTF_8,
            )
        }
    }

    fun validerKanFattesINyLøsning(behandling: BehandlingSimple) {
        kanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        if (!behandling.kanFatteVedtak()) {
            throw HttpClientErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "Behandling kan ikke fattes i ny løsning",
                commonObjectmapper.writeValueAsBytes(
                    commonObjectmapper.writeValueAsBytes(KanBehandlesINyLøsningResponse(emptyList())),
                ),
                Charsets.UTF_8,
            )
        }
    }

    fun validerKanBehandlesINyLøsning(request: KanBehandlesINyLøsningRequest) {
        val resultat = kanBehandlesINyLøsning(request)
        if (resultat != null) {
            secureLogger.debug {
                "Behandling engangsbeløpstype=${request.engangsbeløpstype}, stønadstype=${request.stønadstype} kan ikke behandles i ny løsning: $request med begrunnelse $resultat"
            }
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
        this.søknadstype == Behandlingstype.BEGRENSET_REVURDERING ||
            this.søknadstype == Behandlingstype.PARAGRAF_35_C_BEGRENSET_SATS ||
            this.søknadstype == Behandlingstype.KLAGE_BEGRENSET_SATS ||
            this.søknadstype == Behandlingstype.OMGJØRING_BEGRENSET_SATS ||
            this.søknadstype == Behandlingstype.REVURDERING

    private fun harIngenHistoriskePerioderMedUtenlandskValuta(
        request: KanBehandlesINyLøsningRequest,
        stønadstype: Stønadstype,
    ): Boolean =
        request.søknadsbarn.filter { it.ident != null }.all {
            bidragBeløpshistorikkConsumer
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
