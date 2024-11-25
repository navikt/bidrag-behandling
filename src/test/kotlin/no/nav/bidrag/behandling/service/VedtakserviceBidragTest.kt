package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.utils.hentGrunnlagstype
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.hentGrunnlagstyperForReferanser
import no.nav.bidrag.behandling.utils.hentNotat
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.shouldContainPerson
import no.nav.bidrag.behandling.utils.søknad
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.erstattVariablerITestFil
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetillegg
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggTilFaktiskTilsynsutgift
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.leggTilTillegsstønad
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandlingMedReelMottaker
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.behandling.utils.virkningsdato
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.BarnetilsynMedStønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsfradrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUnderholdskostnad
import no.nav.bidrag.transport.behandling.felles.grunnlag.FaktiskUtgiftPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import java.math.BigDecimal
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class VedtakserviceBidragTest : CommonVedtakTilBehandlingTest() {
    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en bidrag behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilTillegsstønad(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(4), null), medId = true)
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), testdataHusstandsmedlem1, medId = true)
        behandling.leggTilFaktiskTilsynsutgift(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null),
            testdataBarnBm,
            medId = true,
        )
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!, null), medId = true)
        behandling.leggTilBarnetilsyn(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), generateId = true)
        behandling.leggTilBarnetilsyn(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)),
            generateId = true,
            tilsynstype = Tilsynstype.IKKE_ANGITT,
            under_skolealder = null,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatGrunnlag.NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatGrunnlag.NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.refVedtaksid = 553
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 1
            request.engangsbeløpListe.shouldHaveSize(2)
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 167
            }
        }
        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(2)
            val gebyrMottaker = it.find { it.type == Engangsbeløptype.GEBYR_MOTTAKER }!!

            gebyrMottaker.beløp shouldBe BigDecimal(0)
            gebyrMottaker.kravhaver shouldBe Personident("NAV")
            gebyrMottaker.mottaker shouldBe Personident("NAV")
            gebyrMottaker.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            gebyrMottaker.resultatkode shouldBe Resultatkode.GEBYR_ILAGT.name
            gebyrMottaker.sak shouldBe Saksnummer(SAKSNUMMER)
            gebyrMottaker.skyldner shouldBe Personident(testdataBM.ident)

            val gebyrSkyldner = it.find { it.type == Engangsbeløptype.GEBYR_SKYLDNER }!!

            gebyrSkyldner.beløp shouldBe BigDecimal(0)
            gebyrSkyldner.kravhaver shouldBe Personident("NAV")
            gebyrSkyldner.mottaker shouldBe Personident("NAV")
            gebyrSkyldner.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            gebyrSkyldner.resultatkode shouldBe Resultatkode.GEBYR_ILAGT.name
            gebyrSkyldner.sak shouldBe Saksnummer(SAKSNUMMER)
            gebyrSkyldner.skyldner shouldBe Personident(testdataBP.ident)
        }

        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        opprettVedtakRequest.validerPersongrunnlag()
        opprettVedtakRequest.validerSluttberegning()
        opprettVedtakRequest.validerBosstatusPerioder()
        opprettVedtakRequest.validerInntekter()
        opprettVedtakRequest.validerSamvær()
        opprettVedtakRequest.validerUndeholdskostnad()

        assertSoftly(opprettVedtakRequest) {
            val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!

            assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, bpGrunnlag.referanse)) {
                val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
                it.gjelderReferanse.shouldBe(bpGrunnlag.referanse)
                innhold.summertMånedsinntektListe.shouldHaveSize(13)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE)) {
                shouldHaveSize(2)
                assertSoftly(this[0].innholdTilObjekt<BarnetilsynMedStønadPeriode>()) {
                    skolealder shouldBe Skolealder.UNDER
                    tilsynstype shouldBe Tilsynstype.HELTID
                }
                assertSoftly(this[1].innholdTilObjekt<BarnetilsynMedStønadPeriode>()) {
                    skolealder shouldBe Skolealder.IKKE_ANGITT
                    tilsynstype shouldBe Tilsynstype.IKKE_ANGITT
                }
            }
            validerNotater(behandling)
            hentGrunnlagstyper(Grunnlagstype.TILLEGGSSTØNAD_PERIODE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.FAKTISK_UTGIFT_PERIODE) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSPERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSKALKULATOR) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 31
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak med reel mottaker`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatGrunnlag.NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatGrunnlag.NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.refVedtaksid = 553
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandlingMedReelMottaker(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 1
            assertSoftly(request.stønadsendringListe[0]) {
                skyldner.verdi shouldBe testdataBP.ident
                kravhaver.verdi shouldBe testdataBarn1.ident
                mottaker.verdi shouldBe "REEL_MOTTAKER"
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak med innbetalt beløp`() {
        stubPersonConsumer()
        val innbetaltBeløp = BigDecimal(10000)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatGrunnlag.NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatGrunnlag.NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.refVedtaksid = 553
        behandling.søknadsbarn.first().innbetaltBeløp = innbetaltBeløp
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 1
        }
        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(3)

            it.any { it.type == Engangsbeløptype.GEBYR_MOTTAKER }.shouldBeTrue()
            it.any { it.type == Engangsbeløptype.GEBYR_SKYLDNER }.shouldBeTrue()
            it.any { it.type == Engangsbeløptype.DIREKTE_OPPGJØR }.shouldBeTrue()
            assertSoftly(it.find { it.type == Engangsbeløptype.DIREKTE_OPPGJØR }!!) {
                beløp shouldBe innbetaltBeløp
                skyldner shouldBe Personident(testdataBP.ident)
                kravhaver shouldBe Personident(testdataBarn1.ident)
                mottaker shouldBe Personident(testdataBM.ident)
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.DIREKTE_OPPJØR.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                beslutning shouldBe Beslutningstype.ENDRING
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak med direkte avslag`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.refVedtaksid = 553
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.grunnlagListe shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG) shouldHaveSize 1
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SØKNAD)) {
                shouldHaveSize(1)
                val innhold = it[0].innholdTilObjekt<SøknadGrunnlag>()
                innhold.søktAv shouldBe SøktAvType.BIDRAGSMOTTAKER
            }

            request.stønadsendringListe shouldHaveSize 1
            assertSoftly(request.stønadsendringListe[0]) {
                it.type shouldBe Stønadstype.BIDRAG
                it.beslutning shouldBe Beslutningstype.ENDRING
                it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                it.sak shouldBe Saksnummer(SAKSNUMMER)
                it.skyldner shouldBe Personident(testdataBP.ident)
                it.kravhaver shouldBe Personident(testdataBarn1.ident)
                it.mottaker shouldBe Personident(testdataBM.ident)
                it.grunnlagReferanseListe shouldHaveSize 2
                it.periodeListe shouldHaveSize 1
                assertSoftly(it.periodeListe[0]) {
                    it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
                    it.periode.til shouldBe null
                    it.beløp shouldBe null
                    it.resultatkode shouldBe Resultatkode.BIDRAGSPLIKTIG_ER_DØD.name
                }
            }
        }
        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(2)

            it.any { it.type == Engangsbeløptype.GEBYR_MOTTAKER }.shouldBeTrue()
            it.any { it.type == Engangsbeløptype.GEBYR_SKYLDNER }.shouldBeTrue()
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak med direkte avslag med reel mottaker`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.refVedtaksid = 553
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandlingMedReelMottaker(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 1
            assertSoftly(request.stønadsendringListe[0]) {
                it.skyldner shouldBe Personident(testdataBP.ident)
                it.kravhaver shouldBe Personident(testdataBarn1.ident)
                it.mottaker shouldBe Personident("REEL_MOTTAKER")
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    private fun OpprettVedtakRequestDto.validerVedtaksdetaljer(behandling: Behandling) {
        assertSoftly("Søknadsdetaljer") {
            grunnlagListe.virkningsdato shouldNotBe null
            val virkningsdato =
                grunnlagListe.virkningsdato?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
            virkningsdato!!.virkningstidspunkt shouldHaveSameDayAs behandling.virkningstidspunkt!!
            virkningsdato.årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT

            grunnlagListe.søknad shouldNotBe null
            val søknad = grunnlagListe.søknad?.innholdTilObjekt<SøknadGrunnlag>()
            søknad!!.mottattDato shouldHaveSameDayAs behandling.mottattdato
            søknad.søktAv shouldBe behandling.soknadFra
            søknad.klageMottattDato shouldBe null
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
    }

    private fun OpprettVedtakRequestDto.validerPersongrunnlag() {
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN)) {
            shouldHaveSize(1)
            it.shouldContainPerson(testdataBarn1.ident)
        }
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)) {
            shouldHaveSize(4)
            it.shouldContainPerson(testdataHusstandsmedlem1.ident)
        }
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER)) {
            shouldHaveSize(1)
            it.shouldContainPerson(testdataBM.ident)
        }
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG)) {
            shouldHaveSize(1)
            it.shouldContainPerson(testdataBP.ident)
        }
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER)) {
            shouldHaveSize(1)
            it.shouldContainPerson(testdataBarnBm.ident)
        }
    }
}

