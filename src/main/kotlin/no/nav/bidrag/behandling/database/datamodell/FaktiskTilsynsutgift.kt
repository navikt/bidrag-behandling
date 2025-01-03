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
open class FaktiskTilsynsutgift(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "underholdskostnad_id", nullable = false)
    open val underholdskostnad: Underholdskostnad,
    open var fom: LocalDate,
    open var tom: LocalDate? = null,
    open var tilsynsutgift: BigDecimal,
    open var kostpenger: BigDecimal? = null,
    open var kommentar: String? = null,
) {
    override fun toString(): String =
        "FaktiskTilsynsutgift(id=$id, underholdskostnad=${underholdskostnad.id}, fom=$fom, tom=$tom, tilsynsutgift=$tilsynsutgift, kostpenger=$kostpenger, kommentar=$kommentar)"
}
