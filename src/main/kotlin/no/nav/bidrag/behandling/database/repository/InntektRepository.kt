package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Inntekt
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface InntektRepository : CrudRepository<Inntekt, Long> {
    @Modifying
    @Query("delete from inntekt i where i.behandling.id = :behandlingsid and  i.id in :ids")
    fun sletteInntekterFraBehandling(
        behandlingsid: Long,
        ids: Set<Long>,
    )

    @Modifying
    @Query("delete from inntekt i where i.behandling.id = :behandlingsid and  i.kilde = 'OFFENTLIG'")
    fun sletteOffentligeInntekterFraBehandling(behandlingsid: Long)
}
