package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Rolle
import org.springframework.data.repository.CrudRepository

interface GrunnlagRepository : CrudRepository<Grunnlag, Long> {
    fun findTopByBehandlingIdAndRolleIdAndTypeOrderByInnhentetDesc(
        behandlingsid: Long,
        rolleid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): Grunnlag?

    fun findTopByBehandlingIdAndTypeOrderByAktivDescIdDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): Grunnlag?

    fun findTopByBehandlingIdAndTypeAndRolleOrderByAktivDescIdDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        rolle: Rolle,
    ): Grunnlag?
}
