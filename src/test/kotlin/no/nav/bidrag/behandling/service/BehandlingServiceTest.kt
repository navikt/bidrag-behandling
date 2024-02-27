package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereManuellInntekt
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Calendar

class BehandlingServiceTest : TestContainerRunner() {
    @MockBean
    lateinit var forsendelseService: ForsendelseService

    @Autowired
    lateinit var behandlingService: BehandlingService

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var testdataManager: TestdataManager

    @PersistenceContext
    lateinit var entityManager: EntityManager

    @Nested
    open inner class HenteBehandling {
        @Test
        fun `skal kaste 404 exception hvis behandlingen ikke er der`() {
            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.henteBehandling(1234)
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere lista over ikke-aktiverte endringer i grunnlagsdata dersom grunnlag har blitt oppdatert`() {
            // gitt
            val behandling = oppretteBehandling()

            // Setter innhentetdato til før innhentetdato i stub-input-fil hente-grunnlagrespons.json
            testdataManager.oppretteOgLagreGrunnlag(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.INNTEKT, true),
                innhentet = LocalDateTime.now(),
                grunnlagsdata =
                    SummerteMånedsOgÅrsinntekter(
                        versjon = "123",
                        summerteMånedsinntekter =
                            listOf(
                                SummertMånedsinntekt(
                                    gjelderÅrMåned = YearMonth.now().minusYears(1).withMonth(12),
                                    sumInntekt = BigDecimal(45000),
                                    inntektPostListe =
                                        listOf(
                                            InntektPost(
                                                beløp = BigDecimal(45000),
                                                inntekstype = Inntektstype.LØNNSINNTEKT,
                                                kode = "lønnFraSmåbrukarlaget",
                                                visningsnavn = "Lønn fra småbrukarlaget",
                                            ),
                                        ),
                                ),
                            ),
                        summerteÅrsinntekter =
                            listOf(
                                SummertÅrsinntekt(
                                    sumInntekt = BigDecimal(388000),
                                    inntektRapportering = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                                    periode =
                                        ÅrMånedsperiode(
                                            YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                                            YearMonth.now().withMonth(1).atDay(1),
                                        ),
                                    inntektPostListe = emptyList(),
                                    visningsnavn = "Egensnekra inntekt",
                                ),
                            ),
                    ),
            )

            testdataManager.oppretteOgLagreGrunnlag(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.INNTEKT, false),
                innhentet = LocalDateTime.now(),
                grunnlagsdata =
                    GrunnlagInntekt(
                        ainntekt = emptyList(),
                        listOf(
                            SkattegrunnlagGrunnlagDto(
                                behandling.bidragsmottaker!!.ident!!,
                                periodeFra = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
                                periodeTil = YearMonth.now().withMonth(1).atDay(1),
                                skattegrunnlagspostListe = emptyList(),
                            ),
                        ),
                    ),
            )

            val personidentBm = Personident(behandling.bidragsmottaker!!.ident!!)

            stubUtils.stubHentePersoninfo(personident = personidentBm.verdi)
            stubUtils.stubHenteGrunnlagOk()
            stubUtils.stubKodeverkSkattegrunnlag()
            stubUtils.stubKodeverkLønnsbeskrivelse()
            stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
            stubUtils.stubKodeverkYtelsesbeskrivelser()

            // hvis
            val behandlingDto = behandlingService.henteBehandling(behandling.id!!)

            // så
            assertSoftly {
                behandlingDto.aktiveGrunnlagsdata.size shouldBe 20
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata.size shouldBe 1
                behandlingDto.ikkeAktiverteEndringerIGrunnlagsdata.filter { g ->
                    g.nyeData.grunnlagsdatatype == Grunnlagsdatatype.INNTEKT_BEARBEIDET
                }.size shouldBe 1
            }
        }
    }

    @Nested
    open inner class OppretteBehandling {
        @Test
        fun `skal opprette en forskuddsbehandling`() {
            val actualBehandling = oppretteBehandling()

            assertNotNull(actualBehandling.id)
            assertEquals(Stønadstype.FORSKUDD, actualBehandling.stonadstype)
            assertEquals(3, actualBehandling.roller.size)

            val actualBehandlingFetched =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(Stønadstype.FORSKUDD, actualBehandlingFetched.stonadstype)
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
                        Kilde.OFFENTLIG,
                        true,
                        behandling = behandling,
                    ),
                )

            val actualBehandling = behandlingRepository.save(behandling)

            assertNotNull(actualBehandling.id)

            val actualBehandlingFetched =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(Stønadstype.FORSKUDD, actualBehandlingFetched.stonadstype)
            assertEquals(1, actualBehandlingFetched.inntekter.size)
            assertEquals(
                BigDecimal.valueOf(555.55),
                actualBehandlingFetched.inntekter.iterator().next().belop,
            )
        }
    }

    @Nested
    open inner class SletteBehandling {
        @Test
        fun `delete behandling`() {
            val behandling = oppretteBehandling()
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
            val b = oppretteBehandling()

            behandlingService.syncRoller(
                b.id!!,
                listOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("newident"),
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                    ),
                ),
            )

            assertEquals(4, behandlingService.hentBehandlingById(b.id!!).roller.size)
        }

        @Test
        fun `behandling må synce roller og slette behandling`() {
            val b = oppretteBehandling()
            behandlingService.syncRoller(
                b.id!!,
                listOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("1111"),
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
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
            val b = oppretteBehandling()
            behandlingService.syncRoller(
                b.id!!,
                listOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("1111"),
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                        true,
                    ),
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("111123"),
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
                    ),
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("1111234"),
                        null,
                        fødselsdato = LocalDate.now().minusMonths(144),
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
        fun `skal oppdatere virkningstidspunkt data`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"
            val medIVedtak = "med i vedtak"

            val createdBehandling = behandlingRepository.save(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.årsak)

            behandlingService.oppdaterBehandling(
                createdBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    virkningstidspunkt =
                        OppdaterVirkningstidspunkt(
                            årsak = VirkningstidspunktÅrsakstype.FRA_BARNETS_FØDSEL,
                            virkningstidspunkt = null,
                            notat =
                                OppdaterNotat(
                                    notat,
                                    medIVedtak,
                                ),
                        ),
                ),
            )

            val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(VirkningstidspunktÅrsakstype.FRA_BARNETS_FØDSEL, updatedBehandling.årsak)
            assertEquals(notat, updatedBehandling.virkningstidspunktbegrunnelseKunINotat)
            assertEquals(
                medIVedtak,
                updatedBehandling.virkningstidspunktsbegrunnelseIVedtakOgNotat,
            )
        }

        @Test
        fun `skal caste 404 exception hvis behandlingen ikke er der - oppdater`() {
            Assertions.assertThrows(HttpClientErrorException::class.java) {
                behandlingService.oppdaterBehandling(
                    1234,
                    OppdaterBehandlingRequestV2(
                        virkningstidspunkt =
                            OppdaterVirkningstidspunkt(
                                notat =
                                    OppdaterNotat(
                                        "New Notat",
                                        "Med i Vedtak",
                                    ),
                            ),
                    ),
                )
            }
        }

        @Test
        @Transactional
        open fun `skal oppdatere boforhold data`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"
            val medIVedtak = "med i vedtak"

            val createdBehandling = behandlingRepository.save(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.årsak)
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

            behandlingService.oppdaterBehandling(
                createdBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    boforhold =
                        OppdaterBoforholdRequest(
                            husstandsBarn,
                            sivilstand,
                            notat =
                                OppdaterNotat(
                                    kunINotat = notat,
                                    medIVedtaket = medIVedtak,
                                ),
                        ),
                ),
            )

            val updatedBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(1, updatedBehandling.husstandsbarn.size)
            assertEquals(1, updatedBehandling.sivilstand.size)
            assertEquals(notat, updatedBehandling.boforholdsbegrunnelseKunINotat)
            assertEquals(medIVedtak, updatedBehandling.boforholdsbegrunnelseIVedtakOgNotat)
        }

        @Test
        fun `skal oppdatere behandling`() {
            val behandling = prepareBehandling()

            val notat = "New Notat"
            val medIVedtak = "med i vedtak"

            val createdBehandling = behandlingRepository.save(behandling)

            assertNotNull(createdBehandling.id)
            assertNull(createdBehandling.årsak)

            behandlingService.oppdaterBehandling(
                createdBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    virkningstidspunkt =
                        OppdaterVirkningstidspunkt(
                            notat =
                                OppdaterNotat(
                                    notat,
                                    medIVedtak,
                                ),
                        ),
                    inntekter =
                        OppdatereInntekterRequestV2(
                            notat =
                                OppdaterNotat(
                                    notat,
                                    medIVedtak,
                                ),
                        ),
                    boforhold =
                        OppdaterBoforholdRequest(
                            notat =
                                OppdaterNotat(
                                    notat,
                                    medIVedtak,
                                ),
                        ),
                ),
            )

            val oppdatertBehandling = behandlingService.hentBehandlingById(createdBehandling.id!!)

            assertEquals(3, oppdatertBehandling.roller.size)
            assertEquals(notat, oppdatertBehandling.virkningstidspunktbegrunnelseKunINotat)
            assertEquals(
                medIVedtak,
                oppdatertBehandling.virkningstidspunktsbegrunnelseIVedtakOgNotat,
            )
        }

        @Test
        fun `skal oppdatere behandling med grunnlagspakkeId`() {
            val b = oppretteBehandling()

            behandlingService.oppdaterBehandling(
                b.id!!,
                OppdaterBehandlingRequestV2(
                    grunnlagspakkeId = 123L,
                ),
            )

            assertEquals(123L, behandlingService.hentBehandlingById(b.id!!).grunnlagspakkeid)
        }

        @Test
        @Disabled("Kontekstproblem - OK hvis kjøres alene - feiler sammen med andre tester")
        open fun `skal aktivere valgte nyinnhenta grunnlag`() {
            // gitt
            val behandling = behandlingRepository.save(prepareBehandling())

            testdataManager.oppretteOgLagreGrunnlag<GrunnlagInntekt>(
                behandling = behandling,
                grunnlagstype = Grunnlagstype(Grunnlagsdatatype.INNTEKT, false),
                innhentet = LocalDate.of(2024, 1, 1).atStartOfDay(),
                aktiv = null,
            )

            val opppdatereBehandlingRequest =
                OppdaterBehandlingRequestV2(aktivereGrunnlag = setOf(behandling.id!!))

            // hvis
            behandlingService.oppdaterBehandling(behandling.id!!, opppdatereBehandlingRequest)

            // så
            val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

            oppdatertBehandling.get().grunnlag.first().aktiv shouldNotBe null
        }
    }

    @Nested
    open inner class OppdatereInntekter {
        @Test
        fun `skal legge til inntekter manuelt`() {
            val actualBehandling = oppretteBehandling()

            assertNotNull(actualBehandling.id)

            assertEquals(0, actualBehandling.inntekter.size)
            assertNull(actualBehandling.inntektsbegrunnelseIVedtakOgNotat)
            assertNull(actualBehandling.inntektsbegrunnelseKunINotat)

            behandlingService.oppdaterBehandling(
                actualBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    inntekter =
                        OppdatereInntekterRequestV2(
                            oppdatereManuelleInntekter =
                                mutableSetOf(
                                    OppdatereManuellInntekt(
                                        type = Inntektsrapportering.KAPITALINNTEKT,
                                        beløp = BigDecimal.valueOf(4000),
                                        datoFom = LocalDate.now().minusMonths(4),
                                        datoTom = LocalDate.now().plusMonths(4),
                                        ident = Personident("123"),
                                    ),
                                ),
                            notat =
                                OppdaterNotat(
                                    "Kun i Notat",
                                    "Med i Vedtaket",
                                ),
                        ),
                ),
            )

            val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(1, expectedBehandling.inntekter.size)
            assertEquals("Med i Vedtaket", expectedBehandling.inntektsbegrunnelseIVedtakOgNotat)
            assertEquals("Kun i Notat", expectedBehandling.inntektsbegrunnelseKunINotat)
        }

        @Test
        @Transactional
        open fun `skal slette manuelt oppretta inntekter`() {
            stubUtils.stubOpprettForsendelse()

            val actualBehandling = oppretteBehandling()

            assertNotNull(actualBehandling.id)

            assertEquals(0, actualBehandling.inntekter.size)

            behandlingService.oppdaterBehandling(
                actualBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    inntekter =
                        OppdatereInntekterRequestV2(
                            oppdatereManuelleInntekter =
                                mutableSetOf(
                                    OppdatereManuellInntekt(
                                        type = Inntektsrapportering.KAPITALINNTEKT,
                                        beløp = BigDecimal.valueOf(4000),
                                        datoFom = LocalDate.now().minusMonths(4),
                                        datoTom = LocalDate.now().plusMonths(4),
                                        ident = Personident("123"),
                                    ),
                                ),
                            notat =
                                OppdaterNotat(
                                    "not null",
                                    "not null",
                                ),
                        ),
                ),
            )

            val expectedBehandling = behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(1, expectedBehandling.inntekter.size)
            assertNotNull(expectedBehandling.inntektsbegrunnelseIVedtakOgNotat)
            assertNotNull(expectedBehandling.inntektsbegrunnelseKunINotat)

            behandlingService.oppdaterBehandling(
                actualBehandling.id!!,
                OppdaterBehandlingRequestV2(
                    inntekter =
                        OppdatereInntekterRequestV2(
                            sletteInntekter = expectedBehandling.inntekter.map { it.id!! }.toSet(),
                        ),
                ),
            )

            val expectedBehandlingWithoutInntekter =
                behandlingService.hentBehandlingById(actualBehandling.id!!)

            assertEquals(0, expectedBehandlingWithoutInntekter.inntekter.size)
            assertNotNull(expectedBehandlingWithoutInntekter.inntektsbegrunnelseIVedtakOgNotat)
            assertNotNull(expectedBehandlingWithoutInntekter.inntektsbegrunnelseKunINotat)
        }
    }

    @Test
    fun `delete behandling rolle`() {
        val behandling = oppretteBehandling()

        assertEquals(3, behandling.roller.size)
        behandling.roller.removeIf { it.rolletype == Rolletype.BARN }

        behandlingRepository.save(behandling)

        val updatedBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()
        assertEquals(2, updatedBehandling.roller.size)

        val realCount =
            entityManager.createNativeQuery("select count(*) from rolle r where r.behandling_id = " + behandling.id)
                .singleResult

        val deletedCount =
            entityManager.createNativeQuery(
                "select count(*) from rolle r where r.behandling_id = ${behandling.id} and r.deleted = true",
            ).singleResult

        assertEquals(3L, realCount)
        assertEquals(1L, deletedCount)
    }

    companion object {
        fun prepareBehandling(): Behandling {
            val behandling =
                Behandling(
                    Vedtakstype.FASTSETTELSE,
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
                    Stønadstype.FORSKUDD,
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
                            it.opprettet,
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
                    LocalDateTime.now().minusMonths(2),
                ),
                Rolle(
                    behandling,
                    Rolletype.BIDRAGSPLIKTIG,
                    "44332211",
                    LocalDate.now().minusMonths(1068),
                    LocalDateTime.now().minusMonths(2),
                ),
                Rolle(
                    behandling,
                    Rolletype.BARN,
                    "1111",
                    LocalDate.now().minusMonths(154),
                    LocalDateTime.now().minusMonths(2),
                ),
            )
        }
    }

    fun oppretteBehandling(): Behandling {
        val behandling = prepareBehandling()

        return behandlingRepository.save(behandling)
    }
}
