package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.`404`
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.OpplysningerRepository
import org.springframework.stereotype.Service
import java.util.Date
import java.util.Optional

@Service
class OpplysningerService(
    val opplysningerRepository: OpplysningerRepository,
    val behandlingRepository: BehandlingRepository,
) {

    fun opprett(behandlingId: Long, opplysningerType: OpplysningerType, data: String, hentetDato: Date): Opplysninger {
        // oppdater eksisterende opplysninger
        opplysningerRepository.findActiveByBehandlingIdAndType(behandlingId, opplysningerType).ifPresent {
            // update aktiv to false
            updateAktivOpplysninger(it, false)
        }

        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        return opplysningerRepository.save<Opplysninger>(Opplysninger(behandling, true, opplysningerType, data, hentetDato))
    }

    fun updateAktivOpplysninger(opplysninger: Opplysninger, aktiv: Boolean) {
        opplysningerRepository.save(
            opplysninger.copy(
                aktiv = aktiv,
            ),
        )
    }

    fun hentSistAktiv(behandlingId: Long, opplysningerType: OpplysningerType): Optional<Opplysninger> {
        return opplysningerRepository.findActiveByBehandlingIdAndType(behandlingId, opplysningerType)
    }
}
