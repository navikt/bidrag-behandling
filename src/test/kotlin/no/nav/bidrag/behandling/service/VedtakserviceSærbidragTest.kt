package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.validering.virkningstidspunkt
import no.nav.bidrag.behandling.utils.hentGrunnlagstype
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.hentNotat
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.shouldContainPerson
import no.nav.bidrag.behandling.utils.søknad
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.initGrunnlagRespons
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.taMedInntekt
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.behandling.utils.virkningsdato
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesBeregnedeTotalbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningSærbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SærbidragskategoriGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftDirekteBetaltGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftMaksGodkjentBeløpGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftspostGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjektListe
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import stubHentPersonNyIdent
import stubPersonConsumer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class VedtakserviceSærbidragTest : VedtakserviceTest() {
    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatType.INNTEKT,
            behandling.søknadsbarn.first()!!,
        )
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.klageMottattdato = LocalDate.now()
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(4000)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Maks godkjent beløp"
        behandling.utgift!!.maksGodkjentBeløpTaMed = false
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                ),
            )
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)
        entityManager.flush()
        entityManager.refresh(behandling)
        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 110
            }
            validerNotater()
        }

        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        opprettVedtakRequest.validerPersongrunnlag()
        opprettVedtakRequest.validerSluttberegning(behandling.virkningstidspunkt!!)
        opprettVedtakRequest.validerBosstatusPerioder(behandling.virkningstidspunkt!!)
        opprettVedtakRequest.validerInntekter()
