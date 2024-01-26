package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import org.springframework.data.repository.CrudRepository

interface GrunnlagRepository : CrudRepository<Grunnlag, Long> {
    fun findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): Grunnlag?

    /*
    @Modifying
    @Query(
        "update no.nav.bidrag.behandling.database.datamodell.Grunnlag g " +
            "set g.endringskontrollert = :tidspunktForEndringskontroll where g.id = :id",
    )
    fun oppdatereTidspunktForEndringskontroll(
        id: Long,
        tidspunktForEndringskontroll: LocalDateTime,
    )
     */
}
