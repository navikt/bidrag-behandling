package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.`404`
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.OpplysningerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.Optional

@Service
class OpplysningerService(
    private val opplysningerRepository: OpplysningerRepository,
    private val behandlingRepository: BehandlingRepository,
) {

    @Transactional
    fun opprett(behandlingId: Long, opplysningerType: OpplysningerType, data: String, hentetDato: Date): Opplysninger {
        opplysningerRepository.deactivateOpplysninger(behandlingId, opplysningerType)

        behandlingRepository
            .findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
            .let {
                return opplysningerRepository.save<Opplysninger>(Opplysninger(it, true, opplysningerType, data, hentetDato))
            }
    }

    fun hentSistAktiv(behandlingId: Long, opplysningerType: OpplysningerType): Optional<Opplysninger> =
        opplysningerRepository.findActiveByBehandlingIdAndType(behandlingId, opplysningerType)
}
