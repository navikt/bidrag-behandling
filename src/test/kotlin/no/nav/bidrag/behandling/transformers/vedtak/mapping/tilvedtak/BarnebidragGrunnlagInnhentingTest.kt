package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockkClass
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.BarnebidragGrunnlagInnhenting
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.personIdentNav
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettStønadDto
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.toCompactString
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test

@ExtendWith(SpringExtension::class)
class BarnebidragGrunnlagInnhentingTest {
    val bidragStønadConsumer = mockkClass(BidragBeløpshistorikkConsumer::class)
    var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting = BarnebidragGrunnlagInnhenting(bidragStønadConsumer)

    @Test
    fun `skal hente grunnlag hvis søknadstype er SØKNAD`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.søknadstype = BisysSøknadstype.SØKNAD
        val grunnlagsliste = barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(behandling, behandling.søknadsbarn.first())
        grunnlagsliste shouldHaveSize 1
    }

    @Test
    fun `skal hente grunnlag hvis stønadstype er BIDRAG18AAR`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.stonadstype = Stønadstype.BIDRAG18AAR
        behandling.grunnlag.add(
            Grunnlag(
                type = Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR,
                rolle = behandling.bidragspliktig!!,
                behandling = behandling,
                innhentet = LocalDateTime.now(),
                aktiv = LocalDateTime.now(),
                gjelder = behandling.søknadsbarn.first().ident,
                data =
                    commonObjectmapper.writeValueAsString(
                        opprettStønadDto(
                            stønadstype = Stønadstype.BIDRAG18AAR,
                            periodeListe =
                                listOf(
                                    opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))).copy(
                                        vedtaksid = 200,
                                        valutakode = "NOK",
                                    ),
                                    opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null), beløp = null),
                                ),
                        ),
                    ),
            ),
        )
        behandling.søknadstype = BisysSøknadstype.SØKNAD
        val grunnlagsliste = barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(behandling, behandling.søknadsbarn.first())
        grunnlagsliste shouldHaveSize 2
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagstype.BELØPSHISTORIKK_BIDRAG }) {
            shouldNotBeNull()
            val innhold = innholdTilObjekt<BeløpshistorikkGrunnlag>()
            referanse shouldBe "BELØPSHISTORIKK_BIDRAG_${SAKSNUMMER}_" +
                "${behandling.søknadsbarn.first().ident}_${behandling.bidragspliktig!!.ident}_${LocalDate.now().toCompactString()}"
            gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
            innhold.nesteIndeksreguleringsår shouldBe null
            grunnlagsreferanseListe shouldHaveSize 0
            innhold.tidspunktInnhentet shouldHaveSameDayAs LocalDateTime.now()
            innhold.beløpshistorikk shouldHaveSize 0
        }
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagstype.BELØPSHISTORIKK_BIDRAG_18_ÅR }) {
            shouldNotBeNull()
            val innhold = innholdTilObjekt<BeløpshistorikkGrunnlag>()
            referanse shouldBe "BELØPSHISTORIKK_BIDRAG_18_ÅR_${SAKSNUMMER}_" +
                "${behandling.søknadsbarn.first().ident}_${behandling.bidragspliktig!!.ident}_${LocalDate.now().toCompactString()}"
            gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
            innhold.nesteIndeksreguleringsår shouldBe 2025
            grunnlagsreferanseListe shouldHaveSize 0
            innhold.tidspunktInnhentet shouldHaveSameDayAs LocalDateTime.now()
            innhold.beløpshistorikk shouldHaveSize 2
        }
    }

    @Test
    fun `skal ikke hente grunnlag hvis stønadstype er forskudd`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)

        behandling.stonadstype = Stønadstype.FORSKUDD
        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING
        val grunnlagsliste = barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(behandling, behandling.søknadsbarn.first())
        grunnlagsliste shouldHaveSize 0
    }

    @Test
    fun `skal ikke hente grunnlag hvis særbidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)

        behandling.stonadstype = null
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING
        val grunnlagsliste = barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(behandling, behandling.søknadsbarn.first())
        grunnlagsliste shouldHaveSize 0
    }

    @Test
    fun `skal hente grunnlag hvis søknadstype er Begrenset revurdering `() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)

        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING
        behandling.grunnlag.add(
            Grunnlag(
                type = Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD,
                rolle = behandling.bidragsmottaker!!,
                behandling = behandling,
                innhentet = LocalDateTime.now(),
                aktiv = LocalDateTime.now(),
                gjelder = behandling.søknadsbarn.first().ident,
                data =
                    commonObjectmapper.writeValueAsString(
                        opprettStønadDto(
                            stønadstype = Stønadstype.FORSKUDD,
                            periodeListe =
                                listOf(
                                    opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-07-31"))),
                                    opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2024-08-01"), null)),
                                ),
                        ).copy(
                            førsteIndeksreguleringsår = null,
                        ),
                    ),
            ),
        )
        behandling.grunnlag.add(
            Grunnlag(
                type = Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
                rolle = behandling.bidragspliktig!!,
                behandling = behandling,
                innhentet = LocalDateTime.now(),
                aktiv = LocalDateTime.now(),
                gjelder = behandling.søknadsbarn.first().ident,
                data =
                    commonObjectmapper.writeValueAsString(
                        opprettStønadDto(
                            stønadstype = Stønadstype.BIDRAG,
                            periodeListe =
                                listOf(
                                    opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), null)).copy(
                                        vedtaksid = 200,
                                        valutakode = "NOK",
                                    ),
                                ),
                        ),
                    ),
            ),
        )
        val grunnlagsliste = barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(behandling, behandling.søknadsbarn.first())
        grunnlagsliste shouldHaveSize 2
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagstype.BELØPSHISTORIKK_FORSKUDD }) {
            shouldNotBeNull()
            val innhold = innholdTilObjekt<BeløpshistorikkGrunnlag>()
            referanse shouldBe "BELØPSHISTORIKK_FORSKUDD_${SAKSNUMMER}_" +
                "${behandling.søknadsbarn.first().ident}_${personIdentNav.verdi}_${LocalDate.now().toCompactString()}"
            gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
            gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
            grunnlagsreferanseListe shouldHaveSize 0
            innhold.beløpshistorikk shouldHaveSize 2
            innhold.nesteIndeksreguleringsår shouldBe null
            innhold.tidspunktInnhentet shouldHaveSameDayAs LocalDateTime.now()
            val periode1 =
                innhold.beløpshistorikk.first()
            periode1.vedtaksid shouldBe 1
            periode1.valutakode shouldBe "NOK"
            periode1.periode.fom shouldBe YearMonth.of(2024, 1)
        }
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagstype.BELØPSHISTORIKK_BIDRAG }) {
            shouldNotBeNull()
            val innhold = innholdTilObjekt<BeløpshistorikkGrunnlag>()
            referanse shouldBe "BELØPSHISTORIKK_BIDRAG_${SAKSNUMMER}_" +
                "${behandling.søknadsbarn.first().ident}_${behandling.bidragspliktig!!.ident}_${LocalDate.now().toCompactString()}"
            gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
            grunnlagsreferanseListe shouldHaveSize 0
            innhold.beløpshistorikk shouldHaveSize 1
            innhold.nesteIndeksreguleringsår shouldBe 2025
            innhold.tidspunktInnhentet shouldHaveSameDayAs LocalDateTime.now()
            val periode1 =
                innhold.beløpshistorikk.first()
            periode1.vedtaksid shouldBe 200
            periode1.valutakode shouldBe "NOK"
            periode1.periode.fom shouldBe YearMonth.of(2023, 1)
        }
    }

    @Test
    fun `skal hente grunnlag hvis søknadstype er Begrenset revurdering hvis ingen bidrag eller forskudd `() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)

        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING
        val grunnlagsliste = barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(behandling, behandling.søknadsbarn.first())
        grunnlagsliste shouldHaveSize 2
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagstype.BELØPSHISTORIKK_FORSKUDD }) {
            shouldNotBeNull()
            val innhold = innholdTilObjekt<BeløpshistorikkGrunnlag>()
            referanse shouldBe "BELØPSHISTORIKK_FORSKUDD_${SAKSNUMMER}_" +
                "${behandling.søknadsbarn.first().ident}_${personIdentNav.verdi}_${LocalDate.now().toCompactString()}"
            gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
            gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
            grunnlagsreferanseListe shouldHaveSize 0
            innhold.beløpshistorikk shouldHaveSize 0
            innhold.nesteIndeksreguleringsår shouldBe null
            innhold.tidspunktInnhentet shouldHaveSameDayAs LocalDateTime.now()
        }
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagstype.BELØPSHISTORIKK_BIDRAG }) {
            shouldNotBeNull()
            val innhold = innholdTilObjekt<BeløpshistorikkGrunnlag>()
            referanse shouldBe "BELØPSHISTORIKK_BIDRAG_${SAKSNUMMER}_" +
                "${behandling.søknadsbarn.first().ident}_${behandling.bidragspliktig!!.ident}_${LocalDate.now().toCompactString()}"
            gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            gjelderBarnReferanse shouldBe behandling.søknadsbarn.first().tilGrunnlagsreferanse()
            innhold.nesteIndeksreguleringsår shouldBe null
            grunnlagsreferanseListe shouldHaveSize 0
            innhold.tidspunktInnhentet shouldHaveSameDayAs LocalDateTime.now()
            innhold.beløpshistorikk shouldHaveSize 0
        }
    }
}
