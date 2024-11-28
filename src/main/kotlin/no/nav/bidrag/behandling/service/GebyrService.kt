package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterGebyrResponsDto
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterManueltGebyrDto
import no.nav.bidrag.behandling.ugyldigForespørsel

class GebyrService(
    private val behandlingService: BehandlingService,
) {
    fun oppdaterManueltGebyr(
        behandlingsId: Long,
        request: OppdaterManueltGebyrDto,
    ): OppdaterGebyrResponsDto {
        val behandling = behandlingService.hentBehandlingById(behandlingsId)
        val rolle = behandling.roller.find { it.id == request.rolleId } ?: ugyldigForespørsel("Fant ikke rolle ${request.rolleId}")
        if (request.overstyrtGebyr != null) {
            val manueltOverstyrtGebyr = rolle.manueltOverstyrtGebyr ?: RolleManueltOverstyrtGebyr(true)
        }
        return OppdaterGebyrResponsDto(behandlingsId, gebyr)
    }
}
