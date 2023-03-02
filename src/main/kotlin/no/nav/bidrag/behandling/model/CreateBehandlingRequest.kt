package no.nav.bidrag.behandling.model

import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import java.util.Date

data class CreateBehandlingRequest(
    val behandlingType: BehandlingType,
    val soknadType: SoknadType,
    val datoFom: Date,
    val datoTom: Date,
    val saksnummer: String,
    val behandlerEnhet: String,
    val rolle: Rolle,
)
