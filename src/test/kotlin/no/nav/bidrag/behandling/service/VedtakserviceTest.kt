package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.transformers.tilBehandlingDtoV2
import no.nav.bidrag.behandling.utils.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdataBM
import no.nav.bidrag.behandling.utils.testdataBarn1
import no.nav.bidrag.behandling.utils.testdataBarn2
import no.nav.bidrag.behandling.utils.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.AldersgruppeForskudd
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningForskudd
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.math.BigDecimal
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class VedtakserviceTest {
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
        unleash.enableAll()
        beregningService =
            BeregningService(
                behandlingService,
                BeregnForskuddApi(),
            )
        vedtakService =
            VedtakService(
                behandlingService,
                beregningService,
                grunnlagService,
                vedtakConsumer,
                sakConsumer,
                unleash,
            )
        every {
            behandlingService.oppdaterBehandling(
                any(),
                any(),
            )
        } returns
            oppretteBehandling(1).tilBehandlingDtoV2(
                emptyList(),
            )

        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        stubSjablonProvider()
        stubKodeverkProvider()
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for en forskudd behandling`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
        behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
        behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
        behandling.boforholdsbegrunnelseKunINotat = "Boforhold"
        behandling.boforholdsbegrunnelseIVedtakOgNotat = "Boforhold kun i notat"
        behandling.grunnlagListe =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        every { behandlingService.hentBehandlingById(any(), any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        every { grunnlagService.hentAlleSistAktiv(any()) } returns
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )
        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) { request ->
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 2
            request.engangsbeløpListe.shouldBeEmpty()
            request.grunnlagListe.shouldHaveSize(86)

            validerVedtaksdetaljer(behandling)
            validerPersongrunnlag()
            validerSluttberegning()
            validerBosstatusPerioder()
            validerInntektrapportering()

            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SIVILSTAND_PERIODE)) {
                shouldHaveSize(1)
                it[0].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
                val sivilstandGrunnlag = it.innholdTilObjekt<SivilstandPeriode>()
                sivilstandGrunnlag[0].sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                sivilstandGrunnlag[0].periode.fom shouldBe YearMonth.parse("2022-02")
                sivilstandGrunnlag[0].periode.til shouldBe null
            }

            assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT)) {
                val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
                it.gjelderReferanse.shouldBe(bmGrunnlag.referanse)
                innhold.summertMånedsinntektListe.shouldHaveSize(24)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(6)
                assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.virkningstidspunktbegrunnelseKunINotat
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
                }
            }

            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SJABLON) shouldHaveSize 14
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE) shouldHaveSize 13
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 1
        }
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for avslag av forskudd behandling`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
        behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
        behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
        every { behandlingService.hentBehandlingById(any(), any()) } returns behandling
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)
        every { grunnlagService.hentAlleSistAktiv(any()) } returns
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )
        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) { request ->
            request.type shouldBe Vedtakstype.FASTSETTELSE
            request.stønadsendringListe shouldHaveSize 2
            request.engangsbeløpListe.shouldBeEmpty()
            request.grunnlagListe shouldHaveSize 6
            assertSoftly(behandlingsreferanseListe) { behandlingRef ->
                behandlingRef shouldHaveSize 2
                with(behandlingRef[0]) {
                    kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BEHANDLING_ID
                    referanse shouldBe behandling.id.toString()
                }
                with(behandlingRef[1]) {
                    kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BISYS_SØKNAD
                    referanse shouldBe behandling.soknadsid.toString()
                }
            }

            assertSoftly(stønadsendringListe) {
                withClue("Stønadsendring søknadsbarn 1") {
                    it[0].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
                    it[0].kravhaver.verdi shouldBe behandling.søknadsbarn[0].ident
                    it[0].skyldner.verdi shouldBe "NAV"
                    it[0].grunnlagReferanseListe.shouldHaveSize(6)
                    it[0].grunnlagReferanseListe.forEach {
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
                    }
                    assertSoftly(it[0].periodeListe) {
                        shouldHaveSize(1)
                        assertSoftly(it[0]) {
                            periode shouldBe
                                ÅrMånedsperiode(
                                    behandling.søktFomDato,
                                    null,
                                )
                            beløp shouldBe BigDecimal.ZERO
                            valutakode shouldBe "NOK"
                            resultatkode shouldBe Resultatkode.AVSLAG.name
                            grunnlagReferanseListe.shouldBeEmpty()
                        }
                    }
                }
                withClue("Stønadsendring søknadsbarn 2") {
                    it[1].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
                    it[1].kravhaver.verdi shouldBe behandling.søknadsbarn[1].ident
                    it[1].skyldner.verdi shouldBe "NAV"
                    it[1].grunnlagReferanseListe.shouldHaveSize(6)
                    it[1].grunnlagReferanseListe.forEach {
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
                    }
                    assertSoftly(it[1].periodeListe) {
                        shouldHaveSize(1)
                        assertSoftly(it[0]) {
                            periode shouldBe
                                ÅrMånedsperiode(
                                    behandling.søktFomDato,
                                    null,
                                )
                            beløp shouldBe BigDecimal.ZERO
                            valutakode shouldBe "NOK"
                            resultatkode shouldBe Resultatkode.AVSLAG.name
                            grunnlagReferanseListe.shouldBeEmpty()
                        }
                    }
                }
                hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
                hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            }
        }
    }
}

