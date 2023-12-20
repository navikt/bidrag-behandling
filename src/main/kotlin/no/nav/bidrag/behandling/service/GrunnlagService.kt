package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import no.nav.bidrag.behandling.database.datamodell.Jsonb
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
        grunnlagstype: Grunnlagstype,
        data: String,
        innhentet: LocalDateTime,
    ): Grunnlag {
        behandlingRepository
            .findBehandlingById(behandlingId)
            .orElseThrow { behandlingNotFoundException(behandlingId) }
            .let {
                return grunnlagRepository.save<Grunnlag>(
                    Grunnlag(
                        it,
                        grunnlagstype.getOrMigrate(),
                        data =  Jsonb(data),
                        innhentet,
                    ),
                )
            }
    }

    fun hentSistAktiv(
        behandlingsid: Long,
        grunnlagstype: Grunnlagstype,
    ): Grunnlag? =
        grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
            behandlingsid,
            grunnlagstype.getOrMigrate(),
        )
}
