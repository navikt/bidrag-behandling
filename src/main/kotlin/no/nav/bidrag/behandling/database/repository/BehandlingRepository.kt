package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface BehandlingRepository : CrudRepository<Behandling, Long> {

    fun findBehandlingById(id: Long): Behandling

    @Query("select b from behandling b")
    fun hentBehandlinger(): List<Behandling>
}
