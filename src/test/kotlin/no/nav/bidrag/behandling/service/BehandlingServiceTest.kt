package no.nav.bidrag.behandling.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.behandling.CreateRolleDto
import no.nav.bidrag.behandling.dto.behandling.CreateRolleRolleType
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnDto
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import no.nav.bidrag.domain.enums.Rolletype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date

class BehandlingServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @PersistenceContext
    lateinit var entityManager: EntityManager

    @Test
    fun `skal opprette en behandling`() {
        val actualBehandling = createBehandling()

        assertNotNull(actualBehandling.id)
        assertEquals(BehandlingType.FORSKUDD, actualBehandling.behandlingType)
        assertEquals(3, actualBehandling.roller.size)

        val actualBehandlingFetched = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(BehandlingType.FORSKUDD, actualBehandlingFetched.behandlingType)
        assertEquals(3, actualBehandlingFetched.roller.size)
        assertNotNull(actualBehandlingFetched.roller.iterator().next().fodtDato)
    }

    @Test
    fun `delete behandling`() {
        val behandling = createBehandling()
        behandlingService.deleteBehandlingById(behandling.id!!)

        Assertions.assertThrows(HttpClientErrorException::class.java) {
            behandlingService.hentBehandlingById(behandling.id!!)
        }
    }

    @Test
    fun `legge til flere roller`() {
        val b = createBehandling()

        behandlingService.syncRoller(
            b.id!!,
            listOf(
                CreateRolleDto(
                    CreateRolleRolleType.BARN,
                    "newident",
                    Date(1),
                    Date(2),
                ),
            ),
        )

        assertEquals(4, behandlingService.hentBehandlingById(b.id!!).roller.size)
    }

    @Test
    fun `behandling må synce roller og slette behandling`() {
        val b = createBehandling()
        behandlingService.syncRoller(
            b.id!!,
            listOf(
                CreateRolleDto(CreateRolleRolleType.BARN, "1111", Date(1), Date(2), true),
            ),
        )

        Assertions.assertThrows(HttpClientErrorException::class.java) {
            behandlingService.hentBehandlingById(b.id!!)
        }
    }

    @Test
    fun `behandling må synce roller`() {
        val b = createBehandling()
        behandlingService.syncRoller(
            b.id!!,
            listOf(
                CreateRolleDto(CreateRolleRolleType.BARN, "1111", Date(1), Date(2), true),
                CreateRolleDto(CreateRolleRolleType.BARN, "111123", Date(1), Date(2)),
                CreateRolleDto(CreateRolleRolleType.BARN, "1111234", Date(1), Date(2)),
            ),
        )

        assertEquals(2, behandlingService.hentBehandlingById(b.id!!).getSøknadsBarn().size)
    }

    @Test
    fun `delete behandling rolle`() {
        val behandling = createBehandling()

        assertEquals(3, behandling.roller.size)
        behandling.roller.removeIf { it.rolleType == Rolletype.BARN }

        behandlingRepository.save(behandling)

        val updatedBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        assertEquals(2, updatedBehandling.roller.size)

        val realCount =
            entityManager.createNativeQuery("select count(*) from rolle r where r.behandling_id = " + behandling.id!!).getSingleResult()

        val deletedCount =
            entityManager.createNativeQuery(
                "select count(*) from rolle r where r.behandling_id = " + behandling.id!! + " and r.deleted = true",
            ).getSingleResult()

        assertEquals(3L, realCount)
        assertEquals(1L, deletedCount)
    }

    @Test
    fun `skal opprette en behandling med grunnlagspakkeId`() {
        val b = createBehandling()

        behandlingService.updateBehandling(b.id!!, 123L)

        assertEquals(123L, behandlingService.hentBehandlingById(b.id!!).grunnlagspakkeId)
    }

    @Test
    fun `skal opprette en behandling med inntekter`() {
        val behandling = prepareBehandling()

        behandling.inntekter = mutableSetOf(Inntekt(behandling, true, "", BigDecimal.valueOf(555.55), null, null, "ident", true))

        val actualBehandling = behandlingService.createBehandling(behandling)

        assertNotNull(actualBehandling.id)

        val actualBehandlingFetched = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(BehandlingType.FORSKUDD, actualBehandlingFetched.behandlingType)
        assertEquals(1, actualBehandlingFetched.inntekter.size)
        assertEquals(BigDecimal.valueOf(555.55), actualBehandlingFetched.inntekter.iterator().next().belop)
    }

    companion object {
        fun prepareBehandling(): Behandling {
            val behandling =
                Behandling(
                    BehandlingType.FORSKUDD,
                    SoknadType.FASTSETTELSE,
                    Calendar.getInstance().time,
                    Calendar.getInstance().time,
                    Calendar.getInstance().time,
                    "1234",
                    123213L,
                    null,
                    "1234",
                    SoknadFraType.BIDRAGSMOTTAKER,
                    null,
                    null,
                )
            val createRoller = prepareRoles(behandling)
            val roller =
                HashSet(
                    createRoller.map {
                        Rolle(
                            behandling,
                            it.rolleType,
                            it.ident,
                            it.fodtDato,
                            it.opprettetDato,
                        )
                    },
                )

            behandling.roller.addAll(roller)
            return behandling
        }

        fun prepareRoles(behandling: Behandling): Set<Rolle> {
            val someDate = Calendar.getInstance().time
            return setOf(
                Rolle(behandling, Rolletype.BIDRAGSMOTTAKER, "123344", someDate, someDate),
                Rolle(behandling, Rolletype.BIDRAGSPLIKTIG, "44332211", someDate, someDate),
                Rolle(behandling, Rolletype.BARN, "1111", someDate, someDate),
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
        assertNull(actualBehandling.inntektBegrunnelseMedIVedtakNotat)
        assertNull(actualBehandling.inntektBegrunnelseKunINotat)

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
                    true,
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
            "Med i Vedtaket",
            "Kun i Notat",
        )

        val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(1, expectedBehandling.inntekter.size)
        assertEquals(1, expectedBehandling.barnetillegg.size)
        assertEquals(1, expectedBehandling.utvidetbarnetrygd.size)
        assertEquals("Med i Vedtaket", expectedBehandling.inntektBegrunnelseMedIVedtakNotat)
        assertEquals("Kun i Notat", expectedBehandling.inntektBegrunnelseKunINotat)
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
                    true,
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
            "null",
            "null",
        )

        val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(1, expectedBehandling.inntekter.size)
        assertEquals(1, expectedBehandling.barnetillegg.size)
        assertNotNull(expectedBehandling.inntektBegrunnelseMedIVedtakNotat)
        assertNotNull(expectedBehandling.inntektBegrunnelseKunINotat)

        behandlingService.oppdaterInntekter(
            actualBehandling.id!!,
            mutableSetOf(),
            expectedBehandling.barnetillegg,
            mutableSetOf(),
            null,
            null,
        )

        val expectedBehandlingWithoutInntekter = behandlingService.hentBehandlingById(actualBehandling.id!!)

        assertEquals(0, expectedBehandlingWithoutInntekter.inntekter.size)
        assertEquals(1, expectedBehandlingWithoutInntekter.barnetillegg.size)
        assertNull(expectedBehandlingWithoutInntekter.inntektBegrunnelseMedIVedtakNotat)
        assertNull(expectedBehandlingWithoutInntekter.inntektBegrunnelseKunINotat)
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
        assertNull(createdBehandling.aarsak)

        val oppdatertBehandling =
            behandlingService.oppdaterBehandling(
                createdBehandling.id!!,
                MED_I_VEDTAK,
                NOTAT,
                MED_I_VEDTAK,
                NOTAT,
                MED_I_VEDTAK,
                NOTAT,
            )

        val hentBehandlingById = behandlingService.hentBehandlingById(createdBehandling.id!!)

        assertEquals(3, hentBehandlingById.roller.size)
//        assertEquals(AvslagType.MANGL_DOK, hentBehandlingById.avslag)

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
        assertNull(createdBehandling.aarsak)

        behandlingService.updateVirkningsTidspunkt(
            createdBehandling.id!!,
            ForskuddAarsakType.BF,
            null,
            NOTAT,
            MED_I_VEDTAK,
        )

        val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

        assertEquals(ForskuddAarsakType.BF, updatedBehandling.aarsak)
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
        assertNull(createdBehandling.aarsak)
        assertEquals(0, createdBehandling.husstandsBarn.size)
        assertEquals(0, createdBehandling.sivilstand.size)

        val husstandsBarn = setOf(HusstandsBarnDto(null, true, emptySet(), "Manuelt", "ident!"))
        val sivilstand =
            setOf(
                SivilstandDto(
                    null,
                    Calendar.getInstance().time.toLocalDate(),
                    Calendar.getInstance().time.toLocalDate(),
                    SivilstandType.ENKE_ELLER_ENKEMANN,
                ),
            )

        behandlingService.updateBoforhold(
            createdBehandling.id!!,
            husstandsBarn.toDomain(createdBehandling),
            sivilstand.toSivilstandDomain(createdBehandling),
            NOTAT,
            MED_I_VEDTAK,
        )

        val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

        assertEquals(1, updatedBehandling.husstandsBarn.size)
        assertEquals(1, updatedBehandling.sivilstand.size)
        assertEquals(NOTAT, updatedBehandling.boforholdBegrunnelseKunINotat)
        assertEquals(MED_I_VEDTAK, updatedBehandling.boforholdBegrunnelseMedIVedtakNotat)
    }

    fun createBehandling(): Behandling {
        val behandling = prepareBehandling()

        return behandlingService.createBehandling(behandling)
    }
}
