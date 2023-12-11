package no.nav.bidrag.behandling.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.ForskuddAarsakType
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.behandling.CreateRolleDto
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.transformers.toDomain
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.transformers.toSivilstandDomain
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

class BehandlingServiceTest : TestContainerRunner() {
    @MockBean
    lateinit var forsendelseService: ForsendelseService

    @Autowired
    lateinit var behandlingService: BehandlingService

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @PersistenceContext
    lateinit var entityManager: EntityManager

    @Nested
    open inner class HenteBehandling {
        @Test
        fun `skal kaste 404 exception hvis behandlingen ikke er der`() {
            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.hentBehandlingById(1234)
            }
        }
    }

    @Nested
    open inner class OppretteBehandling {
        @Test
        fun `skal opprette en forskuddsbehandling`() {
            val actualBehandling = createBehandling()

            assertNotNull(actualBehandling.id)
            assertEquals(Behandlingstype.FORSKUDD, actualBehandling.behandlingstype)
            assertEquals(3, actualBehandling.roller.size)

            val actualBehandlingFetched =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(Behandlingstype.FORSKUDD, actualBehandlingFetched.behandlingstype)
            assertEquals(3, actualBehandlingFetched.roller.size)
            assertNotNull(actualBehandlingFetched.roller.iterator().next().foedselsdato)
        }

        @Test
        fun `skal opprette en behandling med inntekter`() {
            val behandling = prepareBehandling()

            behandling.inntekter =
                mutableSetOf(
                    Inntekt(
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
                        BigDecimal.valueOf(555.55),
                        LocalDate.now().minusMonths(4),
                        null,
                        "ident",
                        true,
                        true,
                        behandling = behandling,
                    ),
                )

            val actualBehandling = behandlingService.createBehandling(behandling)

            assertNotNull(actualBehandling.id)

            val actualBehandlingFetched =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(Behandlingstype.FORSKUDD, actualBehandlingFetched.behandlingstype)
            assertEquals(1, actualBehandlingFetched.inntekter.size)
            assertEquals(
                BigDecimal.valueOf(555.55),
                actualBehandlingFetched.inntekter.iterator().next().belop,
            )
        }

        @Test
        fun `skal oppdatere boforhold data`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"
            val medIVedtak = "med i vedtak"

            val createdBehandling = behandlingService.createBehandling(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.aarsak)
            assertEquals(0, createdBehandling.husstandsbarn.size)
            assertEquals(0, createdBehandling.sivilstand.size)

            val husstandsBarn =
                setOf(
                    HusstandsbarnDto(
                        null,
                        true,
                        emptySet(),
                        ident = "Manuelt",
                        navn = "ident!",
                        fødselsdato = LocalDate.now().minusMonths(156),
                    ),
                )
            val sivilstand =
                setOf(
                    SivilstandDto(
                        null,
                        Calendar.getInstance().time.toLocalDate(),
                        Calendar.getInstance().time.toLocalDate(),
                        Sivilstandskode.BOR_ALENE_MED_BARN,
                        Kilde.OFFENTLIG,
                    ),
                )

            behandlingService.updateBoforhold(
                createdBehandling.id!!,
                husstandsBarn.toDomain(createdBehandling),
                sivilstand.toSivilstandDomain(createdBehandling),
                notat,
                medIVedtak,
            )

            val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(1, updatedBehandling.husstandsbarn.size)
            assertEquals(1, updatedBehandling.sivilstand.size)
            assertEquals(notat, updatedBehandling.boforholdsbegrunnelseKunINotat)
            assertEquals(medIVedtak, updatedBehandling.boforholdsbegrunnelseIVedtakOgNotat)
        }

        @Test
        fun `skal oppdatere virkningstidspunkt data`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"
            val medIVedtak = "med i vedtak"

            val createdBehandling = behandlingService.createBehandling(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.aarsak)

            behandlingService.oppdatereVirkningstidspunkt(
                createdBehandling.id!!,
                ForskuddAarsakType.BF,
                null,
                notat,
                medIVedtak,
            )

            val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(ForskuddAarsakType.BF, updatedBehandling.aarsak)
            assertEquals(notat, updatedBehandling.virkningstidspunktbegrunnelseKunINotat)
            assertEquals(
                medIVedtak,
                updatedBehandling.virkningstidspunktsbegrunnelseIVedtakOgNotat,
            )
        }
    }

    @Nested
    open inner class SletteBehandling {
        @Test
        fun `delete behandling`() {
            val behandling = createBehandling()
            behandlingService.deleteBehandlingById(behandling.id!!)

            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.hentBehandlingById(behandling.id!!)
            }
        }
    }

    @Nested
    open inner class SynkronisereRoller {
        @Test
        fun `legge til flere roller`() {
            val b = createBehandling()

            behandlingService.syncRoller(
                b.id!!,
                listOf(
                    CreateRolleDto(
                        Rolletype.BARN,
                        "newident",
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                        opprettetdato = LocalDate.now().minusMonths(4),
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
                    CreateRolleDto(
                        Rolletype.BARN,
                        "1111",
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                        opprettetdato = LocalDate.now().minusMonths(4),
                        true,
                    ),
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
                    CreateRolleDto(
                        Rolletype.BARN,
                        "1111",
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                        opprettetdato = LocalDate.now().minusMonths(4),
                        true,
                    ),
                    CreateRolleDto(
                        Rolletype.BARN,
                        "111123",
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                        opprettetdato = LocalDate.now().minusMonths(4),
                    ),
                    CreateRolleDto(
                        Rolletype.BARN,
                        "1111234",
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                        opprettetdato = LocalDate.now().minusMonths(4),
                    ),
                ),
            )

            assertEquals(
                2,
                behandlingService.hentBehandlingById(b.id!!).roller.filter { r -> r.rolletype == Rolletype.BARN }.size,
            )
        }
    }

    @Nested
    open inner class OppdatereBehandling {
        @Test
        fun `skal caste 404 exception hvis behandlingen ikke er der - oppdater`() {
            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.oppdaterBehandling(1234, "New Notat", "Med i Vedtak")
            }
        }

        @Test
        fun `skal oppdatere en behandling`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"
            val medIVedtak = "med i vedtak"

            val createdBehandling = behandlingService.createBehandling(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.aarsak)

            val oppdatertBehandling =
                behandlingService.oppdaterBehandling(
                    createdBehandling.id!!,
                    medIVedtak,
                    notat,
                    medIVedtak,
                    notat,
                    medIVedtak,
                    notat,
                )

            val hentBehandlingById = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(3, hentBehandlingById.roller.size)
            assertEquals(notat, oppdatertBehandling.virkningstidspunktbegrunnelseKunINotat)
            assertEquals(
                medIVedtak,
                oppdatertBehandling.virkningstidspunktsbegrunnelseIVedtakOgNotat,
            )
        }

        @Test
        fun `skal opprette en behandling med grunnlagspakkeId`() {
            val b = createBehandling()

            behandlingService.updateBehandling(b.id!!, 123L)

            assertEquals(123L, behandlingService.hentBehandlingById(b.id!!).grunnlagspakkeid)
        }
    }

    @Nested
    open inner class OppdatereInntekter {
        @Test
        fun `skal legge til inntekter`() {
            val actualBehandling = createBehandling()

            assertNotNull(actualBehandling.id)

            assertEquals(0, actualBehandling.inntekter.size)
            assertEquals(0, actualBehandling.barnetillegg.size)
            assertEquals(0, actualBehandling.utvidetBarnetrygd.size)
            assertNull(actualBehandling.inntektsbegrunnelseIVedtakOgNotat)
            assertNull(actualBehandling.inntektsbegrunnelseKunINotat)

            behandlingService.oppdaterInntekter(
                actualBehandling.id!!,
                mutableSetOf(
                    InntektDto(
                        taMed = true,
                        inntektstype = Inntektsrapportering.KAPITALINNTEKT,
                        beløp = BigDecimal.valueOf(4000),
                        datoFom = LocalDate.now().minusMonths(4),
                        datoTom = LocalDate.now().plusMonths(4),
                        ident = "123",
                        fraGrunnlag = true,
                        inntektsposter = emptySet(),
                    ),
                ),
                mutableSetOf(
                    BarnetilleggDto(
                        ident = "123",
                        barnetillegg = BigDecimal.TEN,
                        datoFom = LocalDate.now().minusMonths(3),
                        datoTom = LocalDate.now().plusMonths(3),
                    ),
                ),
                mutableSetOf(
                    UtvidetBarnetrygdDto(
                        deltBosted = false,
                        beløp = BigDecimal.TEN,
                        datoFom = LocalDate.now().minusMonths(3),
                        datoTom = LocalDate.now().plusMonths(3),
                    ),
                ),
                "Med i Vedtaket",
                "Kun i Notat",
            )

            val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(1, expectedBehandling.inntekter.size)
            assertEquals(1, expectedBehandling.barnetillegg.size)
            assertEquals(1, expectedBehandling.utvidetBarnetrygd.size)
            assertEquals("Med i Vedtaket", expectedBehandling.inntektsbegrunnelseIVedtakOgNotat)
            assertEquals("Kun i Notat", expectedBehandling.inntektsbegrunnelseKunINotat)
        }

        @Test
        @Transactional
        open fun `skal slette inntekter`() {
            stubUtils.stubOpprettForsendelse()

            val actualBehandling = createBehandling()

            assertNotNull(actualBehandling.id)

            assertEquals(0, actualBehandling.inntekter.size)
            assertEquals(0, actualBehandling.barnetillegg.size)
            assertEquals(0, actualBehandling.utvidetBarnetrygd.size)

            behandlingService.oppdaterInntekter(
                actualBehandling.id!!,
                mutableSetOf(
                    InntektDto(
                        taMed = true,
                        inntektstype = Inntektsrapportering.KAPITALINNTEKT,
                        beløp = BigDecimal.valueOf(4000),
                        datoFom = LocalDate.now().minusMonths(4),
                        datoTom = LocalDate.now().plusMonths(4),
                        ident = "123",
                        fraGrunnlag = true,
                        inntektsposter = emptySet(),
                    ),
                ),
                mutableSetOf(
                    BarnetilleggDto(
                        ident = "123",
                        barnetillegg = BigDecimal.TEN,
                        datoFom = LocalDate.now().minusMonths(3),
                        datoTom = LocalDate.now().plusMonths(3),
                    ),
                ),
                mutableSetOf(),
                "null",
                "null",
            )

            val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(1, expectedBehandling.inntekter.size)
            assertEquals(1, expectedBehandling.barnetillegg.size)
            assertNotNull(expectedBehandling.inntektsbegrunnelseIVedtakOgNotat)
            assertNotNull(expectedBehandling.inntektsbegrunnelseKunINotat)

            behandlingService.oppdaterInntekter(
                actualBehandling.id!!,
                mutableSetOf(),
                mutableSetOf(
                    BarnetilleggDto(
                        ident = "123",
                        barnetillegg = BigDecimal.TEN,
                        datoFom = LocalDate.now().minusMonths(3),
                        datoTom = LocalDate.now().plusMonths(3),
                    ),
                ),
                mutableSetOf(),
                null,
                null,
            )

            val expectedBehandlingWithoutInntekter =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(0, expectedBehandlingWithoutInntekter.inntekter.size)
            assertEquals(1, expectedBehandlingWithoutInntekter.barnetillegg.size)
            assertNull(expectedBehandlingWithoutInntekter.inntektsbegrunnelseIVedtakOgNotat)
            assertNull(expectedBehandlingWithoutInntekter.inntektsbegrunnelseKunINotat)
        }
    }

    @Test
    fun `delete behandling rolle`() {
        val behandling = createBehandling()

        assertEquals(3, behandling.roller.size)
        behandling.roller.removeIf { it.rolletype == Rolletype.BARN }

        behandlingRepository.save(behandling)

        val updatedBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        assertEquals(2, updatedBehandling.roller.size)

        val realCount =
            entityManager.createNativeQuery("select count(*) from rolle r where r.behandling_id = " + behandling.id!!)
                .getSingleResult()

        val deletedCount =
            entityManager.createNativeQuery(
                "select count(*) from rolle r where r.behandling_id = " + behandling.id!! + " and r.deleted = true",
            ).getSingleResult()

        assertEquals(3L, realCount)
        assertEquals(1L, deletedCount)
    }

    companion object {
        fun prepareBehandling(): Behandling {
            val behandling =
                Behandling(
                    Behandlingstype.FORSKUDD,
                    Soknadstype.FASTSETTELSE,
                    YearMonth.now().atDay(1),
                    YearMonth.now().atEndOfMonth(),
                    LocalDate.now(),
                    "1900000",
                    123213L,
                    null,
                    "1234",
                    "Z9999",
                    "Navn Navnesen",
                    "bisys",
                    SøktAvType.BIDRAGSMOTTAKER,
                    null,
                    null,
                )
            val createRoller = prepareRoles(behandling)
            val roller =
                HashSet(
                    createRoller.map {
                        Rolle(
                            behandling,
                            it.rolletype,
                            it.ident,
                            it.foedselsdato,
                            it.opprettetDato,
                        )
                    },
                )

            behandling.roller.addAll(roller)
            return behandling
        }

        fun prepareRoles(behandling: Behandling): Set<Rolle> {
            return setOf(
                Rolle(
                    behandling,
                    Rolletype.BIDRAGSMOTTAKER,
                    "123344",
                    LocalDate.now().minusMonths(1025),
                    LocalDate.now().minusMonths(2),
                ),
                Rolle(
                    behandling,
                    Rolletype.BIDRAGSPLIKTIG,
                    "44332211",
                    LocalDate.now().minusMonths(1068),
                    LocalDate.now().minusMonths(2),
                ),
                Rolle(
                    behandling,
                    Rolletype.BARN,
                    "1111",
                    LocalDate.now().minusMonths(154),
                    LocalDate.now().minusMonths(2),
                ),
            )
        }
    }

    fun createBehandling(): Behandling {
        val behandling = prepareBehandling()

        return behandlingService.createBehandling(behandling)
    }
}
