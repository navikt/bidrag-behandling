package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.verify
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.vedtak.grunnlagsreferanse_løpende_bidrag
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettLøpendeBidragGrunnlag
import no.nav.bidrag.behandling.utils.testdata.oppretteUtgift
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.beregn.særbidrag.BeregnSærbidragApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.søknadsbarn
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.client.HttpClientErrorException
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class BeregningServiceTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @BeforeEach
    fun initMocks() {
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
        val behandling =
            opprettGyldigBehandlingForBeregningOgVedtak(
                true,
                typeBehandling = TypeBehandling.SÆRBIDRAG,
            )
        val grunnlag = behandling.søknadsbarn.map { it.tilGrunnlagPerson() }.toMutableList()
        grunnlag.add(
            testdataHusstandsmedlem1.tilRolle(behandling, 1).tilGrunnlagPerson(),
        )
        grunnlag.add(
            GrunnlagDto(
                referanse = grunnlagsreferanse_løpende_bidrag,
                type = Grunnlagstype.LØPENDE_BIDRAG,
                innhold =
                    POJONode(
                        LøpendeBidragGrunnlag(
                            løpendeBidragListe =
                                listOf(
                                    opprettLøpendeBidragGrunnlag(testdataBarn1, Stønadstype.BIDRAG, 2),
                                    opprettLøpendeBidragGrunnlag(testdataHusstandsmedlem1, Stønadstype.BIDRAG, 1),
                                ),
                        ),
                    ),
            ),
        )

        every { evnevurderingService.opprettGrunnlagLøpendeBidrag(any(), any()) } returns grunnlag
    }

    @Test
    fun `skal bygge grunnlag for forskudd beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        mockkConstructor(BeregnForskuddApi::class)
        every { BeregnForskuddApi().beregn(capture(beregnCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, evnevurderingService).beregneForskudd(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 2) {
            BeregnForskuddApi().beregn(any())
        }
        resultat shouldHaveSize 2
        resultat[0].resultat.grunnlagListe shouldHaveSize 31
        beregnGrunnlagList shouldHaveSize 2
        assertSoftly(beregnGrunnlagList[0]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.now().plusMonths(1)
            it.grunnlagListe shouldHaveSize 16

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 4
            personer.hentPerson(testdataBarn1.ident) shouldNotBe null

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 5
        }
        assertSoftly(beregnGrunnlagList[1]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.now().plusMonths(1)
            it.grunnlagListe shouldHaveSize 14

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 4
            personer.hentPerson(testdataBarn2.ident) shouldNotBe null

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 3
        }
    }

    @Test
    fun `skal bygge grunnlag for særbidrag beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.utgift!!.maksGodkjentBeløp = null
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Maks godkjent beløp"
        behandling.utgift!!.maksGodkjentBeløpTaMed = false
        behandling.utgift!!.utgiftsposter.add(
            Utgiftspost(
                dato = LocalDate.now().minusDays(3),
                type = Utgiftstype.KONFIRMASJONSLEIR.name,
                kravbeløp = BigDecimal(3000),
                godkjentBeløp = BigDecimal(2500),
                kommentar = "Trekker fra alkohol",
                utgift = behandling.utgift!!,
            ),
        )
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()
        mockkConstructor(BeregnSærbidragApi::class)
        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, evnevurderingService).beregneSærbidrag(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 1) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        resultat shouldNotBe null
        vedtaksTypeCapture.captured shouldBe Vedtakstype.FASTSETTELSE
        resultat.grunnlagListe shouldHaveSize 31
        beregnGrunnlagList shouldHaveSize 1
        assertSoftly(beregnGrunnlagList[0]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.now().plusMonths(1)
            it.grunnlagListe shouldHaveSize 12

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 5
            personer.hentPerson(testdataBarn1.ident) shouldNotBe null
            personer.map { it.type } shouldContainAll
                listOf(
                    Grunnlagstype.PERSON_SØKNADSBARN,
                    Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
                    Grunnlagstype.PERSON_BIDRAGSMOTTAKER,
                    Grunnlagstype.PERSON_HUSSTANDSMEDLEM,
                )

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 3
            val barnStatus = bostatuser.find { it.gjelderReferanse == grunnlagListe.søknadsbarn.first().referanse }
            barnStatus!!.innholdTilObjekt<BostatusPeriode>().bostatus shouldBe Bostatuskode.MED_FORELDER

            val andreVoksneIHusstanden = bostatuser.find { it.gjelderReferanse == grunnlagListe.bidragspliktig!!.referanse }
            andreVoksneIHusstanden!!.innholdTilObjekt<BostatusPeriode>().bostatus shouldBe Bostatuskode.BOR_MED_ANDRE_VOKSNE

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 0

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 2
            inntekter.find { it.gjelderReferanse == grunnlagListe.bidragspliktig!!.referanse } shouldNotBe null
            inntekter.find { it.gjelderReferanse == grunnlagListe.bidragsmottaker!!.referanse } shouldNotBe null

            val delberegningUtgift = it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.DELBEREGNING_UTGIFT).first()
            delberegningUtgift.innholdTilObjekt<DelberegningUtgift>().sumGodkjent shouldBe BigDecimal(5000)
        }
    }

    @Test
    fun `skal bygge grunnlag for særbidrag beregning med maks godkjent beløp tatt med`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.utgift!!.utgiftsposter.add(
            Utgiftspost(
                dato = LocalDate.now().minusDays(3),
                type = Utgiftstype.KONFIRMASJONSLEIR.name,
                kravbeløp = BigDecimal(3000),
                godkjentBeløp = BigDecimal(2500),
                kommentar = "Trekker fra alkohol",
                utgift = behandling.utgift!!,
            ),
        )
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(3000)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Maks godkjent beløp"
        behandling.utgift!!.maksGodkjentBeløpTaMed = true
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()
        mockkConstructor(BeregnSærbidragApi::class)
        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, evnevurderingService).beregneSærbidrag(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 1) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        resultat shouldNotBe null
        vedtaksTypeCapture.captured shouldBe Vedtakstype.FASTSETTELSE
        resultat.grunnlagListe shouldHaveSize 31
        beregnGrunnlagList shouldHaveSize 1
        assertSoftly(beregnGrunnlagList[0]) {
            val delberegningUtgift = it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.DELBEREGNING_UTGIFT).first()
            delberegningUtgift.innholdTilObjekt<DelberegningUtgift>().sumGodkjent shouldBe BigDecimal(3000)
        }
    }

    @Test
    fun `skal bygge grunnlag for særbidrag beregning med opprinnelig vedtakstype`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.opprinneligVedtakstype = Vedtakstype.ENDRING
        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()
        mockkConstructor(BeregnSærbidragApi::class)
        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, evnevurderingService).beregneSærbidrag(1)

        verify(exactly = 1) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        resultat shouldNotBe null
        vedtaksTypeCapture.captured shouldBe Vedtakstype.ENDRING
    }

    @Test
    fun `skal feile hvis ugyldig behandling for særbidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.virkningstidspunkt = LocalDate.now().minusMonths(2).withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        mockkConstructor(BeregnSærbidragApi::class)
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()

        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }

        val exception =
            assertThrows<HttpClientErrorException> { BeregningService(behandlingService, evnevurderingService).beregneSærbidrag(1) }
        verify(exactly = 0) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        exception.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
    }
}
