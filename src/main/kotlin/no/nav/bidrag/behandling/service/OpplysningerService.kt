package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.datamodell.getOrMigrate
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.OpplysningerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

@Service
class OpplysningerService(
    private val opplysningerRepository: OpplysningerRepository,
    private val behandlingRepository: BehandlingRepository,
) {
    @Transactional
    fun opprett(
        behandlingId: Long,
        opplysningerType: OpplysningerType,
        data: String,
        hentetDato: Date,
    ): Opplysninger {
        behandlingRepository
            .findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }
            .let {
                return opplysningerRepository.save<Opplysninger>(
                    Opplysninger(
                        it,
                        opplysningerType.getOrMigrate(),
                        data,
                        hentetDato,
                    ),
                )
            }
    }

    fun hentSistAktiv(
        behandlingId: Long,
        opplysningerType: OpplysningerType,
    ): Opplysninger? =
        opplysningerRepository.findTopByBehandlingIdAndOpplysningerTypeOrderByTsDescIdDesc(
            behandlingId,
            opplysningerType.getOrMigrate()
        )

}
