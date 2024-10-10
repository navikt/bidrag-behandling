package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.person.PersonDto
import org.springframework.stereotype.Service

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

fun hentNyesteIdent(ident: String?) = hentPerson(ident)?.ident

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
