package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.math.BigDecimal
import java.time.LocalDate

@Entity
class FaktiskTilsynsutgift(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "underholdskostnad_id", nullable = false)
    open val underholdskostnad: Underholdskostnad,
    open val fom: LocalDate,
    open val tom: LocalDate? = null,
    open val tilsynsutgift: BigDecimal,
    open val kostpenger: BigDecimal? = null,
    open val kommentar: String? = null,
)
