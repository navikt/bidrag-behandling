package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.utils.hentInntektForBarn
import no.nav.bidrag.behandling.utils.testdata.TestDataPerson
import no.nav.bidrag.behandling.utils.testdata.initGrunnlagRespons
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettRolle
import no.nav.bidrag.behandling.utils.testdata.opprettSivilstand
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate

class HentBehandlingTest : BehandlingControllerTest() {
    @Test
    fun `skal hente behandling`() {
        // gitt
        stubUtils.stubHenteGrunnlag(
            navnResponsfil = "grunnlagresponse.json",
            rolleIdent = testdataBM.ident,
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolleIdent = testdataBarn1.ident,
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolleIdent = testdataBarn2.ident,
        )

        val behandling = testdataManager.lagreBehandling(opprettBehandling())

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                BehandlingDtoV2::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        assertSoftly(behandlingRes.body!!) {
            it.inntekter.beregnetInntekter shouldHaveSize 3
            val beregnetInntekterBM =
                it.inntekter.beregnetInntekter.find { it.rolle == Rolletype.BIDRAGSMOTTAKER }!!
            beregnetInntekterBM.inntekter shouldHaveSize 3
            val inntekterAlle =
                beregnetInntekterBM.inntekter.find { it.inntektGjelderBarnIdent == null }
            val inntekterBarn1 =
                beregnetInntekterBM.inntekter.hentInntektForBarn(testdataBarn1.ident)
            val inntekterBarn2 =
                beregnetInntekterBM.inntekter.hentInntektForBarn(testdataBarn2.ident)
            inntekterAlle.shouldNotBeNull()
            inntekterBarn1.shouldNotBeNull()
            inntekterBarn2.shouldNotBeNull()

            assertSoftly(it.inntekter.barnetillegg.toList()) {
                this shouldHaveSize 2
                this[0].gjelderBarn shouldBe Personident(testdataBarn1.ident)
                this[0].inntektsposter shouldHaveSize 1
                this[0].inntektsposter.first().beløp shouldBe this[0].beløp
                this[0].inntektsposter.first().inntektstype shouldBe Inntektstype.BARNETILLEGG_PENSJON
            }

            assertSoftly(inntekterAlle) {
                summertInntektListe shouldHaveSize 3
                summertInntektListe[0].skattepliktigInntekt shouldBe BigDecimal(55000)
                summertInntektListe[0].barnetillegg shouldBe null
                summertInntektListe[0].kontantstøtte shouldBe null
            }
            assertSoftly(inntekterBarn2) {
                summertInntektListe shouldHaveSize 3
                summertInntektListe[0].skattepliktigInntekt shouldBe BigDecimal(55000)
                summertInntektListe[0].barnetillegg shouldBe null
                summertInntektListe[0].kontantstøtte shouldBe null
            }
            assertSoftly(inntekterBarn1) {
                summertInntektListe shouldHaveSize 5
                summertInntektListe[0].skattepliktigInntekt shouldBe BigDecimal(55000)
                summertInntektListe[0].barnetillegg shouldBe BigDecimal(5000)
                summertInntektListe[0].kontantstøtte shouldBe null
            }
        }
    }

    @Test
    fun `skal hente behandling særbidrag med avslag når alle utgifter er foreldet`() {
        // gitt

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(500)
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = behandling.mottattdato.minusYears(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
            )
        testdataManager.lagreBehandling(behandling)
        behandling.initGrunnlagRespons(stubUtils)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                BehandlingDtoV2::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        assertSoftly(behandlingRes.body!!) {
            it.utgift!!.avslag shouldBe Resultatkode.ALLE_UTGIFTER_ER_FORELDET
        }
    }

