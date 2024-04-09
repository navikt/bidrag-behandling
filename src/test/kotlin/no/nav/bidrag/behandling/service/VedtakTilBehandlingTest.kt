package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.grunnlag.ainntektListe
import no.nav.bidrag.behandling.transformers.grunnlag.skattegrunnlagListe
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.filtrerEtterTypeOgIdent
import no.nav.bidrag.behandling.utils.testdata.hentFil
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
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

@ExtendWith(SpringExtension::class)
class VedtakTilBehandlingTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var grunnlagService: GrunnlagService

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
                beregningService,
                vedtakConsumer,
                sakConsumer,
                unleash,
            )
        every {
            behandlingService.oppdaterBehandling(
                any(),
                any(),
            )
        } returns Unit
        every { grunnlagService.oppdatereGrunnlagForBehandling(any()) } returns Unit

        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        stubSjablonProvider()
        stubPersonConsumer()
        stubTokenUtils()
        stubSaksbehandlernavnProvider()
        stubKodeverkProvider()
    }

    @Test
    fun `Skal konvertere vedtak til behandling for lesemodus`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_response")
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe SAKSNUMMER
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            avslag shouldBe null
            virkningstidspunkt shouldBe LocalDate.parse("2022-11-01")
            soknadFra shouldBe SøktAvType.BIDRAGSMOTTAKER
            stonadstype shouldBe Stønadstype.FORSKUDD
            behandlerEnhet shouldBe "4806"
            mottattdato shouldBe LocalDate.parse("2023-01-01")
            vedtakstype shouldBe Vedtakstype.FASTSETTELSE
            vedtaksid shouldBe null
            refVedtaksid shouldBe 1
            soknadsid shouldBe 101
            opprettetAv shouldBe "Z994977"
            opprettetAvNavn shouldBe "F_Z994977 E_Z994977"
            virkningstidspunktsbegrunnelseIVedtakOgNotat shouldBe "Notat virkningstidspunkt med i vedtak"
            virkningstidspunktbegrunnelseKunINotat shouldBe "Notat virkningstidspunkt"
            boforholdsbegrunnelseIVedtakOgNotat shouldBe "Notat boforhold med i vedtak"
            boforholdsbegrunnelseKunINotat shouldBe "Notat boforhold"
            inntektsbegrunnelseIVedtakOgNotat shouldBe "Notat inntekt med i vedtak"
            inntektsbegrunnelseKunINotat shouldBe "Notat inntekt"
            validerRoller()
            validerHusstandsbarn()
            validerSivilstand()
            validerInntekter()
            validerGrunnlag()
        }
    }

    @Test
    fun `Skal konvertere vedtak til behandling for lesemodus hvis resultat er avslag`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_respons_resultat_avslag")
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe SAKSNUMMER
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_KRAVFREMSETTELSE
            avslag shouldBe null
            virkningstidspunkt shouldBe LocalDate.parse("2024-02-01")
            opprinneligVirkningstidspunkt shouldBe LocalDate.parse("2024-02-01")
        }
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for en forskudd behandling`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_response")
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
            saksnummer shouldBe "1233333"
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            avslag shouldBe null
            virkningstidspunkt shouldBe LocalDate.parse("2022-11-01")
            opprinneligVirkningstidspunkt shouldBe LocalDate.parse("2022-11-01")
            søktFomDato shouldBe LocalDate.parse("2021-01-01")
            soknadFra shouldBe SøktAvType.BIDRAGSPLIKTIG
            stonadstype shouldBe Stønadstype.FORSKUDD
            behandlerEnhet shouldBe "9999"
            mottattdato shouldBe LocalDate.parse("2024-01-01")
            vedtakstype shouldBe Vedtakstype.KLAGE
            vedtaksid shouldBe null
            soknadRefId shouldBe 222
            soknadsid shouldBe 100
            opprettetAv shouldBe "Z99999"
            opprettetAvNavn shouldBe "Fornavn Etternavn"
            refVedtaksid shouldBe 12333

            validerRoller()
            assertSoftly(inntekter) {
                this shouldHaveSize 15
                val inntekt12Mnd = find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND }
                val inntekt12MndOpprinnelig = find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAK }
                inntekt12Mnd shouldNotBe null
                inntekt12Mnd!!.taMed shouldBe false
                inntekt12Mnd!!.kilde shouldBe Kilde.OFFENTLIG

                inntekt12MndOpprinnelig shouldNotBe null
                inntekt12MndOpprinnelig!!.taMed shouldBe true
                inntekt12MndOpprinnelig.kilde shouldBe Kilde.MANUELL
                inntekt12Mnd.isEqual(inntekt12MndOpprinnelig)

                inntekt12Mnd.opprinneligFom shouldBe LocalDate.parse("2023-02-01")
                inntekt12Mnd.opprinneligTom shouldBe LocalDate.parse("2023-12-31")
                inntekt12Mnd.datoFom shouldBe LocalDate.parse("2023-02-01")
                inntekt12Mnd.datoTom shouldBe LocalDate.parse("2024-01-31")
                inntekt12Mnd.belop shouldBe BigDecimal(25245987)
                assertSoftly(inntekt12Mnd.inntektsposter.toList()) {
                    this shouldHaveSize 1
                    this[0].beløp shouldBe BigDecimal(25245987)
                    this[0].kode shouldBe "fastloenn"
                    this[0].inntektstype shouldBe Inntektstype.AAP
                }

                val inntekt3Mnd = find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_3MND }
                val inntekt3MndOpprinnelig = find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAK }
                inntekt3Mnd shouldNotBe null
                inntekt3MndOpprinnelig shouldNotBe null
                inntekt3Mnd!!.taMed shouldBe false
                inntekt3MndOpprinnelig!!.taMed shouldBe true
                inntekt3MndOpprinnelig!!.kilde shouldBe Kilde.MANUELL
                inntekt3Mnd.isEqual(inntekt3MndOpprinnelig)
                inntekt3Mnd.opprinneligFom shouldBe LocalDate.parse("2023-11-01")
                inntekt3Mnd.datoFom shouldBe LocalDate.parse("2023-11-01")
                inntekt3Mnd.opprinneligTom shouldBe LocalDate.parse("2024-01-31")
                inntekt3Mnd.datoTom shouldBe LocalDate.parse("2024-01-31")
                inntekt3Mnd.inntektsposter shouldHaveSize 1
                inntekt3Mnd.kilde shouldBe Kilde.OFFENTLIG
                inntekt3Mnd.belop shouldBe BigDecimal(5330000)
                inntekt3Mnd.ident shouldBe testdataBM.ident
                assertSoftly(inntekt3Mnd.inntektsposter.toList()) {
                    this shouldHaveSize 1
                    this[0].beløp shouldBe BigDecimal(5330000)
                    this[0].kode shouldBe "fastloenn"
                    this[0].inntektstype shouldBe Inntektstype.AAP
                }

                val saksbehandlersBeregnetInntekt =
                    find { it.type == Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT }
                saksbehandlersBeregnetInntekt shouldNotBe null
                saksbehandlersBeregnetInntekt!!.belop shouldBe BigDecimal(300000)
                saksbehandlersBeregnetInntekt.kilde shouldBe Kilde.MANUELL
                saksbehandlersBeregnetInntekt.opprinneligFom shouldBe null
                saksbehandlersBeregnetInntekt.datoFom shouldBe LocalDate.parse("2023-01-01")
                saksbehandlersBeregnetInntekt.opprinneligTom shouldBe null
                saksbehandlersBeregnetInntekt.datoTom shouldBe LocalDate.parse("2024-05-31")
                saksbehandlersBeregnetInntekt.taMed shouldBe true
                saksbehandlersBeregnetInntekt.ident shouldBe testdataBM.ident

                val barnetillegg = find { it.type == Inntektsrapportering.BARNETILLEGG }
                barnetillegg!!.gjelderBarn shouldBe testdataBarn2.ident
                barnetillegg.ident shouldBe testdataBM.ident
                barnetillegg.kilde shouldBe Kilde.MANUELL
            }

            assertSoftly(grunnlag) {
                this shouldHaveSize 18
                filter { it.erBearbeidet && it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER }.shouldHaveSize(
                    7,
                )
                filter { it.erBearbeidet && it.rolle.rolletype == Rolletype.BARN }.shouldHaveSize(1)
                filter { !it.erBearbeidet && it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER }.shouldHaveSize(
                    9,
                )
                filter { !it.erBearbeidet && it.rolle.rolletype == Rolletype.BARN }.shouldHaveSize(1)
            }
            assertSoftly(sivilstand.toList()) {
                this shouldHaveSize 2
                this[0].sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                this[0].kilde shouldBe Kilde.OFFENTLIG
                this[0].datoFom shouldBe LocalDate.parse("2022-11-01")
                this[0].datoTom shouldBe LocalDate.parse("2023-06-30")

                this[1].sivilstand shouldBe Sivilstandskode.GIFT_SAMBOER
                this[1].kilde shouldBe Kilde.MANUELL
                this[1].datoFom shouldBe LocalDate.parse("2023-07-01")
                this[1].datoTom shouldBe null
            }

            assertSoftly(husstandsbarn.toList()) {
                this shouldHaveSize 6
                assertSoftly(filter { Kilde.OFFENTLIG == it.kilde }) {
                    this shouldHaveSize 2
                    this[0].ident shouldBe testdataBarn1.ident
                    this[0].navn shouldBe null
                    this[0].perioder shouldHaveSize 1
                    this[1].ident shouldBe testdataBarn2.ident
                    this[1].navn shouldBe null
                    this[1].perioder shouldHaveSize 2
                }

                val husstandsmedlemUtenIdent = find { it.ident == null }
                husstandsmedlemUtenIdent shouldNotBe null
                husstandsmedlemUtenIdent!!.navn shouldBe "Per Hansen"
                husstandsmedlemUtenIdent.fødselsdato shouldBe LocalDate.parse("2024-02-06")
            }
        }
    }

    private fun Inntekt.isEqual(other: Inntekt) {
        opprinneligFom shouldBe other.opprinneligFom
        opprinneligTom shouldBe other.opprinneligTom
        datoFom shouldBe other.datoFom
        datoTom shouldBe other.datoTom
        belop shouldBe other.belop
        assertSoftly(inntektsposter.toList()) {
            this shouldHaveSize other.inntektsposter.size
            this[0].beløp shouldBe other.inntektsposter.first().beløp
            this[0].kode shouldBe other.inntektsposter.first().kode
            this[0].inntektstype shouldBe other.inntektsposter.first().inntektstype
        }
    }

    @Test
    fun `skal konvertere vedtak avslag til behandling`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_response_avslag")
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            avslag shouldBe Resultatkode.FULLT_UNDERHOLDT_AV_OFFENTLIG
            årsak shouldBe null
            saksnummer shouldBe SAKSNUMMER
            virkningstidspunkt shouldBe null
            opprinneligVirkningstidspunkt shouldBe LocalDate.parse("2022-01-01")
            soknadFra shouldBe SøktAvType.BIDRAGSMOTTAKER
            stonadstype shouldBe Stønadstype.FORSKUDD
            behandlerEnhet shouldBe "4806"
            mottattdato shouldBe LocalDate.parse("2023-01-01")
            vedtakstype shouldBe Vedtakstype.FASTSETTELSE
            opprettetAv shouldBe "Z994977"
            opprettetAvNavn shouldBe "F_Z994977 E_Z994977"

            validerRoller()
        }
    }

    fun filTilVedtakDto(filnavn: String): VedtakDto {
        return commonObjectmapper.readValue(
            hentFil("/__files/$filnavn.json"),
            VedtakDto::class.java,
        )
    }

    private fun Behandling.validerInntekter() {
        assertSoftly(inntekter) {
            size shouldBe 13
            val barnetillegg = filter { it.type == Inntektsrapportering.BARNETILLEGG }
            assertSoftly(barnetillegg) {
                shouldHaveSize(1)
                it[0].ident shouldBe testdataBM.ident
                it[0].belop shouldBe BigDecimal(5000)
                it[0].gjelderBarn shouldBe testdataBarn2.ident
                it[0].taMed shouldBe false
                it[0].datoFom shouldBe LocalDate.parse("2024-01-01")
                it[0].datoTom shouldBe LocalDate.parse("2024-01-31")
                it[0].inntektsposter shouldHaveSize 0
                it[0].kilde shouldBe Kilde.MANUELL
            }
            val ainntekt = filter { it.type == Inntektsrapportering.AINNTEKT }
            assertSoftly(ainntekt) {
                shouldHaveSize(2)
                it[0].ident shouldBe testdataBM.ident
                it[0].belop shouldBe BigDecimal(2859987)
                it[0].gjelderBarn shouldBe null
                it[0].taMed shouldBe true
                it[0].kilde shouldBe Kilde.OFFENTLIG
                it[0].datoFom shouldBe LocalDate.parse("2022-01-01")
                it[0].datoTom shouldBe LocalDate.parse("2022-12-31")
                it[0].opprinneligFom shouldBe LocalDate.parse("2023-01-01")
                it[0].opprinneligTom shouldBe LocalDate.parse("2023-06-30")
                it[0].inntektsposter shouldHaveSize 1
                assertSoftly(it[0].inntektsposter.first()) {
                    beløp shouldBe BigDecimal(2859987)
                    kode shouldBe "fastloenn"
                    inntektstype shouldBe Inntektstype.AAP
                }
            }
            filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.LIGNINGSINNTEKT } shouldHaveSize 3
            filter { it.type == Inntektsrapportering.KAPITALINNTEKT } shouldHaveSize 3
            filter { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_3MND } shouldHaveSize 1
        }
    }

    fun Behandling.validerHusstandsbarn() {
        assertSoftly(husstandsbarn) {
            size shouldBe 6
            val barn1 = husstandsbarn.find { it.ident == testdataBarn1.ident }
            val barn2 = husstandsbarn.find { it.ident == testdataBarn2.ident }
            assertSoftly(barn1!!) {
                it.ident shouldBe testdataBarn1.ident
                it.fødselsdato shouldBe testdataBarn1.foedselsdato
                it.navn shouldBe null
                it.kilde shouldBe Kilde.OFFENTLIG
                val periode = it.perioder.first()
                assertSoftly(periode) {
                    it.datoFom shouldBe LocalDate.of(2022, 11, 1)
                    it.datoTom shouldBe null
                    it.bostatus shouldBe Bostatuskode.MED_FORELDER
                    it.kilde shouldBe Kilde.OFFENTLIG
                }
            }
            assertSoftly(barn2!!) {
                it.ident shouldBe testdataBarn2.ident
                it.fødselsdato shouldBe testdataBarn2.foedselsdato
                it.navn shouldBe null
                it.kilde shouldBe Kilde.OFFENTLIG
                it.perioder shouldHaveSize 2
                val periode = it.perioder.first()
                assertSoftly(periode) {
                    it.datoFom shouldBe LocalDate.of(2023, 7, 1)
                    it.datoTom shouldBe null
                    it.bostatus shouldBe Bostatuskode.REGNES_IKKE_SOM_BARN
                    it.kilde shouldBe Kilde.OFFENTLIG
                }
                assertSoftly(it.perioder.toList()[1]) {
                    it.datoFom shouldBe LocalDate.of(2022, 11, 1)
                    it.datoTom shouldBe LocalDate.of(2023, 6, 30)
                    it.bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                    it.kilde shouldBe Kilde.OFFENTLIG
                }
            }
            assertSoftly(it.last()) {
                it.ident shouldBe null
                it.fødselsdato shouldBe LocalDate.parse("2024-02-06")
                it.navn shouldBe "Per Hansen"
                it.kilde shouldBe Kilde.MANUELL
                it.perioder shouldHaveSize 1
                val periode = it.perioder.first()
                assertSoftly(periode) {
                    it.datoFom shouldBe LocalDate.parse("2024-02-01")
                    it.datoTom shouldBe null
                    it.bostatus shouldBe Bostatuskode.MED_FORELDER
                    it.kilde shouldBe Kilde.MANUELL
                }
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
                it.foedselsdato shouldBe testdataBM.foedselsdato
                it.navn shouldBe null
                it.deleted shouldBe false
            }
            val søknadsbarn = roller.filter { it.rolletype == Rolletype.BARN }
            søknadsbarn shouldHaveSize 2
            val søknadsbarn1 = søknadsbarn[0]
            val søknadsbarn2 = søknadsbarn[1]
            assertSoftly(søknadsbarn1) {
                it.ident shouldBe testdataBarn1.ident
                it.foedselsdato shouldBe testdataBarn1.foedselsdato
                it.navn shouldBe null
                it.deleted shouldBe false
            }
            assertSoftly(søknadsbarn2) {
                it.ident shouldBe testdataBarn2.ident
                it.foedselsdato shouldBe testdataBarn2.foedselsdato
                it.navn shouldBe null
                it.deleted shouldBe false
            }
        }
    }

    private fun Behandling.validerSivilstand() {
        assertSoftly(sivilstand.toList()) {
            size shouldBe 2
            assertSoftly(this[0]) {
                it.datoFom shouldBe LocalDate.parse("2022-11-01")
                it.datoTom shouldBe LocalDate.parse("2023-06-30")
                it.kilde shouldBe Kilde.OFFENTLIG
                it.sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
            }
            assertSoftly(this[1]) {
                it.datoFom shouldBe LocalDate.parse("2023-07-01")
                it.datoTom shouldBe null
                it.kilde shouldBe Kilde.MANUELL
                it.sivilstand shouldBe Sivilstandskode.GIFT_SAMBOER
            }
        }
    }

    private fun Behandling.validerGrunnlag() {
        assertSoftly(grunnlagListe) {
            size shouldBe 18
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                testdataBarn2.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SMÅBARNSTILLEGG,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.KONTANTSTØTTE,
                testdataBM.ident,
            ) shouldHaveSize 1
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
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.ARBEIDSFORHOLD,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SIVILSTAND,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BOFORHOLD,
                testdataBM.ident,
            ) shouldHaveSize 1

            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
                testdataBM.ident,
                true,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
                testdataBarn2.ident,
                true,
            ) shouldHaveSize 1

            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BARNETILLEGG,
                testdataBM.ident,
                true,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BARNETILSYN,
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
                skattepliktig[0].konverterData<SummerteInntekter<SummertÅrsinntekt>>()
            skattepliktigInnhold!!.versjon shouldBe "V1"
            skattepliktigInnhold.inntekter shouldHaveSize 10
            skattepliktigInnhold.inntekter.ainntektListe shouldHaveSize 4
            skattepliktigInnhold.inntekter.skattegrunnlagListe shouldHaveSize 6
        }
    }
}
