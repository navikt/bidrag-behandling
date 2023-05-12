package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.ForskuddBeregningKodeAarsakType
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.dto.behandling.CreateRolleDto
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.behandlingbarn.BehandlingBarnDto
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.util.Calendar

class BehandlingServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling`() {
        val actualBehandling = createBehandling()

        assertNotNull(actualBehandling.id)
        assertEquals(BehandlingType.FORSKUDD, actualBehandling.behandlingType)
        assertEquals(3, actualBehandling.roller.size)

        val actualBehandlingFetched = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(BehandlingType.FORSKUDD, actualBehandlingFetched.behandlingType)
        assertEquals(3, actualBehandlingFetched.roller.size)
    }

    companion object {
        fun prepareBehandling(): Behandling {
            val createRoller = prepareRoles()
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
                        it.ident,
                        it.opprettetDato,
                    )
                },
            )

            behandling.roller.addAll(roller)
            return behandling
        }

        fun prepareRoles(): Set<CreateRolleDto> {
            return setOf(
                CreateRolleDto(RolleType.BIDRAGS_MOTTAKER, "123344", Calendar.getInstance().time),
                CreateRolleDto(RolleType.BIDRAGS_PLIKTIG, "44332211", Calendar.getInstance().time),
                CreateRolleDto(RolleType.BARN, "1111", Calendar.getInstance().time),
            )
        }
    }

    @Test
    fun `skal legge til inntekter`() {
        val actualBehandling = createBehandling()

        assertNotNull(actualBehandling.id)

        assertEquals(0, actualBehandling.inntekter.size)
        assertEquals(0, actualBehandling.barnetillegg.size)
        assertEquals(0, actualBehandling.utvidetbarnetrygd.size)

        behandlingService.oppdaterInntekter(
            actualBehandling.id!!,
            mutableSetOf(
                Inntekt(
                    actualBehandling,
                    true,
                    "",
                    BigDecimal.valueOf(1.111),
                    Calendar.getInstance().time,
                    Calendar.getInstance().time,
                    "ident",
                ),
            ),
            mutableSetOf(
                Barnetillegg(
                    actualBehandling,
                    "ident",
                    BigDecimal.ONE,
                    Calendar.getInstance().time,
                    Calendar.getInstance().time,
                ),
            ),
            mutableSetOf(
                Utvidetbarnetrygd(
                    actualBehandling,
                    true,
                    BigDecimal.TEN,
                    Calendar.getInstance().time,
                    Calendar.getInstance().time,
                ),
            ),
        )

        val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(1, expectedBehandling.inntekter.size)
        assertEquals(1, expectedBehandling.barnetillegg.size)
        assertEquals(1, expectedBehandling.utvidetbarnetrygd.size)
    }

    @Test
    fun `skal slette inntekter`() {
        val actualBehandling = createBehandling()

        assertNotNull(actualBehandling.id)

        assertEquals(0, actualBehandling.inntekter.size)
        assertEquals(0, actualBehandling.barnetillegg.size)
        assertEquals(0, actualBehandling.utvidetbarnetrygd.size)

        behandlingService.oppdaterInntekter(
            actualBehandling.id!!,
            mutableSetOf(
                Inntekt(
                    actualBehandling,
                    true,
                    "",
                    BigDecimal.valueOf(1.111),
                    Calendar.getInstance().time,
                    Calendar.getInstance().time,
                    "ident",
                ),
            ),
            mutableSetOf(
                Barnetillegg(
                    actualBehandling,
                    "ident",
                    BigDecimal.ONE,
                    Calendar.getInstance().time,
                    Calendar.getInstance().time,
                ),
            ),
            mutableSetOf(),
        )

        val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(1, expectedBehandling.inntekter.size)
        assertEquals(1, expectedBehandling.barnetillegg.size)

        behandlingService.oppdaterInntekter(actualBehandling.id!!, mutableSetOf(), expectedBehandling.barnetillegg, mutableSetOf())

        val expectedBehandlingWithoutInntekter = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(0, expectedBehandlingWithoutInntekter.inntekter.size)
        assertEquals(1, expectedBehandlingWithoutInntekter.barnetillegg.size)
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
            behandlingService.oppdaterBehandling(1234, "New Notat", "Med i Vedtak")
        }
    }

    @Test
    fun `skal oppdatere en behandling`() {
        val behandling = prepareBehandling()

        val NOTAT = "New Notat"
        val MED_I_VEDTAK = "med i vedtak"

        val createdBehandling = behandlingService.createBehandling(behandling)

        assertNotNull(createdBehandling.id)
        assertNull(createdBehandling.avslag)

        val oppdatertBehandling = behandlingService.oppdaterBehandling(
            createdBehandling.id!!,
            MED_I_VEDTAK,
            NOTAT,
            MED_I_VEDTAK,
            NOTAT,
            MED_I_VEDTAK,
            NOTAT,
            AvslagType.MANGL_DOK,
        )

        val hentBehandlingById = behandlingService.hentBehandlingById(createdBehandling.id!!)

        assertEquals(3, hentBehandlingById.roller.size)
        assertEquals(AvslagType.MANGL_DOK, hentBehandlingById.avslag)

        assertEquals(NOTAT, oppdatertBehandling.virkningsTidspunktBegrunnelseKunINotat)
        assertEquals(MED_I_VEDTAK, oppdatertBehandling.virkningsTidspunktBegrunnelseMedIVedtakNotat)
    }

    @Test
    fun `skal oppdatere virkningstidspunkt data`() {
        val behandling = prepareBehandling()

        val NOTAT = "New Notat"
        val MED_I_VEDTAK = "med i vedtak"

        val createdBehandling = behandlingService.createBehandling(behandling)

        assertNotNull(createdBehandling.id)
        assertNull(createdBehandling.avslag)
        assertNull(createdBehandling.aarsak)

        behandlingService.updateVirkningsTidspunkt(
            createdBehandling.id!!,
            ForskuddBeregningKodeAarsakType.BF,
            AvslagType.BARNS_EKTESKAP,
            null,
            NOTAT,
            MED_I_VEDTAK,
        )

        val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

        assertEquals(ForskuddBeregningKodeAarsakType.BF, updatedBehandling.aarsak)
        assertEquals(AvslagType.BARNS_EKTESKAP, updatedBehandling.avslag)
        assertEquals(NOTAT, updatedBehandling.virkningsTidspunktBegrunnelseKunINotat)
        assertEquals(MED_I_VEDTAK, updatedBehandling.virkningsTidspunktBegrunnelseMedIVedtakNotat)
    }

    @Test
    fun `skal oppdatere boforhold data`() {
        val behandling = prepareBehandling()

        val NOTAT = "New Notat"
        val MED_I_VEDTAK = "med i vedtak"

        val createdBehandling = behandlingService.createBehandling(behandling)

        assertNotNull(createdBehandling.id)
        assertNull(createdBehandling.avslag)
        assertEquals(0, createdBehandling.behandlingBarn.size)
        assertEquals(0, createdBehandling.sivilstand.size)

        val behandlingBarn = setOf(BehandlingBarnDto(null, true, emptySet(), "Manuelt", "ident!"))
        val sivilstand = setOf(
            SivilstandDto(
                null,
                Calendar.getInstance().time.toLocalDate(),
                Calendar.getInstance().time.toLocalDate(),
                SivilstandType.ENKE_ELLER_ENKEMANN,
            ),
        )

        behandlingService.updateBoforhold(
            createdBehandling.id!!,
            behandlingBarn.toDomain(createdBehandling),
            sivilstand.toSivilstandDomain(createdBehandling),
            NOTAT,
            MED_I_VEDTAK,
        )

        val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

        assertEquals(1, updatedBehandling.behandlingBarn.size)
        assertEquals(1, updatedBehandling.sivilstand.size)
        assertEquals(NOTAT, updatedBehandling.boforholdBegrunnelseKunINotat)
        assertEquals(MED_I_VEDTAK, updatedBehandling.boforholdBegrunnelseMedIVedtakNotat)
    }

    fun createBehandling(): Behandling {
        val behandling = prepareBehandling()

        val actualBehandling = behandlingService.createBehandling(behandling)
        return actualBehandling
    }
}