private fun OpprettVedtakRequestDto.validerNotater(behandling: Behandling) {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
        shouldHaveSize(6)
        assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
            innhold shouldBe henteNotatinnhold(behandling, NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT)
            erMedIVedtaksdokumentet shouldBe false
            type shouldBe NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
        }

        assertSoftly(hentNotat(NotatGrunnlag.NotatType.SAMVÆR, gjelderReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Samvær"
        }

        assertSoftly(hentNotat(NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD, gjelderReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Underhold barn"
        }

        assertSoftly(hentNotat(NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Underhold andre barn"
        }

        assertSoftly(hentNotat(NotatGrunnlag.NotatType.INNTEKT, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Inntektsbegrunnelse kun i notat"
        }
    }
}

private fun OpprettVedtakRequestDto.validerSluttberegning() {
    val sluttberegning =
        hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG)
    sluttberegning shouldHaveSize (8)
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!

    val sluttberegningPeriode = sluttberegning[6]
    assertSoftly(sluttberegningPeriode) {
        val innhold = innholdTilObjekt<SluttberegningBarnebidrag>()
        innhold.resultatVisningsnavn!!.intern shouldBe "Kostnadsberegnet bidrag"
        innhold.beregnetBeløp shouldBe BigDecimal("6121.53")
        innhold.resultatBeløp shouldBe BigDecimal("6120")
        it.grunnlagsreferanseListe shouldHaveSize 8
        hentGrunnlagstyperForReferanser(Grunnlagstype.PERSON_SØKNADSBARN, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.PERSON_SØKNADSBARN, it.grunnlagsreferanseListe).first().referanse shouldBe søknadsbarn1Grunnlag.referanse
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSEVNE, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE, it.grunnlagsreferanseListe) shouldHaveSize 2
        hentGrunnlagstyperForReferanser(Grunnlagstype.SAMVÆRSPERIODE, it.grunnlagsreferanseListe) shouldHaveSize 1
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSEVNE, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningBidragsevne>()
        innhold.beløp shouldBe BigDecimal("9482.45")
        it.grunnlagsreferanseListe shouldHaveSize 14
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningBidragspliktigesAndel>()
        innhold.andelBeløp shouldBe BigDecimal("7132.53")
        it.grunnlagsreferanseListe shouldHaveSize 10
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningUnderholdskostnad>()
        innhold.underholdskostnad shouldBe BigDecimal("8559.04")
        innhold.nettoTilsynsutgift shouldBe BigDecimal("1287.04")
        innhold.barnetilsynMedStønad shouldBe BigDecimal("630.00")
        it.grunnlagsreferanseListe shouldHaveSize 6
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningSamværsfradrag>()
        innhold.beløp shouldBe BigDecimal("1011.00")
        it.grunnlagsreferanseListe shouldHaveSize 3
    }
}

