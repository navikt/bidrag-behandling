package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.getOrMigrate
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class GrunnlagService(
    private val grunnlagRepository: GrunnlagRepository,
    private val behandlingRepository: BehandlingRepository,
) {
    @Transactional
    fun opprett(
        behandlingId: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
        data: String,
        innhentet: LocalDateTime,
    ): BehandlingGrunnlag {
        behandlingRepository
            .findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }
            .let {
                return grunnlagRepository.save<BehandlingGrunnlag>(
                    BehandlingGrunnlag(
                        it,
                        grunnlagsdatatype.getOrMigrate(),
                        data = data,
                        innhentet,
                    ),
                )
            }
    }

    fun hentSistAktiv(
        behandlingsid: Long,
        grunnlagsdatatype: Grunnlagsdatatype,
    ): BehandlingGrunnlag? =
        grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
            behandlingsid,
            grunnlagsdatatype.getOrMigrate(),
        )

    fun hentAlleSistAktiv(behandlingId: Long): List<BehandlingGrunnlag> =
        Grunnlagsdatatype.entries.toTypedArray().mapNotNull {
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                behandlingId,
                it,
            )
        }
}
