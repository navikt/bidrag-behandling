package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface SivilstandRepository : CrudRepository<Sivilstand, Long> {
    @Transactional
    fun deleteByBehandlingId(behandlingsid: Long): Long
}