fun List<OpprettGrunnlagRequestDto>.hentPerson(ident: String) =
    hentGrunnlagstyper("PERSON_")
        .find { grunnlag -> grunnlag.innholdTilObjekt<Person>().ident?.verdi == ident }

fun List<OpprettGrunnlagRequestDto>.shouldContainPerson(
    ident: String,
    navn: String? = null,
) {
    withClue("Should have person with ident $ident and name $navn") {
        val person =
            filter { it.type.name.startsWith("PERSON_") }
                .map { it.innholdTilObjekt<Person>() }
                .find { it.ident?.verdi == ident && (navn == null || it.navn == navn) }
        person shouldNotBe null
    }
}

private fun OpprettVedtakRequestDto.validerPersongrunnlag() {
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN)) {
        shouldHaveSize(2)
        it.shouldContainPerson(testdataBarn1.ident)
        it.shouldContainPerson(testdataBarn2.ident)
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)) {
        shouldHaveSize(3)
        it.shouldContainPerson(testdataHusstandsmedlem1.ident)
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER)) {
        shouldHaveSize(1)
        it.shouldContainPerson(testdataBM.ident)
    }
}

private fun OpprettVedtakRequestDto.validerVedtaksdetaljer(behandling: Behandling) {
    assertSoftly("Søknadsdetaljer") {
        grunnlagListe.virkningsdato shouldNotBe null
        val virkningsdato =
            grunnlagListe.virkningsdato?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
        virkningsdato!!.virkningstidspunkt shouldHaveSameDayAs behandling.virkningstidspunkt!!
        virkningsdato.årsak shouldBe behandling.årsak

        grunnlagListe.søknad shouldNotBe null
        val søknad = grunnlagListe.søknad?.innholdTilObjekt<SøknadGrunnlag>()
        søknad!!.mottattDato shouldHaveSameDayAs behandling.mottattdato
        søknad.søktAv shouldBe behandling.soknadFra
        søknad.søktFraDato shouldBe behandling.søktFomDato
    }

    assertSoftly(behandlingsreferanseListe) { behandlingRef ->
        behandlingRef shouldHaveSize 2
        with(behandlingRef[0]) {
            kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BEHANDLING_ID
            referanse shouldBe behandling.id.toString()
        }
        with(behandlingRef[1]) {
            kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BISYS_SØKNAD
            referanse shouldBe behandling.soknadsid.toString()
        }
    }

    assertSoftly(stønadsendringListe) {
        withClue("Stønadsendring søknadsbarn 1") {
            it[0].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
            it[0].kravhaver.verdi shouldBe behandling.søknadsbarn[0].ident
            it[0].skyldner.verdi shouldBe "NAV"
            it[0].grunnlagReferanseListe.shouldHaveSize(8)
            it[0].grunnlagReferanseListe.forEach {
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
            }
            assertSoftly(it[0].periodeListe) {
                shouldHaveSize(8)
                assertSoftly(it[0]) {
                    periode shouldBe
                        ÅrMånedsperiode(
                            YearMonth.parse("2023-02"),
                            YearMonth.parse("2023-07"),
                        )
                    beløp shouldBe BigDecimal(1760)
                    valutakode shouldBe "NOK"
                    resultatkode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT.name
                    grunnlagReferanseListe shouldHaveSize 1
                    val grunnlag =
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = grunnlagReferanseListe[0])
                    grunnlag shouldHaveSize 1
                    grunnlag[0].type shouldBe Grunnlagstype.SLUTTBEREGNING_FORSKUDD
                }
            }
        }
        withClue("Stønadsendring søknadsbarn 2") {
            it[1].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
            it[1].kravhaver.verdi shouldBe behandling.søknadsbarn[1].ident
            it[1].skyldner.verdi shouldBe "NAV"
            it[1].grunnlagReferanseListe.shouldHaveSize(8)
            it[1].grunnlagReferanseListe.forEach {
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
            }
            assertSoftly(it[1].periodeListe) {
                shouldHaveSize(8)
                assertSoftly(it[0]) {
                    periode shouldBe
                        ÅrMånedsperiode(
                            YearMonth.parse("2023-02"),
                            YearMonth.parse("2023-07"),
                        )
                    beløp shouldBe BigDecimal(1760)
                    valutakode shouldBe "NOK"
                    resultatkode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT.name
                    grunnlagReferanseListe shouldHaveSize 1
                    val grunnlag =
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = grunnlagReferanseListe[0])
                    grunnlag shouldHaveSize 1
                    grunnlag[0].type shouldBe Grunnlagstype.SLUTTBEREGNING_FORSKUDD
                }
            }
        }
    }
}

