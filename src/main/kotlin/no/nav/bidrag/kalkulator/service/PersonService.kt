package no.nav.bidrag.kalkulator.service

import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.kalkulator.consumer.BidragPersonConsumer
import no.nav.bidrag.transport.person.MotpartBarnRelasjonDto
import no.nav.bidrag.transport.person.PersonDto
import org.springframework.stereotype.Service

fun <T, R> T?.takeIfNotNullOrEmpty(block: (T) -> R): R? = if (this == null || this is String && this.trim().isEmpty()) null else block(this)

fun hentPerson(ident: String?): PersonDto? =
    try {
        ident.takeIfNotNullOrEmpty {
            AppContext.getBean(BidragPersonConsumer::class.java).hentPerson(it)
        }
    } catch (e: Exception) {
        secureLogger.debug(e) { "Feil ved henting av person for ident $ident" }
        null
    }

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

    fun hentFamilie(ident: String?): MotpartBarnRelasjonDto? =
        try {
            ident.takeIfNotNullOrEmpty {
                personConsumer.hentFamilie(it)
            }
        } catch (e: Exception) {
            secureLogger.debug(e) { "Feil ved henting av familie for ident $ident" }
            null
        }
}
