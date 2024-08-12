package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.grunnlag.ainntektListe
import no.nav.bidrag.behandling.transformers.grunnlag.skattegrunnlagListe
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.filtrerEtterTypeOgIdent
import no.nav.bidrag.behandling.utils.testdata.hentFil
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import stubSaksbehandlernavnProvider
import stubTokenUtils
import java.math.BigDecimal
import java.time.LocalDate
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

@ExtendWith(SpringExtension::class)
class VedtakTilBehandlingSærbidragTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var grunnlagService: GrunnlagService

    @MockkBean
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer
    lateinit var vedtakService: VedtakService
    lateinit var beregningService: BeregningService

    val unleash = FakeUnleash()

    @BeforeEach
    fun initMocks() {
        beregningService =
            BeregningService(
                behandlingService,
            )
        vedtakService =
            VedtakService(
                behandlingService,
                grunnlagService,
                notatOpplysningerService,
                beregningService,
                tilgangskontrollService,
                vedtakConsumer,
                sakConsumer,
                unleash,
            )
        every { grunnlagService.oppdatereGrunnlagForBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangSak(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every { notatOpplysningerService.opprettNotat(any()) } returns "213"
        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        stubSjablonProvider()
        stubPersonConsumer()
        stubTokenUtils()
        stubSaksbehandlernavnProvider()
        stubKodeverkProvider()
    }

    @Test
    fun `Skal konvertere vedtak til behandling for lesemodus for SÆRBIDRAG`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_response-særbidrag")
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe SAKSNUMMER
            årsak shouldBe null
            avslag shouldBe null
            virkningstidspunkt shouldBe LocalDate.parse("2024-07-01")
            soknadFra shouldBe SøktAvType.BIDRAGSPLIKTIG
            stonadstype shouldBe null
            engangsbeloptype shouldBe Engangsbeløptype.SÆRBIDRAG
            behandlerEnhet shouldBe "4806"
            mottattdato shouldBe LocalDate.parse("2024-01-15")
            klageMottattdato shouldBe null
            vedtakstype shouldBe Vedtakstype.ENDRING
            vedtaksid shouldBe null
            refVedtaksid shouldBe 1
            kategori shouldBe "ANNET"
            kategoriBeskrivelse shouldBe "Utstyr til høreapparat"
            soknadsid shouldBe 101
            opprettetAv shouldBe "Z994977"
            opprettetAvNavn shouldBe null
            notater.find { Notattype.UTGIFTER == it.type }?.innhold shouldBe "Dette er en begrunnelse på hvorfor utgifter ble beregnet slik"
            validerUtgifter()
            validerRoller()
            validerHusstandsmedlem()
            validerInntekter()
            validerGrunnlag()
        }
    }

    @Test
    fun `Skal konvertere vedtak til behandling for lesemodus for SÆRBIDRAG med klage mottatt dato`() {
        val originalVedtak = filTilVedtakDto("vedtak_response-særbidrag")
        val vedtak1 =
            originalVedtak.copy(
                vedtakstidspunkt = LocalDate.parse("2024-02-01").atStartOfDay(),
                grunnlagListe =
                    originalVedtak.grunnlagListe.map {
                        if (it.type == Grunnlagstype.SØKNAD) {
                            it.copy(
                                innhold =
                                    POJONode(
                                        it.innholdTilObjekt<SøknadGrunnlag>().copy(
                                            mottattDato = LocalDate.parse("2024-05-01"),
                                            klageMottattDato = LocalDate.parse("2024-03-01"),
                                        ),
                                    ),
                            )
                        } else {
                            it
                        }
                    },
            )
        every { vedtakConsumer.hentVedtak(eq(1)) } returns vedtak1
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe SAKSNUMMER
            årsak shouldBe null
            avslag shouldBe null
            virkningstidspunkt shouldBe LocalDate.parse("2024-07-01")
            soknadFra shouldBe SøktAvType.BIDRAGSPLIKTIG
            stonadstype shouldBe null
            engangsbeloptype shouldBe Engangsbeløptype.SÆRBIDRAG
            behandlerEnhet shouldBe "4806"
            mottattdato shouldBe LocalDate.parse("2024-05-01")
            klageMottattdato shouldBe LocalDate.parse("2024-03-01")
            vedtakstype shouldBe Vedtakstype.ENDRING
            vedtaksid shouldBe null
            refVedtaksid shouldBe 1
            kategori shouldBe "ANNET"
            kategoriBeskrivelse shouldBe "Utstyr til høreapparat"
            soknadsid shouldBe 101
            opprettetAv shouldBe "Z994977"
            opprettetAvNavn shouldBe null
            notater.find { Notattype.UTGIFTER == it.type }?.innhold shouldBe
                "Dette er en begrunnelse på hvorfor utgifter ble beregnet slik"
            validerUtgifter()
            validerRoller()
            validerHusstandsmedlem()
            validerInntekter()
            validerGrunnlag()
        }
    }

    @Test
    fun `Skal opprette behandling og lagre vedtakstidspunkt for forrige vedtak`() {
        val originalVedtak = filTilVedtakDto("vedtak_response-særbidrag")
        val vedtak1 =
            originalVedtak.copy(
                vedtakstidspunkt = LocalDate.parse("2024-02-01").atStartOfDay(),
                engangsbeløpListe =
                    originalVedtak.engangsbeløpListe.map {
                        it.copy(
                            omgjørVedtakId = 123,
                        )
                    },
            )
        val vedtak2 =
            originalVedtak.copy(
                vedtakstidspunkt = LocalDate.parse("2024-03-01").atStartOfDay(),
                engangsbeløpListe =
                    originalVedtak.engangsbeløpListe.map {
                        it.copy(
                            omgjørVedtakId = 124,
                        )
                    },
            )
        val vedtak3 =
            originalVedtak.copy(
                vedtakstidspunkt = LocalDate.parse("2024-04-01").atStartOfDay(),
                engangsbeløpListe =
                    originalVedtak.engangsbeløpListe.map {
                        it.copy(
                            omgjørVedtakId = null,
                        )
                    },
            )
        every { vedtakConsumer.hentVedtak(eq(12333)) } returns vedtak1
        every { vedtakConsumer.hentVedtak(eq(123)) } returns vedtak2
        every { vedtakConsumer.hentVedtak(eq(124)) } returns vedtak3
        val behandling =
            vedtakService.konverterVedtakTilBehandling(
                OpprettBehandlingFraVedtakRequest(
                    vedtakstype = Vedtakstype.KLAGE,
                    søknadsid = 100,
                    søknadsreferanseid = 222,
                    søknadFra = SøktAvType.BIDRAGSPLIKTIG,
                    saksnummer = "123213213",
                    mottattdato = LocalDate.parse("2024-01-01"),
                    søktFomDato = LocalDate.parse("2021-01-01"),
                    behandlerenhet = "9999",
                ),
                12333,
            )!!

        assertSoftly(behandling) {
            opprinneligVedtakstidspunkt shouldHaveSize 3
            opprinneligVedtakstidspunkt shouldContain LocalDate.parse("2024-04-01").atStartOfDay()
            opprinneligVedtakstidspunkt shouldContain LocalDate.parse("2024-03-01").atStartOfDay()
            opprinneligVedtakstidspunkt shouldContain LocalDate.parse("2024-02-01").atStartOfDay()
        }
    }

    @Test
    fun `Skal opprette behandling med klage mottatt dato`() {
        val originalVedtak = filTilVedtakDto("vedtak_response-særbidrag")
        val vedtak1 =
            originalVedtak.copy(
                vedtakstidspunkt = LocalDate.parse("2024-02-01").atStartOfDay(),
                grunnlagListe =
                    originalVedtak.grunnlagListe.map {
                        if (it.type == Grunnlagstype.SØKNAD) {
                            it.copy(
                                innhold =
                                    POJONode(
                                        it.innholdTilObjekt<SøknadGrunnlag>().copy(
                                            mottattDato = LocalDate.parse("2024-05-01"),
                                            klageMottattDato = LocalDate.parse("2024-03-01"),
                                        ),
                                    ),
                            )
                        } else {
                            it
                        }
                    },
            )
        every { vedtakConsumer.hentVedtak(eq(12333)) } returns vedtak1
        val behandling =
            vedtakService.konverterVedtakTilBehandling(
                OpprettBehandlingFraVedtakRequest(
                    vedtakstype = Vedtakstype.KLAGE,
                    søknadsid = 100,
                    søknadsreferanseid = 222,
                    søknadFra = SøktAvType.BIDRAGSPLIKTIG,
                    saksnummer = "123213213",
                    mottattdato = LocalDate.parse("2024-07-01"),
                    søktFomDato = LocalDate.parse("2024-07-01"),
                    behandlerenhet = "9999",
                ),
                12333,
            )!!

        assertSoftly(behandling) {
            klageMottattdato shouldBe LocalDate.parse("2024-07-01")
            mottattdato shouldBe LocalDate.parse("2024-05-01")
        }
    }

    @Test
    fun `Skal konvertere vedtak til behandling for lesemodus hvis direkte avslag`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_respons_avslag-særbidrag")
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe "2400042"
            årsak shouldBe null
            avslag shouldBe Resultatkode.PRIVAT_AVTALE_OM_SÆRBIDRAG
            kategori shouldBe Særbidragskategori.ANNET.name
            kategoriBeskrivelse shouldBe "Utstyr til høreapparat"
            notater.find { Notattype.UTGIFTER == it.type }?.innhold shouldBe
                "Dette er en begrunnelse på hvorfor utgifter ble beregnet slik"
            virkningstidspunkt shouldBe LocalDate.parse("2024-07-01")
            opprinneligVirkningstidspunkt shouldBe LocalDate.parse("2024-07-01")
        }
    }

    fun filTilVedtakDto(filnavn: String): VedtakDto =
        commonObjectmapper.readValue(
            hentFil("/__files/$filnavn.json"),
            VedtakDto::class.java,
        )

    private fun Behandling.validerInntekter() {
        assertSoftly(inntekter) {
            size shouldBe 8
            filter { it.type == Inntektsrapportering.BARNETILLEGG } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.LØNN_MANUELT_BEREGNET } shouldHaveSize 2
            filter { it.type == Inntektsrapportering.KAPITALINNTEKT } shouldHaveSize 2
            filter { it.type == Inntektsrapportering.LIGNINGSINNTEKT } shouldHaveSize 2
            find { it.type == Inntektsrapportering.LØNN_MANUELT_BEREGNET && it.ident == testdataBP.ident } shouldNotBe null
            find { it.type == Inntektsrapportering.LIGNINGSINNTEKT && it.ident == testdataBM.ident } shouldNotBe null
            find { it.type == Inntektsrapportering.LØNN_MANUELT_BEREGNET && it.ident == testdataBarn1.ident } shouldNotBe null
        }
    }

    fun Behandling.validerHusstandsmedlem() {
        assertSoftly(husstandsmedlem) {
            size shouldBe 5
            val barn1 = husstandsmedlem.find { it.ident == testdataBarn1.ident }
            assertSoftly(barn1!!) {
                it.ident shouldBe testdataBarn1.ident
                it.fødselsdato shouldBe testdataBarn1.fødselsdato
                it.navn shouldBe null
                it.kilde shouldBe Kilde.OFFENTLIG
                val periode = it.perioder.first()
                assertSoftly(periode) {
                    it.datoFom shouldBe LocalDate.of(2024, 7, 1)
                    it.datoTom shouldBe null
                    it.bostatus shouldBe Bostatuskode.MED_FORELDER
                    it.kilde shouldBe Kilde.OFFENTLIG
                }
            }
        }
        husstandsmedlem.barn shouldHaveSize 4
        husstandsmedlem.voksneIHusstanden shouldNotBe null
        assertSoftly(husstandsmedlem.voksneIHusstanden!!) {
            it.rolle shouldNotBe null
            it.rolle!!.rolletype shouldBe Rolletype.BIDRAGSPLIKTIG
            it.kilde shouldBe Kilde.OFFENTLIG
            val periode = it.perioder.first()
            assertSoftly(periode) {
                it.datoFom shouldBe LocalDate.of(2024, 7, 1)
                it.datoTom shouldBe null
                it.bostatus shouldBe Bostatuskode.BOR_MED_ANDRE_VOKSNE
                it.kilde shouldBe Kilde.OFFENTLIG
            }
        }
    }

    fun Behandling.validerRoller() {
        assertSoftly(roller.toList()) {
            size shouldBe 3
            val bidragsmotaker = roller.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
            bidragsmotaker shouldNotBe null
            assertSoftly(bidragsmotaker!!) {
                it.ident shouldBe testdataBM.ident
                it.fødselsdato shouldBe testdataBM.fødselsdato
                it.navn shouldBe null
                it.deleted shouldBe false
            }
            val bidragspliktig = roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
            bidragspliktig shouldNotBe null
            assertSoftly(bidragspliktig!!) {
                it.ident shouldBe testdataBP.ident
                it.fødselsdato shouldBe testdataBP.fødselsdato
                it.navn shouldBe null
                it.deleted shouldBe false
            }
            val søknadsbarn = roller.find { it.rolletype == Rolletype.BARN }

            assertSoftly(søknadsbarn!!) {
                it.ident shouldBe testdataBarn1.ident
                it.fødselsdato shouldBe testdataBarn1.fødselsdato
                it.navn shouldBe null
                it.deleted shouldBe false
            }
        }
    }

    private fun Behandling.validerUtgifter() {
        utgift shouldNotBe null
        assertSoftly(utgift!!) {
            beløpDirekteBetaltAvBp shouldBe BigDecimal(2500)
            utgiftsposter shouldHaveSize 3
            assertSoftly(utgiftsposter.find { it.type == "Ny høreapparat" }!!) {
                kravbeløp shouldBe BigDecimal(9000)
                godkjentBeløp shouldBe BigDecimal(6000)
                type shouldBe "Ny høreapparat"
                betaltAvBp shouldBe false
                begrunnelse shouldBe "Inkluderer ikke frakt og andre kostnader"
                dato shouldBe LocalDate.parse("2024-05-06")
            }
        }
    }

    private fun Behandling.validerGrunnlag() {
        assertSoftly(grunnlagListe) {
            size shouldBe 17
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SMÅBARNSTILLEGG,
                testdataBM.ident,
            ) shouldHaveSize 0
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.KONTANTSTØTTE,
                testdataBM.ident,
            ) shouldHaveSize 0
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BARNETILLEGG,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BARNETILSYN,
                testdataBM.ident,
            ) shouldHaveSize 0
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.ARBEIDSFORHOLD,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BOFORHOLD,
                testdataBP.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                testdataBP.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
                testdataBM.ident,
                true,
            ) shouldHaveSize 0
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                testdataBP.ident,
                true,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BARNETILLEGG,
                testdataBM.ident,
                true,
            ) shouldHaveSize 1

            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SMÅBARNSTILLEGG,
                testdataBM.ident,
                true,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.KONTANTSTØTTE,
                testdataBM.ident,
                true,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                testdataBM.ident,
                true,
            ) shouldHaveSize 1

            val skattepliktig =
                filtrerEtterTypeOgIdent(
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    testdataBM.ident,
                    true,
                )
            skattepliktig shouldHaveSize 1
            val skattepliktigInnhold =
                skattepliktig[0].konvertereData<SummerteInntekter<SummertÅrsinntekt>>()
            skattepliktigInnhold!!.inntekter shouldHaveSize 4
            skattepliktigInnhold.inntekter.ainntektListe shouldHaveSize 0
            skattepliktigInnhold.inntekter.skattegrunnlagListe shouldHaveSize 4
        }
    }
}
