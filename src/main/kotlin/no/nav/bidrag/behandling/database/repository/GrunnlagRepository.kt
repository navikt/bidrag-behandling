package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import org.springframework.data.repository.CrudRepository

interface GrunnlagRepository : CrudRepository<Grunnlag, Long> {
    fun findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
        behandlingId: Long,
        grunnlagstype: Grunnlagstype,
    ): Grunnlag?
}
