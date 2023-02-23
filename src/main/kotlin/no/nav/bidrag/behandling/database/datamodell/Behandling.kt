package no.nav.bidrag.behandling.database.datamodell

import java.util.*
import javax.persistence.*

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

    val behandlerEnhet: String
)