private fun OpprettVedtakRequestDto.validerBosstatusPerioder() {
    val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    val husstandsmedlemGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
        shouldHaveSize(6)
        val bostatusSøknadsbarn1 =
            it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag.referanse)
        bostatusSøknadsbarn1.shouldHaveSize(2)
        it[0].gjelderReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[1].gjelderReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[2].gjelderReferanse shouldBe husstandsmedlemGrunnlag.referanse
        it[3].gjelderReferanse shouldBe husstandsmedlemGrunnlag.referanse
        assertSoftly(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-02")
            periode.til shouldBe YearMonth.parse("2023-08")
            relatertTilPart shouldBe bpGrunnlag.referanse
        }
        assertSoftly(bostatusSøknadsbarn1[1].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-08")
            periode.til shouldBe null
            relatertTilPart shouldBe bpGrunnlag.referanse
        }

        it.filtrerBasertPåFremmedReferanse(referanse = husstandsmedlemGrunnlag.referanse).shouldHaveSize(2)
    }
}

private fun OpprettVedtakRequestDto.validerUndeholdskostnad() {
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    val husstandsmedlemGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
    val bmBarnGrunnlag = grunnlagListe.hentPerson(testdataBarnBm.ident)!!
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!

    assertSoftly(hentGrunnlagstyper(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE)) {
        shouldHaveSize(2)
        assertSoftly(it[0]) {
            gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
            gjelderReferanse shouldBe bmGrunnlag.referanse
            grunnlagsreferanseListe shouldHaveSize 0
        }
        assertSoftly(it[1]) {
            gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
            gjelderReferanse shouldBe bmGrunnlag.referanse
            grunnlagsreferanseListe shouldHaveSize 1
        }
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.TILLEGGSSTØNAD_PERIODE)) {
        shouldHaveSize(1)
        it[0].gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.FAKTISK_UTGIFT_PERIODE)) {
        shouldHaveSize(3)
        it[0].gjelderReferanse shouldBe bmGrunnlag.referanse
        it[1].gjelderReferanse shouldBe bmGrunnlag.referanse
        it[2].gjelderReferanse shouldBe bmGrunnlag.referanse

        val søknadsbarnFU = it.find { it.gjelderBarnReferanse == søknadsbarnGrunnlag.referanse }!!
        søknadsbarnFU shouldNotBe null
        val innholdSøknadsbarnFU = søknadsbarnFU.innholdTilObjekt<FaktiskUtgiftPeriode>()
        innholdSøknadsbarnFU.kommentar shouldBe "Kommentar på tilsynsutgift"
        innholdSøknadsbarnFU.faktiskUtgiftBeløp shouldBe BigDecimal(4000)
        innholdSøknadsbarnFU.kostpengerBeløp shouldBe BigDecimal(1000)

        val bmBarnFU = it.find { it.gjelderBarnReferanse == bmBarnGrunnlag.referanse }
        bmBarnFU shouldNotBe null

        val hustandsmedlemFU = it.find { it.gjelderBarnReferanse == husstandsmedlemGrunnlag.referanse }
        hustandsmedlemFU shouldNotBe null
    }
}

