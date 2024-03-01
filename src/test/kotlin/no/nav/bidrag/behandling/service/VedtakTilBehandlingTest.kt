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
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.transformers.filtrerEtterTypeOgIdent
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
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

        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        stubSjablonProvider()
        stubPersonConsumer()
        stubTokenUtils()
        stubSaksbehandlernavnProvider()
        stubKodeverkProvider()
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for en forskudd behandling i lesemodus`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_response")
        val behandling = vedtakService.konverterVedtakTilBehandling(1)!!

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
            omgjørVedtaksid shouldBe 1
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
    fun `Skal opprette grunnlagsstruktur for en forskudd behandling`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_response")
        val behandling =
            vedtakService.konverterVedtakTilBehandling(
                OpprettBehandlingFraVedtakRequest(
                    vedtakId = 1,
                    vedtakstype = Vedtakstype.KLAGE,
                    søknadsid = 100,
                    søknadsreferanseid = 222,
                    søknadFra = SøktAvType.BIDRAGSPLIKTIG,
                    saksnummer = "123213213",
                    mottattdato = LocalDate.parse("2024-01-01"),
                    søktFomDato = LocalDate.parse("2021-01-01"),
                    behandlerenhet = "9999",
                ),
            )!!

        assertSoftly(behandling) {
            saksnummer shouldBe "1233333"
            årsak shouldBe null
            avslag shouldBe null
            virkningstidspunkt shouldBe null
            søktFomDato shouldBe LocalDate.parse("2021-01-01")
            soknadFra shouldBe SøktAvType.BIDRAGSPLIKTIG
            stonadstype shouldBe Stønadstype.FORSKUDD
            behandlerEnhet shouldBe "9999"
            mottattdato shouldBe LocalDate.parse("2024-01-01")
            vedtakstype shouldBe Vedtakstype.KLAGE
            vedtaksid shouldBe null
            soknadRefId shouldBe 222
            omgjørVedtaksid shouldBe 1
            soknadsid shouldBe 100
            opprettetAv shouldBe "Z99999"
            opprettetAvNavn shouldBe "Fornavn Etternavn"
        }
    }

    @Test
    fun `skal konvertere vedtak avslag til behandling`() {
        every { vedtakConsumer.hentVedtak(any()) } returns filTilVedtakDto("vedtak_response_avslag")
        val behandling = vedtakService.konverterVedtakTilBehandling(1)!!

        assertSoftly(behandling) {
            avslag shouldBe Resultatkode.FULLT_UNDERHOLDT_AV_OFFENTLIG
            årsak shouldBe null
            saksnummer shouldBe SAKSNUMMER
            virkningstidspunkt shouldBe null
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
            size shouldBe 12
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
                    visningsnavn shouldBe "Visningsnavn"
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
                it.foedselsdato shouldBe testdataBarn1.foedselsdato
                it.navn shouldBe null
                it.medISaken shouldBe true
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
                it.foedselsdato shouldBe testdataBarn2.foedselsdato
                it.navn shouldBe null
                it.medISaken shouldBe true
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
                it.foedselsdato shouldBe LocalDate.parse("2024-02-06")
                it.navn shouldBe "asdsadsad"
                it.medISaken shouldBe false
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
            size shouldBe 13
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SKATTEGRUNNLAG,
                testdataBarn2.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.INNTEKT_BEARBEIDET,
                testdataBarn2.ident,
            ) shouldHaveSize 1

            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.SKATTEGRUNNLAG,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.AINNTEKT,
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
                Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
                testdataBM.ident,
            ) shouldHaveSize 1
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.INNTEKT_BEARBEIDET,
                testdataBM.ident,
            ) shouldHaveSize 1
        }
    }
}
