package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.datamodell.opprettUnikReferanse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.opprettGrunnlagsreferanseVirkningstidspunkt
import no.nav.bidrag.behandling.utils.harReferanseTilGrunnlag
import no.nav.bidrag.behandling.utils.hentGrunnlagstype
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.hentGrunnlagstyperForReferanser
import no.nav.bidrag.behandling.utils.hentNotat
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.shouldContainPerson
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.søknad
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.erstattVariablerITestFil
import no.nav.bidrag.behandling.utils.testdata.lagVedtaksdata
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetillegg
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggTilFaktiskTilsynsutgift
import no.nav.bidrag.behandling.utils.testdata.leggTilGebyrSøknad
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagBeløpshistorikk
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagManuelleVedtak
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilPrivatAvtale
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.leggTilTillegsstønad
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingAldersjustering
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandlingMedReelMottaker
import no.nav.bidrag.behandling.utils.testdata.opprettStønadDto
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.behandling.utils.validerHarReferanseTilGrunnlagIReferanser
import no.nav.bidrag.behandling.utils.validerHarReferanseTilSjablonIReferanser
import no.nav.bidrag.behandling.utils.virkningsdato
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadPeriodeDto
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManuellVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjektListe
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.client.HttpStatusCodeException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Optional

@ExtendWith(SpringExtension::class)
class VedtakserviceBidragTest : CommonVedtakTilBehandlingTest() {
    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en bidrag aldersjustering behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingAldersjustering(true)
        behandling.virkningstidspunkt = YearMonth.now().withMonth(7).atDay(1)
        behandling.søknadsbarn.first().virkningstidspunkt = behandling.virkningstidspunkt
        behandling.søknadsbarn.first().årsak = behandling.årsak

        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("2600"),
                ),
            ),
        )

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("2600"),
                ),
            ),
        )
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")

        behandling.søknadsbarn.first().grunnlagFraVedtak = 1
        every { vedtakConsumer.hentVedtak(any()) } returns originalVedtak
        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.BIDRAG,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-07-31"))),
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2024-08-01"), null)),
                    ),
            )

        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1
