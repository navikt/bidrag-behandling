package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity(name = "behandling")
data class Behandling(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    val behandlingType: BehandlingType,

    @Enumerated(EnumType.STRING)
    val soknadType: SoknadType,

    val datoFom: Date,

    val datoTom: Date,

    val saksnummer: String,

    val behandlerEnhet: String,
)
