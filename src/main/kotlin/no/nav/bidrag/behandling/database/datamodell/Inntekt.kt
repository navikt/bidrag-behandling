package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
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
import jakarta.persistence.OneToMany
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import java.math.BigDecimal
import java.time.LocalDate

@Entity(name = "inntekt")
open class Inntekt(
    @Enumerated(EnumType.STRING)
    @Column(name = "inntektsrapportering")
    open var type: Inntektsrapportering,
    open var belop: BigDecimal,
    open var datoFom: LocalDate?,
    open var datoTom: LocalDate?,
    open val ident: String,
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde,
    open var taMed: Boolean,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "inntekt",
        cascade = [CascadeType.PERSIST, CascadeType.REMOVE],
        orphanRemoval = true,
    )
    open var inntektsposter: MutableSet<Inntektspost> = mutableSetOf(),
    open var gjelderBarn: String? = null,
    open val opprinneligFom: LocalDate? = null,
    open val opprinneligTom: LocalDate? = null,
) {
    val datoFomEllerOpprinneligFom get() = datoFom ?: opprinneligFom

    override fun toString(): String {
        return try {
            "Inntekt($type, beløp=$belop, datoFom=$datoFom, " +
                "datoTom=$datoTom, ident='$ident', gjelderBarn='$gjelderBarn'," +
                "opprinneligFom=$opprinneligFom, opprinneligTom=$opprinneligTom, " +
                " kilde=$kilde, taMed=$taMed, id=$id, behandling=${behandling?.id})"
        } catch (e: Exception) {
            "Inntekt${this.hashCode()}"
        }
    }
}
