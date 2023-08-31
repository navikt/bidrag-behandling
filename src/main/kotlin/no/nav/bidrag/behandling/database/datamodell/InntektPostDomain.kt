package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.math.BigDecimal

@Entity(name = "inntekt_post")
data class InntektPostDomain(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inntekt_id", nullable = false)
    val inntekt: Inntekt,

    @Column(name = "belop")
    val beløp: BigDecimal,

    val kode: String,

    val visningsnavn: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
