package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import no.nav.bidrag.domene.tid.Datoperiode
import java.math.BigDecimal
import java.time.LocalDate

@Entity(name = "privat_avtale_periode")
open class PrivatAvtalePeriode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privat_avtale_id", nullable = false)
    open var privatAvtale: PrivatAvtale,
    open var fom: LocalDate,
    open var tom: LocalDate?,
    open var beløp: BigDecimal,
) {
    fun copy(
        fom: LocalDate,
        tom: LocalDate?,
    ) = PrivatAvtalePeriode(
        id = id,
        privatAvtale = privatAvtale,
        fom = fom,
        tom = tom,
        beløp = beløp,
    )

    fun tilDatoperiode() = Datoperiode(fom, tom)

    override fun toString(): String = "PrivatAvtalePeriode(id=$id, privatAvtale=${privatAvtale.id}, fom=$fom, tom=$tom, beløp=$beløp)"
}
