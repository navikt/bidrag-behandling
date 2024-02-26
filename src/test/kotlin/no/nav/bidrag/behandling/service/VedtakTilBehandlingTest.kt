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
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.hentFil
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.person.Bostatuskode
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
        stubKodeverkProvider()
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for en forskudd behandling`() {
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
            vedtaksid shouldBe 1
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
            assertSoftly(sivilstand) {
                size shouldBe 1
            }
            assertSoftly(inntekter) {
                size shouldBe 11
            }
            assertSoftly(behandling.grunnlagListe) {
                size shouldBe 5
            }
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
        }
    }

    fun filTilVedtakDto(filnavn: String): VedtakDto {
        return commonObjectmapper.readValue(
            hentFil("/__files/$filnavn.json"),
            VedtakDto::class.java,
        )
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
}
