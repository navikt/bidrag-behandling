package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface GrunnlagRepository : CrudRepository<Grunnlag, Long> {
    fun findTopByBehandlingIdAndTypeOrderByInnhentetDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): Grunnlag?

    fun findTopByBehandlingIdAndTypeOrderByAktivDescIdDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): Grunnlag?

    @Modifying
    @Query("update grunnlag g set g.aktiv = :aktiveringstidspunkt where g.id in :iderTilGrunnlagSomSkalAktiveres")
    fun aktivereGrunnlag(
        iderTilGrunnlagSomSkalAktiveres: Set<Long>,
        aktiveringstidspunkt: LocalDateTime,
    )
}
