package no.nav.bidrag.behandling.service

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
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.utils.hentGrunnlagstype
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.hentGrunnlagstyperForReferanser
import no.nav.bidrag.behandling.utils.hentNotat
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.shouldContainPerson
import no.nav.bidrag.behandling.utils.stubHentPersonNyIdent
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.søknad
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandlingMedReelMottaker
import no.nav.bidrag.behandling.utils.testdata.synkSøknadsbarnVirkningstidspunkt
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.behandling.utils.virkningsdato
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningForskudd
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.søknadsbarn
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional

class VedtakserviceForskuddTest : CommonVedtakTilBehandlingTest() {
    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en forskudd behandling opphør`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        behandling.virkningstidspunkt = LocalDate.parse("2024-01-01")
        behandling.synkSøknadsbarnVirkningstidspunkt()
        behandling.inntekter = behandling.inntekter.filter { it.type != Inntektsrapportering.BARNETILLEGG }.toMutableSet()
        val søknadsbarn = behandling.søknadsbarn.first()
        søknadsbarn.fødselsdato = LocalDate.now().minusMonths(2).minusYears(18)

        behandling.søknadsbarn.first().opphørsdato =
            søknadsbarn.fødselsdato
                .plusYears(18)
                .plusMonths(1)
                .withDayOfMonth(1)
        behandling.roller = (behandling.søknadsbarn.filter { it.id == søknadsbarn.id } + listOf(behandling.bidragsmottaker!!)).toMutableSet()
        val husstandsmedlemSøknadsbarn = behandling.husstandsmedlem.find { it.ident == søknadsbarn.ident }!!
        husstandsmedlemSøknadsbarn.perioder =
            mutableSetOf(
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlemSøknadsbarn,
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = behandling.søknadsbarn.first().opphørsdato,
                    bostatus = Bostatuskode.MED_FORELDER,
                    kilde = Kilde.MANUELL,
                    id = 2,
                ),
            )
        behandling.inntekter.forEach {
            if (it.datoTom == null) {
                it.datoTom =
                    behandling.søknadsbarn
                        .first()
                        .opphørsdato!!
                        .minusDays(1)
            }
        }
        behandling.husstandsmedlem.forEach {
            it.perioder.forEach { p ->
                if (p.datoTom == null) {
                    p.datoTom =
                        behandling.søknadsbarn
                            .first()
                            .opphørsdato!!
                            .minusDays(1)
                }
            }
        }
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
                testdataBarn =
                    testdataBarn1.copy(
                        fødselsdato = søknadsbarn.fødselsdato,
                    ),
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
            val periodeliste = request.stønadsendringListe.first().periodeListe
            periodeliste shouldHaveSize 4
            periodeliste.last().periode.fom shouldBe YearMonth.from(behandling.søknadsbarn.first().opphørsdato)
            periodeliste.last().resultatkode shouldBe Resultatkode.OPPHØR.name
            request.engangsbeløpListe.shouldBeEmpty()
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en forskudd behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
        )
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
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

            request.stønadsendringListe shouldHaveSize 2
            request.engangsbeløpListe.shouldBeEmpty()
        }
        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        opprettVedtakRequest.validerPersongrunnlag()
        opprettVedtakRequest.validerSluttberegning()
        opprettVedtakRequest.validerBosstatusPerioder()
        opprettVedtakRequest.validerInntektrapportering()

        assertSoftly(opprettVedtakRequest) {
            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
            validerNotater()

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SIVILSTAND_PERIODE)) {
                shouldHaveSize(1)
                it[0].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
                val sivilstandGrunnlag = it.innholdTilObjekt<SivilstandPeriode>()
                sivilstandGrunnlag[0].sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                sivilstandGrunnlag[0].periode.fom shouldBe YearMonth.parse("2022-02")
                sivilstandGrunnlag[0].periode.til shouldBe null
            }

            assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, bmGrunnlag.referanse)) {
                val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
                it.gjelderReferanse.shouldBe(bmGrunnlag.referanse)
                innhold.summertMånedsinntektListe.shouldHaveSize(13)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(3)
                assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe henteNotatinnhold(behandling, NotatType.VIRKNINGSTIDSPUNKT)
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe NotatType.VIRKNINGSTIDSPUNKT
                }

                val notatInntekter = this.find { it.innholdTilObjekt<NotatGrunnlag>().type == NotatType.INNTEKT }
                notatInntekter!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe "Inntektsbegrunnelse kun i notat"
            }

            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3 // TODO: Hvorfor 3?
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 26
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 4
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 1
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal opprette stønadsendringer med reel mottaker`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns
            opprettSakForBehandlingMedReelMottaker(
                behandling,
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
            assertSoftly(stønadsendringListe[0]) {
                skyldner.verdi shouldBe "NAV"
                kravhaver.verdi shouldBe testdataBarn1.ident
                mottaker.verdi shouldBe "REEL_MOTTAKER"
            }
            assertSoftly(stønadsendringListe[1]) {
                skyldner.verdi shouldBe "NAV"
                kravhaver.verdi shouldBe testdataBarn2.ident
                mottaker.verdi shouldBe testdataBM.ident
            }
        }
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal opprette husstandsmedlem uten navn og bruke nyeste identer`() {
        val nyIdentBm = "ny_ident_bm"
        val nyIdentBarn1 = "ny_ident_barn_1"
        val nyIdentBarn2 = "ny_ident_barn_2"
        val nyIdentHusstandsmedlem = "ny_ident_husstandsmedlem"
        stubHentPersonNyIdent(testdataBarn1.ident, nyIdentBarn1, personConsumer)
        stubHentPersonNyIdent(testdataBarn2.ident, nyIdentBarn2, personConsumer)
        stubHentPersonNyIdent(testdataBM.ident, nyIdentBm, personConsumer)
        stubHentPersonNyIdent(testdataHusstandsmedlem1.ident, nyIdentHusstandsmedlem, personConsumer)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        val husstandsmedlemUtenIdent =
            Husstandsmedlem(
                behandling = behandling,
                kilde = Kilde.MANUELL,
                ident = null,
                navn = "Mr Hansen",
                fødselsdato = LocalDate.parse("2020-01-01"),
                id = 8,
            )
        husstandsmedlemUtenIdent.perioder =
            mutableSetOf(
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlemUtenIdent,
                    datoFom = behandling.søktFomDato,
                    datoTom = null,
                    bostatus = Bostatuskode.MED_FORELDER,
                    kilde = Kilde.MANUELL,
                    id = 2,
                ),
            )
        behandling.husstandsmedlem.add(husstandsmedlemUtenIdent)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
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

        assertSoftly(opprettVedtakRequest) { request ->
            request.type shouldBe Vedtakstype.FASTSETTELSE
            request.stønadsendringListe shouldHaveSize 2
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe[0]) {
            skyldner.verdi shouldBe "NAV"
            kravhaver.verdi shouldBe nyIdentBarn1
            mottaker.verdi shouldBe nyIdentBm
        }
        assertSoftly(opprettVedtakRequest.stønadsendringListe[1]) {
            skyldner.verdi shouldBe "NAV"
            kravhaver.verdi shouldBe nyIdentBarn2
            mottaker.verdi shouldBe nyIdentBm
        }
        opprettVedtakRequest.engangsbeløpListe.shouldBeEmpty()

        opprettVedtakRequest.grunnlagListe.hentAllePersoner() shouldHaveSize 7
        opprettVedtakRequest.grunnlagListe.søknadsbarn
            .toList()[0]
            .personIdent shouldBe nyIdentBarn1
        opprettVedtakRequest.grunnlagListe.søknadsbarn
            .toList()[1]
            .personIdent shouldBe nyIdentBarn2
        opprettVedtakRequest.grunnlagListe.bidragsmottaker!!.personIdent shouldBe nyIdentBm

        val husstandsmedlemmer =
            opprettVedtakRequest.grunnlagListe.hentGrunnlagstyper(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)
        husstandsmedlemmer shouldHaveSize 4
        husstandsmedlemmer[0].personIdent shouldBe testdataHusstandsmedlem1.ident
        assertSoftly(husstandsmedlemmer[1].innholdTilObjekt<Person>()) {
            ident shouldBe null
            navn shouldBe "Mr Hansen"
            fødselsdato shouldBe LocalDate.parse("2020-01-01")
        }
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal bruke nyeste identer for avslag`() {
        val nyIdentBm = "ny_ident_bm"
        val nyIdentBarn1 = "ny_ident_barn_1"
        val nyIdentBarn2 = "ny_ident_barn_2"
        val nyIdentHusstandsmedlem = "ny_ident_husstandsmedlem"
        val mock = stubHentPersonNyIdent(testdataBarn1.ident, nyIdentBarn1)
        stubHentPersonNyIdent(testdataBarn2.ident, nyIdentBarn2, mock)
        stubHentPersonNyIdent(testdataBM.ident, nyIdentBm, mock)
        stubHentPersonNyIdent(testdataHusstandsmedlem1.ident, nyIdentHusstandsmedlem, mock)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                omgjørVedtakId = 553,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
        )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured
        opprettVedtakRequest.type shouldBe Vedtakstype.FASTSETTELSE
        opprettVedtakRequest.stønadsendringListe shouldHaveSize 2

