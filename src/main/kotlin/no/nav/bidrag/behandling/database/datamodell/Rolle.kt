package no.nav.bidrag.behandling.database.datamodell

import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity(name = "rolle")
data class Rolle(
    @ManyToOne(cascade = [CascadeType.ALL])
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    @Enumerated(EnumType.STRING)
    val rolleType: RolleType,

    val ident: String,

    val opprettetDato: LocalDateTime,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
