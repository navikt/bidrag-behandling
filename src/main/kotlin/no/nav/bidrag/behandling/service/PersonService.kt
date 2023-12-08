package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.transport.person.PersonDto

fun hentPerson(ident: String?): PersonDto? =
    try {
        ident?.let { AppContext.getBean(BidragPersonConsumer::class.java).hentPerson(ident) }
    } catch (e: Exception) {
        null
    }

fun hentPersonFødselsdato(ident: String?) = hentPerson(ident)?.fødselsdato

fun hentPersonVisningsnavn(ident: String?) = hentPerson(ident)?.visningsnavn