private fun OpprettVedtakRequestDto.validerSamvær() {
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!

    val samværsperioder = hentGrunnlagstyper(Grunnlagstype.SAMVÆRSPERIODE)
    samværsperioder shouldHaveSize 2
    val manuellPeriode = samværsperioder.find { grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE, it.grunnlagsreferanseListe).isEmpty() }!!
    val beregnetPeriode = samværsperioder.find { grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE, it.grunnlagsreferanseListe).isNotEmpty() }!!
    assertSoftly(manuellPeriode) {
        it.grunnlagsreferanseListe shouldHaveSize 0
        it.innholdTilObjekt<SamværsperiodeGrunnlag>().samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_1
        it.gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
        it.gjelderReferanse shouldBe bpGrunnlag.referanse
    }
    assertSoftly(beregnetPeriode) {
        it.grunnlagsreferanseListe shouldHaveSize 8
        grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.SJABLON_SAMVARSFRADRAG, it) shouldHaveSize 5
        grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER, it) shouldHaveSize 1
        grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE, it) shouldHaveSize 1
        grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.SAMVÆRSKALKULATOR, it) shouldHaveSize 1
        it.gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
        it.gjelderReferanse shouldBe bpGrunnlag.referanse
        val innhold = it.innholdTilObjekt<SamværsperiodeGrunnlag>()
        innhold.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
        val kalkulator = grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.SAMVÆRSKALKULATOR, it).first()
        val innholdKalkulator = kalkulator.innholdTilObjekt<SamværskalkulatorDetaljer>()
        kalkulator.gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
        kalkulator.gjelderReferanse shouldBe bpGrunnlag.referanse
        innholdKalkulator.ferier shouldHaveSize 5
        innholdKalkulator.regelmessigSamværNetter shouldBe BigDecimal(4)

        val delberegningSamværsklasse = grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE, it).first()
        val innholdSamværsklasse = delberegningSamværsklasse.innholdTilObjekt<DelberegningSamværsklasse>()
        delberegningSamværsklasse.gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
        delberegningSamværsklasse.gjelderReferanse shouldBe bpGrunnlag.referanse
        innholdSamværsklasse.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
        innholdSamværsklasse.gjennomsnittligSamværPerMåned shouldBe BigDecimal("8.01")
    }
}

private fun OpprettVedtakRequestDto.validerInntekter() {
    val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)) {
        shouldHaveSize(4)
        it[0].gjelderReferanse.shouldBe(bpGrunnlag.referanse)
        it[1].gjelderReferanse.shouldBe(bpGrunnlag.referanse)
        it[2].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[3].gjelderReferanse.shouldBe(bmGrunnlag.referanse)

        assertSoftly(it[0].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2023-02")
            periode.til shouldBe null
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 500000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[1].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2023-07")
            periode.til shouldBe null
            inntekstpostListe shouldHaveSize 1
            beløp shouldBe 3000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
            gjelderBarn shouldBe søknadsbarnGrunnlag.referanse
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }

        assertSoftly(it[3].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2023-07")
            periode.til shouldBe null
            inntekstpostListe shouldHaveSize 1
            beløp shouldBe 3000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
            gjelderBarn shouldBe søknadsbarnGrunnlag.referanse
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
    }
}