        assertSoftly(opprettVedtakRequest.stønadsendringListe[0]) {
            skyldner.verdi shouldBe "NAV"
            kravhaver.verdi shouldBe nyIdentBarn1
            omgjørVedtakId shouldBe 553
            mottaker.verdi shouldBe nyIdentBm
        }
        assertSoftly(opprettVedtakRequest.stønadsendringListe[1]) {
            skyldner.verdi shouldBe "NAV"
            omgjørVedtakId shouldBe 553
            kravhaver.verdi shouldBe nyIdentBarn2
            mottaker.verdi shouldBe nyIdentBm
        }
        opprettVedtakRequest.engangsbeløpListe.shouldBeEmpty()
        opprettVedtakRequest.grunnlagListe.shouldHaveSize(8)
        opprettVedtakRequest.grunnlagListe.hentAllePersoner() shouldHaveSize 3
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for avslag av forskudd behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
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
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }

        assertSoftly(opprettVedtakRequest) { request ->
            request.type shouldBe Vedtakstype.FASTSETTELSE
            request.stønadsendringListe shouldHaveSize 2
            request.engangsbeløpListe.shouldBeEmpty()
            request.grunnlagListe shouldHaveSize 8
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
                    it[0].grunnlagReferanseListe.shouldHaveSize(4)
                    it[0].grunnlagReferanseListe.forEach {
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
                    }
                    assertSoftly(it[0].periodeListe) {
                        shouldHaveSize(1)
                        assertSoftly(it[0]) {
                            periode shouldBe
                                ÅrMånedsperiode(
                                    behandling.virkningstidspunkt!!,
                                    null,
                                )
                            beløp shouldBe null
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
                    it[1].grunnlagReferanseListe.shouldHaveSize(4)
                    it[1].grunnlagReferanseListe.forEach {
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
                    }
                    assertSoftly(it[1].periodeListe) {
                        shouldHaveSize(1)
                        assertSoftly(it[0]) {
                            periode shouldBe
                                ÅrMånedsperiode(
                                    behandling.virkningstidspunkt!!,
                                    null,
                                )
                            beløp shouldBe null
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
            it[0].omgjørVedtakId shouldBe 553
            it[0].grunnlagReferanseListe.shouldHaveSize(6)
            it[0].grunnlagReferanseListe.forEach {
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
            }
            assertSoftly(it[0].periodeListe) {
                shouldHaveSize(7)
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
            it[1].omgjørVedtakId shouldBe 553
            it[1].grunnlagReferanseListe.shouldHaveSize(6)
            it[1].grunnlagReferanseListe.forEach {
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
            }
            assertSoftly(it[1].periodeListe) {
                shouldHaveSize(7)
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
    val husstandsmedlemGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
        shouldHaveSize(6)
        val bostatusSøknadsbarn1 =
            it.filtrerBasertPåFremmedReferanse(gjelderBarnReferanse = søknadsbarn1Grunnlag.referanse)
        bostatusSøknadsbarn1.shouldHaveSize(2)
        it[0].gjelderBarnReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[1].gjelderBarnReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[4].gjelderBarnReferanse shouldBe søknadsbarn2Grunnlag.referanse
        it[5].gjelderBarnReferanse shouldBe søknadsbarn2Grunnlag.referanse
        it[2].gjelderBarnReferanse shouldBe husstandsmedlemGrunnlag.referanse
        it[3].gjelderBarnReferanse shouldBe husstandsmedlemGrunnlag.referanse
        assertSoftly(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-02")
            periode.til shouldBe YearMonth.parse("2023-08")
            relatertTilPart shouldBe bmGrunnlag.referanse
        }
        assertSoftly(bostatusSøknadsbarn1[1].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-08")
            periode.til shouldBe null
            relatertTilPart shouldBe bmGrunnlag.referanse
        }

        it
            .filtrerBasertPåFremmedReferanse(gjelderBarnReferanse = søknadsbarn2Grunnlag.referanse)
            .shouldHaveSize(2)
        it
            .filtrerBasertPåFremmedReferanse(gjelderBarnReferanse = husstandsmedlemGrunnlag.referanse)
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
            inntektspostListe shouldHaveSize 0
            beløp shouldBe 50000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[1].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-07")
            periode.til shouldBe YearMonth.parse("2022-10")
            inntektspostListe shouldHaveSize 0
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[2]) {
            it.grunnlagsreferanseListe shouldHaveSize 1
            val grunnlag =
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it.grunnlagsreferanseListe[0])
            grunnlag[0].type shouldBe Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT
            assertSoftly(innholdTilObjekt<InntektsrapporteringPeriode>()) {
                periode.fom shouldBe YearMonth.parse("2022-10")
                periode.til shouldBe null
                inntektspostListe shouldHaveSize 1
                beløp shouldBe 60000.toBigDecimal()
                inntektsrapportering shouldBe Inntektsrapportering.AINNTEKT_BEREGNET_12MND
                gjelderBarn shouldBe null
                valgt shouldBe true
                manueltRegistrert shouldBe false
            }
        }
        assertSoftly(it[3].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2023-01")
            periode.til shouldBe YearMonth.parse("2024-01")
            inntektspostListe shouldHaveSize 1
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
            gjelderBarn shouldBe søknadsbarnGrunnlag.referanse
            valgt shouldBe true
            manueltRegistrert shouldBe false
        }
        assertSoftly(it[4].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-01")
            periode.til shouldBe null
            inntektspostListe shouldHaveSize 0
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
        shouldHaveSize(14)
        it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn2Grunnlag!!.referanse) shouldHaveSize 7
    }

    val sluttberegningForskudd =
        hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_FORSKUDD)
            .filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag!!.referanse)
    sluttberegningForskudd shouldHaveSize (7)

    assertSoftly(sluttberegningForskudd[5]) {
        val innhold = innholdTilObjekt<SluttberegningForskudd>()
        innhold.beløp.toBigInteger() shouldBe 1880.toBigInteger()
        innhold.resultatKode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
        innhold.aldersgruppe shouldBe AldersgruppeForskudd.ALDER_0_10_ÅR
    }
    val delberegningInntekt =
        hentGrunnlagstyperForReferanser(
            Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
            sluttberegningForskudd[3].grunnlagsreferanseListe,
        )

    delberegningInntekt shouldHaveSize (1)
    val delberegningInnhold = delberegningInntekt[0].innholdTilObjekt<DelberegningSumInntekt>()

    assertSoftly(delberegningInntekt[0]) { delberegning ->
        delberegningInnhold.totalinntekt shouldBe "120000.00".toBigDecimal()
        delberegningInnhold.skattepliktigInntekt shouldBe "60000.00".toBigDecimal()
        delberegningInnhold.barnetillegg shouldBe "60000.00".toBigDecimal()
        delberegning.grunnlagsreferanseListe shouldHaveSize 2
    }

    val delberegningInntektFiltrertPåEgenReferanse =
        grunnlagListe
            .filtrerBasertPåEgenReferanse(referanse = delberegningInntekt[0].grunnlagsreferanseListe[0])
            .first()

    assertSoftly(delberegningInntektFiltrertPåEgenReferanse) {
        it.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
        it.grunnlagsreferanseListe shouldHaveSize 1
    }

    val innhentetAinntekt =
        grunnlagListe
            .filtrerBasertPåEgenReferanse(referanse = delberegningInntektFiltrertPåEgenReferanse.grunnlagsreferanseListe[0])
            .first()
    assertSoftly(innhentetAinntekt) {
        it.type shouldBe Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT
        it.grunnlagsreferanseListe shouldHaveSize 0
        it.gjelderReferanse shouldBe bmGrunnlag.referanse
    }

    val delberegningBarnIHusstand =
        hentGrunnlagstyperForReferanser(
            Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND,
            sluttberegningForskudd[4].grunnlagsreferanseListe,
        )

    assertSoftly(delberegningBarnIHusstand) {
        shouldHaveSize(1)
        assertSoftly(it[0]) { delberegning ->
            delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().antallBarn shouldBe 2.0
            delberegning.grunnlagsreferanseListe shouldHaveSize 2

            val bosstatusHusstandsmedlem =
                grunnlagListe
                    .filtrerBasertPåEgenReferanse(referanse = delberegning.grunnlagsreferanseListe[0])
                    .first()
            bosstatusHusstandsmedlem.type shouldBe Grunnlagstype.BOSTATUS_PERIODE
            bosstatusHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 1

            val innhentetHusstandsmedlem =
                grunnlagListe
                    .filtrerBasertPåEgenReferanse(referanse = bosstatusHusstandsmedlem.grunnlagsreferanseListe[0])
                    .first()
            innhentetHusstandsmedlem.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
            innhentetHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 0
            innhentetHusstandsmedlem.gjelderReferanse shouldBe bmGrunnlag.referanse
        }
    }
}

private fun OpprettVedtakRequestDto.validerNotater() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
        shouldHaveSize(3)
        assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Virkningstidspunkt kun i notat"
        }

        assertSoftly(hentNotat(NotatType.BOFORHOLD)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Boforhold"
        }
        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Inntektsbegrunnelse kun i notat"
        }
    }
}
