package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.extensions.LasterGrunnlagDetaljer

interface CustomBehandlingRepository {
    fun hentLasterGrunnlagStatus(id: Long): LasterGrunnlagDetaljer?
}
