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

@Entity(name = "opplysninger")
data class Opplysninger(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    val aktiv: Boolean,

    @Enumerated(EnumType.STRING)
    val opplysningerType: OpplysningerType,

    val data: String,

    val hentetDato: Date,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
