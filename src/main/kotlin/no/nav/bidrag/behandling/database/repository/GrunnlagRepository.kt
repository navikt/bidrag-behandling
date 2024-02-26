package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
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

    fun findTopByBehandlingIdAndTypeAndRolleIdOrderByAktivDescIdDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        rolleid: Long,
    ): Grunnlag?
}