//

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe
        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.beløp shouldBe BigDecimal(9839)
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 9
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SÆRBIDRAG_KATEGORI,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.NOTAT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                5
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SØKNAD,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.VIRKNINGSTIDSPUNKT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            it.betaltBeløp shouldBe BigDecimal(500)
        }
        assertSoftly(opprettVedtakRequest) {
            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
            val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
            val barn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SÆRBIDRAG_KATEGORI)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SærbidragskategoriGrunnlag>().first()
                innhold.kategori shouldBe Særbidragskategori.KONFIRMASJON
                innhold.beskrivelse shouldBe null
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<UtgiftDirekteBetaltGrunnlag>().first()
                innhold.beløpDirekteBetalt shouldBe BigDecimal(500)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER)) {
                shouldHaveSize(1)
                val innholdList = innholdTilObjektListe<List<UtgiftspostGrunnlag>>().first()
                innholdList shouldHaveSize 3
                val utgiftspost = innholdList.find { it.type == Utgiftstype.KONFIRMASJONSAVGIFT.name }!!
                utgiftspost.dato shouldBe LocalDate.now().minusMonths(3)
                utgiftspost.type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
                utgiftspost.kravbeløp shouldBe BigDecimal(15000)
                utgiftspost.godkjentBeløp shouldBe BigDecimal(5000)
                utgiftspost.kommentar shouldBe "Inneholder avgifter for alkohol og pynt"
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.LØPENDE_BIDRAG)) {
                it.shouldHaveSize(1)
                val innhold = innholdTilObjekt<LøpendeBidragGrunnlag>().first()
                innhold.løpendeBidragListe shouldHaveSize 3
                innhold.løpendeBidragListe[0].type shouldBe Stønadstype.BIDRAG
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[0].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_SØKNADSBARN
                innhold.løpendeBidragListe[1].type shouldBe Stønadstype.BIDRAG
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[1].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                innhold.løpendeBidragListe[2].type shouldBe Stønadstype.BIDRAG18AAR
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[1].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_HUSSTANDSMEDLEM
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(5)
                val innholdListe = innholdTilObjekt<NotatGrunnlag>()
                innholdListe.find { it.type == NotatType.UTGIFTER }!!.innhold shouldBe
                    henteNotatinnhold(behandling, NotatType.UTGIFTER)
                val notatInntekter = this.filter { it.innholdTilObjekt<NotatGrunnlag>().type == NotatType.INNTEKT }
                notatInntekter.find { it.gjelderReferanse == bmGrunnlag.referanse }!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe
                    "Notat inntekt BM"
                notatInntekter.find { it.gjelderReferanse == bpGrunnlag.referanse }!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe
                    "Notat inntekt BP"
                notatInntekter.find { it.gjelderReferanse == barn1Grunnlag.referanse }!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe
                    "Notat inntekt BA"
            }

            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.UTGIFT_MAKS_GODKJENT_BELØP) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3 // TODO: Hvorfor 3?
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 7
            hentGrunnlagstyper(Grunnlagstype.SJABLON_BIDRAGSEVNE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SJABLON_TRINNVIS_SKATTESATS) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 8
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == bmGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == bpGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == barn1Grunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG)
                .find { it.gjelderReferanse == bmGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG)
                .find { it.gjelderReferanse == bpGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 11
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling hvor betalt av BP og direkte betalt av BP er satt`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatType.INNTEKT,
            behandling.søknadsbarn.first()!!,
        )
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.klageMottattdato = LocalDate.now()
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(4000)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Maks godkjent beløp"
        behandling.utgift!!.maksGodkjentBeløpTaMed = false
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                    betaltAvBp = true,
                ),
            )
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)
        entityManager.flush()
        entityManager.refresh(behandling)
        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }

        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.beløp shouldBe BigDecimal(9839)
            it.betaltBeløp shouldBe BigDecimal(5500)
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling med løpende bidrag og personobjekter`() {
        stubPersonConsumer()
        stubUtils.stubBidragStonadLøpendeSaker("løpende-bidragssaker-bp_annen_barn")
        stubUtils.stubBidraBBMHentBeregning("bbm-beregning_annen_barn")
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.refVedtaksid = 553
        behandling.klageMottattdato = LocalDate.now()
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                ),
            )
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)
        entityManager.flush()
        entityManager.refresh(behandling)
        val opprettVedtakRequest = opprettVedtakSlot.captured

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe

        assertSoftly(opprettVedtakRequest) {
            grunnlagsliste shouldHaveSize 107
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.LØPENDE_BIDRAG)) {
                it.shouldHaveSize(1)
                val innhold = innholdTilObjekt<LøpendeBidragGrunnlag>().first()
                innhold.løpendeBidragListe shouldHaveSize 4
                innhold.løpendeBidragListe[0].type shouldBe Stønadstype.BIDRAG
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[0].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_SØKNADSBARN
                innhold.løpendeBidragListe[1].type shouldBe Stønadstype.BIDRAG
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[1].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                innhold.løpendeBidragListe[2].type shouldBe Stønadstype.BIDRAG18AAR
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[2].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG
                innhold.løpendeBidragListe[3].type shouldBe Stønadstype.BIDRAG
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[3].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling med løpende bidrag med flere vedtak`() {
        stubPersonConsumer()
        stubUtils.stubBidragVedtakForStønad(testdataBarn1.ident, "vedtak-for-stønad-barn1_2")
        stubUtils.stubBidragStonadLøpendeSaker("løpende-bidragssaker-bp_2")
        stubUtils.stubBidraBBMHentBeregning("bbm-beregning_2")
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.refVedtaksid = 553
        behandling.klageMottattdato = LocalDate.now()
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                ),
            )
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)
        entityManager.flush()
        entityManager.refresh(behandling)
        val opprettVedtakRequest = opprettVedtakSlot.captured

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe

        assertSoftly(opprettVedtakRequest) {
            grunnlagsliste shouldHaveSize 103
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_BEREGNEDE_TOTALBIDRAG)) {
                shouldHaveSize(1)
                val grunnlag = it.first()
                val innhold = grunnlag.innholdTilObjekt<DelberegningBidragspliktigesBeregnedeTotalbidrag>()
                innhold.bidragspliktigesBeregnedeTotalbidrag shouldBe BigDecimal("4208.00")
                grunnlagsliste
                    .finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                        Grunnlagstype.SJABLON_SAMVARSFRADRAG,
                        grunnlag.grunnlagsreferanseListe,
                    ).shouldNotBeEmpty()
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_BIDRAGSEVNE)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<DelberegningBidragsevne>().first()
                innhold.beløp shouldBe BigDecimal("18506.04")
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<DelberegningBidragspliktigesAndel>().first()
                innhold.endeligAndelFaktor shouldBe BigDecimal("0.4919354839")
                innhold.andelBeløp shouldBe BigDecimal("9838.71")
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_BEREGNEDE_TOTALBIDRAG)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<DelberegningBidragspliktigesBeregnedeTotalbidrag>().first()
                innhold.bidragspliktigesBeregnedeTotalbidrag shouldBe BigDecimal("4208.00")
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.LØPENDE_BIDRAG)) {
                it.shouldHaveSize(1)
                val innhold = innholdTilObjekt<LøpendeBidragGrunnlag>().first()
                innhold.løpendeBidragListe shouldHaveSize 1
                innhold.løpendeBidragListe[0].type shouldBe Stønadstype.BIDRAG
                innhold.løpendeBidragListe[0].løpendeBeløp shouldBe BigDecimal.ZERO
                innhold.løpendeBidragListe[0].beregnetBeløp shouldBe BigDecimal("3159.00")
                innhold.løpendeBidragListe[0].samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
                grunnlagsliste.filtrerBasertPåEgenReferanse(referanse = innhold.løpendeBidragListe[0].gjelderBarn).first().type shouldBe
                    Grunnlagstype.PERSON_SØKNADSBARN
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling med maks beløp satt`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatType.INNTEKT,
            behandling.søknadsbarn.first()!!,
        )
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.klageMottattdato = LocalDate.now()
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(4000)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Maks godkjent beløp"
        behandling.utgift!!.maksGodkjentBeløpTaMed = true
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                ),
            )
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)
        entityManager.flush()
        entityManager.refresh(behandling)
        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 111
            }
        }
        val grunnlagsliste = opprettVedtakRequest.grunnlagListe
        val sluttberegningSærbidrag = grunnlagsliste.hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG)

        assertSoftly(sluttberegningSærbidrag) {
            shouldHaveSize(1)
            val innhold = innholdTilObjekt<SluttberegningSærbidrag>().first()
            innhold.resultatKode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET
            innhold.periode shouldBe ÅrMånedsperiode(virkningstidspunkt, virkningstidspunkt.plusMonths(1))
            innhold.beregnetBeløp shouldBe BigDecimal("1967.74")
            innhold.resultatBeløp shouldBe BigDecimal(1968)
        }
        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.beløp shouldBe BigDecimal(1968)
            it.betaltBeløp shouldBe BigDecimal(500)
        }

        assertSoftly(opprettVedtakRequest) {
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFT_MAKS_GODKJENT_BELØP)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<UtgiftMaksGodkjentBeløpGrunnlag>().first()
                innhold.beløp shouldBe BigDecimal(4000)
                innhold.begrunnelse shouldBe "Maks godkjent beløp"
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_UTGIFT)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<DelberegningUtgift>().first()
                innhold.sumGodkjent shouldBe BigDecimal(4000)
                innhold.sumBetaltAvBp shouldBe BigDecimal(500)
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling avslag`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatType.INNTEKT,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.klageMottattdato = LocalDate.now()
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                ),
            )

        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.inntekter.add(
            opprettInntekt(
                datoFom = virkningstidspunkt,
                type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                ident = behandling.bidragspliktig!!.ident!!,
                beløp = BigDecimal(100000),
                kilde = Kilde.MANUELL,
                taMed = true,
                behandling = behandling,
                medId = false,
            ),
        )
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured
        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }
        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 111
            }
        }

        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        opprettVedtakRequest.validerPersongrunnlag()

        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.beløp shouldBe null
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.SÆRBIDRAG_IKKE_FULL_BIDRAGSEVNE.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 9
            it.betaltBeløp shouldBe BigDecimal(500)
        }
        assertSoftly(opprettVedtakRequest) {
            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
            val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
            val barn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
            val sluttberegningSærbidrag = hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG)

            assertSoftly(sluttberegningSærbidrag) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SluttberegningSærbidrag>().first()
                innhold.resultatKode shouldBe Resultatkode.SÆRBIDRAG_IKKE_FULL_BIDRAGSEVNE
                innhold.periode shouldBe ÅrMånedsperiode(virkningstidspunkt, virkningstidspunkt.plusMonths(1))
                innhold.beregnetBeløp shouldBe BigDecimal("2580.65")
                innhold.resultatBeløp shouldBe null
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SÆRBIDRAG_KATEGORI)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SærbidragskategoriGrunnlag>().first()
                innhold.kategori shouldBe Særbidragskategori.KONFIRMASJON
                innhold.beskrivelse shouldBe null
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<UtgiftDirekteBetaltGrunnlag>().first()
                innhold.beløpDirekteBetalt shouldBe BigDecimal(500)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER)) {
                shouldHaveSize(1)
                val innholdList = innholdTilObjektListe<List<UtgiftspostGrunnlag>>().first()
                innholdList shouldHaveSize 3
                val utgiftspost = innholdList.find { it.type == Utgiftstype.KONFIRMASJONSAVGIFT.name }!!
                utgiftspost.dato shouldBe LocalDate.now().minusMonths(3)
                utgiftspost.type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
                utgiftspost.kravbeløp shouldBe BigDecimal(15000)
                utgiftspost.godkjentBeløp shouldBe BigDecimal(5000)
                utgiftspost.kommentar shouldBe "Inneholder avgifter for alkohol og pynt"
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(5)
                val innholdListe = innholdTilObjekt<NotatGrunnlag>()
                innholdListe.find { it.type == NotatType.UTGIFTER }!!.innhold shouldBe
                    henteNotatinnhold(behandling, NotatType.UTGIFTER)
                val notatInntekter = this.filter { it.innholdTilObjekt<NotatGrunnlag>().type == NotatType.INNTEKT }
                notatInntekter.find { it.gjelderReferanse == bmGrunnlag.referanse }!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe
                    "Notat inntekt BM"
                notatInntekter.find { it.gjelderReferanse == bpGrunnlag.referanse }!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe
                    "Notat inntekt BP"
                notatInntekter.find { it.gjelderReferanse == barn1Grunnlag.referanse }!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe
                    "Notat inntekt BA"
            }

            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.LØPENDE_BIDRAG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3 // TODO: Hvorfor 3?
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 7
            hentGrunnlagstyper(Grunnlagstype.SJABLON_BIDRAGSEVNE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SJABLON_TRINNVIS_SKATTESATS) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 8
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == bmGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == bpGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == barn1Grunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG)
                .find { it.gjelderReferanse == bmGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG)
                .find { it.gjelderReferanse == bpGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 11
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling direkte avslag`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.avslag = Resultatkode.IKKE_NØDVENDIGE_UTGIFTER
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.ANNET.name
        behandling.kategoriBeskrivelse = "Batterier til høreapparat"
        behandling.utgift = null
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured
        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }
        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde 7 grunnlag") {
                request.grunnlagListe shouldHaveSize 7
            }
        }

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe

        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.beløp shouldBe null
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.IKKE_NØDVENDIGE_UTGIFTER.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 4
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                0
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SÆRBIDRAG_KATEGORI,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.NOTAT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SØKNAD,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.VIRKNINGSTIDSPUNKT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            it.betaltBeløp shouldBe null
        }
        assertSoftly(opprettVedtakRequest) {
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SÆRBIDRAG_KATEGORI)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SærbidragskategoriGrunnlag>().first()
                innhold.kategori shouldBe Særbidragskategori.ANNET
                innhold.beskrivelse shouldBe "Batterier til høreapparat"
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(1)
                val innholdListe = innholdTilObjekt<NotatGrunnlag>()
                innholdListe.find { it.type == NotatType.UTGIFTER }!!.innhold shouldBe
                    henteNotatinnhold(behandling, NotatType.UTGIFTER)
            }

            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling avslag alle utgifter foreldet`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusYears(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(0),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusYears(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(0),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusYears(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(0),
                ),
            )
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(0)
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured
        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }
        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde 9 grunnlag") {
                request.grunnlagListe shouldHaveSize 9
            }
        }

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe

        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.beløp shouldBe null
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.ALLE_UTGIFTER_ER_FORELDET.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 6
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                0
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SÆRBIDRAG_KATEGORI,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.NOTAT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SØKNAD,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.VIRKNINGSTIDSPUNKT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            it.betaltBeløp shouldBe null
        }
        assertSoftly(opprettVedtakRequest) {
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SÆRBIDRAG_KATEGORI)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SærbidragskategoriGrunnlag>().first()
                innhold.kategori shouldBe Særbidragskategori.KONFIRMASJON
                innhold.beskrivelse shouldBe null
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(1)
                val innholdListe = innholdTilObjekt<NotatGrunnlag>()
                innholdListe.find { it.type == NotatType.UTGIFTER }!!.innhold shouldBe
                    henteNotatinnhold(behandling, NotatType.UTGIFTER)
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<UtgiftDirekteBetaltGrunnlag>().first()
                innhold.beløpDirekteBetalt shouldBe BigDecimal(0)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER)) {
                shouldHaveSize(1)
                val innholdList = innholdTilObjektListe<List<UtgiftspostGrunnlag>>().first()
                innholdList shouldHaveSize 3
                val utgiftspost = innholdList.find { it.type == Utgiftstype.KONFIRMASJONSAVGIFT.name }!!
                utgiftspost.dato shouldBe LocalDate.now().minusYears(3)
                utgiftspost.type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
                utgiftspost.kravbeløp shouldBe BigDecimal(15000)
                utgiftspost.godkjentBeløp shouldBe BigDecimal(0)
                utgiftspost.kommentar shouldBe null
            }

            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en særbidrag behandling avslag godkjent beløp lavere enn forskuddsats`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Begrunnelse",
                ),
            )
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(0)
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured
        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }
        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 12
            }
            val sluttberegningSærbidrag = hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG)

            assertSoftly(sluttberegningSærbidrag) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SluttberegningSærbidrag>().first()
                innhold.resultatKode shouldBe Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS
                innhold.periode shouldBe ÅrMånedsperiode(virkningstidspunkt, virkningstidspunkt.plusMonths(1))
                innhold.beregnetBeløp shouldBe BigDecimal(0)
                innhold.resultatBeløp shouldBe null
            }
        }

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe

        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.beløp shouldBe null
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 5
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SÆRBIDRAG_KATEGORI,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.NOTAT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SØKNAD,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.VIRKNINGSTIDSPUNKT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            it.betaltBeløp shouldBe BigDecimal.ZERO
        }
        assertSoftly(opprettVedtakRequest) {
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SÆRBIDRAG_KATEGORI)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SærbidragskategoriGrunnlag>().first()
                innhold.kategori shouldBe Særbidragskategori.KONFIRMASJON
                innhold.beskrivelse shouldBe null
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(1)
                val innholdListe = innholdTilObjekt<NotatGrunnlag>()
                innholdListe.find { it.type == NotatType.UTGIFTER }!!.innhold shouldBe
                    henteNotatinnhold(behandling, NotatType.UTGIFTER)
            }

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<UtgiftDirekteBetaltGrunnlag>().first()
                innhold.beløpDirekteBetalt shouldBe BigDecimal(0)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER)) {
                shouldHaveSize(1)
                val innholdList = innholdTilObjektListe<List<UtgiftspostGrunnlag>>().first()
                innholdList shouldHaveSize 1
                val utgiftspost = innholdList.find { it.type == Utgiftstype.KONFIRMASJONSAVGIFT.name }!!
                utgiftspost.dato shouldBe LocalDate.now().minusMonths(3)
                utgiftspost.type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
                utgiftspost.kravbeløp shouldBe BigDecimal(15000)
                utgiftspost.godkjentBeløp shouldBe BigDecimal(500)
                utgiftspost.kommentar shouldBe "Begrunnelse"
            }

            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    @Transactional
    fun `Skal bruke nyeste identer for særbidrag`() {
        val nyIdentBm = "ny_ident_bm"
        val nyIdentBp = "ny_ident_bp"
        val nyIdentBarn1 = "ny_i_barn_1"
        stubHentPersonNyIdent(testdataBarn1.ident, nyIdentBarn1, bidragPersonConsumer)
        stubHentPersonNyIdent(testdataBM.ident, nyIdentBm, bidragPersonConsumer)
        stubHentPersonNyIdent(testdataBP.ident, nyIdentBp, bidragPersonConsumer)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        testdataManager.lagreBehandling(behandling)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                ),
            )
        behandling.initGrunnlagRespons(
            stubUtils,
            bmIdent = nyIdentBm,
            bpIdent = nyIdentBp,
            baIdent = nyIdentBarn1,
        )

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe
        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(nyIdentBp)
            it.kravhaver shouldBe Personident(nyIdentBarn1)
            it.mottaker shouldBe Personident(nyIdentBm)
            it.beløp shouldBe BigDecimal(9839)
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 5
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SÆRBIDRAG_KATEGORI,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.NOTAT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SØKNAD,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.VIRKNINGSTIDSPUNKT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            it.betaltBeløp shouldBe BigDecimal(500)
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    @Transactional
    fun `Skal bruke nyeste identer for avslag`() {
        val nyIdentBm = "ny_ident_bm"
        val nyIdentBp = "ny_ident_bp"
        val nyIdentBarn1 = "ny_i_barn_1"
        val mock = stubHentPersonNyIdent(testdataBarn1.ident, nyIdentBarn1)
        stubHentPersonNyIdent(testdataBM.ident, nyIdentBm, mock)
        stubHentPersonNyIdent(testdataBP.ident, nyIdentBp, mock)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.avslag = Resultatkode.PRIVAT_AVTALE
        testdataManager.lagreBehandling(behandling)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift = null
        behandling.initGrunnlagRespons(
            stubUtils,
            bmIdent = nyIdentBm,
            bpIdent = nyIdentBp,
            baIdent = nyIdentBarn1,
        )

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(nyIdentBp)
            it.kravhaver shouldBe Personident(nyIdentBarn1)
            it.mottaker shouldBe Personident(nyIdentBm)
            it.beløp shouldBe null
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.PRIVAT_AVTALE.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 4
            it.betaltBeløp shouldBe null
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak med avslag for særbidrag behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.leggTilNotat(
            "Utgiftsbegrunnelse",
            NotatType.UTGIFTER,
        )
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.avslag = Resultatkode.PRIVAT_AVTALE
        behandling.klageMottattdato = LocalDate.now()
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(8),
                    type = Utgiftstype.KLÆR.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(10000),
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.SELSKAP.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(10000),
                    godkjentBeløp = BigDecimal(5000),
                    kommentar = "Inneholder utgifter til mat og drikke",
                ),
            )
        behandling.initGrunnlagRespons(stubUtils)

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)

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

            request.stønadsendringListe.shouldBeEmpty()
            request.engangsbeløpListe shouldHaveSize 1
            withClue("Grunnlagliste skal inneholde 7 grunnlag") {
                request.grunnlagListe shouldHaveSize 7
            }
        }

        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        assertSoftly(opprettVedtakRequest.engangsbeløpListe[0]) {
            it.type shouldBe Engangsbeløptype.SÆRBIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.beløp shouldBe null
            it.valutakode shouldBe "NOK"
            it.resultatkode shouldBe Resultatkode.PRIVAT_AVTALE.name
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING
            it.grunnlagReferanseListe shouldHaveSize 4
            it.betaltBeløp shouldBe null
        }
        assertSoftly(opprettVedtakRequest) {
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(1)
                val innholdListe = innholdTilObjekt<NotatGrunnlag>()
                innholdListe.find { it.type == NotatType.UTGIFTER }!!.innhold shouldBe
                    henteNotatinnhold(behandling, NotatType.UTGIFTER)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SÆRBIDRAG_KATEGORI)) {
                shouldHaveSize(1)
                val innhold = innholdTilObjekt<SærbidragskategoriGrunnlag>().first()
                innhold.kategori shouldBe Særbidragskategori.KONFIRMASJON
                innhold.beskrivelse shouldBe null
            }

            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.UTGIFTSPOSTER) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.UTGIFT_DIREKTE_BETALT) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    private fun OpprettVedtakRequestDto.validerBosstatusPerioder(virkningstidspunkt: LocalDate) {
        val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
        val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
        val husstandsmedlemGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
            shouldHaveSize(5)
            val bostatusSøknadsbarn1 =
                it.filtrerBasertPåFremmedReferanse(gjelderBarnReferanse = søknadsbarn1Grunnlag.referanse)
            bostatusSøknadsbarn1.shouldHaveSize(1)

            assertSoftly(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
                bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                periode.fom shouldBe YearMonth.from(virkningstidspunkt)
                periode.til shouldBe null
                relatertTilPart shouldBe bpGrunnlag.referanse
            }
            val bostatusBp =
                it.filtrerBasertPåFremmedReferanse(referanse = bpGrunnlag.referanse).filter { it.gjelderBarnReferanse == null }
            bostatusBp.shouldHaveSize(1)
            assertSoftly(bostatusBp[0].innholdTilObjekt<BostatusPeriode>()) {
                bostatus shouldBe Bostatuskode.BOR_MED_ANDRE_VOKSNE
                periode.fom shouldBe YearMonth.from(virkningstidspunkt)
                periode.til shouldBe null
                relatertTilPart shouldBe bpGrunnlag.referanse
            }
            it
                .filtrerBasertPåFremmedReferanse(gjelderBarnReferanse = husstandsmedlemGrunnlag.referanse)
                .shouldHaveSize(1)
        }
    }

    private fun OpprettVedtakRequestDto.validerInntekter() {
        val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
        val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
        val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
        assertSoftly(hentGrunnlagstyper(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)) {
            shouldHaveSize(24)
            val inntekterBM = it.filter { it.gjelderReferanse == bmGrunnlag.referanse }
            val inntekterBP = it.filter { it.gjelderReferanse == bpGrunnlag.referanse }
            val inntekterBA = it.filter { it.gjelderReferanse == søknadsbarnGrunnlag.referanse }
            inntekterBM shouldHaveSize 11
            inntekterBP shouldHaveSize 8
            inntekterBA shouldHaveSize 5

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
            innhold.beløp shouldBe BigDecimal("18506.04")
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
            virkningsdato.årsak shouldBe null

            grunnlagListe.søknad shouldNotBe null
            val søknad = grunnlagListe.søknad?.innholdTilObjekt<SøknadGrunnlag>()
            søknad!!.mottattDato shouldHaveSameDayAs behandling.mottattdato
            søknad.søktAv shouldBe behandling.soknadFra
            søknad.klageMottattDato shouldBe LocalDate.now()
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
            shouldHaveSize(10)
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
    }
}

private fun OpprettVedtakRequestDto.validerNotater() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val baGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
        shouldHaveSize(5)
        assertSoftly(hentNotat(NotatType.UTGIFTER)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Utgiftsbegrunnelse"
        }

        assertSoftly(hentNotat(NotatType.BOFORHOLD)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Boforhold"
        }
        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Notat inntekt BM"
        }
        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = bpGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Notat inntekt BP"
        }
        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = baGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Notat inntekt BA"
        }
    }
}
