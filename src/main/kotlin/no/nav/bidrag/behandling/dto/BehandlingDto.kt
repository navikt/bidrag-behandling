package no.nav.bidrag.behandling.dto

import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import java.util.Date

data class BehandlingDto(
    val id: Long,
    val behandlingType: BehandlingType,
    val soknadType: SoknadType,
    val datoFom: Date,
    val datoTom: Date,
    val mottatDato: Date,
    val soknadFraType: SoknadFraType,
    val saksnummer: String,
    val behandlerEnhet: String,
    val roller: Set<RolleDto>,
    val virkningsDato: Date? = null,
    val aarsak: ForskuddBeregningKodeAarsakType? = null,
    val avslag: String? = null,
    val begrunnelseMedIVedtakNotat: String? = null,
    val begrunnelseKunINotat: String? = null,
)
