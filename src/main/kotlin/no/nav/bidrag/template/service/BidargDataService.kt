package no.nav.bidrag.template.service

import no.nav.bidrag.template.consumer.BidragPersonConsumer
import no.nav.bidrag.template.database.repository.BehandlingDataRepository
import no.nav.bidrag.template.model.HentPersonResponse
import no.nav.domain.ident.PersonIdent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BidargDataService(
    val bidragPersonConsumer: BidragPersonConsumer,
    val behandlingDataRepository: BehandlingDataRepository
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun hentDialogerForPerson(personIdent: PersonIdent): HentPersonResponse {
        behandlingDataRepository.hentBidrgaDataById(1)

        return bidragPersonConsumer.hentPerson(personIdent)
    }
}
