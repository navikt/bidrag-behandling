package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import no.nav.bidrag.domene.enums.diverse.InntektBeløpstype
import java.math.BigDecimal
import java.time.LocalDate

@Entity
class Tilleggsstønad(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "underholdskostnad_id", nullable = false)
    open var underholdskostnad: Underholdskostnad,
    open var fom: LocalDate,
    open var tom: LocalDate? = null,
    @Column(name = "dagsats")
    open var beløp: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    open var beløpstype: InntektBeløpstype = if (beløp != null) InntektBeløpstype.DAGSATS else InntektBeløpstype.MÅNEDSBELØP_11_MÅNEDER,
) {
    override fun toString(): String =
        "Tilleggsstønad(id=$id, underholdskostnad=${underholdskostnad.id}, fom=$fom, tom=$tom, dagsats=$`beløp`, beløpstype=$beløpstype)"
}
