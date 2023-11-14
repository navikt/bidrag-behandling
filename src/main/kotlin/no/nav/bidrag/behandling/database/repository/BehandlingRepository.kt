package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.Optional

interface BehandlingRepository : CrudRepository<Behandling, Long> {
    fun findBehandlingById(id: Long): Optional<Behandling>

    @Query("select b from behandling b")
    fun hentBehandlinger(): List<Behandling>

    @Transactional
    @Modifying
    @Query(
        "update behandling b set " +
            "b.aarsak = :aarsak," +
            "b.virkningsDato = :virkningsDato," +
            "b.virkningsTidspunktBegrunnelseKunINotat = :virkningsTidspunktBegrunnelseKunINotat," +
            "b.virkningsTidspunktBegrunnelseMedIVedtakNotat = :virkningsTidspunktBegrunnelseMedIVedtakNotat " +
            "where b.id = :behandlingId",
    )
    fun updateVirkningsTidspunkt(
        behandlingId: Long,
        aarsak: ForskuddAarsakType?,
        virkningsDato: Date?,
        virkningsTidspunktBegrunnelseKunINotat: String?,
        virkningsTidspunktBegrunnelseMedIVedtakNotat: String?,
    )

    @Modifying
    @Query(
        "update behandling b set " +
            "b.vedtakId = :vedtakId " +
            "where b.id = :behandlingId",
    )
    fun oppdaterVedtakId(
        behandlingId: Long,
        vedtakId: Long,
    )
}
