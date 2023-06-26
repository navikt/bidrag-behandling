package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.util.Date

@Entity(name = "sivilstand")
data class Sivilstand(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    val gyldigFraOgMed: Date?,

    val datoTom: Date?,

    @Enumerated(EnumType.STRING)
    val sivilstandType: SivilstandType,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
