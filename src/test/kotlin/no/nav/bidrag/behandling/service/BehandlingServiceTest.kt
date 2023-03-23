package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.datamodell.UKJENT
import no.nav.bidrag.behandling.dto.CreateRolleDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import java.util.Calendar

class BehandlingServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling`() {
        val createRoller = prepareRoles()
        val behandling = prepareBehandling(createRoller)

        val actualBehandling = behandlingService.createBehandling(behandling)

        assertNotNull(actualBehandling.id)
        assertEquals(BehandlingType.FORSKUDD, actualBehandling.behandlingType)
        assertEquals(3, actualBehandling.roller.size)

        val actualBehandlingFetched = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(BehandlingType.FORSKUDD, actualBehandlingFetched.behandlingType)
        assertEquals(3, actualBehandlingFetched.roller.size)
    }

    private fun prepareRoles(): Set<CreateRolleDto> {
        val createRoller = setOf<CreateRolleDto>(
            CreateRolleDto(RolleType.BIDRAGS_MOTTAKER, "123344", Calendar.getInstance().time),
            CreateRolleDto(RolleType.BIDRAGS_PLIKTIG, "44332211", Calendar.getInstance().time),
            CreateRolleDto(RolleType.BARN, "1111", Calendar.getInstance().time),
        )
        return createRoller
    }

    private fun prepareBehandling(createRoller: Set<CreateRolleDto>): Behandling {
        val behandling = Behandling(
            BehandlingType.FORSKUDD,
            SoknadType.SOKNAD,
            Calendar.getInstance().time,
            Calendar.getInstance().time,
            Calendar.getInstance().time,
            "1234",
            "1234",
            SoknadFraType.BM,
        )
        val roller = HashSet(
            createRoller.map {
                Rolle(
                    behandling,
                    it.rolleType,
                    it.ident ?: UKJENT,
                    it.opprettetDato,
                )
            },
        )

        behandling.roller.addAll(roller)
        return behandling
    }

    @Test
    fun `skal caste 404 exception hvis behandlingen ikke er der`() {
        Assertions.assertThrows(HttpClientErrorException::class.java) {
            behandlingService.hentBehandlingById(1234)
        }
    }

    @Test
    fun `skal caste 404 exception hvis behandlingen ikke er der - oppdater`() {
        Assertions.assertThrows(HttpClientErrorException::class.java) {
            behandlingService.oppdaterBehandling(1234, "New Notat", "Med i Vedtak"/*, Calendar.getInstance().time*/)
        }
    }

    @Test
    fun `skal oppdatere en behandling`() {
        val createRoller = prepareRoles()
        val behandling = prepareBehandling(createRoller)

        val NOTAT = "New Notat"
        val MED_I_VEDTAK = "med i vedtak"

        val createdBehandling = behandlingService.createBehandling(behandling)

        assertNotNull(createdBehandling.id)

        val oppdatertBehandling = behandlingService.oppdaterBehandling(createdBehandling.id!!, NOTAT, MED_I_VEDTAK)

        val hentBehandlingById = behandlingService.hentBehandlingById(createdBehandling.id!!)

        assertEquals(3, hentBehandlingById.roller.size)

        assertEquals(NOTAT, oppdatertBehandling.begrunnelseKunINotat)
        assertEquals(MED_I_VEDTAK, oppdatertBehandling.begrunnelseMedIVedtakNotat)
    }
}
