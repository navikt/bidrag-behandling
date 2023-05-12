package no.nav.bidrag.behandling.database.datamodell

import java.math.BigDecimal
import java.util.Date
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity(name = "barnetillegg")
data class Barnetillegg(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    val ident: String,
    val barnetillegg: BigDecimal,
    val datoFom: Date,
    val datoTom: Date,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