private fun OpprettVedtakRequestDto.validerBosstatusPerioder() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    val søknadsbarn2Grunnlag = grunnlagListe.hentPerson(testdataBarn2.ident)!!
    val husstandsbarnGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
        shouldHaveSize(6)
        val bostatusSøknadsbarn1 =
            it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag.referanse)
        bostatusSøknadsbarn1.shouldHaveSize(2)
        it[0].gjelderReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[1].gjelderReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[2].gjelderReferanse shouldBe søknadsbarn2Grunnlag.referanse
        it[3].gjelderReferanse shouldBe søknadsbarn2Grunnlag.referanse
        it[4].gjelderReferanse shouldBe husstandsbarnGrunnlag.referanse
        it[5].gjelderReferanse shouldBe husstandsbarnGrunnlag.referanse
        assertSoftly(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-02")
            periode.til shouldBe YearMonth.parse("2024-10")
            relatertTilPart shouldBe bmGrunnlag.referanse
        }
        assertSoftly(bostatusSøknadsbarn1[1].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2024-10")
            periode.til shouldBe null
            relatertTilPart shouldBe bmGrunnlag.referanse
        }

        it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn2Grunnlag.referanse)
            .shouldHaveSize(2)
        it.filtrerBasertPåFremmedReferanse(referanse = husstandsbarnGrunnlag.referanse)
            .shouldHaveSize(2)
    }
}

private fun OpprettVedtakRequestDto.validerInntektrapportering() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)) {
        shouldHaveSize(5)
        it[0].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[1].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[2].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[3].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[4].gjelderReferanse.shouldBe(søknadsbarnGrunnlag.referanse)

        assertSoftly(it[0].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-01")
            periode.til shouldBe YearMonth.parse("2022-07")
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 50000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[1].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-07")
            periode.til shouldBe YearMonth.parse("2022-09")
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[2]) {
            it.grunnlagsreferanseListe shouldHaveSize 13
            val grunnlag =
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it.grunnlagsreferanseListe[0])
            grunnlag[0].type shouldBe Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE
            assertSoftly(innholdTilObjekt<InntektsrapporteringPeriode>()) {
                periode.fom shouldBe YearMonth.parse("2022-01")
                periode.til shouldBe null
                inntekstpostListe shouldHaveSize 1
                beløp shouldBe 60000.toBigDecimal()
                inntektsrapportering shouldBe Inntektsrapportering.AINNTEKT_BEREGNET_12MND
                gjelderBarn shouldBe null
                valgt shouldBe true
                manueltRegistrert shouldBe false
            }
        }
        assertSoftly(it[3].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-01")
            periode.til shouldBe null
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
            gjelderBarn shouldBe søknadsbarnGrunnlag.referanse
            valgt shouldBe true
            manueltRegistrert shouldBe false
        }
        assertSoftly(it[4].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-01")
            periode.til shouldBe null
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.AINNTEKT_BEREGNET_12MND
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe false
        }
    }
}

