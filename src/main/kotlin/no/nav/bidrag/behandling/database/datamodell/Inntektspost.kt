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
import no.nav.bidrag.domene.enums.diverse.InntektBeløpstype
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import java.math.BigDecimal

@Entity(name = "inntektspost")
open class Inntektspost(
    @Column(name = "belop")
    open val beløp: BigDecimal,
    open val kode: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inntekt_id", nullable = false)
    open var inntekt: Inntekt? = null,
    @Enumerated(EnumType.STRING)
    open val inntektstype: Inntektstype?,
    @Enumerated(EnumType.STRING)
    open val beløpstype: InntektBeløpstype? = InntektBeløpstype.ÅRSBELØP,
    open var skattefaktor: BigDecimal? = null,
)
