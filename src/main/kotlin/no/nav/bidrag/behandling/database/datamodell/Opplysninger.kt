package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity(name = "opplysninger")
data class Opplysninger(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    val aktiv: Boolean,

    @Enumerated(EnumType.STRING)
    val opplysningerType: OpplysningerType,

    @Column(name = "`data`")
    val data: String,

    val hentetDato: Date,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
