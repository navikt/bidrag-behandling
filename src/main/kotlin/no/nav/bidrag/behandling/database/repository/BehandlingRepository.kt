package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime
import java.util.Optional

interface BehandlingRepository : CrudRepository<Behandling, Long> {
    fun findBehandlingById(id: Long): Optional<Behandling>

    fun findFirstBySoknadsid(soknadsId: Long): Behandling?

    @Modifying
    @Query("update behandling b set b.grunnlagSistInnhentet = :tidspunktInnhentet where b.id = :behandlingsid")
    fun oppdatereTidspunktGrunnlagsinnhenting(
        behandlingsid: Long,
        tidspunktInnhentet: LocalDateTime = LocalDateTime.now(),
    )
}