private fun OpprettVedtakRequestDto.validerSluttberegning() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)
    val søknadsbarn2Grunnlag = grunnlagListe.hentPerson(testdataBarn2.ident)
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_FORSKUDD)) {
        shouldHaveSize(16)
        it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn2Grunnlag!!.referanse) shouldHaveSize 8
        assertSoftly(it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag!!.referanse)) {
            shouldHaveSize(8)
            assertSoftly(it[3]) {
                val innhold = innholdTilObjekt<SluttberegningForskudd>()
                innhold.beløp.toBigInteger() shouldBe 1880.toBigInteger()
                innhold.resultatKode shouldBe no.nav.bidrag.domene.enums.beregning.Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
                innhold.aldersgruppe shouldBe AldersgruppeForskudd.ALDER_0_10_ÅR
                val delberegningInntekt =
                    hentGrunnlagstyperForReferanser(
                        Grunnlagstype.DELBEREGNING_INNTEKT,
                        it.grunnlagsreferanseListe,
                    )
                assertSoftly(delberegningInntekt) {
                    shouldHaveSize(2)
                    assertSoftly(it[0]) { delberegning ->
                        delberegning.innholdTilObjekt<DelberegningInntekt>().summertBeløp shouldBe 120000.toBigDecimal()
                        delberegning.grunnlagsreferanseListe shouldHaveSize 2

                        val delberegningInntekt =
                            grunnlagListe.filtrerBasertPåEgenReferanse(referanse = delberegning.grunnlagsreferanseListe[0])
                                .first()
                        delberegningInntekt.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
                        delberegningInntekt.grunnlagsreferanseListe shouldHaveSize 13

                        val innhentetAinntekt =
                            grunnlagListe.filtrerBasertPåEgenReferanse(referanse = delberegningInntekt.grunnlagsreferanseListe[0])
                                .first()
                        innhentetAinntekt.type shouldBe Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE
                        innhentetAinntekt.grunnlagsreferanseListe shouldHaveSize 0
                        innhentetAinntekt.gjelderReferanse shouldBe bmGrunnlag.referanse
                    }
                }
                val delberegningBarnIHusstand =
                    hentGrunnlagstyperForReferanser(
                        Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND,
                        it.grunnlagsreferanseListe,
                    )

                assertSoftly(delberegningBarnIHusstand) {
                    shouldHaveSize(1)
                    assertSoftly(it[0]) { delberegning ->
                        delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().antallBarn shouldBe 1
                        delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().periode.fom shouldBe
                            YearMonth.parse(
                                "2024-08",
                            )
                        delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().periode.til shouldBe
                            YearMonth.parse(
                                "2024-10",
                            )
                        delberegning.grunnlagsreferanseListe shouldHaveSize 1

                        val bosstatusHusstandsmedlem =
                            grunnlagListe.filtrerBasertPåEgenReferanse(referanse = delberegning.grunnlagsreferanseListe[0])
                                .first()
                        bosstatusHusstandsmedlem.type shouldBe Grunnlagstype.BOSTATUS_PERIODE
                        bosstatusHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 1

                        val innhentetHusstandsmedlem =
                            grunnlagListe.filtrerBasertPåEgenReferanse(referanse = bosstatusHusstandsmedlem.grunnlagsreferanseListe[0])
                                .first()
                        innhentetHusstandsmedlem.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                        innhentetHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 0
                        innhentetHusstandsmedlem.gjelderReferanse shouldBe bmGrunnlag.referanse
                    }
                }
            }
        }
    }
}

fun OpprettVedtakRequestDto.hentGrunnlagstyperForReferanser(
    grunnlagstype: Grunnlagstype,
    referanseListe: List<Grunnlagsreferanse>,
) = grunnlagListe.filter { it.type == grunnlagstype && referanseListe.contains(it.referanse) }

fun OpprettVedtakRequestDto.hentGrunnlagstyper(grunnlagstype: Grunnlagstype) = grunnlagListe.filter { it.type == grunnlagstype }

fun OpprettVedtakRequestDto.hentGrunnlagstype(grunnlagstype: Grunnlagstype) = grunnlagListe.find { it.type == grunnlagstype }

fun List<OpprettGrunnlagRequestDto>.hentGrunnlagstyper(grunnlagstype: Grunnlagstype) = filter { it.type == grunnlagstype }

fun List<OpprettGrunnlagRequestDto>.hentGrunnlagstyper(prefix: String) = filter { it.type.name.startsWith(prefix) }

val List<OpprettGrunnlagRequestDto>.søknad get() = find { it.type == Grunnlagstype.SØKNAD }
val List<OpprettGrunnlagRequestDto>.virkningsdato get() = find { it.type == Grunnlagstype.VIRKNINGSTIDSPUNKT }
