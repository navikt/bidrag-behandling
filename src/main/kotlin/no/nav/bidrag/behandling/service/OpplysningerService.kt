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

    fun opprett(behandlingId: Long, aktiv: Boolean, opplysningerType: OpplysningerType, data: String, hentetDato: Date): Opplysninger {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).orElseThrow { `404`(behandlingId) }
        return opplysningerRepository.save<Opplysninger>(Opplysninger(behandling, aktiv, opplysningerType, data, hentetDato))
    }

    fun settAktiv(opplysningerId: Long, behandlingId: Long) {
        opplysningerRepository.findActiveByBehandlingId(behandlingId).ifPresent {
            // update aktiv to false
            updateAktivOpplysninger(it, false)
        }

        val opp = opplysningerRepository.findById(opplysningerId).orElseThrow { `404`(opplysningerId) }
        // update aktiv to true
        updateAktivOpplysninger(opp, true)
    }

    fun updateAktivOpplysninger(opplysninger: Opplysninger, aktiv: Boolean) {
        opplysningerRepository.save(
            opplysninger.copy(
                aktiv = aktiv,
            ),
        )
    }

    fun hentSistAktiv(behandlingId: Long): Optional<Opplysninger> {
        return opplysningerRepository.findActiveByBehandlingId(behandlingId)
    }
}
