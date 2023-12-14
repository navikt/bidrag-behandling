package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface HusstandsbarnRepository : CrudRepository<Husstandsbarn, Long> {
    @Transactional
    fun deleteByBehandlingId(behandlingsid: Long): Long
}
