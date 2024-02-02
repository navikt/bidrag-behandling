package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import org.springframework.data.repository.CrudRepository

interface GrunnlagRepository : CrudRepository<BehandlingGrunnlag, Long> {
    fun findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): BehandlingGrunnlag?
}
