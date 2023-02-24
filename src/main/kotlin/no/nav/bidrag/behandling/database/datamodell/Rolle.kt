package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity(name = "rolle")
data class Rolle(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    val rolleType: RolleType,

    val ident: String,

    val opprettetDato: Date,

    val navn: String,
)
