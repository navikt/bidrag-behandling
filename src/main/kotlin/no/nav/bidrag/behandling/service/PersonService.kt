package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.person.PersonDto

fun hentPerson(ident: String?): PersonDto? =
    try {
        ident?.let { AppContext.getBean(BidragPersonConsumer::class.java).hentPerson(ident) }
    } catch (e: Exception) {
        secureLogger.warn(e) { "Feil ved henting av person for ident $ident" }
        null
    }

fun hentPersonFødselsdato(ident: String?) = hentPerson(ident)?.fødselsdato

fun hentPersonVisningsnavn(ident: String?) = hentPerson(ident)?.visningsnavn

fun hentNyesteIdent(ident: String?) = hentPerson(ident)?.ident
