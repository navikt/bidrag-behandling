package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface BehandlingRepository : CrudRepository<Behandling, Long> {
    fun findBehandlingById(id: Long): Optional<Behandling>

    @Query("select b from behandling b")
    fun hentBehandlinger(): List<Behandling>

    @Modifying
    @Query(
        "update behandling b set " +
            "b.vedtaksid = :vedtaksid " +
            "where b.id = :behandlingsid",
    )
    fun oppdaterVedtakId(
        behandlingsid: Long,
        vedtaksid: Long,
    )
}
