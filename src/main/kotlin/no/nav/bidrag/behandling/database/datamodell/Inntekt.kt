package no.nav.bidrag.behandling.database.datamodell

import java.math.BigDecimal
import java.util.Date
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity(name = "inntekt")
data class Inntekt(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    val taMed: Boolean,
    val beskrivelse: String,
    @Suppress("NonAsciiCharacters")
    @Column(name = "BELOP")
    val beløp: BigDecimal,
    val datoTom: Date,
    val datoFom: Date,
    val ident: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
