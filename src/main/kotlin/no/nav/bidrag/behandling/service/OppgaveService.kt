package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.consumer.OppgaveConsumer
import no.nav.bidrag.behandling.consumer.dto.OppgaveSokRequest
import no.nav.bidrag.behandling.consumer.dto.OppgaveType
import no.nav.bidrag.behandling.consumer.dto.OpprettOppgaveRequest
import no.nav.bidrag.behandling.consumer.dto.lagBeskrivelseHeader
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.transformers.vedtak.skyldnerNav
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.stonad.request.HentStønadHistoriskRequest
import no.nav.bidrag.transport.behandling.stonad.response.StønadDto
import no.nav.bidrag.transport.behandling.vedtak.VedtakHendelse
import no.nav.bidrag.transport.behandling.vedtak.behandlingId
import no.nav.bidrag.transport.behandling.vedtak.saksnummer
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger {}
val revurderForskuddBeskrivelse = "Løper forskuddet med riktig sats? Vurder om forskuddet må revurderes."
val enhet_farskap = "4860"

@Component
class OppgaveService(
    private val oppgaveConsumer: OppgaveConsumer,
    private val bidragStønadConsumer: BidragStønadConsumer,
    private val behandlingRepository: BehandlingRepository,
) {
    fun opprettRevurderForskuddOppgave(vedtakHendelse: VedtakHendelse) {
        opprettRevurderForskuddOppgaveBidrag(vedtakHendelse)
        opprettRevurderForskuddOppgaveSærbidrag(vedtakHendelse)
    }

    fun opprettRevurderForskuddOppgaveSærbidrag(vedtakHendelse: VedtakHendelse) {
        val bidragSærbidragListe = vedtakHendelse.engangsbeløpListe!!.filter { it.type == Engangsbeløptype.SÆRBIDRAG }
        if (bidragSærbidragListe.isEmpty()) return

        bidragSærbidragListe
            .forEach { stønad ->
                hentLøpendeForskuddForSak(stønad.sak.verdi, stønad.kravhaver.verdi)?.let {
                    val sistePeriode = it.periodeListe.maxBy { it.periode.fom }
                    if (sistePeriode.periode.til == null) {
                        log.info {
                            "Sak ${stønad.sak.verdi} har løpende forskudd. Opprett revurder forskudd oppgave"
                        }
                        secureLogger.info {
                            "Sak ${stønad.sak.verdi} har løpende forskudd for mottaker ${stønad.mottaker.verdi}. Opprett revurder forskudd oppgave"
                        }
                        vedtakHendelse.opprettRevurderForskuddOppgave(stønad.sak.verdi, stønad.mottaker.verdi)
                        return // Opprett kun en oppgave per sak
                    } else {
                        secureLogger.info {
                            "Sak ${stønad.sak.verdi} har ingen løpende forskudd for mottaker ${stønad.mottaker.verdi} og kravhaver ${stønad.kravhaver.verdi}. Oppretter ikke revurder forskudd oppgave"
                        }
                    }
                } ?: kotlin.run {
                    log.info { "Fant ikke løpende forskudd for sak ${stønad.sak.verdi}. Oppretter ikke revurder forskudd oppgave" }
                    secureLogger.info {
                        "Fant ikke løpende forskudd for sak ${stønad.sak.verdi} og søknadsbarn ${stønad.kravhaver.verdi}. Oppretter ikke revurder forskudd oppgave"
                    }
                }
            }
    }

    fun opprettRevurderForskuddOppgaveBidrag(vedtakHendelse: VedtakHendelse) {
        val bidragStønadsendringer = vedtakHendelse.stønadsendringListe!!.filter { it.type == Stønadstype.BIDRAG }
        if (bidragStønadsendringer.isEmpty()) return

        bidragStønadsendringer
            .forEach { stønad ->
                hentLøpendeForskuddForSak(stønad.sak.verdi, stønad.kravhaver.verdi)?.let {
                    val sistePeriode = it.periodeListe.maxBy { it.periode.fom }
                    if (sistePeriode.periode.til == null) {
                        log.info {
                            "Sak ${stønad.sak.verdi} har løpende forskudd. Opprett revurder forskudd oppgave"
                        }
                        secureLogger.info {
                            "Sak ${stønad.sak.verdi} har løpende forskudd for mottaker ${stønad.mottaker.verdi}. Opprett revurder forskudd oppgave"
                        }
                        vedtakHendelse.opprettRevurderForskuddOppgave(stønad.sak.verdi, stønad.mottaker.verdi)
                        return // Opprett kun en oppgave per sak
                    } else {
                        secureLogger.info {
                            "Sak ${stønad.sak.verdi} har ingen løpende forskudd for mottaker ${stønad.mottaker.verdi} og kravhaver ${stønad.kravhaver.verdi}. Oppretter ikke revurder forskudd oppgave"
                        }
                    }
                } ?: kotlin.run {
                    log.info { "Fant ikke løpende forskudd for sak ${stønad.sak.verdi}. Oppretter ikke revurder forskudd oppgave" }
                    secureLogger.info {
                        "Fant ikke løpende forskudd for sak ${stønad.sak.verdi} og søknadsbarn ${stønad.kravhaver.verdi}. Oppretter ikke revurder forskudd oppgave"
                    }
                }
            }
    }

    fun VedtakHendelse.opprettRevurderForskuddOppgave(
        saksnummer: String,
        mottaker: String,
    ) {
        if (finnesDetRevurderForskuddOppgaveISak(saksnummer, mottaker)) return
        val enhet = finnEnhetsnummer(saksnummer)
        val oppgaveResponse =
            oppgaveConsumer.opprettOppgave(
                OpprettOppgaveRequest(
                    beskrivelse = lagBeskrivelseHeader(opprettetAv, enhet) + revurderForskuddBeskrivelse,
                    oppgavetype = OppgaveType.GEN,
                    tema = if (enhet_farskap == enhet) "FAR" else "BID",
                    saksreferanse = saksnummer,
                    tilordnetRessurs = finnTilordnetRessurs(),
                    tildeltEnhetsnr = enhet,
                    personident = mottaker,
                ),
            )

        log.info { "Opprettet revurder forskudd oppgave ${oppgaveResponse.id} for sak $saksnummer" }
        secureLogger.info { "Opprettet revurder forskudd oppgave $oppgaveResponse for sak $saksnummer og bidragsmottaker $mottaker" }
    }

    fun VedtakHendelse.finnTilordnetRessurs(): String? {
        val vedtakEnhet = enhetsnummer!!.verdi
        if (!vedtakEnhet.erKlageinstans()) return opprettetAv
        return null
    }

    fun VedtakHendelse.finnEnhetsnummer(saksnummer: String): String {
        val vedtakEnhet = enhetsnummer!!.verdi
        if (!vedtakEnhet.erKlageinstans()) return vedtakEnhet
        val behandling = behandlingRepository.findBehandlingById(behandlingId!!).getOrNull() ?: return vedtakEnhet
        return behandling.behandlerEnhet
    }

    fun String.erKlageinstans() = startsWith("42")

    fun VedtakHendelse.finnesDetRevurderForskuddOppgaveISak(
        saksnummer: String,
        mottaker: String,
    ): Boolean {
        val oppgaver =
            oppgaveConsumer.hentOppgave(
                OppgaveSokRequest()
                    .søkForGenerellOppgave()
                    .leggTilSaksreferanse(saksnummer),
            )
        val revurderForskuddOppgave = oppgaver.oppgaver.find { it.beskrivelse!!.contains(revurderForskuddBeskrivelse) }
        if (revurderForskuddOppgave != null) {
            log.info { "Fant revurder forskudd oppgave for sak $saksnummer og bidragsmottaker $mottaker. Oppretter ikke ny oppgave" }
            secureLogger.info {
                "Fant revurder forskudd oppgave $revurderForskuddOppgave for sak $saksnummer og bidragsmottaker $mottaker. Oppretter ikke ny oppgave"
            }
            return true
        }
        return false
    }

    private fun hentLøpendeForskuddForSak(
        saksnummer: String,
        søknadsbarnIdent: String,
    ): StønadDto? =
        bidragStønadConsumer.hentHistoriskeStønader(
            HentStønadHistoriskRequest(
                type = Stønadstype.FORSKUDD,
                sak = Saksnummer(saksnummer),
                skyldner = skyldnerNav,
                kravhaver = Personident(søknadsbarnIdent),
                gyldigTidspunkt = LocalDateTime.now(),
            ),
        )
}
