package no.nav.bidrag.template.database.repository

import no.nav.bidrag.template.database.datamodell.BehandlingData

interface BehandlingDataRepository {

//    @Query("select d from behandling_data d where d.id = :id")
    fun hentBidrgaDataById(id: Long): List<BehandlingData>
}
