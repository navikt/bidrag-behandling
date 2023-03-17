package no.nav.bidrag.behandling.dto

import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import java.util.Date

data class CreateBehandlingRequest(
    val behandlingType: BehandlingType,
    val soknadType: SoknadType,
    val datoFom: Date,
    val datoTom: Date,
    val mottatDato: Date,
    val soknadFra: SoknadFraType,
    val saksnummer: String,
    val behandlerEnhet: String,
    val roller: Set<CreateRolleDto>,
)
