package no.nav.bidrag.behandling.controller

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import no.nav.bidrag.behandling.consumer.BidragTilgangskontrollConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBostatusperiode
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OpprettHusstandsstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlemMedOffentligePerioder
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.ident.Personident
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

@RunWith(Enclosed::class)
class BoforholdControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    private fun opprettBehandling(): Behandling {
        val behandling = oppretteTestbehandling()
        behandling.oppdaterVirkningstidspunktForAlle(LocalDate.parse("2023-01-01"))
        behandling.husstandsmedlem.clear()
        behandling.husstandsmedlem.addAll(
            setOf(
                oppretteHusstandsmedlem(behandling, testdataBarn1).let {
                    it.perioder =
                        mutableSetOf(
                            Bostatusperiode(
                                datoFom = LocalDate.parse("2023-01-01"),
                                datoTom = LocalDate.parse("2023-05-31"),
                                bostatus = Bostatuskode.MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                husstandsmedlem = it,
                            ),
                            Bostatusperiode(
                                datoFom = LocalDate.parse("2023-06-01"),
                                datoTom = null,
                                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                husstandsmedlem = it,
                            ),
                        )
                    it
                },
                oppretteHusstandsmedlem(behandling, testdataBarn2).let {
                    it.perioder =
                        mutableSetOf(
                            Bostatusperiode(
                                datoFom = LocalDate.parse("2023-01-01"),
                                datoTom = LocalDate.parse("2023-10-31"),
                                bostatus = Bostatuskode.MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                husstandsmedlem = it,
                            ),
                            Bostatusperiode(
                                datoFom = LocalDate.parse("2023-11-01"),
                                datoTom = LocalDate.parse("2023-12-31"),
                                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                husstandsmedlem = it,
                            ),
                            Bostatusperiode(
                                datoFom = LocalDate.parse("2024-01-01"),
                                datoTom = null,
                                bostatus = Bostatuskode.MED_FORELDER,
                                kilde = Kilde.MANUELL,
                                husstandsmedlem = it,
                            ),
                        )
                    it
                },
            ),
        )
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )

        return testdataManager.lagreBehandlingNewTransaction(behandling)
    }

    @Nested
    open inner class OppdatereBoforhold {
        @Test
        fun `skal kunne legge til ny manuell periode`() {
            // gitt
            val behandling = opprettBehandling()

            val eksisterendeHusstandsmedlem = behandling.husstandsmedlem.find { it.ident == testdataBarn1.ident }
            val sistePeriode = eksisterendeHusstandsmedlem!!.perioder.maxBy { it.datoFom!! }
            eksisterendeHusstandsmedlem.perioder.shouldHaveSize(2)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            oppdaterPeriode =
                                OppdatereBostatusperiode(
                                    idHusstandsmedlem = eksisterendeHusstandsmedlem.id!!,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    datoFom = sistePeriode.datoFom!!.plusMonths(2),
                                    datoTom = null,
                                ),
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertSoftly(boforholdResponse) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                it.body?.valideringsfeil?.husstandsmedlem shouldBe emptyList()
                it.body?.oppdatertHusstandsmedlem shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                oppdatertHusstandsmedlem?.perioder.shouldNotBeEmpty()
                oppdatertHusstandsmedlem!!.perioder shouldHaveSize 3
                oppdatertHusstandsmedlem.perioder.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                val sisteOppdatertPeriode = oppdatertHusstandsmedlem.perioder.maxBy { it.datoFom!! }
                sisteOppdatertPeriode.datoFom shouldBe request.oppdatereHusstandsmedlem!!.oppdaterPeriode!!.datoFom
                sisteOppdatertPeriode.kilde shouldBe Kilde.MANUELL
                sisteOppdatertPeriode.bostatus shouldBe Bostatuskode.MED_FORELDER
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
                val oppdaterHusstandsmedlem = it.husstandsmedlem.find { it.id == eksisterendeHusstandsmedlem.id }
                oppdaterHusstandsmedlem!!.perioder shouldHaveSize 3
                oppdaterHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }
        }

        @Test
        fun `skal oppdatere manuell periode`() {
            // gitt
            val behandling = opprettBehandling()

            val eksisterendeHusstandsmedlem = behandling.husstandsmedlem.find { it.ident == testdataBarn2.ident }
            val manuellPeriode = eksisterendeHusstandsmedlem!!.perioder.find { it.kilde == Kilde.MANUELL }!!
            eksisterendeHusstandsmedlem.perioder.shouldHaveSize(3)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            oppdaterPeriode =
                                OppdatereBostatusperiode(
                                    idHusstandsmedlem = eksisterendeHusstandsmedlem.id!!,
                                    idPeriode = manuellPeriode.id,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    datoFom = manuellPeriode.datoFom!!.plusMonths(1),
                                    datoTom = null,
                                ),
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertSoftly(boforholdResponse) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                it.body
                    ?.valideringsfeil
                    ?.husstandsmedlem
                    .shouldBeEmpty()
                it.body?.oppdatertHusstandsmedlem shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                oppdatertHusstandsmedlem?.perioder.shouldNotBeEmpty()
                oppdatertHusstandsmedlem!!.perioder shouldHaveSize 3
                oppdatertHusstandsmedlem.perioder.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                val sisteOppdatertPeriode = oppdatertHusstandsmedlem.perioder.maxBy { it.datoFom!! }
                sisteOppdatertPeriode.datoFom shouldBe request.oppdatereHusstandsmedlem!!.oppdaterPeriode!!.datoFom
                sisteOppdatertPeriode.kilde shouldBe Kilde.MANUELL
                sisteOppdatertPeriode.bostatus shouldBe Bostatuskode.MED_FORELDER
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
                val oppdaterHusstandsmedlem = it.husstandsmedlem.find { it.id == eksisterendeHusstandsmedlem.id }
                oppdaterHusstandsmedlem!!.perioder shouldHaveSize 3
                oppdaterHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }
        }

        @Test
        fun `skal kunne slette husstandsmedlemperiode`() {
            // gitt
            val behandling = opprettBehandling()
            val eksisterendeHusstandsmedlem = behandling.husstandsmedlem.find { it.ident == testdataBarn2.ident }!!
            eksisterendeHusstandsmedlem.perioder.shouldHaveSize(3)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            slettPeriode = eksisterendeHusstandsmedlem!!.perioder.first { Kilde.MANUELL == it.kilde }.id,
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertSoftly(boforholdResponse) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                it.body?.valideringsfeil?.husstandsmedlem shouldBe emptyList()
                it.body?.oppdatertHusstandsmedlem shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                oppdatertHusstandsmedlem?.perioder.shouldNotBeEmpty()
                oppdatertHusstandsmedlem!!.perioder shouldHaveSize 2
                oppdatertHusstandsmedlem.perioder.find { Kilde.MANUELL == it.kilde } shouldBe null
                oppdatertHusstandsmedlem.perioder.maxBy { it.datoFom!! }.kilde shouldBe Kilde.OFFENTLIG
            }
            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
                val oppdaterHusstandsmedlem = it.husstandsmedlem.find { it.id == eksisterendeHusstandsmedlem.id }
                oppdaterHusstandsmedlem!!.perioder shouldHaveSize 2
                oppdaterHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }
        }

        @Test
        open fun `skal kunne legge til et nytt husstandsmedlem som saksbehandler har tilgang til`() {
            // gitt
            val behandling = opprettBehandling()
            behandling.husstandsmedlem.shouldHaveSize(2)
            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            opprettHusstandsmedlem =
                                OpprettHusstandsstandsmedlem(
                                    personident = Personident("1234"),
                                    fødselsdato = LocalDate.now().minusMonths(156),
                                    navn = "Per Spelemann",
                                ),
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertSoftly(boforholdResponse) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                it.body
                    ?.valideringsfeil
                    ?.husstandsmedlem
                    .shouldBeEmpty()
                it.body?.oppdatertHusstandsmedlem shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                oppdatertHusstandsmedlem!!.kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsmedlem.ident shouldBe
                    request.oppdatereHusstandsmedlem!!
                        .opprettHusstandsmedlem!!
                        .personident!!
                        .verdi
                oppdatertHusstandsmedlem.navn shouldBe
                    request.oppdatereHusstandsmedlem.opprettHusstandsmedlem!!.navn
                oppdatertHusstandsmedlem.perioder.shouldHaveSize(1)
                oppdatertHusstandsmedlem.perioder.first().kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsmedlem.perioder.first().datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                oppdatertHusstandsmedlem.perioder
                    .first()
                    .datoTom
                    .shouldBeNull()
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsmedlem) {
                it.size shouldBe 3
                it.find { nyttBarn ->
                    nyttBarn.ident ==
                        request.oppdatereHusstandsmedlem!!
                            .opprettHusstandsmedlem!!
                            .personident!!
                            .verdi
                } shouldNotBe null
            }
        }

        @Test
        fun `skal kunne slette manuelt husstandsmedlem`() {
            // gitt
            val behandling = opprettBehandling()

            val manueltHusstandsmedlem = behandling.husstandsmedlem.first()
            manueltHusstandsmedlem.kilde = Kilde.MANUELL
            testdataManager.lagreBehandlingNewTransaction(behandling)
            behandling.husstandsmedlem.shouldHaveSize(2)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            slettHusstandsmedlem = behandling.husstandsmedlem.first { Kilde.MANUELL == it.kilde }.id,
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertSoftly(boforholdResponse) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                it.body
                    ?.valideringsfeil
                    ?.husstandsmedlem
                    .shouldNotBeEmpty()
                it.body?.oppdatertHusstandsmedlem shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                oppdatertHusstandsmedlem!!.kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsmedlem.id shouldBe request.oppdatereHusstandsmedlem!!.slettHusstandsmedlem
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsmedlem) {
                it.size shouldBe 1
                it.find { slettetBarn ->
                    slettetBarn.id == request.oppdatereHusstandsmedlem!!.slettHusstandsmedlem
                } shouldBe null
            }
        }

        @Test
        fun `skal tilbakestille husstandsmedlem periode`() {
            // gitt
            val behandling = opprettBehandling()

            val oppdaterHusstandsmedlem = behandling.husstandsmedlem.first()
            oppdaterHusstandsmedlem.perioder.clear()
            oppdaterHusstandsmedlem.perioder.addAll(
                setOf(
                    Bostatusperiode(
                        datoFom = LocalDate.parse("2023-01-01"),
                        datoTom = LocalDate.parse("2023-03-31"),
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        husstandsmedlem = oppdaterHusstandsmedlem,
                    ),
                    Bostatusperiode(
                        datoFom = LocalDate.parse("2023-04-01"),
                        datoTom = LocalDate.parse("2023-04-30"),
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        kilde = Kilde.MANUELL,
                        husstandsmedlem = oppdaterHusstandsmedlem,
                    ),
                    Bostatusperiode(
                        datoFom = LocalDate.parse("2023-05-01"),
                        datoTom = null,
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.MANUELL,
                        husstandsmedlem = oppdaterHusstandsmedlem,
                    ),
                ),
            )
            testdataManager.lagreBehandlingNewTransaction(behandling)
            behandling.husstandsmedlem.shouldHaveSize(2)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            tilbakestillPerioderForHusstandsmedlem = oppdaterHusstandsmedlem.id,
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertSoftly(boforholdResponse) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                it.body?.valideringsfeil?.husstandsmedlem shouldBe emptyList()
                it.body?.oppdatertHusstandsmedlem shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                oppdatertHusstandsmedlem!!.perioder.shouldHaveSize(2)
                oppdatertHusstandsmedlem.perioder.filter { it.kilde == Kilde.MANUELL }.shouldBeEmpty()
            }
            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsmedlem) {
                it.size shouldBe 2
                val oppdatertHusstandsmedlem = it.find { it.id == oppdaterHusstandsmedlem.id }!!
                oppdatertHusstandsmedlem.perioder.shouldHaveSize(2)
                oppdatertHusstandsmedlem.perioder.filter { it.kilde == Kilde.MANUELL }.shouldBeEmpty()
                oppdatertHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }
        }

        @Test
        fun `skal angre endring av husstandsmedlem periode`() {
            // gitt
            val behandling = opprettBehandling()

            val oppdaterHusstandsmedlem = behandling.husstandsmedlem.find { it.ident == testdataBarn1.ident }!!

            val nyPeriodeRequest =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            oppdaterPeriode =
                                OppdatereBostatusperiode(
                                    idHusstandsmedlem = oppdaterHusstandsmedlem.id!!,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    datoFom = LocalDate.parse("2024-01-01"),
                                    datoTom = null,
                                ),
                        ),
                )

            val responsNyPeriode =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(nyPeriodeRequest),
                    OppdatereBoforholdResponse::class.java,
                )

            assertSoftly(responsNyPeriode) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                assertSoftly(body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                    oppdatertHusstandsmedlem!!.perioder.filter { it.kilde == Kilde.MANUELL }.shouldHaveSize(1)
                    oppdatertHusstandsmedlem.perioder.shouldHaveSize(3)
                }
            }

            val angreRespons =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdatereBoforholdRequestV2(
                            oppdatereHusstandsmedlem =
                                OppdatereHusstandsmedlem(
                                    angreSisteStegForHusstandsmedlem = oppdaterHusstandsmedlem.id,
                                ),
                        ),
                    ),
                    OppdatereBoforholdResponse::class.java,
                )

            assertSoftly(angreRespons) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                assertSoftly(body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                    oppdatertHusstandsmedlem!!.perioder.filter { it.kilde == Kilde.MANUELL }.shouldHaveSize(0)
                    oppdatertHusstandsmedlem.perioder.shouldHaveSize(2)
                }
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsmedlem) {
                it.size shouldBe 2
                val oppdatertHusstandsmedlem = it.find { it.id == oppdaterHusstandsmedlem.id }!!
                oppdatertHusstandsmedlem.perioder.shouldHaveSize(2)
                oppdatertHusstandsmedlem.perioder.filter { it.kilde == Kilde.MANUELL }.shouldBeEmpty()
                oppdatertHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }

            // Angre angringen av endring av husstandsmedlem periode
            val angreRespons2 =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(
                        OppdatereBoforholdRequestV2(
                            oppdatereHusstandsmedlem =
                                OppdatereHusstandsmedlem(
                                    angreSisteStegForHusstandsmedlem = oppdaterHusstandsmedlem.id,
                                ),
                        ),
                    ),
                    OppdatereBoforholdResponse::class.java,
                )

            assertSoftly(angreRespons2) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                assertSoftly(body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                    oppdatertHusstandsmedlem!!.perioder.filter { it.kilde == Kilde.MANUELL }.shouldHaveSize(1)
                    oppdatertHusstandsmedlem.perioder.shouldHaveSize(3)
                }
            }
        }
    }

    @Nested
    open inner class OppdatereBoforholdVerifisereTilgangskontroll {
        @MockkBean
        lateinit var bidragTilgangskontrollConsumer: BidragTilgangskontrollConsumer

        @BeforeEach
        fun setup() {
            every { bidragTilgangskontrollConsumer.sjekkTilgangPersonISak(any(), any()) } returns false
        }

        @Test
        open fun `skal kunne legge til et nytt husstandsmedlem som saksbehandler ikke har tilgang til`() {
            // gitt
            val behandling = opprettBehandling()
            behandling.husstandsmedlem.shouldHaveSize(2)
            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            opprettHusstandsmedlem =
                                OpprettHusstandsstandsmedlem(
                                    personident = Personident("1234"),
                                    fødselsdato = LocalDate.now().minusMonths(156),
                                    navn = "Per Spelemann",
                                ),
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
                it.husstandsmedlem shouldHaveSize 3
            }

            assertSoftly(boforholdResponse) {
                it.statusCode shouldBe HttpStatus.OK
                it.body shouldNotBe null
                it.body
                    ?.valideringsfeil
                    ?.husstandsmedlem
                    .shouldBeEmpty()
                it.body?.oppdatertHusstandsmedlem shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsmedlem) { oppdatertHusstandsmedlem ->
                oppdatertHusstandsmedlem!!.kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsmedlem.ident shouldBe null
                oppdatertHusstandsmedlem.navn shouldBe
                    "Person skjermet, født ${request.oppdatereHusstandsmedlem!!.opprettHusstandsmedlem!!.fødselsdato.year}"
                oppdatertHusstandsmedlem.perioder.shouldHaveSize(1)
                oppdatertHusstandsmedlem.perioder.first().kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsmedlem.perioder.first().datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                oppdatertHusstandsmedlem.perioder
                    .first()
                    .datoTom
                    .shouldBeNull()
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsmedlem) {
                it.size shouldBe 3
                it.find { nyttBarn ->
                    nyttBarn.ident ==
                        request.oppdatereHusstandsmedlem!!
                            .opprettHusstandsmedlem!!
                            .personident!!
                            .verdi
                } shouldNotBe null
            }
        }
    }
}
