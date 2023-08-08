package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.math.BigDecimal
import java.util.Date

@Entity(name = "utvidetbarnetrygd")
data class Utvidetbarnetrygd(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    val deltBoSted: Boolean,

    val belop: BigDecimal,
    val datoFom: Date?,
    val datoTom: Date?,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
