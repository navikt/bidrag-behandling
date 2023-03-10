package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.dto.HentPersonResponse
import no.nav.domain.ident.PersonIdent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BidargDataService(
    val bidragPersonConsumer: BidragPersonConsumer,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun hentDialogerForPerson(personIdent: PersonIdent): HentPersonResponse {
        return bidragPersonConsumer.hentPerson(personIdent)
    }
}
