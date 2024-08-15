package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@JsonIgnoreProperties(value = ["utgift", "id"])
open class Utgiftspost(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "utgiftspost_id_seq")
    @SequenceGenerator(
        name = "utgiftspost_id_seq",
        sequenceName = "utgiftspost_id_seq",
        initialValue = 1,
        allocationSize = 1,
    )
    open var id: Long? = null,
    open var dato: LocalDate,
    open var type: String,
    open var kravbeløp: BigDecimal,
    open var godkjentBeløp: BigDecimal,
    @Column(name = "begrunnelse")
    open var kommentar: String? = null,
    open var betaltAvBp: Boolean = false,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utgift_id", nullable = false)
    open var utgift: Utgift,
) {
    override fun toString(): String =
        "Utgiftspost(id=$id, dato=$dato, type=$type, kravbeløp=$kravbeløp, betaltAvBp=$betaltAvBp" +
            "godkjentBeløp=$godkjentBeløp, kommentar=$kommentar)"
}
