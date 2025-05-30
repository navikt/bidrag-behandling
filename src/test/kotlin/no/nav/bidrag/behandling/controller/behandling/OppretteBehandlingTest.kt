package no.nav.bidrag.behandling.controller.behandling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettKategoriRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertNotNull

@Suppress("NonAsciiCharacters")
class OppretteBehandlingTest : BehandlingControllerTest() {
    @Nested
    @DisplayName("Positiv testing av  opprette behandling")
    open inner class OppretteBehandlingPositiv {
        @Test
        @Transactional
        open fun `skal opprette en behandling med null opprettetDato og så hente den`() {
            // gitt
            val personidentBm = Personident("12345678910")
            val personidentBarn1 = Personident("12345678912")
            val personidentBarn2 = Personident("12345678913")

            stubUtils.stubHenteGrunnlag()

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn1,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn2,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(555),
                    ),
                )
            val behandlingReq = oppretteBehandlingRequestTest("1900000", "en12", roller, 1234444)

            // hvis
            val behandlingRes =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(behandlingReq),
                    OpprettBehandlingResponse::class.java,
                )

            // så
            Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

            val behandling = behandlingRepository.findBehandlingById(behandlingRes.body!!.id)
            assertNotNull(behandling)
            Assertions.assertEquals(3, behandling.get().roller.size)
        }

        @Test
        fun `skal opprette en behandling`() {
            val personidentBm = Personident("12345678912")
            val personidentBarn = Personident("12345678912")

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(499),
                    ),
                )
            val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", roller)

            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        }

        @Test
        fun `skal opprette en behandling for særbidrag`() {
            val personidentBp = Personident("12345678912")
            val personidentBm = Personident("213213")
            val personidentBarn = Personident("123213123")

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(499),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSPLIKTIG,
                        personidentBp,
                        fødselsdato = LocalDate.now().minusMonths(499),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest("1900000", "en12", roller, søknadsid = 1239988330001323)
                    .copy(
                        engangsbeløpstype = Engangsbeløptype.SÆRBIDRAG,
                        stønadstype = null,
                        vedtakstype = Vedtakstype.FASTSETTELSE,
                        innkrevingstype = Innkrevingstype.UTEN_INNKREVING,
                        kategori =
                            OpprettKategoriRequestDto(
                                kategori = Særbidragskategori.KONFIRMASJON.name,
                            ),
                    )

            stubUtils.stubHenteGrunnlag()
            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    OpprettBehandlingResponse::class.java,
                )
            Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)

            val behandling = testdataManager.hentBehandling(responseMedNull.body!!.id)!!

            behandling.virkningstidspunkt shouldBe LocalDate.now().withDayOfMonth(1)
            behandling.engangsbeloptype shouldBe Engangsbeløptype.SÆRBIDRAG
            behandling.særbidragKategori shouldBe Særbidragskategori.KONFIRMASJON
            behandling.stonadstype shouldBe null
            behandling.innkrevingstype shouldBe Innkrevingstype.UTEN_INNKREVING
            behandling.utgift shouldNotBe null
        }

        @Test
        fun `skal opprette en behandling for særbidrag med kategori ANNET`() {
            val personidentBp = Personident("12345678912")
            val personidentBm = Personident("213213")
            val personidentBarn = Personident("123213123")

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(499),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSPLIKTIG,
                        personidentBp,
                        fødselsdato = LocalDate.now().minusMonths(499),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest("1900000", "en12", roller, søknadsid = 12399883303001323)
                    .copy(
                        engangsbeløpstype = Engangsbeløptype.SÆRBIDRAG,
                        stønadstype = null,
                        vedtakstype = Vedtakstype.FASTSETTELSE,
                        kategori =
                            OpprettKategoriRequestDto(
                                kategori = Særbidragskategori.ANNET.name,
                                beskrivelse = "Batteri til høreapparat",
                            ),
                    )

            stubUtils.stubHenteGrunnlag()
            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    OpprettBehandlingResponse::class.java,
                )
            Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)

            val behandling = testdataManager.hentBehandling(responseMedNull.body!!.id)!!

            behandling.virkningstidspunkt shouldBe LocalDate.now().withDayOfMonth(1)
            behandling.engangsbeloptype shouldBe Engangsbeløptype.SÆRBIDRAG
            behandling.særbidragKategori shouldBe Særbidragskategori.ANNET
            behandling.stonadstype shouldBe null
            behandling.kategoriBeskrivelse shouldBe "Batteri til høreapparat"
            behandling.utgift shouldNotBe null
        }

        @Test
        fun `skal opprette en behandling og forsendelse for stonadType BIDRAG`() {
            // gitt
            val personidentBarn = Personident("12345678910")
            val personidentBm = Personident("12345678911")

            stubUtils.stubHenteGrunnlag()

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(609),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest(
                    "1900000",
                    "en12",
                    roller,
                    12312312321,
                ).copy(stønadstype = Stønadstype.FORSKUDD)

            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)
            /*
            stubUtils.Verify()
                .opprettForsendelseKaltMed("\"gjelderIdent\":\"12345678911\"")
            stubUtils.Verify()
                .opprettForsendelseKaltMed("\"barnIBehandling\":[\"12345678910\"]")

             */
        }

        @Test
        fun `skal opprette en behandling og ikke opprette forsendelse for forskudd`() {
            // gitt
            val personidentBarn = Personident("12345678912")
            val personidentBm = Personident("12345678912")

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(511),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest("1900000", "en12", roller)

            // hvis
            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )

            // så
            Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)
            stubUtils.Verify().opprettForsendelseIkkeKalt()
        }

        @Test
        fun `skal opprette en behandling og ignorere feil hvis opprett forsendelse feiler`() {
            // gitt
            val personidentBarn = Personident("12345678910")
            val personidentBm = Personident("12345678911")

            stubUtils.stubOpprettForsendelse(status = HttpStatus.BAD_REQUEST)
            stubUtils.stubHenteGrunnlag()

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(609),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest(
                    "1900000",
                    "en12",
                    roller,
                ).copy(stønadstype = Stønadstype.FORSKUDD)

            // hvis
            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )

            // så
            Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        }

        @Test
        fun `skal opprette behandling som inkluderer barn med navn men uten ident`() {
            // gitt
            val personidentBm = Personident("12345678910")

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        null,
                        fødselsdato = LocalDate.now().minusMonths(136),
                        navn = "Ola Dunk",
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(682),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest("1900000", "en12", roller)

            // when
            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )

            // then
            Assertions.assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        }

        @Test
        open fun `skal ikke opprette behandling hvis det finnes en behandling med samme søknadsid`() {
            val behandling = testdataManager.oppretteBehandling()
            behandling.soknadsid = 1239999
            testdataManager.lagreBehandling(behandling)
            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        null,
                        fødselsdato = LocalDate.now().minusMonths(136),
                        navn = "Ola Dunk",
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        Personident(testdataBM.ident),
                        fødselsdato = LocalDate.now().minusMonths(682),
                    ),
                )
            val request =
                oppretteBehandlingRequestTest("1900000", "en12", roller, behandling.soknadsid!!)
            val response =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(request),
                    OpprettBehandlingResponse::class.java,
                )

            response.body!!.id shouldBe behandling.id
        }
    }

    @Nested
    @DisplayName("Negativ testing av opprette av behandling")
    open inner class OppretteBehandlingNegativ {
        @Test
        fun `skal ikke opprette en behandling med bare én rolle`() {
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest(
                    "1900000",
                    "en12",
                    setOf(
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident("12345678910"),
                            fødselsdato = LocalDate.now().minusMonths(136),
                        ),
                    ),
                )

            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
        }

        @Test
        fun `skal ikke opprette en behandling uten roller`() {
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest("1900000", "en12", setOf())

            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
        }

        @Test
        fun `skal ikke opprette behandling som inkluderer barn uten navn og ident`() {
            // given
            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        null,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(399),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest("1900000", "en12", roller)

            // when
            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )

            // then
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
        }

        @Test
        fun `skal ikke opprette behandling som inkluderer BP uten ident`() {
            // gitt
            val personidentBarn = Personident("12351234567")

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        null,
                        navn = "Ola Dunk",
                        fødselsdato = LocalDate.now().minusMonths(529),
                    ),
                )
            val testBehandlingMedNull =
                oppretteBehandlingRequestTest("1900000", "en12", roller)

            // when
            val responseMedNull =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedNull),
                    Void::class.java,
                )

            // then
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
        }

        @Test
        fun `skal ikke opprette en behandling med blank sak`() {
            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(444),
                    ),
                )
            val testBehandlingMedBlank = oppretteBehandlingRequestTest("   ", "en12", roller)
            val responseMedBlank =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedBlank),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
        }

        @Test
        fun `skal ikke opprette en behandling med blank sak1`() {
            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(478),
                    ),
                )
            val testBehandlingMedBlank = oppretteBehandlingRequestTest("", "en12", roller)
            val responseMedBlank =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedBlank),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
        }

        @Test
        fun `skal ikke opprette en behandling dersom saksnummer er mer enn sju tegn`() {
            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        Personident("12345678911"),
                        fødselsdato = LocalDate.now().minusMonths(533),
                    ),
                )
            val testBehandlingMedBlank =
                oppretteBehandlingRequestTest("3456789565", "en12", roller)
            val responseMedBlank =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(testBehandlingMedBlank),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
        }

        @Test
        fun `skal ikke opprette en behandling med ugyldig enhet`() {
            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        Personident("12345678910"),
                        fødselsdato = LocalDate.now().minusMonths(500),
                    ),
                )
            val b = oppretteBehandlingRequestTest(null, "10101", roller)
            val r =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(b),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.BAD_REQUEST, r.statusCode)
        }

        @Test
        fun `skal ikke opprette en særbidrag behandling hvis BP har løpende bidrag med utenlandsk valuta`() {
            val personidentBp = Personident("12345678912")
            val personidentBm = Personident("213213")
            val personidentBarn = Personident("123213123")
            stubUtils.stubBidragBeløpshistorikkLøpendeSaker("løpende-bidragssaker-bp_utenlandsk_valuta")

            val roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        personidentBarn,
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        personidentBm,
                        fødselsdato = LocalDate.now().minusMonths(499),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSPLIKTIG,
                        personidentBp,
                        fødselsdato = LocalDate.now().minusMonths(499),
                    ),
                )
            val request =
                oppretteBehandlingRequestTest("1900000", "en12", roller, søknadsid = 1239988330001323)
                    .copy(
                        engangsbeløpstype = Engangsbeløptype.SÆRBIDRAG,
                        stønadstype = null,
                        vedtakstype = Vedtakstype.FASTSETTELSE,
                        innkrevingstype = Innkrevingstype.UTEN_INNKREVING,
                        kategori =
                            OpprettKategoriRequestDto(
                                kategori = Særbidragskategori.KONFIRMASJON.name,
                            ),
                    )
            val r =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling",
                    HttpMethod.POST,
                    HttpEntity(request),
                    Void::class.java,
                )
            Assertions.assertEquals(HttpStatus.PRECONDITION_FAILED, r.statusCode)
        }
    }
}
