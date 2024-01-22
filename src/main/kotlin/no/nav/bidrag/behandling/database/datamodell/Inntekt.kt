package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
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
    open val inntektsrapportering: Inntektsrapportering,
    open val belop: BigDecimal,
    open val datoFom: LocalDate,
    open val datoTom: LocalDate?,
    open val ident: String,
    @Enumerated(EnumType.STRING)
    open val kilde: Kilde,
    open val taMed: Boolean,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling? = null,
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "inntekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var inntektsposter: MutableSet<Inntektspost> = mutableSetOf(),
    open val gjelderBarn: String? = null,
    open val opprinneligFom: LocalDate? = null,
    open val opprinneligTom: LocalDate? = null,
)
