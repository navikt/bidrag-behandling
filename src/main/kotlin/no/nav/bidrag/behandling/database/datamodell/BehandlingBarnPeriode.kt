package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity(name = "behandling_barn_periode")
data class BehandlingBarnPeriode(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_barn_id", nullable = false)
    val behandlingBarn: BehandlingBarn,

    val fraDato: Date,

    val tilDato: Date,

    @Enumerated(EnumType.STRING)
    val boStatus: BoStatusType,

    val kilde: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
