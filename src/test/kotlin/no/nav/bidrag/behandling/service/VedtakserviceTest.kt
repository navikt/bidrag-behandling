package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.utils.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.opprettGyldigBehandlingForBeregning
import no.nav.bidrag.behandling.utils.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.sjablonResponse
import no.nav.bidrag.behandling.utils.testdataBM
import no.nav.bidrag.behandling.utils.testdataBarn1
import no.nav.bidrag.behandling.utils.testdataBarn2
import no.nav.bidrag.behandling.utils.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.service.sjablon.SjablonProvider
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
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
        beregningService =
            BeregningService(
                behandlingService,
                BeregnForskuddApi(),
                unleash,
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

        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        mockkObject(SjablonProvider)
        every {
            SjablonProvider.hentSjablontall()
        } returns sjablonResponse()
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for en forskudd behandling`() {
        val behandling = opprettGyldigBehandlingForBeregning(true)
        behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
        behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
        behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
        every { behandlingService.hentBehandlingById(any()) } returns behandling

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

            assertSoftly(request.behandlingsreferanseListe) { behandlingRef ->
                behandlingRef shouldHaveSize 2
                with(behandlingRef[0]) {
                    kilde shouldBe BehandlingsrefKilde.BEHANDLING_ID
                    referanse shouldBe behandling.id.toString()
                }
                with(behandlingRef[1]) {
                    kilde shouldBe BehandlingsrefKilde.BISYS_SØKNAD
                    referanse shouldBe behandling.soknadsid.toString()
                }
            }

            assertSoftly(request.stønadsendringListe) {
                withClue("Stønadsendring søknadsbarn 1") {
                    it[0].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
                    it[0].kravhaver.verdi shouldBe behandling.søknadsbarn[0].ident
                    it[0].skyldner.verdi shouldBe "NAV"
                    it[0].grunnlagReferanseListe.shouldHaveSize(6)
                    it[0].periodeListe.shouldHaveSize(4)
                }
                withClue("Stønadsendring søknadsbarn 2") {
                    it[1].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
                    it[1].kravhaver.verdi shouldBe behandling.søknadsbarn[1].ident
                    it[1].skyldner.verdi shouldBe "NAV"
                    it[1].grunnlagReferanseListe.shouldHaveSize(6)
                    it[1].periodeListe.shouldHaveSize(4)
                }
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN)) {
                shouldHaveSize(2)
                it.shouldContainPerson(testdataBarn1.ident)
                it.shouldContainPerson(testdataBarn2.ident)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)) {
                shouldHaveSize(1)
                it.shouldContainPerson(testdataHusstandsmedlem1.ident)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER)) {
                shouldHaveSize(1)
                it.shouldContainPerson(testdataBM.ident)
            }

            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
            val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)
            val søknadsbarn2Grunnlag = grunnlagListe.hentPerson(testdataBarn2.ident)
            val husstandsbarnGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
                shouldHaveSize(6)
                val bostatusSøknadsbarn1 =
                    it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag!!.referanse)
                bostatusSøknadsbarn1.shouldHaveSize(2)
                with(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
                    bostatus shouldBe Bostatuskode.MED_FORELDER
                    periode.fom shouldBe YearMonth.parse("2022-02")
                    periode.til shouldBe YearMonth.parse("2022-05")
                }
                with(bostatusSøknadsbarn1[1].innholdTilObjekt<BostatusPeriode>()) {
                    bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                    periode.fom shouldBe YearMonth.parse("2022-05")
                    periode.til shouldBe null
                }

                it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn2Grunnlag!!.referanse)
                    .shouldHaveSize(2)
                it.filtrerBasertPåFremmedReferanse(referanse = husstandsbarnGrunnlag!!.referanse)
                    .shouldHaveSize(2)
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SIVILSTAND_PERIODE)) {
                shouldHaveSize(1)
                it[0].grunnlagsreferanseListe.shouldContain(bmGrunnlag.referanse)
                val sivilstandGrunnlag = it.innholdTilObjekt<SivilstandPeriode>()
                sivilstandGrunnlag[0].sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                sivilstandGrunnlag[0].periode.fom shouldBe YearMonth.parse("2022-02")
                sivilstandGrunnlag[0].periode.til shouldBe null
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)) {
                shouldHaveSize(3)
                it[0].grunnlagsreferanseListe.shouldContain(bmGrunnlag.referanse)
                it[1].grunnlagsreferanseListe.shouldContain(bmGrunnlag.referanse)
                it[2].grunnlagsreferanseListe.shouldContain(bmGrunnlag.referanse)
                with(it[0].innholdTilObjekt<InntektsrapporteringPeriode>()) {
                    periode.fom shouldBe YearMonth.parse("2022-01")
                    periode.til shouldBe YearMonth.parse("2022-07")
                    inntekstpostListe shouldHaveSize 0
                    beløp shouldBe 50000.toBigDecimal()
                    inntektsrapportering shouldBe Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER
                    valgt shouldBe true
                    manueltRegistrert shouldBe true
                }
                with(it[2].innholdTilObjekt<InntektsrapporteringPeriode>()) {
                    periode.fom shouldBe YearMonth.parse("2022-01")
                    periode.til shouldBe YearMonth.parse("2023-01")
                    inntekstpostListe shouldHaveSize 1
                    beløp shouldBe 60000.toBigDecimal()
                    inntektsrapportering shouldBe Inntektsrapportering.AINNTEKT_BEREGNET_12MND
                    valgt shouldBe false
                    manueltRegistrert shouldBe false
                }
            }

            assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT)) {
                val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
                it.grunnlagsreferanseListe.shouldContain(bmGrunnlag.referanse)
                innhold.summertMånedsinntektListe.shouldHaveSize(24)
            }

            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SJABLON) shouldHaveSize 14
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 4
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE) shouldHaveSize 13
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD_PERIODE) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE) shouldHaveSize 6
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND_PERIODE) shouldHaveSize 3
        }
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for en enkel forskudd behandling2`() {
        val behandling = opprettGyldigBehandlingForBeregning(true)
        behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
        behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
        behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
        every { behandlingService.hentBehandlingById(any()) } returns behandling
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
        }
    }
}

