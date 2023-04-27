package no.nav.bidrag.template.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

//@Entity(name = "behandling_data")
data class BehandlingData(
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val fri_text: String ? = null
)
