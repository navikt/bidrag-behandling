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
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import java.math.BigDecimal

@Entity(name = "inntektspost")
class Inntektspost(
    @Column(name = "belop")
    val beløp: BigDecimal,
    val kode: String,
    val visningsnavn: String,
    @Enumerated(EnumType.STRING)
    val inntektstype: Inntektstype,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inntekt_id", nullable = false)
    val inntekt: Inntekt? = null,
)
