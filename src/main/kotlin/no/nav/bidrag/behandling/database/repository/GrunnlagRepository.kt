package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import org.springframework.data.repository.CrudRepository

interface GrunnlagRepository : CrudRepository<Grunnlag, Long> {
    fun findTopByBehandlingIdAndRolleIdAndTypeAndErBearbeidetOrderByInnhentetDesc(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        erBearbeidet: Boolean,
    ): Grunnlag?

    fun findTopByBehandlingIdAndTypeAndErBearbeidetAndRolleOrderByAktivDescIdDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        erBearbeidet: Boolean,
        rolle: Rolle,
    ): Grunnlag?
}