fun List<OpprettGrunnlagRequestDto>.hentPerson(ident: String) =
    hentGrunnlagstyper("PERSON_")
        .find { grunnlag -> grunnlag.innholdTilObjekt<Person>().ident.verdi == ident }

fun List<OpprettGrunnlagRequestDto>.shouldContainPerson(
    ident: String,
    navn: String? = null,
) {
    withClue("Should have person with ident $ident and name $navn") {
        val person =
            filter { it.type.name.startsWith("PERSON_") }
                .map { it.innholdTilObjekt<Person>() }
                .find { it.ident.verdi == ident && (navn == null || it.navn == navn) }
        person shouldNotBe null
    }
}

fun OpprettVedtakRequestDto.hentGrunnlagstyper(grunnlagstype: Grunnlagstype) = grunnlagListe.filter { it.type == grunnlagstype }

fun OpprettVedtakRequestDto.hentGrunnlagstype(grunnlagstype: Grunnlagstype) = grunnlagListe.find { it.type == grunnlagstype }

fun List<OpprettGrunnlagRequestDto>.hentGrunnlagstyper(grunnlagstype: Grunnlagstype) = filter { it.type == grunnlagstype }

fun List<OpprettGrunnlagRequestDto>.hentGrunnlagstyper(prefix: String) = filter { it.type.name.startsWith(prefix) }

val List<OpprettGrunnlagRequestDto>.søknad get() = find { it.type == Grunnlagstype.SØKNAD }
val List<OpprettGrunnlagRequestDto>.virkningsdato get() = find { it.type == Grunnlagstype.VIRKNINGSTIDSPUNKT }
