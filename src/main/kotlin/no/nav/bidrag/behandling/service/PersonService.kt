package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.belopshistorikk.response.SkyldnerStønaderResponse
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.person.PersonDto
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException

fun hentAlleStønaderForBidragspliktig(bp: Personident): SkyldnerStønaderResponse? =
    try {
        AppContext.getBean(BidragBeløpshistorikkConsumer::class.java).hentAlleStønaderForBidragspliktig(bp)
    } catch (e: Exception) {
        secureLogger.debug(e) { "Feil ved henting av beløpshistorikk $bp" }
        null
    }

fun hentVedtak(vedtaksid: Int?): VedtakDto? =
    try {
        vedtaksid.takeIfNotNullOrEmpty {
            AppContext.getBean(BidragVedtakConsumer::class.java).hentVedtak(it)
        }
    } catch (e: Exception) {
        secureLogger.debug(e) { "Feil ved henting av vedtak $vedtaksid" }
        if (e is HttpStatusCodeException) {
            throw e
        }
        null
    }

fun hentPerson(ident: String?): PersonDto? =
    try {
        ident.takeIfNotNullOrEmpty {
            AppContext.getBean(BidragPersonConsumer::class.java).hentPerson(it)
        }
    } catch (e: Exception) {
        secureLogger.debug(e) { "Feil ved henting av person for ident $ident" }
        null
    }

fun hentPersonFødselsdato(ident: String?) = hentPerson(ident)?.fødselsdato

fun hentPersonVisningsnavn(ident: String?) = hentPerson(ident)?.visningsnavn

fun hentNyesteIdent(ident: String?) = ident?.let { hentPerson(ident)?.ident ?: Personident(ident) }

@Service
data class PersonService(
    val personConsumer: BidragPersonConsumer,
) {
    fun hentPerson(ident: String?): PersonDto? =
        try {
            ident.takeIfNotNullOrEmpty {
                personConsumer.hentPerson(it)
            }
        } catch (e: Exception) {
            secureLogger.debug(e) { "Feil ved henting av person for ident $ident" }
            null
        }

    fun hentPersonFødselsdato(ident: String?) = hentPerson(ident)?.fødselsdato

    fun hentPersonVisningsnavn(ident: String?) = hentPerson(ident)?.visningsnavn

    fun hentNyesteIdent(ident: String?) = hentPerson(ident)?.ident
}
