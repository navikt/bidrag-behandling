package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdaterHusstandsmedlemPeriode
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OpprettHusstandsstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.opprettBoforholdBearbeidetGrunnlagForHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.opprettHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.opprettHusstandsbarnMedOffentligePerioder
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.ident.Personident
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(Enclosed::class)
class BoforholdControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Nested
    open inner class OppdatereBehandling {
        @Test
        fun `skal lagre boforhold data`() {
            // 1. Create new behandling
            val behandling = testdataManager.oppretteBehandling()

            // 2.1 Prepare husstandsBarn
            val perioder =
                setOf(
                    HusstandsbarnperiodeDto(
                        null,
                        LocalDate.parse("2022-01-01"),
                        null,
                        Bostatuskode.IKKE_MED_FORELDER,
                        Kilde.OFFENTLIG,
                    ),
                )

            val husstandsBarn =
                setOf(
                    HusstandsbarnDtoV2(
                        behandling.id,
                        Kilde.OFFENTLIG,
                        true,
                        perioder,
                        "ident",
                        null,
                        fødselsdato = LocalDate.now().minusMonths(687),
                    ),
                )

            // 2.2
            val boforholddata =
                OppdaterBoforholdRequest(
                    husstandsBarn,
                    emptySet(),
                    notat =
                        OppdaterNotat(
                            "med i vedtak",
                        ),
                )
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}",
                    HttpMethod.PUT,
                    HttpEntity(OppdaterBehandlingRequestV2(boforhold = boforholddata)),
                    BehandlingDtoV2::class.java,
                )

            assertEquals(1, boforholdResponse.body!!.boforhold.husstandsbarn.size)
            val husstandsBarnDto = boforholdResponse.body!!.boforhold.husstandsbarn.iterator().next()
            assertEquals(1, husstandsBarnDto.perioder.size)
        }
    }

    @Nested
    open inner class OppdatereBoforhold {
        private fun opprettBehandling(): Behandling {
            val behandling = testdataManager.oppretteBehandling()
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            behandling.husstandsbarn.clear()
            behandling.husstandsbarn.addAll(
                setOf(
                    opprettHusstandsbarn(behandling, testdataBarn1).let {
                        it.perioder =
                            mutableSetOf(
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2023-01-01"),
                                    datoTom = LocalDate.parse("2023-05-31"),
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsbarn = it,
                                ),
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2023-06-01"),
                                    datoTom = null,
                                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsbarn = it,
                                ),
                            )
                        it
                    },
                    opprettHusstandsbarn(behandling, testdataBarn2).let {
                        it.perioder =
                            mutableSetOf(
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2023-01-01"),
                                    datoTom = LocalDate.parse("2023-10-31"),
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsbarn = it,
                                ),
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2023-11-01"),
                                    datoTom = LocalDate.parse("2023-12-31"),
                                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsbarn = it,
                                ),
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2024-01-01"),
                                    datoTom = null,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    kilde = Kilde.MANUELL,
                                    husstandsbarn = it,
                                ),
                            )
                        it
                    },
                ),
            )
            behandling.grunnlag.addAll(
                opprettBoforholdBearbeidetGrunnlagForHusstandsbarn(
                    opprettHusstandsbarnMedOffentligePerioder(behandling),
                ),
            )
            return testdataManager.lagreBehandlingNewTransaction(behandling)
        }

        @Test
        fun `skal kunne legge til ny manuell periode`() {
            // gitt
            val behandling = opprettBehandling()

            val eksisterendeHusstandsbarn = behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }
            val sistePeriode = eksisterendeHusstandsbarn!!.perioder.maxBy { it.datoFom!! }
            eksisterendeHusstandsbarn.perioder.shouldHaveSize(2)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            oppdaterPeriode =
                                OppdaterHusstandsmedlemPeriode(
                                    idHusstandsbarn = eksisterendeHusstandsbarn.id!!,
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
                it.body?.valideringsfeil?.husstandsbarn shouldBe emptyList()
                it.body?.oppdatertHusstandsbarn shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                oppdatertHusstandsbarn?.perioder.shouldNotBeEmpty()
                oppdatertHusstandsbarn!!.perioder shouldHaveSize 3
                oppdatertHusstandsbarn.perioder.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                val sisteOppdatertPeriode = oppdatertHusstandsbarn.perioder.maxBy { it.datoFom!! }
                sisteOppdatertPeriode.datoFom shouldBe request.oppdatereHusstandsmedlem!!.oppdaterPeriode!!.datoFom
                sisteOppdatertPeriode.kilde shouldBe Kilde.MANUELL
                sisteOppdatertPeriode.bostatus shouldBe Bostatuskode.MED_FORELDER
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
                val oppdaterHusstandsmedlem = it.husstandsbarn.find { it.id == eksisterendeHusstandsbarn.id }
                oppdaterHusstandsmedlem!!.perioder shouldHaveSize 3
                oppdaterHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }
        }

        @Test
        fun `skal oppdatere manuell periode`() {
            // gitt
            val behandling = opprettBehandling()

            val eksisterendeHusstandsbarn = behandling.husstandsbarn.find { it.ident == testdataBarn2.ident }
            val manuellPeriode = eksisterendeHusstandsbarn!!.perioder.find { it.kilde == Kilde.MANUELL }!!
            eksisterendeHusstandsbarn.perioder.shouldHaveSize(3)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            oppdaterPeriode =
                                OppdaterHusstandsmedlemPeriode(
                                    idHusstandsbarn = eksisterendeHusstandsbarn.id!!,
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
                it.body?.valideringsfeil?.husstandsbarn.shouldBeEmpty()
                it.body?.oppdatertHusstandsbarn shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                oppdatertHusstandsbarn?.perioder.shouldNotBeEmpty()
                oppdatertHusstandsbarn!!.perioder shouldHaveSize 3
                oppdatertHusstandsbarn.perioder.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                val sisteOppdatertPeriode = oppdatertHusstandsbarn.perioder.maxBy { it.datoFom!! }
                sisteOppdatertPeriode.datoFom shouldBe request.oppdatereHusstandsmedlem!!.oppdaterPeriode!!.datoFom
                sisteOppdatertPeriode.kilde shouldBe Kilde.MANUELL
                sisteOppdatertPeriode.bostatus shouldBe Bostatuskode.MED_FORELDER
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
                val oppdaterHusstandsmedlem = it.husstandsbarn.find { it.id == eksisterendeHusstandsbarn.id }
                oppdaterHusstandsmedlem!!.perioder shouldHaveSize 3
                oppdaterHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }
        }

        @Test
        fun `skal kunne slette husstandsbarnperiode`() {
            // gitt
            val behandling = opprettBehandling()
            val eksisterendeHusstandsbarn = behandling.husstandsbarn.find { it.ident == testdataBarn2.ident }!!
            eksisterendeHusstandsbarn.perioder.shouldHaveSize(3)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            slettPeriode = eksisterendeHusstandsbarn!!.perioder.first { Kilde.MANUELL == it.kilde }.id,
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
                it.body?.valideringsfeil?.husstandsbarn shouldBe emptyList()
                it.body?.oppdatertHusstandsbarn shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                oppdatertHusstandsbarn?.perioder.shouldNotBeEmpty()
                oppdatertHusstandsbarn!!.perioder shouldHaveSize 2
                oppdatertHusstandsbarn.perioder.find { Kilde.MANUELL == it.kilde } shouldBe null
                oppdatertHusstandsbarn.perioder.maxBy { it.datoFom!! }.kilde shouldBe Kilde.OFFENTLIG
            }
            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
                val oppdaterHusstandsmedlem = it.husstandsbarn.find { it.id == eksisterendeHusstandsbarn.id }
                oppdaterHusstandsmedlem!!.perioder shouldHaveSize 2
                oppdaterHusstandsmedlem.forrigePerioder.shouldNotBeNull()
            }
        }

        @Test
        open fun `skal kunne legge til et nytt husstandsmedlem`() {
            // gitt
            val behandling = opprettBehandling()
            behandling.husstandsbarn.shouldHaveSize(2)
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
                it.body?.valideringsfeil?.husstandsbarn.shouldBeEmpty()
                it.body?.oppdatertHusstandsbarn shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                oppdatertHusstandsbarn!!.kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsbarn.ident shouldBe
                    request.oppdatereHusstandsmedlem!!.opprettHusstandsmedlem!!.personident!!.verdi
                oppdatertHusstandsbarn.navn shouldBe request.oppdatereHusstandsmedlem!!.opprettHusstandsmedlem!!.navn
                oppdatertHusstandsbarn.perioder.shouldHaveSize(1)
                oppdatertHusstandsbarn.perioder.first().kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsbarn.perioder.first().datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                oppdatertHusstandsbarn.perioder.first().datoTom.shouldBeNull()
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsbarn) {
                it.size shouldBe 3
                it.find { nyttBarn ->
                    nyttBarn.ident ==
                        request.oppdatereHusstandsmedlem!!.opprettHusstandsmedlem!!.personident!!.verdi
                } shouldNotBe null
            }
        }

        @Test
        fun `skal kunne slette manuelt husstandsbarn`() {
            // gitt
            val behandling = opprettBehandling()

            val manueltHusstandsbarn = behandling.husstandsbarn.first()
            manueltHusstandsbarn.kilde = Kilde.MANUELL
            testdataManager.lagreBehandlingNewTransaction(behandling)
            behandling.husstandsbarn.shouldHaveSize(2)

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            slettHusstandsmedlem = behandling.husstandsbarn.first { Kilde.MANUELL == it.kilde }.id,
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
                it.body?.valideringsfeil?.husstandsbarn shouldBe emptyList()
                it.body?.oppdatertHusstandsbarn shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                oppdatertHusstandsbarn!!.kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsbarn.id shouldBe request.oppdatereHusstandsmedlem!!.slettHusstandsmedlem
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsbarn) {
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

            val oppdaterHusstandsmedlem = behandling.husstandsbarn.first()
            oppdaterHusstandsmedlem.perioder.clear()
            oppdaterHusstandsmedlem.perioder.addAll(
                setOf(
                    Husstandsbarnperiode(
                        datoFom = LocalDate.parse("2023-01-01"),
                        datoTom = LocalDate.parse("2023-03-31"),
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        husstandsbarn = oppdaterHusstandsmedlem,
                    ),
                    Husstandsbarnperiode(
                        datoFom = LocalDate.parse("2023-04-01"),
                        datoTom = LocalDate.parse("2023-04-30"),
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        kilde = Kilde.MANUELL,
                        husstandsbarn = oppdaterHusstandsmedlem,
                    ),
                    Husstandsbarnperiode(
                        datoFom = LocalDate.parse("2023-05-01"),
                        datoTom = null,
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.MANUELL,
                        husstandsbarn = oppdaterHusstandsmedlem,
                    ),
                ),
            )
            testdataManager.lagreBehandlingNewTransaction(behandling)
            behandling.husstandsbarn.shouldHaveSize(2)

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
                it.body?.valideringsfeil?.husstandsbarn shouldBe emptyList()
                it.body?.oppdatertHusstandsbarn shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                oppdatertHusstandsbarn!!.perioder.shouldHaveSize(2)
                oppdatertHusstandsbarn.perioder.filter { it.kilde == Kilde.MANUELL }.shouldBeEmpty()
            }
            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsbarn) {
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

            val oppdaterHusstandsmedlem = behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }!!

            val nyPeriodeRequest =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            oppdaterPeriode =
                                OppdaterHusstandsmedlemPeriode(
                                    idHusstandsbarn = oppdaterHusstandsmedlem.id!!,
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
                assertSoftly(body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                    oppdatertHusstandsbarn!!.perioder.filter { it.kilde == Kilde.MANUELL }.shouldHaveSize(1)
                    oppdatertHusstandsbarn.perioder.shouldHaveSize(3)
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
                assertSoftly(body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                    oppdatertHusstandsbarn!!.perioder.filter { it.kilde == Kilde.MANUELL }.shouldHaveSize(0)
                    oppdatertHusstandsbarn.perioder.shouldHaveSize(2)
                }
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsbarn) {
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
                assertSoftly(body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                    oppdatertHusstandsbarn!!.perioder.filter { it.kilde == Kilde.MANUELL }.shouldHaveSize(1)
                    oppdatertHusstandsbarn.perioder.shouldHaveSize(3)
                }
            }
        }
    }
}
