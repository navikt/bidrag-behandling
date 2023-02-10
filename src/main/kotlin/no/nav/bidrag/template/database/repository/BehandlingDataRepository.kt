package no.nav.bidrag.template.database.repository

import no.nav.bidrag.template.database.datamodell.BehandlingData
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface BehandlingDataRepository : CrudRepository<BehandlingData, Long> {

    @Query("select d from behandling_data d where d.id = :id")
    fun hentBidrgaDataById(id: Long): List<BehandlingData>
}
