package no.nav.bidrag.behandling.database.datamodell

import java.util.*
import javax.persistence.*

@Entity(name = "rolle")
data class Rolle(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    val rolleType: RolleType,

    val ident: String,

    val opprettetDato: Date,

    val navn: String
)
