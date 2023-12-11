package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Optional

interface BehandlingRepository : CrudRepository<Behandling, Long> {
    fun findBehandlingById(id: Long): Optional<Behandling>

    @Query("select b from behandling b")
    fun hentBehandlinger(): List<Behandling>

    @Transactional
    @Modifying
    @Query(
        "update behandling b set " +
            "b.aarsak = :årsak," +
            "b.virkningsdato = :virkningsdato," +
            "b.virkningstidspunktbegrunnelseKunINotat = :virkningstidspunktsbegrunnelseKunINotat," +
            "b.virkningstidspunktsbegrunnelseIVedtakOgNotat = :virkningstidspunktsbegrunnelseIVedtakOgNotat " +
            "where b.id = :behandlingsid",
    )
    fun updateVirkningstidspunkt(
        behandlingsid: Long,
        årsak: ForskuddAarsakType?,
        virkningsdato: LocalDate?,
        virkningstidspunktsbegrunnelseKunINotat: String?,
        virkningstidspunktsbegrunnelseIVedtakOgNotat: String?,
    )

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
