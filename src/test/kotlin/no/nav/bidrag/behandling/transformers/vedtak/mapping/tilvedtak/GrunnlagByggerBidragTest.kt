package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.utils.harReferanseTilGrunnlag
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggTilFaktiskTilsynsutgift
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.leggTilTillegsstønad
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.samværskalkulator.SamværskalkulatorFerietype
import no.nav.bidrag.domene.enums.samværskalkulator.SamværskalkulatorNetterFrekvens
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.BarnetilsynMedStønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import no.nav.bidrag.transport.behandling.felles.grunnlag.FaktiskUtgiftPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilleggsstønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.søknadsbarn
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth

class GrunnlagByggerBidragTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initPersonMock() {
            stubPersonConsumer()
        }
    }

    val mapper = BehandlingTilGrunnlagMappingV2(PersonService(stubPersonConsumer()), BeregnSamværsklasseApi(stubSjablonService()))

    @Test
    fun `skal mappe barnetilsyn til grunnlag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilBarnetilsyn(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), generateId = true)
        behandling.leggTilBarnetilsyn(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)),
            generateId = true,
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = false,
            kilde = Kilde.OFFENTLIG,
        )

        val grunnlag = behandling.tilGrunnlagBarnetilsyn(true)
        assertSoftly(grunnlag) {
            shouldHaveSize(3)
            it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            it.filtrerBasertPåEgenReferanse(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE) shouldHaveSize 2
            val perioder = it.filtrerBasertPåEgenReferanse(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE)
            assertSoftly(perioder[0]) {
                gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
                gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
                val innhold = it.innholdTilObjekt<BarnetilsynMedStønadPeriode>()
                innhold.skolealder shouldBe Skolealder.UNDER
                innhold.tilsynstype shouldBe Tilsynstype.HELTID
                innhold.manueltRegistrert shouldBe true
                innhold.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt!!.plusMonths(1))
                grunnlagsreferanseListe shouldHaveSize 0
            }
            assertSoftly(perioder[1]) {
                gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
                gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
                val innhold = it.innholdTilObjekt<BarnetilsynMedStønadPeriode>()
                innhold.skolealder shouldBe Skolealder.OVER
                innhold.tilsynstype shouldBe Tilsynstype.HELTID
                innhold.manueltRegistrert shouldBe false
                innhold.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
                innhold.periode.til shouldBe YearMonth.from(behandling.virkningstidspunkt!!.plusMonths(1))
                grunnlagsreferanseListe shouldHaveSize 1
            }
        }
    }

    @Test
    fun `skal mappe faktisk tilsynsutgift til grunnlag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), testdataHusstandsmedlem1, medId = true)
        behandling.leggTilFaktiskTilsynsutgift(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null),
            testdataBarnBm,
            medId = true,
        )
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!, null), medId = true)
        val grunnlag = mapper.run { behandling.tilGrunnlagFaktiskeTilsynsutgifter(behandling.tilPersonobjekter()) }
        assertSoftly(grunnlag) {
            shouldHaveSize(6)
            it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER) shouldHaveSize 2

            val personHusstandsmedlem = it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER).find { it.personIdent == testdataHusstandsmedlem1.ident }
            val personBarnBM = it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER).find { it.personIdent == testdataBarnBm.ident }
            personHusstandsmedlem.shouldNotBeNull()
            personBarnBM.shouldNotBeNull()

            it.filtrerBasertPåEgenReferanse(Grunnlagstype.FAKTISK_UTGIFT_PERIODE) shouldHaveSize 3
            val perioder = it.filtrerBasertPåEgenReferanse(Grunnlagstype.FAKTISK_UTGIFT_PERIODE)

            perioder.any { it.gjelderBarnReferanse == personHusstandsmedlem.referanse }.shouldBeTrue()
            perioder.any { it.gjelderBarnReferanse == personBarnBM.referanse }.shouldBeTrue()

            assertSoftly(perioder.find { it.gjelderBarnReferanse == behandling.søknadsbarn.first().tilGrunnlagsreferanse() }!!) {
                gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
                gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
                val innhold = it.innholdTilObjekt<FaktiskUtgiftPeriode>()
                innhold.faktiskUtgiftBeløp shouldBe BigDecimal(4000)
                innhold.kostpengerBeløp shouldBe BigDecimal(1000)
                innhold.kommentar shouldBe "Kommentar på tilsynsutgift"
                innhold.fødselsdatoBarn shouldBe søknadsbarn.first().personObjekt.fødselsdato
                innhold.manueltRegistrert shouldBe true
                innhold.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
                innhold.periode.til shouldBe null
                grunnlagsreferanseListe shouldHaveSize 0
            }
        }
    }

    @Test
    fun `skal mappe tilleggstønad til grunnlag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilTillegsstønad(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(5)), medId = true)
        behandling.leggTilTillegsstønad(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(5), null), medId = true)

        val grunnlag = behandling.tilGrunnlagTilleggsstønad()
        assertSoftly(grunnlag) {
            shouldHaveSize(3)
            it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            it.filtrerBasertPåEgenReferanse(Grunnlagstype.TILLEGGSSTØNAD_PERIODE) shouldHaveSize 2
            val perioder = it.filtrerBasertPåEgenReferanse(Grunnlagstype.TILLEGGSSTØNAD_PERIODE)
            assertSoftly(perioder[0]) {
                gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
                gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
                val innhold = it.innholdTilObjekt<TilleggsstønadPeriode>()
                innhold.beløpDagsats shouldBe BigDecimal(50)
                innhold.manueltRegistrert shouldBe true
                innhold.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
                innhold.periode.til shouldBe YearMonth.from(behandling.virkningstidspunkt!!.plusMonths(5))
                grunnlagsreferanseListe shouldHaveSize 0
            }
        }
    }

    @Test
    fun `skal mappe samvær til grunnlag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        val grunnlag = mapper.run { behandling.tilGrunnlagSamvær(behandling.tilPersonobjekter().søknadsbarn.first()) }

        val søknadsbarnGrunnlagsreferanse = behandling.søknadsbarn.first().tilGrunnlagsreferanse()
        val bpGrunnlagsreferanse = behandling.bidragspliktig!!.tilGrunnlagsreferanse()
        assertSoftly(grunnlag) {
            shouldHaveSize(10)
            it.filtrerBasertPåEgenReferanse(Grunnlagstype.SAMVÆRSPERIODE) shouldHaveSize 2
            val perioder = it.filtrerBasertPåEgenReferanse(Grunnlagstype.SAMVÆRSPERIODE)
            assertSoftly(perioder[0]) {
                gjelderBarnReferanse shouldBe søknadsbarnGrunnlagsreferanse
                gjelderReferanse shouldBe bpGrunnlagsreferanse
                val innhold = it.innholdTilObjekt<SamværsperiodeGrunnlag>()
                innhold.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_1
                innhold.manueltRegistrert shouldBe true
                innhold.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
                innhold.periode.til shouldBe YearMonth.from(behandling.virkningstidspunkt!!.plusMonths(1))
                grunnlagsreferanseListe shouldHaveSize 0
            }

            assertSoftly(perioder[1]) {
                gjelderBarnReferanse shouldBe søknadsbarnGrunnlagsreferanse
                gjelderReferanse shouldBe bpGrunnlagsreferanse
                val innhold = it.innholdTilObjekt<SamværsperiodeGrunnlag>()
                innhold.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                innhold.manueltRegistrert shouldBe true
                innhold.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt!!.plusMonths(1))
                innhold.periode.til shouldBe null
                grunnlagsreferanseListe shouldHaveSize 1

                grunnlag.filtrerBasertPåEgenReferanse(Grunnlagstype.SJABLON_SAMVARSFRADRAG) shouldHaveSize 5
                grunnlag.filtrerBasertPåEgenReferanse(Grunnlagstype.SAMVÆRSKALKULATOR) shouldHaveSize 1
                grunnlag.filtrerBasertPåEgenReferanse(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE) shouldHaveSize 1
                grunnlag.filtrerBasertPåEgenReferanse(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER) shouldHaveSize 1

                val samværskalkulatorGrunnlag = grunnlag.filtrerBasertPåEgenReferanse(Grunnlagstype.SAMVÆRSKALKULATOR).first()
                samværskalkulatorGrunnlag.gjelderReferanse shouldBe bpGrunnlagsreferanse
                samværskalkulatorGrunnlag.gjelderBarnReferanse shouldBe søknadsbarnGrunnlagsreferanse
                samværskalkulatorGrunnlag.grunnlagsreferanseListe shouldHaveSize 0
                val samværskalkulatorDetaljer = samværskalkulatorGrunnlag.innholdTilObjekt<SamværskalkulatorDetaljer>()
                samværskalkulatorDetaljer.regelmessigSamværNetter shouldBe BigDecimal(4)
                samværskalkulatorDetaljer.ferier shouldHaveSize 5
                samværskalkulatorDetaljer.ferier.filter { it.type == SamværskalkulatorFerietype.JUL_NYTTÅR } shouldHaveSize 1
                samværskalkulatorDetaljer.ferier.filter { it.type == SamværskalkulatorFerietype.PÅSKE } shouldHaveSize 1
                samværskalkulatorDetaljer.ferier.filter { it.type == SamværskalkulatorFerietype.ANNET } shouldHaveSize 1
                samværskalkulatorDetaljer.ferier.filter { it.type == SamværskalkulatorFerietype.VINTERFERIE } shouldHaveSize 1
                samværskalkulatorDetaljer.ferier.filter { it.type == SamværskalkulatorFerietype.SOMMERFERIE } shouldHaveSize 1

                samværskalkulatorDetaljer.ferier.find { it.type == SamværskalkulatorFerietype.SOMMERFERIE }!!.let {
                    it.frekvens shouldBe SamværskalkulatorNetterFrekvens.ANNET_HVERT_ÅR
                    it.bidragsmottakerNetter shouldBe BigDecimal(14)
                    it.bidragspliktigNetter shouldBe BigDecimal(1)
                }

                val delberegningSamværsklasse = grunnlag.finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE, it).first()
                val innholdSamværsklasse = delberegningSamværsklasse.innholdTilObjekt<DelberegningSamværsklasse>()
                delberegningSamværsklasse.grunnlagsreferanseListe shouldHaveSize 2
                delberegningSamværsklasse.gjelderBarnReferanse shouldBe søknadsbarnGrunnlagsreferanse
                delberegningSamværsklasse.gjelderReferanse shouldBe bpGrunnlagsreferanse
                grunnlag.harReferanseTilGrunnlag(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER, delberegningSamværsklasse)
                grunnlag.harReferanseTilGrunnlag(Grunnlagstype.SAMVÆRSKALKULATOR, delberegningSamværsklasse)
                innholdSamværsklasse.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                innholdSamværsklasse.gjennomsnittligSamværPerMåned shouldBe BigDecimal("8.01")

                val delberegningSamværsklasseNetter = grunnlag.finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER, it).first()
                delberegningSamværsklasseNetter.grunnlagsreferanseListe.shouldHaveSize(5)
                delberegningSamværsklasseNetter.gjelderReferanse shouldBe null
                delberegningSamværsklasseNetter.gjelderBarnReferanse shouldBe null
                grunnlag.finnGrunnlagSomErReferertAv(Grunnlagstype.SJABLON_SAMVARSFRADRAG, delberegningSamværsklasseNetter) shouldHaveSize 5
            }
        }
    }
}
