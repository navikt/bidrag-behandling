package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeEmpty
import io.mockk.every
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.grunnlag.ainntektListe
import no.nav.bidrag.behandling.transformers.grunnlag.skattegrunnlagListe
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.filtrerEtterTypeOgIdent
import no.nav.bidrag.behandling.utils.testdata.lagVedtaksdata
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

class VedtakTilBehandlingBidragTest : CommonVedtakTilBehandlingTest() {
    @Test
    fun `Skal konvertere vedtak til behandling for lesemodus for BIDRAG`() {
        every { vedtakConsumer.hentVedtak(any()) } returns lagVedtaksdata("fattetvedtak/bidrag-innvilget")
        every { behandlingService.hentBehandlingById(1) } returns (oppretteBehandling())
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe SAKSNUMMER
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            avslag shouldBe null
            virkningstidspunkt shouldBe LocalDate.parse("2024-02-01")
            søktFomDato shouldBe LocalDate.parse("2024-02-01")
            soknadFra shouldBe SøktAvType.BIDRAGSPLIKTIG
            stonadstype shouldBe Stønadstype.BIDRAG
            engangsbeloptype shouldBe null
            behandlerEnhet shouldBe "4806"
            mottattdato shouldBe LocalDate.parse("2024-11-18")
            klageMottattdato shouldBe null
            vedtakstype shouldBe Vedtakstype.FASTSETTELSE
            vedtaksid shouldBe null
            refVedtaksid shouldBe 1
            soknadsid shouldBe 22233233433323L
            opprettetAv shouldBe "Z994977"
            opprettetAvNavn shouldBe null
            notater shouldHaveSize 5
            henteNotatinnhold(behandling, Notattype.SAMVÆR, behandling.søknadsbarn.first()) shouldBe "Begrunnelse samvær"
            henteNotatinnhold(behandling, Notattype.UNDERHOLDSKOSTNAD, behandling.søknadsbarn.first()) shouldBe "Begrunnelse underholdskostnad"
            validerRoller()
            validerHusstandsmedlem()
            validerInntekter()
            validerGrunnlag()
            validerUnderhold()
            validerSamvær()
        }
    }

    @Test
    fun `Skal konvertere vedtak til beregning resultat for lesemodus begrenset revurdering`() {
        every { vedtakConsumer.hentVedtak(any()) } returns lagVedtaksdata("fattetvedtak/bidrag-vedtak-begrenset-revurdering")
        every { behandlingService.hentBehandlingById(1) } returns (oppretteBehandling())
        val beregning = vedtakService.konverterVedtakTilBeregningResultatBidrag(1)!!

        assertSoftly(beregning) {
            beregning.resultatBarn shouldHaveSize 1
            val resultatBarn = beregning.resultatBarn.first()
            resultatBarn.ugyldigBeregning shouldBe null

            assertSoftly(resultatBarn.perioder.first()) {
                beregningsdetaljer.shouldNotBeNull()
                beregningsdetaljer!!.sluttberegning!!.begrensetRevurderingUtført shouldBe true
                beregningsdetaljer!!.sluttberegning!!.løpendeBidrag shouldBe BigDecimal("3820.0")
            }
        }
    }

    @Test
    fun `Skal konvertere vedtak til behandling for BIDRAG med notat for vedtak`() {
        every { vedtakConsumer.hentVedtak(any()) } returns lagVedtaksdata("fattetvedtak/bidrag-innvilget")
        every { behandlingService.hentBehandlingById(1) } returns (oppretteBehandling())
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

        behandlingRepository.save(behandling)
        assertSoftly(behandling) {
            henteNotatinnhold(behandling, Notattype.VIRKNINGSTIDSPUNKT, begrunnelseDelAvBehandlingen = false) shouldBe "Begrunnelse virkning"
            henteNotatinnhold(behandling, Notattype.BOFORHOLD, begrunnelseDelAvBehandlingen = false) shouldBe "Begrunnelse boforhold BP"
            henteNotatinnhold(behandling, Notattype.INNTEKT, begrunnelseDelAvBehandlingen = false) shouldBe "Begrunnelse inntekter BM"
            henteNotatinnhold(behandling, Notattype.UNDERHOLDSKOSTNAD, søknadsbarn.first(), begrunnelseDelAvBehandlingen = false) shouldBe "Begrunnelse underholdskostnad"
            henteNotatinnhold(behandling, Notattype.SAMVÆR, behandling.søknadsbarn.first(), begrunnelseDelAvBehandlingen = false) shouldBe "Begrunnelse samvær"
            henteNotatinnhold(behandling, Notattype.INNTEKT, begrunnelseDelAvBehandlingen = true).shouldBeEmpty()
            henteNotatinnhold(behandling, Notattype.VIRKNINGSTIDSPUNKT, begrunnelseDelAvBehandlingen = true).shouldBeEmpty()
            henteNotatinnhold(behandling, Notattype.BOFORHOLD, begrunnelseDelAvBehandlingen = true).shouldBeEmpty()
            henteNotatinnhold(behandling, Notattype.UNDERHOLDSKOSTNAD, søknadsbarn.first(), begrunnelseDelAvBehandlingen = true).shouldBeEmpty()
            henteNotatinnhold(behandling, Notattype.SAMVÆR, behandling.søknadsbarn.first(), begrunnelseDelAvBehandlingen = true).shouldBeEmpty()
        }
    }

    @Test
    fun `Skal konvertere vedtak til behandling for lesemodus for BIDRAG med klage mottatt dato`() {
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")
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
        every { behandlingService.hentBehandlingById(1) } returns (oppretteBehandling())
        every { vedtakConsumer.hentVedtak(eq(1)) } returns vedtak1
        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe SAKSNUMMER
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            avslag shouldBe null
            virkningstidspunkt shouldBe LocalDate.parse("2024-02-01")
            søktFomDato shouldBe LocalDate.parse("2024-02-01")
            soknadFra shouldBe SøktAvType.BIDRAGSPLIKTIG
            stonadstype shouldBe Stønadstype.BIDRAG
            engangsbeloptype shouldBe null
            behandlerEnhet shouldBe "4806"
            mottattdato shouldBe LocalDate.parse("2024-05-01")
            klageMottattdato shouldBe LocalDate.parse("2024-03-01")
            vedtakstype shouldBe Vedtakstype.FASTSETTELSE
            vedtaksid shouldBe null
            refVedtaksid shouldBe 1
            soknadsid shouldBe 22233233433323L
            opprettetAv shouldBe "Z994977"
            opprettetAvNavn shouldBe null
        }
    }

    @Test
    fun `Skal opprette behandling fra vedtak`() {
        every { vedtakConsumer.hentVedtak(eq(12333)) } returns lagVedtaksdata("fattetvedtak/bidrag-innvilget")

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
            validerSamvær()
            validerUnderhold()
        }
    }

    @Test
    fun `Skal opprette behandling og lagre vedtakstidspunkt for forrige vedtak`() {
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")
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
    fun `Skal konvertere vedtak til behandling for lesemodus hvis direkte avslag`() {
        every { vedtakConsumer.hentVedtak(any()) } returns lagVedtaksdata("fattetvedtak/bidrag-avslag")
        every { behandlingService.hentBehandlingById(1) } returns (oppretteBehandling())

        val behandling = vedtakService.konverterVedtakTilBehandlingForLesemodus(1)!!

        assertSoftly(behandling) {
            behandling.saksnummer shouldBe "2400116"
            stonadstype shouldBe Stønadstype.BIDRAG
            engangsbeloptype shouldBe null
            vedtakstype shouldBe Vedtakstype.FASTSETTELSE
            behandlerEnhet shouldBe "4806"
            innkrevingstype shouldBe Innkrevingstype.MED_INNKREVING
            årsak shouldBe null
            avslag shouldBe Resultatkode.IKKE_OMSORG_FOR_BARNET
            notater shouldHaveSize 0
            virkningstidspunkt shouldBe LocalDate.parse("2023-09-01")
            opprinneligVirkningstidspunkt shouldBe LocalDate.parse("2023-09-01")
        }
    }

    @Test
    fun `Skal konvertere vedtak for beregning`() {
        every { vedtakConsumer.hentVedtak(any()) } returns lagVedtaksdata("vedtak_response-særbidrag")
        val resultat =
            vedtakService.konverterVedtakTilBeregningResultatBidrag(1)

        resultat shouldNotBe null
    }

    private fun Behandling.validerSamvær() {
        assertSoftly(samvær) {
            size shouldBe 1
            val samværBarn = samvær.first()
            samværBarn.rolle.rolletype shouldBe Rolletype.BARN
            samværBarn.perioder shouldHaveSize 4
            assertSoftly(samværBarn.perioder.first()) {
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                fom shouldBe LocalDate.parse("2024-02-01")
                tom shouldBe LocalDate.parse("2024-05-31")
                beregning shouldNotBe null
            }
            assertSoftly(samværBarn.perioder.toList()[1]) {
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_1
                fom shouldBe LocalDate.parse("2024-06-01")
                tom shouldBe LocalDate.parse("2024-08-31")
                beregning shouldBe null
            }
            assertSoftly(samværBarn.perioder.toList()[2]) {
                samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_4
                fom shouldBe LocalDate.parse("2024-09-01")
                tom shouldBe LocalDate.parse("2024-10-31")
                beregning shouldNotBe null
            }
            assertSoftly(samværBarn.perioder.toList()[3]) {
                samværsklasse shouldBe Samværsklasse.DELT_BOSTED
                fom shouldBe LocalDate.parse("2024-11-01")
                tom shouldBe null
                beregning shouldBe null
            }
        }
    }

    private fun Behandling.validerUnderhold() {
        assertSoftly(underholdskostnader) {
            size shouldBe 3
            val underholdSøknadsbarn = underholdskostnader.find { it.person.rolle.isNotEmpty() }!!
            assertSoftly(underholdSøknadsbarn) {
                harTilsynsordning shouldBe true

                kilde shouldBe null
                barnetilsyn shouldHaveSize 1
                barnetilsyn.first().fom shouldBe LocalDate.parse("2024-02-01")
                barnetilsyn.first().tom shouldBe LocalDate.parse("2024-09-30")
                barnetilsyn.first().under_skolealder shouldBe false
                barnetilsyn.first().omfang shouldBe Tilsynstype.HELTID

                tilleggsstønad shouldHaveSize 1
                tilleggsstønad.first().fom shouldBe LocalDate.parse("2024-02-01")
                tilleggsstønad.first().tom shouldBe LocalDate.parse("2024-09-30")
                tilleggsstønad.first().dagsats shouldBe BigDecimal(20)

                faktiskeTilsynsutgifter shouldHaveSize 2
                faktiskeTilsynsutgifter.first().fom shouldBe LocalDate.parse("2024-02-01")
                faktiskeTilsynsutgifter.first().tom shouldBe LocalDate.parse("2024-09-30")
                faktiskeTilsynsutgifter.first().tilsynsutgift shouldBe BigDecimal(1300)
                faktiskeTilsynsutgifter.first().kostpenger shouldBe BigDecimal(20)
                faktiskeTilsynsutgifter.first().kommentar shouldBe "Dette er test"
            }

            assertSoftly(underholdskostnader.find { it.person.ident == "27461456400" }!!) {
                harTilsynsordning shouldBe null
                kilde shouldBe Kilde.MANUELL
                barnetilsyn shouldHaveSize 0
                tilleggsstønad shouldHaveSize 0
                faktiskeTilsynsutgifter shouldHaveSize 1
                faktiskeTilsynsutgifter.first().fom shouldBe LocalDate.parse("2024-06-01")
                faktiskeTilsynsutgifter.first().tom shouldBe null
                faktiskeTilsynsutgifter.first().tilsynsutgift shouldBe BigDecimal(2756)
                faktiskeTilsynsutgifter.first().kostpenger shouldBe BigDecimal(100)
                faktiskeTilsynsutgifter.first().kommentar shouldBe "Barnehage"
            }

            assertSoftly(underholdskostnader.find { it.person.ident == "11441387387" }!!) {
                harTilsynsordning shouldBe null
                kilde shouldBe Kilde.OFFENTLIG
                barnetilsyn shouldHaveSize 0
                tilleggsstønad shouldHaveSize 0
                faktiskeTilsynsutgifter shouldHaveSize 1
                faktiskeTilsynsutgifter.first().fom shouldBe LocalDate.parse("2024-01-01")
                faktiskeTilsynsutgifter.first().tom shouldBe null
                faktiskeTilsynsutgifter.first().tilsynsutgift shouldBe BigDecimal(12)
                faktiskeTilsynsutgifter.first().kostpenger shouldBe BigDecimal(0)
                faktiskeTilsynsutgifter.first().kommentar shouldBe ""
            }
        }
    }

    private fun Behandling.validerInntekter() {
        assertSoftly(inntekter) {
            size shouldBe 20
            filter { it.type == Inntektsrapportering.BARNETILLEGG } shouldHaveSize 6
            filter { it.type == Inntektsrapportering.KONTANTSTØTTE } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.SYKEPENGER } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD } shouldHaveSize 2
            filter { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_3MND } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.AINNTEKT } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.OVERGANGSSTØNAD } shouldHaveSize 1
            filter { it.type == Inntektsrapportering.LØNN_MANUELT_BEREGNET } shouldHaveSize 4
            filter { it.type == Inntektsrapportering.KAPITALINNTEKT } shouldHaveSize 0
            filter { it.type == Inntektsrapportering.LIGNINGSINNTEKT } shouldHaveSize 0
            find { it.type == Inntektsrapportering.LØNN_MANUELT_BEREGNET && it.ident == testdataBP.ident } shouldNotBe null
            find { it.type == Inntektsrapportering.FORELDREPENGER && it.ident == testdataBM.ident } shouldNotBe null
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
                    it.datoFom shouldBe LocalDate.of(2024, 10, 1)
                    it.datoTom shouldBe null
                    it.bostatus shouldBe Bostatuskode.MED_FORELDER
                    it.kilde shouldBe Kilde.MANUELL
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
                it.datoFom shouldBe LocalDate.of(2024, 2, 1)
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
                it.harGebyrsøknad shouldBe true
                it.manueltOverstyrtGebyr.shouldNotBeNull()
                it.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
                it.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
                it.manueltOverstyrtGebyr!!.begrunnelse shouldBe "test"
                it.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe true
            }
            val bidragspliktig = roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
            bidragspliktig shouldNotBe null
            assertSoftly(bidragspliktig!!) {
                it.ident shouldBe testdataBP.ident
                it.fødselsdato shouldBe testdataBP.fødselsdato
                it.navn shouldBe null
                it.deleted shouldBe false
                it.harGebyrsøknad shouldBe true
                it.manueltOverstyrtGebyr.shouldNotBeNull()
                it.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
                it.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe true
                it.manueltOverstyrtGebyr!!.begrunnelse shouldBe null
                it.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe true
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
            maksGodkjentBeløp shouldBe null
            maksGodkjentBeløpBegrunnelse shouldBe null
            utgiftsposter shouldHaveSize 3
            assertSoftly(utgiftsposter.find { it.type == "Ny høreapparat" }!!) {
                kravbeløp shouldBe BigDecimal(9000)
                godkjentBeløp shouldBe BigDecimal(6000)
                type shouldBe "Ny høreapparat"
                betaltAvBp shouldBe false
                kommentar shouldBe "Inkluderer ikke frakt og andre kostnader"
                dato shouldBe LocalDate.parse("2024-05-06")
            }
        }
    }

    private fun Behandling.validerGrunnlag() {
        assertSoftly(grunnlagListe) {
            size shouldBe 16
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.ANDRE_BARN,
                testdataBM.ident,
            ) shouldHaveSize 1
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
            ) shouldHaveSize 0
            filtrerEtterTypeOgIdent(
                Grunnlagsdatatype.BARNETILLEGG,
                testdataBM.ident,
            ) shouldHaveSize 0
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
            ) shouldHaveSize 1
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
            skattepliktigInnhold!!.inntekter shouldHaveSize 6
            skattepliktigInnhold.inntekter.ainntektListe shouldHaveSize 3
            skattepliktigInnhold.inntekter.skattegrunnlagListe shouldHaveSize 3
        }
    }
}
