package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
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
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.shouldContainPerson
import no.nav.bidrag.behandling.utils.søknad
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetillegg
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggTilFaktiskTilsynsutgift
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.leggTilTillegsstønad
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.behandling.utils.virkningsdato
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesBeregnedeTotalbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningSærbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
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
        behandling.leggTilBarnetilsyn(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatGrunnlag.NotatType.INNTEKT,
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
                "grunnlagresponse_bp.json",
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
            withClue("Grunnlagliste skal inneholde 162 grunnlag") {
                request.grunnlagListe shouldHaveSize 162
            }
        }

        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        opprettVedtakRequest.validerPersongrunnlag()
        opprettVedtakRequest.validerSluttberegning()
//        opprettVedtakRequest.validerBosstatusPerioder()
//        opprettVedtakRequest.validerInntektrapportering()

        assertSoftly(opprettVedtakRequest) {
            val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!

            assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, bpGrunnlag.referanse)) {
                val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
                it.gjelderReferanse.shouldBe(bpGrunnlag.referanse)
                innhold.summertMånedsinntektListe.shouldHaveSize(13)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(5)
                assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe henteNotatinnhold(behandling, NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT)
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
                }

                val notatInntekter = this.find { it.innholdTilObjekt<NotatGrunnlag>().type == NotatGrunnlag.NotatType.INNTEKT }
                notatInntekter!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe "Inntektsbegrunnelse kun i notat"
            }

            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3 // TODO: Hvorfor 3?
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 31
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 4
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 2
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

    private fun OpprettVedtakRequestDto.validerBosstatusPerioder(virkningstidspunkt: LocalDate) {
        val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
        val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
        val husstandsmedlemGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
            shouldHaveSize(5)
            val bostatusSøknadsbarn1 =
                it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag.referanse)
            bostatusSøknadsbarn1.shouldHaveSize(1)

            assertSoftly(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
                bostatus shouldBe Bostatuskode.MED_FORELDER
                periode.fom shouldBe YearMonth.from(virkningstidspunkt)
                periode.til shouldBe null
                relatertTilPart shouldBe bpGrunnlag.referanse
            }
            val bostatusBp =
                it.filtrerBasertPåFremmedReferanse(referanse = bpGrunnlag.referanse)
            bostatusBp.shouldHaveSize(1)
            assertSoftly(bostatusBp[0].innholdTilObjekt<BostatusPeriode>()) {
                bostatus shouldBe Bostatuskode.BOR_MED_ANDRE_VOKSNE
                periode.fom shouldBe YearMonth.from(virkningstidspunkt)
                periode.til shouldBe null
                relatertTilPart shouldBe bpGrunnlag.referanse
            }
            it
                .filtrerBasertPåFremmedReferanse(referanse = husstandsmedlemGrunnlag.referanse)
                .shouldHaveSize(1)
        }
    }

    private fun OpprettVedtakRequestDto.validerInntekter() {
        val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
        val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
        val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)) {
            shouldHaveSize(23)
            val inntekterBM = it.filter { it.gjelderReferanse == bmGrunnlag.referanse }
            val inntekterBP = it.filter { it.gjelderReferanse == bpGrunnlag.referanse }
            val inntekterBA = it.filter { it.gjelderReferanse == søknadsbarnGrunnlag.referanse }
            inntekterBM shouldHaveSize 11
            inntekterBP shouldHaveSize 8
            inntekterBA shouldHaveSize 4

            val inntektBm =
                inntekterBM.map { it.innholdTilObjekt<InntektsrapporteringPeriode>() }.find {
                    it.inntektsrapportering ==
                        Inntektsrapportering.AINNTEKT_BEREGNET_3MND
                }!!
            inntektBm.beløp shouldBe BigDecimal(720000)
            inntektBm.valgt shouldBe true
        }
        assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, søknadsbarnGrunnlag.referanse)) {
            val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
            innhold.summertMånedsinntektListe.shouldHaveSize(3)
        }
        assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, bmGrunnlag.referanse)) {
            val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
            innhold.summertMånedsinntektListe.shouldHaveSize(12)
        }
        assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, bpGrunnlag.referanse)) {
            val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
            innhold.summertMånedsinntektListe.shouldHaveSize(12)
        }
    }

    private fun OpprettVedtakRequestDto.validerSluttberegning(virkningstidspunkt: LocalDate) {
        val sluttberegningSærbidrag = hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG)

        assertSoftly(sluttberegningSærbidrag) {
            shouldHaveSize(1)
            val innhold = innholdTilObjekt<SluttberegningSærbidrag>().first()
            innhold.resultatKode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET
            innhold.periode shouldBe ÅrMånedsperiode(virkningstidspunkt, virkningstidspunkt.plusMonths(1))
            innhold.beregnetBeløp shouldBe BigDecimal("9838.71")
            innhold.resultatBeløp shouldBe BigDecimal(9839)
        }

        val delberegningBidragsevne =
            grunnlagListe
                .finnGrunnlagSomErReferertAv(
                    Grunnlagstype.DELBEREGNING_BIDRAGSEVNE,
                    sluttberegningSærbidrag.first(),
                ).toList() as List<OpprettGrunnlagRequestDto>

        assertSoftly(delberegningBidragsevne) {
            shouldHaveSize(1)
            val innhold = innholdTilObjekt<DelberegningBidragsevne>().first()
            innhold.beløp shouldBe BigDecimal("13939.20")
            innhold.periode shouldBe ÅrMånedsperiode(virkningstidspunkt, virkningstidspunkt.plusMonths(1))
        }

        val delberegningBPsBeregnedeTotalbidragGrunnlag =
            grunnlagListe
                .finnGrunnlagSomErReferertAv(
                    Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_BEREGNEDE_TOTALBIDRAG,
                    sluttberegningSærbidrag.first(),
                ).first()

        assertSoftly(delberegningBPsBeregnedeTotalbidragGrunnlag) {
            val innhold = it.innholdTilObjekt<DelberegningBidragspliktigesBeregnedeTotalbidrag>()
            innhold.bidragspliktigesBeregnedeTotalbidrag.setScale(0, RoundingMode.HALF_UP) shouldBe BigDecimal(9263)
            innhold.periode shouldBe ÅrMånedsperiode(virkningstidspunkt, virkningstidspunkt.plusMonths(1))
        }

        val delberegningBpsAndel =
            grunnlagListe
                .finnGrunnlagSomErReferertAv(
                    Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL,
                    sluttberegningSærbidrag.first(),
                ).toList()

        assertSoftly(delberegningBpsAndel) {
            shouldHaveSize(1)
            val innhold = innholdTilObjekt<DelberegningBidragspliktigesAndel>().first()
            innhold.endeligAndelFaktor shouldBe "0.4919354839".toBigDecimal()
            innhold.andelProsent shouldBe "49.19".toBigDecimal()
            innhold.andelBeløp shouldBe BigDecimal("9838.71")
            innhold.barnetErSelvforsørget shouldBe false
            innhold.periode shouldBe ÅrMånedsperiode(virkningstidspunkt, virkningstidspunkt.plusMonths(1))
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

private fun OpprettVedtakRequestDto.validerSluttberegning() {
    val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)

    val sluttberegning =
        hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG)
    sluttberegning shouldHaveSize (8)

    assertSoftly(sluttberegning[5]) {
        val innhold = innholdTilObjekt<SluttberegningBarnebidrag>()
        innhold.resultatKode shouldBe no.nav.bidrag.domene.enums.beregning.Resultatkode.KOSTNADSBEREGNET_BIDRAG
        innhold.beregnetBeløp shouldBe BigDecimal("5049.00")
        innhold.resultatBeløp shouldBe BigDecimal("5050.00")
        it.grunnlagsreferanseListe shouldHaveSize 7
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSEVNE, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.BARNETILLEGG_PERIODE, it.grunnlagsreferanseListe) shouldHaveSize 1
    }
}