    @Test
    fun `skal hente behandling særbidrag med avslag godkjent beløp lavere enn forskuddsats`() {
        // gitt

        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal.ZERO
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    utgift = behandling.utgift!!,
                    kravbeløp = BigDecimal(15000),
                    godkjentBeløp = BigDecimal(0),
                    kommentar = "Inneholder avgifter for alkohol og pynt",
                ),
            )
        testdataManager.lagreBehandling(behandling)
        behandling.initGrunnlagRespons(stubUtils)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                BehandlingDtoV2::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        assertSoftly(behandlingRes.body!!) {
            it.utgift!!.avslag shouldBe Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS
        }
    }

    @Test
    fun `skal ikke hente behandling hvis ingen tilgang til sak`() {
        val behandling = testdataManager.lagreBehandling(opprettBehandling())

        stubUtils.stubTilgangskontrollSak(false)
        stubUtils.stubTilgangskontrollPersonISak(false)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                Void::class.java,
            )

        Assertions.assertEquals(HttpStatus.FORBIDDEN, behandlingRes.statusCode)
    }

    @Test
    fun `skal ikke hente behandling hvis ingen tilgang til rolle`() {
        val behandling = testdataManager.lagreBehandling(opprettBehandling())

        stubUtils.stubTilgangskontrollSak(true)
        stubUtils.stubTilgangskontrollPersonISak(false)

        behandling.roller.forEachIndexed { index, rolle ->
            stubUtils.stubTilgangskontrollPerson(index == 0, personIdent = rolle.ident)
        }
        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                Void::class.java,
            )

        Assertions.assertEquals(HttpStatus.FORBIDDEN, behandlingRes.statusCode)
    }

    @Test
    fun `skal hente behandling med informajson om feil ved siste grunnlagsinnhenting`() {
        // gitt
        val fomdatoIFeilrespons = LocalDate.of(2020, 6, 1)
        val tildatoIFeilrespons = LocalDate.of(2023, 7, 1)
        val feilmeldingIFeilrespons = "Feil ved henting av ainntekt."

        stubUtils.stubHenteGrunnlag(
            navnResponsfil = "hente-grunnlagrespons-med-feil.json",
            rolleIdent = testdataBM.ident,
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolleIdent = testdataBarn1.ident,
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolleIdent = testdataBarn2.ident,
        )

        val behandling = testdataManager.lagreBehandling(opprettBehandling())

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                BehandlingDtoV2::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        assertSoftly(behandlingRes.body?.feilOppståttVedSisteGrunnlagsinnhenting) { feil ->
            feil shouldNotBe null
            feil!! shouldHaveSize 2
            feil.first().rolle.id shouldBe behandling.bidragsmottaker!!.id!!
            feil.first().periode?.fom shouldBe fomdatoIFeilrespons
            feil.first().periode?.til shouldBe tildatoIFeilrespons
            feil.first().feilmelding shouldBe feilmeldingIFeilrespons
            feil.filter { Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER == it.grunnlagsdatatype } shouldHaveSize 1
            feil.filter { Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER == it.grunnlagsdatatype } shouldHaveSize 1
        }
    }

    private fun opprettBehandling(): Behandling {
        val behandling = oppretteBehandling()
        behandling.husstandsmedlem =
            mutableSetOf(
                oppretteHusstandsmedlem(behandling, testdataBarn1),
                oppretteHusstandsmedlem(behandling, testdataBarn2),
            )
        behandling.roller =
            mutableSetOf(
                opprettRolle(behandling, testdataBarn1),
                opprettRolle(behandling, testdataBarn2),
                opprettRolle(behandling, testdataBM),
            )
        behandling.sivilstand =
            mutableSetOf(
                opprettSivilstand(
                    behandling,
                    LocalDate.parse("2023-01-01"),
                    LocalDate.parse("2023-05-31"),
                    Sivilstandskode.BOR_ALENE_MED_BARN,
                ),
                opprettSivilstand(
                    behandling,
                    LocalDate.parse("2023-06-01"),
                    null,
                    Sivilstandskode.BOR_ALENE_MED_BARN,
                ),
            )
        behandling.inntekter = opprettInntekter(behandling, testdataBM, testdataBarn1)
        return behandling
    }

    private fun opprettInntekter(
        behandling: Behandling,
        gjelder: TestDataPerson,
        barn: TestDataPerson? = null,
    ): MutableSet<Inntekt> {
        val inntekter = mutableSetOf<Inntekt>()
        val inntekt1 =
            Inntekt(
                Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                BigDecimal.valueOf(45000),
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2023-12-31"),
                opprinneligFom = LocalDate.parse("2023-01-01"),
                opprinneligTom = LocalDate.parse("2023-07-01"),
                ident = gjelder.ident,
                kilde = Kilde.OFFENTLIG,
                taMed = false,
                behandling = behandling,
            )
        inntekt1.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    beløp = BigDecimal.valueOf(5000),
                    kode = "fisking",
                    inntektstype = null,
                    inntekt = inntekt1,
                ),
                Inntektspost(
                    beløp = BigDecimal.valueOf(40000),
                    kode = "krypto",
                    inntektstype = null,
                    inntekt = inntekt1,
                ),
            )
        inntekter.add(inntekt1)

        val inntekt2 =
            Inntekt(
                Inntektsrapportering.LIGNINGSINNTEKT,
                BigDecimal.valueOf(33000),
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2023-12-31"),
                opprinneligFom = LocalDate.parse("2023-01-01"),
                opprinneligTom = LocalDate.parse("2024-01-01"),
                ident = gjelder.ident,
                kilde = Kilde.OFFENTLIG,
                taMed = true,
                behandling = behandling,
            )
        inntekt2.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    beløp = BigDecimal.valueOf(5000),
                    kode = "",
                    inntektstype = Inntektstype.NÆRINGSINNTEKT,
                    inntekt = inntekt2,
                ),
                Inntektspost(
                    beløp = BigDecimal.valueOf(28000),
                    kode = "",
                    inntektstype = Inntektstype.LØNNSINNTEKT,
                    inntekt = inntekt2,
                ),
            )
        inntekter.add(inntekt2)

        inntekter.add(
            Inntekt(
                Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                BigDecimal.valueOf(55000),
                LocalDate.parse("2022-01-01"),
                LocalDate.parse("2022-12-31"),
                gjelder.ident,
                Kilde.MANUELL,
                true,
                behandling = behandling,
            ),
        )
        if (barn != null) {
            val inntekt3 =
                Inntekt(
                    Inntektsrapportering.BARNETILLEGG,
                    BigDecimal.valueOf(5000),
                    LocalDate.parse("2022-01-01"),
                    LocalDate.parse("2022-12-31"),
                    gjelder.ident,
                    Kilde.MANUELL,
                    true,
                    opprinneligFom = LocalDate.parse("2023-01-01"),
                    opprinneligTom = LocalDate.parse("2024-01-01"),
                    gjelderBarn = barn.ident,
                    behandling = behandling,
                )
            inntekt3.inntektsposter =
                mutableSetOf(
                    Inntektspost(
                        beløp = BigDecimal.valueOf(5000),
                        kode = "",
                        inntektstype = Inntektstype.BARNETILLEGG_PENSJON,
                        inntekt = inntekt3,
                    ),
                )
            inntekter.add(inntekt3)

            val inntekt4 =
                Inntekt(
                    Inntektsrapportering.KONTANTSTØTTE,
                    BigDecimal.valueOf(5000),
                    LocalDate.parse("2023-01-01"),
                    LocalDate.parse("2023-12-31"),
                    gjelder.ident,
                    Kilde.OFFENTLIG,
                    true,
                    opprinneligFom = LocalDate.parse("2023-01-01"),
                    opprinneligTom = LocalDate.parse("2024-01-01"),
                    gjelderBarn = testdataBarn1.ident,
                    behandling = behandling,
                )
            inntekt4.inntektsposter =
                mutableSetOf(
                    Inntektspost(
                        beløp = BigDecimal.valueOf(5000),
                        kode = "",
                        inntektstype = Inntektstype.KONTANTSTØTTE,
                        inntekt = inntekt4,
                    ),
                )
            inntekter.add(inntekt4)
        }
        return inntekter
    }
}
