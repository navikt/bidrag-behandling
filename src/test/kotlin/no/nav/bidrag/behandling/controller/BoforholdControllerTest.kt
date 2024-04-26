package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.NyHusstandsbarnperiode
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.PersonaliaHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime
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
            val behandling = testdataManager.opprettBehandling()

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
                            "kun i notat",
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
        @Test
        fun `skal kunne legge til åpen manuell periode like etter en åpen offentlig periode`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            val eksisterendeHusstandsbarn = behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }
            val sistePeriode = eksisterendeHusstandsbarn!!.perioder.maxBy { it.datoFom!! }
            behandling.grunnlag.addAll(opprettBoforholdBearbeidetGrunnlag(behandling))
            testdataManager.lagreBehandlingNewTransaction(behandling)
            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            oppdaterPeriode =
                                NyHusstandsbarnperiode(
                                    idHusstandsbarn = eksisterendeHusstandsbarn.id!!,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    fraOgMed = sistePeriode.datoFom!!.plusMonths(2),
                                    tilOgMed = null,
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
                oppdatertHusstandsbarn?.perioder shouldNotBe emptySet<HusstandsbarnperiodeDto>()
                oppdatertHusstandsbarn!!.perioder shouldHaveSize 3
                oppdatertHusstandsbarn.perioder.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                oppdatertHusstandsbarn.perioder.maxBy { it.datoFom!! }.datoFom shouldBe
                    request.oppdatereHusstandsmedlem!!.oppdaterPeriode!!.fraOgMed
                oppdatertHusstandsbarn.perioder.maxBy { it.datoFom!! }.kilde shouldBe
                    Kilde.MANUELL
            }
        }

        @Test
        fun `skal kunne slette husstandsbarnperiode`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()
            behandling.grunnlag.addAll(opprettBoforholdBearbeidetGrunnlag(behandling))
            testdataManager.lagreBehandlingNewTransaction(behandling)
            val eksisterendeHusstandsbarn = behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }

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
                oppdatertHusstandsbarn?.perioder shouldNotBe emptySet<HusstandsbarnperiodeDto>()
                oppdatertHusstandsbarn!!.perioder shouldHaveSize 2
                oppdatertHusstandsbarn.perioder.find { Kilde.MANUELL == it.kilde } shouldBe null
                oppdatertHusstandsbarn.perioder.maxBy { it.datoFom!! }.kilde shouldBe
                    Kilde.OFFENTLIG
            }
        }

        @Test
        open fun `skal kunne legge til et nytt husstandsbarn`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()
            behandling.grunnlag.addAll(opprettBoforholdBearbeidetGrunnlag(behandling))
            testdataManager.lagreBehandlingNewTransaction(behandling)
            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsmedlem =
                        OppdatereHusstandsmedlem(
                            opprett =
                                PersonaliaHusstandsbarn(
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
                it.body?.valideringsfeil?.husstandsbarn shouldBe emptyList()
                it.body?.oppdatertHusstandsbarn shouldNotBe null
            }

            assertSoftly(boforholdResponse.body!!.oppdatertHusstandsbarn) { oppdatertHusstandsbarn ->
                oppdatertHusstandsbarn!!.kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsbarn.ident shouldBe
                    request.oppdatereHusstandsmedlem!!.opprett!!.personident!!.verdi
                oppdatertHusstandsbarn.navn shouldBe request.oppdatereHusstandsmedlem!!.opprett!!.navn
                oppdatertHusstandsbarn.perioder.shouldNotBeEmpty()
                oppdatertHusstandsbarn.perioder.first().kilde shouldBe Kilde.MANUELL
                oppdatertHusstandsbarn.perioder.first().datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                oppdatertHusstandsbarn.perioder.first().datoTom.shouldBeNull()
            }

            assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get().husstandsbarn) {
                it.size shouldBe 3
                it.find { nyttBarn ->
                    nyttBarn.ident ==
                        request.oppdatereHusstandsmedlem!!.opprett!!.personident!!.verdi
                } shouldNotBe null
            }
        }

        @Test
        fun `skal kunne slette manuelt husstandsbarn`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            val manueltHusstandsbarn = behandling.husstandsbarn.first()
            manueltHusstandsbarn.kilde = Kilde.MANUELL
            testdataManager.lagreBehandlingNewTransaction(behandling)

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
    }

    private fun opprettBoforholdBearbeidetGrunnlag(behandling: Behandling): List<Grunnlag> {
        return behandling.husstandsbarn.groupBy { it.ident }.map { (ident, husstandsbarn) ->
            Grunnlag(
                behandling = behandling,
                type = Grunnlagsdatatype.BOFORHOLD,
                erBearbeidet = true,
                gjelder = ident,
                aktiv = LocalDateTime.now(),
                rolle = behandling.bidragsmottaker!!,
                innhentet = LocalDateTime.now(),
                data =
                    commonObjectmapper.writeValueAsString(
                        husstandsbarn.flatMap { hb ->
                            hb.perioder.map {
                                BoforholdResponse(
                                    relatertPersonPersonId = hb.ident,
                                    periodeFom = it.datoFom!!,
                                    periodeTom = it.datoTom,
                                    kilde = it.kilde,
                                    bostatus = it.bostatus,
                                    fødselsdato = hb.fødselsdato,
                                )
                            }
                        },
                    ),
            )
        }
    }
}
