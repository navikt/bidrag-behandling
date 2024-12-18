package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
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
            rolle = testdataBM.tilRolle(),
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolle = testdataBarn1.tilRolle(),
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolle = testdataBarn2.tilRolle(),
        )

        val behandling = testdataManager.lagreBehandling(opprettBehandling())

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.GET,
                null,
                Any::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        val inntekter = (behandlingRes.body as LinkedHashMap<*, *>)["inntekter"] as LinkedHashMap<*, *>
        inntekter shouldHaveSize (10)

        val beregnaInntekter = inntekter["beregnetInntekter"] as ArrayList<*>
        beregnaInntekter shouldHaveSize 3

        val beregnaInntekterBm = beregnaInntekter.filter { (it as LinkedHashMap<*, *>)["rolle"] == "BM" } as ArrayList<*>

        assertSoftly((beregnaInntekterBm.first() as LinkedHashMap<*, *>)["inntekter"] as ArrayList<*>) {
            shouldHaveSize(3)
            val inntekterIkkeBarn = find { (it as LinkedHashMap<*, *>)["inntektGjelderBarnIdent"] == null }
            val inntekterBa1 = find { (it as LinkedHashMap<*, *>)["inntektGjelderBarnIdent"] != null && it["inntektGjelderBarnIdent"] as String == testdataBarn1.ident }
            val inntekterBa2 = find { (it as LinkedHashMap<*, *>)["inntektGjelderBarnIdent"] != null && it["inntektGjelderBarnIdent"] as String == testdataBarn2.ident }

            inntekterIkkeBarn.shouldNotBeNull()
            inntekterBa1.shouldNotBeNull()
            inntekterBa2.shouldNotBeNull()

            assertSoftly(inntekterIkkeBarn as LinkedHashMap<*, *>) {
                get("summertInntektListe") as ArrayList<*> shouldHaveSize 3
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["skattepliktigInntekt"] as Int shouldBe 55000
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["barnetillegg"] shouldBe null
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["kontantstøtte"] shouldBe null
            }

            assertSoftly(inntekterBa1 as LinkedHashMap<*, *>) {
                get("summertInntektListe") as ArrayList<*> shouldHaveSize 5
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["skattepliktigInntekt"] as Int shouldBe 55000
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["barnetillegg"] as Int shouldBe 5000
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["kontantstøtte"] shouldBe null
            }

            assertSoftly(inntekterBa2 as LinkedHashMap<*, *>) {
                get("summertInntektListe") as ArrayList<*> shouldHaveSize 3
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["skattepliktigInntekt"] as Int shouldBe 55000
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["barnetillegg"] shouldBe null
                ((get("summertInntektListe") as ArrayList<*>).first() as LinkedHashMap<*, *>)["kontantstøtte"] shouldBe null
            }
        }

        assertSoftly(inntekter["barnetillegg"] as ArrayList<*>) {
            shouldHaveSize(2)

            (first() as LinkedHashMap<*, *>)["gjelderBarn"] as String shouldBe Personident(testdataBarn1.ident).verdi
            (first() as LinkedHashMap<*, *>)["inntektsposter"] as ArrayList<*> shouldHaveSize 1
            (((first() as LinkedHashMap<*, *>)["inntektsposter"] as ArrayList<*>).first() as LinkedHashMap<*, *>)["beløp"] as Int shouldBe (first() as LinkedHashMap<*, *>)["beløp"] as Int
            (((first() as LinkedHashMap<*, *>)["inntektsposter"] as ArrayList<*>).first() as LinkedHashMap<*, *>)["inntektstype"] as String shouldBe Inntektstype.BARNETILLEGG_PENSJON.name
        }

        /*
                assertSoftly(behandlingRes.body!!) {

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
         */
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
                    dato = LocalDate.now().minusYears(3),
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
                Any::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        assertSoftly((behandlingRes.body as LinkedHashMap<*, *>)["utgift"] as LinkedHashMap<*, *>) {
            get("avslag") as String shouldBe Resultatkode.ALLE_UTGIFTER_ER_FORELDET.name
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
                Any::class.java,
            )

        // så
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        assertSoftly((behandlingRes.body as LinkedHashMap<*, *>)["utgift"] as LinkedHashMap<*, *>) {
            get("avslag") as String shouldBe Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS.name
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
            rolle = testdataBM.tilRolle(),
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolle = testdataBarn1.tilRolle(),
        )
        stubUtils.stubHenteGrunnlag(
            tomRespons = true,
            rolle = testdataBarn2.tilRolle(),
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
        behandling.virkningstidspunktbegrunnelseKunINotat = "notat virkning"
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
