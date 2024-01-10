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
class Inntekt(
    @Enumerated(EnumType.STRING)
    val type: Inntektsrapportering,
    val beløp: BigDecimal,
    val datoFom: LocalDate,
    val datoTom: LocalDate?,
    val opprinneligFom: LocalDate?,
    val opprinneligTom: LocalDate?,
    val ident: String,
    val gjelderBarn: String,
    @Enumerated(EnumType.STRING)
    val kilde: Kilde,
    val taMed: Boolean,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling? = null,
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "inntekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    var inntektsposter: MutableSet<Inntektspost> = mutableSetOf(),
)
