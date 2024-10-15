package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.repository.SamværRepository
import org.springframework.stereotype.Service

@Service
class SamværService(
    private val samværRepository: SamværRepository,
) {

    fun oppdaterSamvær(samvær: Samvær) {
        samværRepository.save(samvær)
    }
}