//        every { vedtakService2.hentBeløpshistorikk(any(), any()) } returns 1
        every { vedtakServiceBeregning.hentBeløpshistorikkSistePeriode(any()) } returns
            StønadPeriodeDto(
                periode = ÅrMånedsperiode(LocalDate.now().minusMonths(5).withDayOfMonth(1), null),
                periodeid = 1,
                stønadsid = 1,
                vedtaksid = 1,
                gyldigFra = LocalDateTime.now(),
                gyldigTil = null,
                beløp = BigDecimal("2600"),
                valutakode = "",
                resultatkode = "KBB",
                periodeGjortUgyldigAvVedtaksid = null,
            )
        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.ALDERSJUSTERING
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 17
            }
            request.unikReferanse shouldBe behandling.opprettUnikReferanse()
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            assertSoftly(stønadsendring) {
                it.type shouldBe Stønadstype.BIDRAG
                it.sak shouldBe Saksnummer(behandling.saksnummer)
                it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
                it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
                it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
                it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                it.beslutning shouldBe Beslutningstype.ENDRING
                it.førsteIndeksreguleringsår shouldBe YearMonth.now().plusYears(1).year

                it.sisteVedtaksid shouldBe 1
                it.periodeListe shouldHaveSize 1
                it.grunnlagReferanseListe shouldHaveSize 3

                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.VIRKNINGSTIDSPUNKT,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    1

                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.ALDERSJUSTERING_DETALJER,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    1

                val manuelleVedtakgrunnlag =
                    opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                        Grunnlagstype.MANUELLE_VEDTAK,
                        it.grunnlagReferanseListe,
                    )

                val søknadsbarnGrunnlag = opprettVedtakRequest.grunnlagListe.hentPerson(behandling.søknadsbarn.first().ident!!)
                manuelleVedtakgrunnlag shouldHaveSize 1
                val manuelleVedtakInnhold = manuelleVedtakgrunnlag.first().innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
                manuelleVedtakInnhold shouldHaveSize 1
                manuelleVedtakgrunnlag.first().gjelderBarnReferanse shouldBe søknadsbarnGrunnlag!!.referanse

                assertSoftly(it.periodeListe[0]) {
                    opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                        Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG_ALDERSJUSTERING,
                        it.grunnlagReferanseListe,
                    ) shouldHaveSize
                        1
                    it.periode.fom.shouldBe(YearMonth.now().withMonth(7))
                    it.resultatkode shouldBe Resultatkode.BEREGNET_BIDRAG.name
                }
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 0) { notatOpplysningerService.opprettNotat(any()) }
        verify(exactly = 1) { forsendelseService.opprettForsendelseForAldersjustering(any()) }
    }

    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en bidrag behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragsmottaker!!.leggTilGebyrSøknad(behandling.soknadsid!!, "REFERANSE_BM_GEBYR")
        behandling.bidragspliktig!!.leggTilGebyrSøknad(behandling.soknadsid!!, "REFERANSE_BP_GEBYR")
        behandling.søknadsbarn.first()!!.leggTilGebyrSøknad(behandling.soknadsid!!, "REFERANSE_BA_GEBYR")
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
            behandling.bidragspliktig,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Privat avtale",
            NotatType.PRIVAT_AVTALE,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = true,
        )

        // Ikke med i behandlingen
        behandling.leggTilNotat(
            "Inntektsbegrunnelse BM - fra opprinnelig vedtak",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat - fra opprinnelig vedtak",
            NotatType.VIRKNINGSTIDSPUNKT,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Boforhold - fra opprinnelig vedtak",
            NotatType.BOFORHOLD,
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Samvær - fra opprinnelig vedtak",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Underhold barn - fra opprinnelig vedtak",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Underhold andre barn - fra opprinnelig vedtak",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Privat avtale - fra opprinnelig vedtak",
            NotatType.PRIVAT_AVTALE,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE
//            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
//                request.grunnlagListe shouldHaveSize 203
//            }
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            assertSoftly(stønadsendring) {
                it.type shouldBe Stønadstype.BIDRAG
                it.sak shouldBe Saksnummer(behandling.saksnummer)
                it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
                it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
                it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
                it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                it.beslutning shouldBe Beslutningstype.ENDRING
                it.førsteIndeksreguleringsår shouldBe 2027

                it.periodeListe shouldHaveSize 9
//                it.grunnlagReferanseListe shouldHaveSize 17
                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.NOTAT,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    15
                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.SØKNAD,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    1
                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.VIRKNINGSTIDSPUNKT,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    1

                assertSoftly(it.periodeListe[0]) {
                    opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                        Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG,
                        it.grunnlagReferanseListe,
                    ) shouldHaveSize
                        1
                }
            }
        }
        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(3)
            val gebyrMottaker = it.find { it.type == Engangsbeløptype.GEBYR_MOTTAKER }!!

            gebyrMottaker.beløp shouldBe null
            gebyrMottaker.valutakode shouldBe null
            gebyrMottaker.referanse shouldBe "REFERANSE_BM_GEBYR"
            gebyrMottaker.kravhaver shouldBe Personident("NAV")
            gebyrMottaker.mottaker shouldBe Personident("NAV")
            gebyrMottaker.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            gebyrMottaker.resultatkode shouldBe Resultatkode.GEBYR_FRITATT.name
            gebyrMottaker.sak shouldBe Saksnummer(SAKSNUMMER)
            gebyrMottaker.skyldner shouldBe Personident(testdataBM.ident)
            gebyrMottaker.grunnlagReferanseListe shouldHaveSize 1
            val sluttberegningGebyrBM = opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.SLUTTBEREGNING_GEBYR, gebyrMottaker.grunnlagReferanseListe).firstOrNull()
            sluttberegningGebyrBM!!.gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
            sluttberegningGebyrBM.grunnlagsreferanseListe shouldHaveSize 2
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR, sluttberegningGebyrBM.grunnlagsreferanseListe)
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilSjablonIReferanser(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP, sluttberegningGebyrBM.grunnlagsreferanseListe)
            val gebyrSkyldner = it.find { it.type == Engangsbeløptype.GEBYR_SKYLDNER }!!

            gebyrSkyldner.beløp shouldBe BigDecimal(1345)
            gebyrSkyldner.valutakode shouldBe "NOK"
            gebyrSkyldner.kravhaver shouldBe Personident("NAV")
            gebyrSkyldner.mottaker shouldBe Personident("NAV")
            gebyrSkyldner.referanse shouldBe "REFERANSE_BP_GEBYR"

            gebyrSkyldner.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            gebyrSkyldner.resultatkode shouldBe Resultatkode.GEBYR_ILAGT.name
            gebyrSkyldner.sak shouldBe Saksnummer(SAKSNUMMER)
            gebyrSkyldner.skyldner shouldBe Personident(testdataBP.ident)
            val sluttberegningGebyrBP = opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.SLUTTBEREGNING_GEBYR, gebyrSkyldner.grunnlagReferanseListe).firstOrNull()
            sluttberegningGebyrBP!!.gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            sluttberegningGebyrBP.grunnlagsreferanseListe shouldHaveSize 2
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR, sluttberegningGebyrBP.grunnlagsreferanseListe)
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilSjablonIReferanser(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP, sluttberegningGebyrBP.grunnlagsreferanseListe)
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
                    skolealder shouldBe Skolealder.UNDER
                    tilsynstype shouldBe Tilsynstype.HELTID
                }
            }
            validerNotater(behandling)
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_GEBYR) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 15
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 35
            hentGrunnlagstyper(Grunnlagstype.TILLEGGSSTØNAD_PERIODE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.FAKTISK_UTGIFT_PERIODE) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSPERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_BIDRAG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_FORSKUDD) shouldHaveSize 0
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSKALKULATOR) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_BARN_TIL_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_TILLEGGSSTØNAD_BEGRENSET) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_BARNETILSYN) shouldHaveSize 1
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
    fun `Skal fatte vedtak med hvor noen perioder har resultat ikke omsorg for barnet`() {
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        val husstandsmedlemBarn1 =
            Husstandsmedlem(
                behandling = behandling,
                kilde = Kilde.OFFENTLIG,
                ident = testdataBarn1.ident,
                navn = testdataBarn1.navn,
                fødselsdato = testdataBarn1.fødselsdato,
                id = 1,
                rolle = behandling.søknadsbarn.first(),
            )
        husstandsmedlemBarn1.perioder.add(
            Bostatusperiode(
                husstandsmedlem = husstandsmedlemBarn1,
                datoFom = behandling.virkningstidspunkt!!.withDayOfMonth(1),
                datoTom = YearMonth.from(behandling.virkningstidspunkt!!.plusMonths(3)).atEndOfMonth(),
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
            ),
        )

        husstandsmedlemBarn1.perioder.add(
            Bostatusperiode(
                husstandsmedlem = husstandsmedlemBarn1,
                datoFom = behandling.virkningstidspunkt!!.plusMonths(4).withDayOfMonth(1),
                datoTom = YearMonth.from(behandling.virkningstidspunkt!!.plusMonths(5)).atEndOfMonth(),
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
            ),
        )
        husstandsmedlemBarn1.perioder.add(
            Bostatusperiode(
                husstandsmedlem = husstandsmedlemBarn1,
                datoFom = behandling.virkningstidspunkt!!.plusMonths(6).withDayOfMonth(1),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
            ),
        )
        val andreVoksna =
            Husstandsmedlem(
                behandling = behandling,
                kilde = Kilde.OFFENTLIG,
                ident = testdataBarn1.ident,
                navn = testdataBarn1.navn,
                fødselsdato = testdataBarn1.fødselsdato,
                id = 1,
                rolle = behandling.bidragspliktig!!,
            )
        andreVoksna.perioder.add(
            Bostatusperiode(
                husstandsmedlem = husstandsmedlemBarn1,
                datoFom = behandling.virkningstidspunkt!!.withDayOfMonth(1),
                datoTom = null,
                bostatus = Bostatuskode.BOR_MED_ANDRE_VOKSNE,
                kilde = Kilde.OFFENTLIG,
            ),
        )
        behandling.husstandsmedlem = mutableSetOf(husstandsmedlemBarn1, andreVoksna)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            stønadsendring.periodeListe shouldHaveSize 9
            val resultatIkkeOmsorgPerioder = stønadsendring.periodeListe.filter { it.resultatkode == Resultatkode.IKKE_OMSORG_FOR_BARNET.name }
            resultatIkkeOmsorgPerioder.shouldHaveSize(1)
            assertSoftly(resultatIkkeOmsorgPerioder[0]) {
                it.beløp shouldBe null
                it.valutakode shouldBe null
            }
            val resultatPerioder = stønadsendring.periodeListe.filter { it.resultatkode == Resultatkode.BEREGNET_BIDRAG.name }
            resultatPerioder shouldHaveSize 8
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak med opphørsdato`() {
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)

        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opphørsdato = LocalDate.parse("2024-07-01")
        virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(behandling.søknadsbarn.first().id!!, opphørsdato = opphørsdato))
        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE
//            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
//                request.grunnlagListe shouldHaveSize 153
//            }
            val virkningstidspunktGrunnlag =
                grunnlagListe.find { it.type == Grunnlagstype.VIRKNINGSTIDSPUNKT && it.gjelderBarnReferanse == behandling.søknadsbarn.first().tilGrunnlagsreferanse() }?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
            virkningstidspunktGrunnlag!!.opphørsdato!! shouldHaveSameDayAs opphørsdato
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            val sistePeriode = stønadsendring.periodeListe.last()
            assertSoftly(sistePeriode) {
                it.periode.fom shouldBe YearMonth.from(opphørsdato)
                it.periode.til shouldBe null
                it.resultatkode shouldBe Resultatkode.OPPHØR.name
                it.beløp shouldBe null
                it.grunnlagReferanseListe shouldContain opprettGrunnlagsreferanseVirkningstidspunkt(behandling.søknadsbarn.first())
            }
            val nestSistePeriode = stønadsendring.periodeListe[stønadsendring.periodeListe.size - 2]
            assertSoftly(nestSistePeriode) {
                it.periode.fom shouldBe YearMonth.parse("2024-01")
                it.periode.til shouldBe YearMonth.from(opphørsdato)
                it.resultatkode shouldBe Resultatkode.BEREGNET_BIDRAG.name
                it.beløp shouldBe BigDecimal(5730)
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak med direkte avslag 18 års bidrag med opphør`() {
        stubPersonConsumer()
        val opphørsdato = LocalDate.now().plusMonths(2).withDayOfMonth(1)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.stonadstype = Stønadstype.BIDRAG18AAR
        val søknadsbarn = behandling.søknadsbarn.first()
        søknadsbarn.fødselsdato = LocalDate.now().minusYears(18).minusMonths(1)
        søknadsbarn.opphørsdato = opphørsdato
        behandling.bidragspliktig!!.gebyr = GebyrRolle(true, true, "Begrunnelse")
        behandling.bidragsmottaker!!.gebyr = GebyrRolle(true, false, "Begrunnelse")
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
            behandling.søknadsbarn.first(),
        )
        behandling.avslag = Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG
        søknadsbarn.avslag = Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG
        behandling.årsak = null
        søknadsbarn.årsak = null
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandlingMedReelMottaker(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            it.innkrevingUtsattTilDato shouldBe LocalDate.now().plusDays(3)
            request.grunnlagListe shouldHaveSize 11
            hentGrunnlagstyper(Grunnlagstype.MANUELT_OVERSTYRT_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SØKNAD)) {
                shouldHaveSize(1)
                val innhold = it[0].innholdTilObjekt<SøknadGrunnlag>()
                innhold.søktAv shouldBe SøktAvType.BIDRAGSMOTTAKER
            }

            request.stønadsendringListe shouldHaveSize 1
            assertSoftly(request.stønadsendringListe[0]) {
                it.type shouldBe Stønadstype.BIDRAG18AAR
                it.beslutning shouldBe Beslutningstype.ENDRING
                it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                it.sak shouldBe Saksnummer(SAKSNUMMER)
                it.skyldner shouldBe Personident(testdataBP.ident)
                it.kravhaver shouldBe Personident(testdataBarn1.ident)
                it.mottaker shouldBe Personident("REEL_MOTTAKER")
                it.grunnlagReferanseListe shouldHaveSize 5
                val vtGrunnlag = request.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.VIRKNINGSTIDSPUNKT, it.grunnlagReferanseListe)
                vtGrunnlag.size shouldBe 1
                val virkningstidspunkt = vtGrunnlag.first().innholdTilObjekt<VirkningstidspunktGrunnlag>()
                virkningstidspunkt.avslag shouldBe Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG
                virkningstidspunkt.årsak shouldBe null
                virkningstidspunkt.virkningstidspunkt shouldBe behandling.virkningstidspunkt
                it.periodeListe shouldHaveSize 1
                assertSoftly(it.periodeListe[0]) {
                    it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
                    it.periode.til shouldBe null
                    it.beløp shouldBe null
                    it.resultatkode shouldBe Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG.name
                }
            }
        }
        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(2)

            it.any { it.type == Engangsbeløptype.GEBYR_MOTTAKER }.shouldBeTrue()
            it.any { it.type == Engangsbeløptype.GEBYR_SKYLDNER }.shouldBeTrue()
            assertSoftly(it.find { it.type == Engangsbeløptype.GEBYR_MOTTAKER }!!) {
                beløp shouldBe null
                valutakode shouldBe null
                kravhaver shouldBe Personident("NAV")
                mottaker shouldBe Personident("NAV")
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.GEBYR_FRITATT.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                skyldner shouldBe Personident(testdataBM.ident)
                grunnlagReferanseListe shouldHaveSize 1
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe)
            }
            assertSoftly(it.find { it.type == Engangsbeløptype.GEBYR_SKYLDNER }!!) {
                beløp shouldBe BigDecimal(1345)
                valutakode shouldBe "NOK"
                kravhaver shouldBe Personident("NAV")
                mottaker shouldBe Personident("NAV")
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.GEBYR_ILAGT.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                skyldner shouldBe Personident(testdataBP.ident)
                grunnlagReferanseListe shouldHaveSize 1
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe)
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte opphørsvedtak m18 års bidrag`() {
        stubPersonConsumer()
        val opphørsdato = LocalDate.now().plusMonths(2).withDayOfMonth(1)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.stonadstype = Stønadstype.BIDRAG18AAR
        behandling.vedtakstype = Vedtakstype.OPPHØR
        val søknadsbarn = behandling.søknadsbarn.first()
        søknadsbarn.fødselsdato = LocalDate.now().minusYears(18).minusMonths(1)
        søknadsbarn.opphørsdato = opphørsdato
        behandling.bidragspliktig!!.gebyr = GebyrRolle(true, true, "Begrunnelse")
        behandling.bidragsmottaker!!.gebyr = GebyrRolle(true, false, "Begrunnelse")
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
            behandling.søknadsbarn.first(),
        )
        behandling.avslag = Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG
        søknadsbarn.avslag = Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG
        behandling.årsak = null
        søknadsbarn.årsak = null
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandlingMedReelMottaker(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.OPPHØR

            it.innkrevingUtsattTilDato shouldBe null

            request.stønadsendringListe shouldHaveSize 1
            assertSoftly(request.stønadsendringListe[0]) {
                it.type shouldBe Stønadstype.BIDRAG18AAR
                it.beslutning shouldBe Beslutningstype.ENDRING
                it.innkreving shouldBe Innkrevingstype.MED_INNKREVING

                val vtGrunnlag = request.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.VIRKNINGSTIDSPUNKT, it.grunnlagReferanseListe)
                vtGrunnlag.size shouldBe 1
                val virkningstidspunkt = vtGrunnlag.first().innholdTilObjekt<VirkningstidspunktGrunnlag>()
                virkningstidspunkt.avslag shouldBe Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG
                virkningstidspunkt.årsak shouldBe null
                virkningstidspunkt.virkningstidspunkt shouldBe behandling.virkningstidspunkt
                it.periodeListe shouldHaveSize 1
                assertSoftly(it.periodeListe[0]) {
                    it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
                    it.periode.til shouldBe null
                    it.beløp shouldBe null
                    it.resultatkode shouldBe Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG.name
                }
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak for bidrag 18 år`() {
        stubPersonConsumer()

        val opphørsdato = LocalDate.now().plusMonths(2).withDayOfMonth(1)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.stonadstype = Stønadstype.BIDRAG18AAR
        behandling.søknadsbarn.first().fødselsdato = LocalDate.now().minusYears(18).minusMonths(1)
        behandling.søknadsbarn.first().opphørsdato = opphørsdato
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01")),
                    beløp = BigDecimal("2000"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null),
                    beløp = BigDecimal("3500"),
                ),
            ),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01")),
                    beløp = BigDecimal("2000"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null),
                    beløp = BigDecimal("3500"),
                ),
            ),
        )
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
            behandling.søknadsbarn.first(),
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 2,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
                testdataBarn1.copy(
                    fødselsdato = behandling.søknadsbarn.first().fødselsdato,
                ),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(behandling.søknadsbarn.first().id!!, opphørsdato = opphørsdato))
        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE
            val virkningstidspunktGrunnlag =
                grunnlagListe.find { it.type == Grunnlagstype.VIRKNINGSTIDSPUNKT && it.gjelderBarnReferanse == behandling.søknadsbarn.first().tilGrunnlagsreferanse() }?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
            virkningstidspunktGrunnlag!!.opphørsdato!! shouldHaveSameDayAs opphørsdato
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_BIDRAG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_BIDRAG_18_ÅR) shouldHaveSize 1
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            val sistePeriode = stønadsendring.periodeListe.last()
            assertSoftly(sistePeriode) {
                it.periode.fom shouldBe YearMonth.from(opphørsdato)
                it.periode.til shouldBe null
                it.resultatkode shouldBe Resultatkode.OPPHØR.name
                it.beløp shouldBe null
                it.grunnlagReferanseListe shouldContain opprettGrunnlagsreferanseVirkningstidspunkt(behandling.søknadsbarn.first())
            }
            val nestSistePeriode = stønadsendring.periodeListe[stønadsendring.periodeListe.size - 2]
            assertSoftly(nestSistePeriode) {
                it.periode.fom shouldBe YearMonth.parse("2026-01")
                it.periode.til shouldBe YearMonth.from(opphørsdato)
                it.resultatkode shouldBe Resultatkode.BEREGNET_BIDRAG.name
                it.beløp shouldBe BigDecimal(6040)
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for bidrag med privat avtale hvor resultat er ingen endring under grense`() {
        stubPersonConsumer()

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.virkningstidspunkt = LocalDate.parse("2024-01-01")
        behandling.søknadsbarn.first().virkningstidspunkt = behandling.virkningstidspunkt
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilPrivatAvtale(testdataBarn1, YearMonth.parse("2023-01"), YearMonth.parse("2024-04"), BigDecimal(6500))
        behandling.leggTilPrivatAvtale(testdataBarn1, YearMonth.parse("2024-05"), null, BigDecimal(5600))
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Privat avtale kun i notat",
            NotatType.PRIVAT_AVTALE,
            behandling.søknadsbarn.first(),
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
                testdataBarn1.copy(
                    fødselsdato = behandling.søknadsbarn.first().fødselsdato,
                ),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_BIDRAG) shouldHaveSize 1
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            stønadsendring.førsteIndeksreguleringsår shouldBe if (YearMonth.now().month.value >= 7) YearMonth.now().plusYears(1).year else YearMonth.now().year
            stønadsendring.periodeListe.forEach {
                it.resultatkode shouldBe Resultatkode.INGEN_ENDRING_UNDER_GRENSE.name
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for bidrag med privat avtale og beløpshistorikk hvor resultat er ingen endring under grense`() {
        stubPersonConsumer()

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.virkningstidspunkt = LocalDate.parse("2024-01-01")
        behandling.søknadsbarn.first().virkningstidspunkt = behandling.virkningstidspunkt
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilPrivatAvtale(testdataBarn1, YearMonth.parse("2023-01"), YearMonth.parse("2023-03"), BigDecimal(2500))
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Privat avtale kun i notat",
            NotatType.PRIVAT_AVTALE,
            behandling.søknadsbarn.first(),
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 2,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
                testdataBarn1.copy(
                    fødselsdato = behandling.søknadsbarn.first().fødselsdato,
                ),
            )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(YearMonth.parse("2023-04"), YearMonth.parse("2024-05")),
                    beløp = BigDecimal("6800"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(YearMonth.parse("2024-05"), null),
                    beløp = BigDecimal("5600"),
                ),
            ),
            2024,
        )
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_BIDRAG) shouldHaveSize 1
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            stønadsendring.førsteIndeksreguleringsår shouldBe 2024
            stønadsendring.periodeListe.forEach {
                it.resultatkode shouldBe Resultatkode.INGEN_ENDRING_UNDER_GRENSE.name
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal ikke fatte vedtak hvis begrenset revurdering beregning feiler`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.søknadstype = Behandlingstype.BEGRENSET_REVURDERING
        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    beløp = BigDecimal(10000000),
                    kilde = Kilde.MANUELL,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    gjelderRolle = behandling.bidragspliktig!!,
                    behandling = behandling,
                ),
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    beløp = BigDecimal(10000),
                    kilde = Kilde.MANUELL,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    behandling = behandling,
                ),
            )
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)

        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01")),
                    beløp = BigDecimal("2600"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null),
                    beløp = BigDecimal("2800"),
                ),
            ),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01")),
                    beløp = BigDecimal("2000"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null),
                    beløp = BigDecimal("3500"),
                ),
            ),
        )
        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        val exception = assertThrows<HttpStatusCodeException> { vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3)) }
        exception.message shouldContain "Kan ikke fatte vedtak: Flere perioder er lik eller lavere enn løpende bidrag"

        verify(exactly = 0) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 0) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en bidrag behandling - begrenset revurdering`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.søknadstype = Behandlingstype.BEGRENSET_REVURDERING
        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    beløp = BigDecimal(10000000),
                    kilde = Kilde.MANUELL,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    gjelderRolle = behandling.bidragspliktig!!,
                    behandling = behandling,
                ),
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    beløp = BigDecimal(10000),
                    kilde = Kilde.MANUELL,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    behandling = behandling,
                ),
            )
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)

        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01")),
                    beløp = BigDecimal("2600"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null),
                    beløp = BigDecimal("2800"),
                ),
            ),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01")),
                    beløp = BigDecimal("2000"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null),
                    beløp = BigDecimal("1500"),
                ),
            ),
        )

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE
//            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
//                request.grunnlagListe shouldHaveSize 183
//            }
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            assertSoftly(stønadsendring) {
                it.type shouldBe Stønadstype.BIDRAG
                it.sak shouldBe Saksnummer(behandling.saksnummer)
                it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
                it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
                it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
                it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                it.beslutning shouldBe Beslutningstype.ENDRING
                it.førsteIndeksreguleringsår shouldBe 2027

                it.periodeListe shouldHaveSize 9
//                it.grunnlagReferanseListe shouldHaveSize 8
                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.NOTAT,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    6
                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.SØKNAD,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    1
                opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.VIRKNINGSTIDSPUNKT,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    1

                assertSoftly(it.periodeListe[0]) {
                    opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                        Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG,
                        it.grunnlagReferanseListe,
                    ) shouldHaveSize
                        1
                }
            }
        }

        assertSoftly(opprettVedtakRequest) {
            val sluttberegning =
                hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG)
            sluttberegning shouldHaveSize (9)

            val sluttberegningPeriode = sluttberegning[6]
            assertSoftly(sluttberegningPeriode) {
                val innhold = innholdTilObjekt<SluttberegningBarnebidrag>()
                innhold.resultatVisningsnavn!!.intern shouldBe "Bidrag justert til forskuddssats"
                innhold.beregnetBeløp shouldBe BigDecimal("2800.00")
                innhold.resultatBeløp shouldBe BigDecimal("2800")
                innhold.begrensetRevurderingUtført shouldBe true
                innhold.bruttoBidragEtterBegrensetRevurdering shouldBe BigDecimal("3848.00")
                innhold.løpendeForskudd shouldBe BigDecimal("2800")
                innhold.løpendeBidrag shouldBe BigDecimal("1500")
                it.grunnlagsreferanseListe shouldHaveSize 12
                hentGrunnlagstyperForReferanser(Grunnlagstype.SØKNAD, it.grunnlagsreferanseListe) shouldHaveSize 1
            }
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 6
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 34
            hentGrunnlagstyper(Grunnlagstype.TILLEGGSSTØNAD_PERIODE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.FAKTISK_UTGIFT_PERIODE) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_BIDRAG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BELØPSHISTORIKK_FORSKUDD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSPERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSKALKULATOR) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_BARN_TIL_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_TILLEGGSSTØNAD_BEGRENSET) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_BARNETILSYN) shouldHaveSize 1
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
    fun `Skal fatte vedtak og lagre notat som er med i behandlingen`() {
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
            behandling.bidragspliktig,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Privat avtale",
            NotatType.PRIVAT_AVTALE,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = true,
        )

        // Ikke med i behandlingen
        behandling.leggTilNotat(
            "Inntektsbegrunnelse BM - fra opprinnelig vedtak",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat - fra opprinnelig vedtak",
            NotatType.VIRKNINGSTIDSPUNKT,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Boforhold - fra opprinnelig vedtak",
            NotatType.BOFORHOLD,
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Samvær - fra opprinnelig vedtak",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Underhold barn - fra opprinnelig vedtak",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Underhold andre barn - fra opprinnelig vedtak",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
            erDelAvBehandlingen = false,
        )
        behandling.leggTilNotat(
            "Privat avtale - fra opprinnelig vedtak",
            NotatType.PRIVAT_AVTALE,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = false,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE
            validerNotater(behandling)
        }
    }

    @Test
    fun `Skal fatte vedtak med manuelt overstyrt gebyr`() {
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)

        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        behandling.bidragsmottaker!!.gebyr = GebyrRolle(true, false, "Begrunnelse")

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(2)
            val gebyrMottaker = it.find { it.type == Engangsbeløptype.GEBYR_MOTTAKER }!!

            gebyrMottaker.beløp shouldBe null
            gebyrMottaker.kravhaver shouldBe Personident("NAV")
            gebyrMottaker.mottaker shouldBe Personident("NAV")
            gebyrMottaker.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            gebyrMottaker.resultatkode shouldBe Resultatkode.GEBYR_FRITATT.name
            gebyrMottaker.sak shouldBe Saksnummer(SAKSNUMMER)
            gebyrMottaker.skyldner shouldBe Personident(testdataBM.ident)
            gebyrMottaker.grunnlagReferanseListe shouldHaveSize 1
            val sluttberegningGebyrBM = opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.SLUTTBEREGNING_GEBYR, gebyrMottaker.grunnlagReferanseListe).firstOrNull()
            sluttberegningGebyrBM!!.gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
            sluttberegningGebyrBM.grunnlagsreferanseListe shouldHaveSize 3
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.MANUELT_OVERSTYRT_GEBYR, sluttberegningGebyrBM.grunnlagsreferanseListe)
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR, sluttberegningGebyrBM.grunnlagsreferanseListe)
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilSjablonIReferanser(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP, sluttberegningGebyrBM.grunnlagsreferanseListe)
            val gebyrSkyldner = it.find { it.type == Engangsbeløptype.GEBYR_SKYLDNER }!!

            gebyrSkyldner.beløp shouldBe BigDecimal(1345)
            gebyrSkyldner.kravhaver shouldBe Personident("NAV")
            gebyrSkyldner.mottaker shouldBe Personident("NAV")
            gebyrSkyldner.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            gebyrSkyldner.resultatkode shouldBe Resultatkode.GEBYR_ILAGT.name
            gebyrSkyldner.sak shouldBe Saksnummer(SAKSNUMMER)
            gebyrSkyldner.skyldner shouldBe Personident(testdataBP.ident)
            val sluttberegningGebyrBP = opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.SLUTTBEREGNING_GEBYR, gebyrSkyldner.grunnlagReferanseListe).firstOrNull()
            sluttberegningGebyrBP!!.gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            sluttberegningGebyrBP.grunnlagsreferanseListe shouldHaveSize 2
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR, sluttberegningGebyrBP.grunnlagsreferanseListe)
            opprettVedtakRequest.grunnlagListe.validerHarReferanseTilSjablonIReferanser(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP, sluttberegningGebyrBP.grunnlagsreferanseListe)
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak hvor BM og BP ikke har gebyrsøknad`() {
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
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)

        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        behandling.bidragsmottaker!!.harGebyrsøknad = false
        behandling.bidragspliktig!!.harGebyrsøknad = false
        behandling.søknadsbarn.first().innbetaltBeløp = BigDecimal(10000)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(1)
            it.any { it.type == Engangsbeløptype.DIREKTE_OPPGJØR }.shouldBeTrue()
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
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandlingMedReelMottaker(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

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
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.søknadsbarn.first().innbetaltBeløp = innbetaltBeløp
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

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
                referanse shouldNotBe null
                skyldner shouldBe Personident(testdataBP.ident)
                kravhaver shouldBe Personident(testdataBarn1.ident)
                mottaker shouldBe Personident(testdataBM.ident)
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.DIREKTE_OPPGJØR.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                beslutning shouldBe Beslutningstype.ENDRING
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak uten innkreving`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val innbetaltBeløp = BigDecimal(10000)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.innkrevingstype = Innkrevingstype.UTEN_INNKREVING
        behandling.søknadsbarn.first().innbetaltBeløp = innbetaltBeløp
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 1
        }
        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            it.first().innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
        }
        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(3)

            it.any { it.type == Engangsbeløptype.GEBYR_MOTTAKER }.shouldBeTrue()
            it.any { it.type == Engangsbeløptype.GEBYR_SKYLDNER }.shouldBeTrue()
            it.find { it.type == Engangsbeløptype.GEBYR_SKYLDNER }!!.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.find { it.type == Engangsbeløptype.GEBYR_MOTTAKER }!!.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.find { it.type == Engangsbeløptype.DIREKTE_OPPGJØR }!!.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak med direkte avslag`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragspliktig!!.gebyr = GebyrRolle(true, true, "Begrunnelse")
        behandling.bidragsmottaker!!.gebyr = GebyrRolle(true, false, "Begrunnelse")
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        val søknadsbarn = behandling.søknadsbarn.first()
        søknadsbarn.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        søknadsbarn.årsak = null
        behandling.årsak = null
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.grunnlagListe shouldHaveSize 11
            hentGrunnlagstyper(Grunnlagstype.MANUELT_OVERSTYRT_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_GEBYR) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSPLIKTIG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
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
                it.grunnlagReferanseListe shouldHaveSize 3
                val vtGrunnlag = request.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.VIRKNINGSTIDSPUNKT, it.grunnlagReferanseListe)
                vtGrunnlag.size shouldBe 1
                val virkningstidspunkt = vtGrunnlag.first().innholdTilObjekt<VirkningstidspunktGrunnlag>()
                virkningstidspunkt.avslag shouldBe Resultatkode.BIDRAGSPLIKTIG_ER_DØD
                virkningstidspunkt.årsak shouldBe null
                virkningstidspunkt.virkningstidspunkt shouldBe behandling.virkningstidspunkt
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
            assertSoftly(it.find { it.type == Engangsbeløptype.GEBYR_MOTTAKER }!!) {
                beløp shouldBe null
                valutakode shouldBe null
                kravhaver shouldBe Personident("NAV")
                mottaker shouldBe Personident("NAV")
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.GEBYR_FRITATT.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                skyldner shouldBe Personident(testdataBM.ident)
                grunnlagReferanseListe shouldHaveSize 1
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe)
            }
            assertSoftly(it.find { it.type == Engangsbeløptype.GEBYR_SKYLDNER }!!) {
                beløp shouldBe BigDecimal(1345)
                valutakode shouldBe "NOK"
                kravhaver shouldBe Personident("NAV")
                mottaker shouldBe Personident("NAV")
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.GEBYR_ILAGT.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                skyldner shouldBe Personident(testdataBP.ident)
                grunnlagReferanseListe shouldHaveSize 1
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe)
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak med direkte avslag og lage grunnlag for gebyr`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(2000),
                datoFom = behandling.virkningstidspunkt,
                datoTom = null,
                rolle = behandling.bidragsmottaker!!,
                taMed = true,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(90000),
                datoFom = null,
                datoTom = null,
                rolle = behandling.bidragspliktig!!,
                taMed = false,
                opprinneligFom = LocalDate.parse("2023-02-01"),
                opprinneligTom = LocalDate.parse("2024-01-31"),
                kilde = Kilde.OFFENTLIG,
                behandling = behandling,
                type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                id = 1,
            ),
        )
        behandling.bidragspliktig!!.gebyr = GebyrRolle(true, true, "Begrunnelse")
        behandling.bidragsmottaker!!.gebyr = GebyrRolle(true, false, "Begrunnelse")
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.søknadsbarn.first().avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest.engangsbeløpListe) {
            shouldHaveSize(2)

            it.any { it.type == Engangsbeløptype.GEBYR_MOTTAKER }.shouldBeTrue()
            it.any { it.type == Engangsbeløptype.GEBYR_SKYLDNER }.shouldBeTrue()
            assertSoftly(it.find { it.type == Engangsbeløptype.GEBYR_SKYLDNER }!!) {
                beløp shouldBe BigDecimal(1345)
                valutakode shouldBe "NOK"
                kravhaver shouldBe Personident("NAV")
                mottaker shouldBe Personident("NAV")
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.GEBYR_ILAGT.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                skyldner shouldBe Personident(testdataBP.ident)
                grunnlagReferanseListe shouldHaveSize 2
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe)
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE, grunnlagReferanseListe)
                val sluttberegningGebyrBM = opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe).firstOrNull()
                sluttberegningGebyrBM!!.gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
                sluttberegningGebyrBM.grunnlagsreferanseListe shouldHaveSize 2
                opprettVedtakRequest.grunnlagListe.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT }.shouldHaveSize(1)
                opprettVedtakRequest.grunnlagListe.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT && it.gjelderReferanse == behandling.bidragspliktig!!.tilGrunnlagsreferanse() }.shouldHaveSize(1)
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.MANUELT_OVERSTYRT_GEBYR, sluttberegningGebyrBM.grunnlagsreferanseListe)
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilSjablonIReferanser(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP, sluttberegningGebyrBM.grunnlagsreferanseListe)
            }
            assertSoftly(it.find { it.type == Engangsbeløptype.GEBYR_MOTTAKER }!!) {
                beløp shouldBe null
                valutakode shouldBe null
                kravhaver shouldBe Personident("NAV")
                mottaker shouldBe Personident("NAV")
                innkreving shouldBe Innkrevingstype.MED_INNKREVING
                resultatkode shouldBe Resultatkode.GEBYR_FRITATT.name
                sak shouldBe Saksnummer(SAKSNUMMER)
                skyldner shouldBe Personident(testdataBM.ident)
                grunnlagReferanseListe shouldHaveSize 1
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe)
                val sluttberegningGebyrBP = opprettVedtakRequest.grunnlagListe.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(Grunnlagstype.SLUTTBEREGNING_GEBYR, grunnlagReferanseListe).firstOrNull()
                sluttberegningGebyrBP!!.gjelderReferanse shouldBe behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
                sluttberegningGebyrBP.grunnlagsreferanseListe shouldHaveSize 2
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilGrunnlagIReferanser(Grunnlagstype.MANUELT_OVERSTYRT_GEBYR, sluttberegningGebyrBP.grunnlagsreferanseListe)
                opprettVedtakRequest.grunnlagListe.validerHarReferanseTilSjablonIReferanser(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP, sluttberegningGebyrBP.grunnlagsreferanseListe)
            }
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal fatte vedtak med direkte avslag med reel mottaker`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragspliktig!!.gebyr = GebyrRolle(true, true, "Begrunnelse")
        behandling.bidragsmottaker!!.gebyr = GebyrRolle(true, true, "Begrunnelse")
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.søknadsbarn.first().avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandlingMedReelMottaker(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = 3))

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
            shouldHaveSize(3)
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
            shouldHaveSize(3)
            it.shouldContainPerson(testdataBarnBm.ident)
        }
    }
}

private fun OpprettVedtakRequestDto.validerNotater(behandling: Behandling) {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
        shouldHaveSize(15)
        assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Virkningstidspunkt kun i notat"
        }

        assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Virkningstidspunkt kun i notat - fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.PRIVAT_AVTALE, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Privat avtale"
        }

        assertSoftly(hentNotat(NotatType.PRIVAT_AVTALE, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Privat avtale - fra opprinnelig vedtak"
        }
        assertSoftly(hentNotat(NotatType.BOFORHOLD)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Boforhold"
        }
        assertSoftly(hentNotat(NotatType.BOFORHOLD, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Boforhold - fra opprinnelig vedtak"
        }
        assertSoftly(hentNotat(NotatType.SAMVÆR, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Samvær"
        }

        assertSoftly(hentNotat(NotatType.SAMVÆR, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Samvær - fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Underhold barn"
        }
        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Underhold barn - fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Underhold andre barn"
        }
        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderReferanse = bmGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Underhold andre barn - fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Inntektsbegrunnelse kun i notat"
        }
        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = bmGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Inntektsbegrunnelse BM - fra opprinnelig vedtak"
        }
    }
}

private fun OpprettVedtakRequestDto.validerSluttberegning() {
    val sluttberegning =
        hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG)
    sluttberegning shouldHaveSize (9)
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!

    val sluttberegningPeriode = sluttberegning[6]
    assertSoftly(sluttberegningPeriode) {
        val innhold = innholdTilObjekt<SluttberegningBarnebidrag>()
        innhold.resultatVisningsnavn!!.intern shouldBe "Kostnadsberegnet bidrag"
        innhold.beregnetBeløp shouldBe BigDecimal("5986.82")
        innhold.resultatBeløp shouldBe BigDecimal("5990")
        it.grunnlagsreferanseListe shouldHaveSize 10
        hentGrunnlagstyperForReferanser(Grunnlagstype.PERSON_SØKNADSBARN, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.PERSON_SØKNADSBARN, it.grunnlagsreferanseListe).first().referanse shouldBe søknadsbarn1Grunnlag.referanse
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSEVNE, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD, it.grunnlagsreferanseListe) shouldHaveSize 1
        hentGrunnlagstyperForReferanser(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE, it.grunnlagsreferanseListe) shouldHaveSize 0
        hentGrunnlagstyperForReferanser(Grunnlagstype.SAMVÆRSPERIODE, it.grunnlagsreferanseListe) shouldHaveSize 1
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSEVNE, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningBidragsevne>()
        innhold.beløp shouldBe BigDecimal("8542.50")
        it.grunnlagsreferanseListe shouldHaveSize 11
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningBidragspliktigesAndel>()
        innhold.andelBeløp shouldBe BigDecimal("7034.82")
        it.grunnlagsreferanseListe shouldHaveSize 6
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningUnderholdskostnad>()
        innhold.underholdskostnad shouldBe BigDecimal("8441.78")
        innhold.nettoTilsynsutgift shouldBe BigDecimal("1273.78")
        innhold.barnetilsynMedStønad shouldBe BigDecimal("621.00")
        it.grunnlagsreferanseListe shouldHaveSize 6
    }

    assertSoftly(hentGrunnlagstyperForReferanser(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, sluttberegningPeriode.grunnlagsreferanseListe).first()) {
        val innhold = innholdTilObjekt<DelberegningSamværsfradrag>()
        innhold.beløp shouldBe BigDecimal("1048.00")
        it.grunnlagsreferanseListe shouldHaveSize 3
    }
}

private fun OpprettVedtakRequestDto.validerBosstatusPerioder() {
    val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    val husstandsmedlemGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
        shouldHaveSize(3)
        val bostatusSøknadsbarn1 =
            it.filtrerBasertPåFremmedReferanse(gjelderBarnReferanse = søknadsbarn1Grunnlag.referanse)
        bostatusSøknadsbarn1.shouldHaveSize(1)
        it[0].gjelderBarnReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[1].gjelderBarnReferanse shouldBe husstandsmedlemGrunnlag.referanse
        it[2].gjelderBarnReferanse shouldBe null
        it[2].gjelderReferanse shouldBe bpGrunnlag.referanse
        assertSoftly(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-02")
            periode.til shouldBe null
            relatertTilPart shouldBe bpGrunnlag.referanse
        }
        it.filtrerBasertPåFremmedReferanse(gjelderBarnReferanse = husstandsmedlemGrunnlag.referanse).shouldHaveSize(1)
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
        it.grunnlagsreferanseListe shouldHaveSize 1
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
        delberegningSamværsklasse.grunnlagsreferanseListe shouldHaveSize 2
        grunnlagListe.harReferanseTilGrunnlag(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER, delberegningSamværsklasse)
        grunnlagListe.harReferanseTilGrunnlag(Grunnlagstype.SAMVÆRSKALKULATOR, delberegningSamværsklasse)
        delberegningSamværsklasse.gjelderBarnReferanse shouldBe søknadsbarnGrunnlag.referanse
        delberegningSamværsklasse.gjelderReferanse shouldBe bpGrunnlag.referanse
        innholdSamværsklasse.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_2
        innholdSamværsklasse.gjennomsnittligSamværPerMåned shouldBe BigDecimal("8.01")

        val delberegningSamværsklasseNetter = grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER, it).first()
        delberegningSamværsklasseNetter.grunnlagsreferanseListe.shouldHaveSize(5)
        grunnlagListe.finnGrunnlagSomErReferertAv(Grunnlagstype.SJABLON_SAMVARSFRADRAG, delberegningSamværsklasseNetter) shouldHaveSize 5
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
            inntektspostListe shouldHaveSize 0
            beløp shouldBe 500000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[1].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2023-07")
            periode.til shouldBe null
            inntektspostListe shouldHaveSize 1
            beløp shouldBe 3000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
            gjelderBarn shouldBe søknadsbarnGrunnlag.referanse
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }

        assertSoftly(it[3].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2023-07")
            periode.til shouldBe null
            inntektspostListe shouldHaveSize 1
            beløp shouldBe 3000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
            gjelderBarn shouldBe søknadsbarnGrunnlag.referanse
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
    }
}
