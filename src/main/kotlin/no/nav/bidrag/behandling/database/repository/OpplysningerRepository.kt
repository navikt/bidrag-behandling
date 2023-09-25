package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface OpplysningerRepository : CrudRepository<Opplysninger, Long> {
    fun findTopByBehandlingIdAndOpplysningerTypeOrderByTsDescIdDesc(
        behandlingId: Long,
        opplysningerType: OpplysningerType,
    ): Optional<Opplysninger>
}
