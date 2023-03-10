package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.dto.CreateRolleDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.Calendar

class BehandlingServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling`() {
        val createRoller = setOf<CreateRolleDto>(
            CreateRolleDto(RolleType.BIDRAGS_MOTTAKER, "123344", Calendar.getInstance().time),
            CreateRolleDto(RolleType.BIDRAGS_PLIKTIG, "44332211", Calendar.getInstance().time),
            CreateRolleDto(RolleType.BARN, "1111", Calendar.getInstance().time),
        )

        val behandling = Behandling(
            BehandlingType.FORSKUDD,
            SoknadType.SOKNAD,
            Calendar.getInstance().time,
            Calendar.getInstance().time,
            "1234",
            "1234",
        )
        val roller = HashSet(
            createRoller.map {
                Rolle(
                    behandling,
                    it.rolleType,
                    it.ident,
                    it.opprettetDato,
                )
            },
        )

        behandling.roller.addAll(roller)

        val actualBehandling = behandlingService.createBehandling(behandling)
        assertNotNull(actualBehandling.id)
        assertEquals(BehandlingType.FORSKUDD, actualBehandling.behandlingType)
        assertEquals(3, actualBehandling.roller.size)
    }
}
