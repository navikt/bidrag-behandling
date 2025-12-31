package no.nav.bidrag.behandling.database.repository

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.bidrag.behandling.database.datamodell.extensions.LasterGrunnlagAsyncStatus
import no.nav.bidrag.behandling.database.datamodell.extensions.LasterGrunnlagDetaljer
import no.nav.bidrag.behandling.transformers.toLocalDateTime
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class CustomBehandlingRepositoryImpl : CustomBehandlingRepository {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun hentLasterGrunnlagStatus(id: Long): LasterGrunnlagDetaljer? =
        try {
            val query =
                entityManager.createNativeQuery(
                    "select hstore_to_json(metadata) ->> 'laster_grunnlag_async_status', hstore_to_json(metadata) ->> 'laster_grunnlag_async_tidspunkt' from behandling b where b.id = :id and b.deleted = false",
                )
            query.setParameter("id", id)
            val result = query.singleResult as Array<Any>
            val statusString = result[0] as String?
            val tidspunktString = result[1] as String?
            LasterGrunnlagDetaljer(
                statusString?.let { LasterGrunnlagAsyncStatus.valueOf(it) } ?: LasterGrunnlagAsyncStatus.FEILET,
                tidspunktString.toLocalDateTime(),
            )
        } catch (e: Exception) {
            null
        }
}
