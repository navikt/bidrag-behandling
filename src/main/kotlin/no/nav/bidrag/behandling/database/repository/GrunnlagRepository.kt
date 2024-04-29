package no.nav.bidrag.behandling.database.repository

import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface GrunnlagRepository : CrudRepository<Grunnlag, Long> {
    fun findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        erBearbeidet: Boolean,
    ): Grunnlag?
}
