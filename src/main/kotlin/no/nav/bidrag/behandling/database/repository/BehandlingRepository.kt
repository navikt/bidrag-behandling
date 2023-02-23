package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface BehandlingRepository : CrudRepository<Behandling, Long> {

    @Query("select d from behandling d where d.id = :id")
    fun hentBehandlingById(id: Long): List<Behandling>
}
