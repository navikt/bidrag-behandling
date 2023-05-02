package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

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
