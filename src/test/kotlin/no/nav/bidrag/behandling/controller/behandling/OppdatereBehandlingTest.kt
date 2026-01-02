package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.service.opprettHentGrunnlagDto
import no.nav.bidrag.behandling.service.tilSummerteInntekter
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlemMedOffentligePerioder
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagspostDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OppdatereBehandlingTest : BehandlingControllerTest() {
    @Test
    fun `skal oppdatere årsak`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling(true)
        behandling.søktFomDato = LocalDate.parse("2019-12-01")
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )
        behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
        behandling.avslag = Resultatkode.AVSLAG_OVER_18_ÅR
        behandling.årsak = null
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val nyVirkningstidspunkt = LocalDate.parse("2022-01-01")

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(
                    OppdatereVirkningstidspunkt(
                        årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT,
                        virkningstidspunkt = nyVirkningstidspunkt,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.virkningstidspunktV3.barn
            .first()
            .virkningstidspunkt shouldBe nyVirkningstidspunkt
        responseBody.virkningstidspunktV3.barn
            .first()
            .årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
        responseBody.virkningstidspunktV3.barn
            .first()
            .avslag shouldBe null

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        assertNotNull(oppdatertBehandling)
        assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
            virkningstidspunkt shouldBe nyVirkningstidspunkt
            avslag shouldBe null
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
        }
    }

    @Test
    fun `skal oppdatere avslag`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling(true)
        behandling.søktFomDato = LocalDate.parse("2019-12-01")
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )
        behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
        behandling.avslag = null
        behandling.årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val nyttVirkningstidspunkt = LocalDate.parse("2022-01-01")

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(
                    OppdatereVirkningstidspunkt(
                        avslag = Resultatkode.AVSLAG,
                        virkningstidspunkt = nyttVirkningstidspunkt,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.virkningstidspunktV3.barn
            .first()
            .virkningstidspunkt shouldBe nyttVirkningstidspunkt
        responseBody.virkningstidspunktV3.barn
            .first()
            .årsak shouldBe null
        responseBody.virkningstidspunktV3.barn
            .first()
            .avslag shouldBe Resultatkode.AVSLAG

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        assertNotNull(oppdatertBehandling)
        assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
            virkningstidspunkt shouldBe nyttVirkningstidspunkt
            avslag shouldBe Resultatkode.AVSLAG
            årsak shouldBe null
        }
    }

    @Test
    fun `skal ikke kunne sette virkningstidspunkt til tom verdi`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling(true)
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )
        behandling.søknadsbarn.forEach {
            it.virkningstidspunkt = LocalDate.parse("2023-01-01")
        }
        behandling.avslag = null
        behandling.årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
        testdataManager.lagreBehandlingNewTransaction(behandling)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(
                    OppdatereVirkningstidspunkt(
                        virkningstidspunkt = null,
                        årsak = null,
                        avslag = null,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.virkningstidspunktV3.barn
            .first()
            .virkningstidspunkt shouldBe LocalDate.parse("2023-01-01")
        responseBody.virkningstidspunktV3.barn
            .first()
            .årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
        responseBody.virkningstidspunktV3.barn
            .first()
            .avslag shouldBe null

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        assertNotNull(oppdatertBehandling)
        assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
            virkningstidspunkt shouldBe LocalDate.parse("2023-01-01")
            årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            avslag shouldBe null
        }
    }

    @Test
    fun `skal ikke kunne sette avslag til tom verdi`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling(true)
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )
        behandling.søknadsbarn.forEach {
            it.virkningstidspunkt = LocalDate.parse("2023-01-01")
            it.årsak = null
        }
        behandling.avslag = Resultatkode.AVSLAG
        behandling.årsak = null
        testdataManager.lagreBehandlingNewTransaction(behandling)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(
                    OppdatereVirkningstidspunkt(
                        virkningstidspunkt = null,
                        årsak = null,
                        avslag = null,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.virkningstidspunktV3.barn
            .first()
            .virkningstidspunkt shouldBe LocalDate.parse("2023-01-01")
        responseBody.virkningstidspunktV3.barn
            .first()
            .årsak shouldBe null
        responseBody.virkningstidspunktV3.barn
            .first()
            .avslag shouldBe Resultatkode.AVSLAG

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        assertNotNull(oppdatertBehandling)
        assertSoftly(behandlingRepository.findBehandlingById(behandling.id!!).get()) {
            virkningstidspunkt shouldBe LocalDate.parse("2023-01-01")
            årsak shouldBe null
            avslag shouldBe Resultatkode.AVSLAG
        }
    }

    @Test
    fun `skal oppdatere virkningstidspunkt og oppdatere fra og med dato på inntekter`() {
        // gitt
        val behandling = oppretteTestbehandling(true)
        behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )

        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-01"),
                    datoTom = YearMonth.parse("2023-05"),
                    type = Inntektsrapportering.AINNTEKT,
                    behandling = behandling,
                    medId = false,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-06"),
                    datoTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.AINNTEKT,
                    behandling = behandling,
                    medId = false,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-06"),
                    datoTom = YearMonth.parse("2024-01"),
                    type = Inntektsrapportering.KAPITALINNTEKT,
                    behandling = behandling,
                    medId = false,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-02"),
                    datoTom = YearMonth.parse("2023-09"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                    medId = false,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-02"),
                    datoTom = YearMonth.parse("2023-06"),
                    type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    behandling = behandling,
                    medId = false,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-02"),
                    datoTom = YearMonth.parse("2024-06"),
                    type = Inntektsrapportering.SMÅBARNSTILLEGG,
                    behandling = behandling,
                    medId = false,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = YearMonth.parse("2024-02"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    behandling = behandling,
                    medId = false,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)

        val b = behandlingRepository.findBehandlingById(behandling.id!!)

        val nyttVirkningstidspunkt = LocalDate.parse("2023-07-01")
        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(
                    OppdatereVirkningstidspunkt(
                        virkningstidspunkt = nyttVirkningstidspunkt,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.virkningstidspunktV3.barn
            .first()
            .virkningstidspunkt shouldBe nyttVirkningstidspunkt

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        oppdatertBehandling.get().virkningstidspunkt shouldBe nyttVirkningstidspunkt
        val inntekter = oppdatertBehandling.get().inntekter.toList()
        inntekter shouldHaveSize 7
        inntekter.filter { it.datoFom == nyttVirkningstidspunkt } shouldHaveSize 4
        inntekter
            .find { it.type == Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT }!!
            .datoFom shouldBe LocalDate.parse("2024-01-01")
        inntekter.filter { it.type == Inntektsrapportering.AINNTEKT } shouldHaveSize 2
        inntekter.filter {
            it.type == Inntektsrapportering.AINNTEKT && !it.taMed && it.datoFom == null && it.datoTom == null
        } shouldHaveSize
            1
        assertSoftly(inntekter.find { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }!!) {
            taMed shouldBe false
            datoFom shouldBe null
            datoTom shouldBe null
        }
    }

    @Test
    @Disabled
    fun `skal oppdatere virkningstidspunkt og ikke oppdatere fra og med dato på inntekter når virkningstidspunkt endres tilbake i tid`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling(true)
        behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )

        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-01"),
                    datoTom = YearMonth.parse("2023-12"),
                    type = Inntektsrapportering.AINNTEKT,
                    behandling = behandling,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-06"),
                    datoTom = YearMonth.parse("2024-01"),
                    type = Inntektsrapportering.KAPITALINNTEKT,
                    behandling = behandling,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-02"),
                    datoTom = YearMonth.parse("2023-09"),
                    type = Inntektsrapportering.BARNETILLEGG,
                    behandling = behandling,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-02"),
                    datoTom = YearMonth.parse("2023-06"),
                    type = Inntektsrapportering.UTVIDET_BARNETRYGD,
                    behandling = behandling,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-02"),
                    datoTom = YearMonth.parse("2024-06"),
                    type = Inntektsrapportering.SMÅBARNSTILLEGG,
                    behandling = behandling,
                ),
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = YearMonth.parse("2024-02"),
                    type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                    behandling = behandling,
                ),
            )

        behandlingRepository.save(behandling)

        testdataManager.lagreBehandlingNewTransaction(behandling)

        val nyttVirkningstidspunkt = LocalDate.parse("2022-01-01")
        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(
                    OppdatereVirkningstidspunkt(
                        virkningstidspunkt = nyttVirkningstidspunkt,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.virkningstidspunktV3.barn
            .first()
            .virkningstidspunkt shouldBe nyttVirkningstidspunkt

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        oppdatertBehandling.get().virkningstidspunkt shouldBe nyttVirkningstidspunkt
        val inntekter = oppdatertBehandling.get().inntekter.toList()
        inntekter shouldHaveSize 6
        inntekter
            .find { it.type == Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT }!!
            .datoFom shouldBe LocalDate.parse("2024-01-01")
        inntekter
            .find { it.type == Inntektsrapportering.KAPITALINNTEKT }!!
            .datoFom shouldBe LocalDate.parse("2023-06-01")
        inntekter
            .find { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }!!
            .datoFom shouldBe LocalDate.parse("2023-02-01")
        inntekter
            .find { it.type == Inntektsrapportering.AINNTEKT }!!
            .datoFom shouldBe LocalDate.parse("2023-01-01")
        inntekter
            .find { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }!!
            .datoFom shouldBe LocalDate.parse("2023-02-01")
    }

    @Test
    fun `skal oppdatere virkningstidspunkt og rekalkulere boforhold periode`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling(true)
        behandling.søktFomDato = LocalDate.parse("2019-12-01")
        behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
        behandling.grunnlag.addAll(
            oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                oppretteHusstandsmedlemMedOffentligePerioder(behandling),
            ),
        )
        behandling.husstandsmedlem.clear()
        behandling.husstandsmedlem.addAll(
            setOf(
                oppretteHusstandsmedlem(behandling, testdataBarn1).let {
                    it.perioder =
                        mutableSetOf(
                            Bostatusperiode(
                                datoFom = LocalDate.parse("2023-01-01"),
                                datoTom = LocalDate.parse("2023-05-31"),
                                bostatus = Bostatuskode.MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                husstandsmedlem = it,
                            ),
                            Bostatusperiode(
                                datoFom = LocalDate.parse("2023-06-01"),
                                datoTom = null,
                                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                husstandsmedlem = it,
                            ),
                        )
                    it
                },
            ),
        )

        testdataManager.lagreBehandlingNewTransaction(behandling)

        val nyttVirkningstidspunkt = LocalDate.parse("2022-01-01")
        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/virkningstidspunkt",
                HttpMethod.PUT,
                HttpEntity(
                    OppdatereVirkningstidspunkt(
                        virkningstidspunkt = nyttVirkningstidspunkt,
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.virkningstidspunktV3.barn
            .first()
            .virkningstidspunkt shouldBe nyttVirkningstidspunkt

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!)

        oppdatertBehandling.get().virkningstidspunkt shouldBe nyttVirkningstidspunkt
        assertSoftly(oppdatertBehandling.get().husstandsmedlem.first()) {
            perioder shouldHaveSize 3
            perioder.minByOrNull { it.datoFom!! }!!.datoFom shouldBe nyttVirkningstidspunkt
            perioder.maxByOrNull { it.datoFom!! }!!.datoFom shouldBe LocalDate.parse("2023-06-01")
        }
    }

    @Test
    @Transactional
    fun `skal aktivere grunnlag`() {
        // gitt
        var behandling = testdataManager.oppretteBehandlingINyTransaksjon(false, false, false)
        behandling.inntekter.add(
            Inntekt(
                Inntektsrapportering.LIGNINGSINNTEKT,
                BigDecimal.valueOf(33000),
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2023-12-31"),
                behandling.bidragsmottaker!!.ident!!,
                Kilde.OFFENTLIG,
                true,
                behandling = behandling,
                opprinneligFom = LocalDate.parse("2023-01-01"),
                opprinneligTom = LocalDate.parse("2023-12-31"),
            ),
        )
        behandling.inntekter.add(
            Inntekt(
                Inntektsrapportering.LIGNINGSINNTEKT,
                BigDecimal.valueOf(333000),
                LocalDate.parse("2022-01-01"),
                LocalDate.parse("2022-12-31"),
                behandling.bidragsmottaker!!.ident!!,
                Kilde.OFFENTLIG,
                true,
                behandling = behandling,
                opprinneligFom = LocalDate.parse("2022-01-01"),
                opprinneligTom = LocalDate.parse("2022-12-31"),
            ),
        )
        behandling = testdataManager.lagreBehandlingNewTransaction(behandling)
        val innhentingstidspunkt: LocalDateTime = LocalDate.of(2024, 1, 1).atStartOfDay()
        val grunnlagLagret =
            SkattegrunnlagGrunnlagDto(
                periodeFra =
                    YearMonth
                        .now()
                        .minusYears(1)
                        .withMonth(1)
                        .atDay(1),
                periodeTil = YearMonth.now().withMonth(1).atDay(1),
                personId = behandling.bidragsmottaker!!.ident!!,
                skattegrunnlagspostListe =
                    listOf(
                        SkattegrunnlagspostDto(
                            beløp = BigDecimal(450000),
                            belop = BigDecimal(450000),
                            inntektType = "renteinntektAvObligasjon",
                            skattegrunnlagType = "Something else",
                        ),
                        SkattegrunnlagspostDto(
                            beløp = BigDecimal(100000),
                            belop = BigDecimal(100000),
                            inntektType = "gevinstVedRealisasjonAvAksje",
                            skattegrunnlagType = "Something else",
                        ),
                    ),
            )
        behandling =
            testdataManager.oppretteOgLagreGrunnlagINyTransaksjon(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentingstidspunkt,
                grunnlagsdata = SkattepliktigeInntekter(skattegrunnlag = listOf(grunnlagLagret)),
            )
        behandling =
            testdataManager.oppretteOgLagreGrunnlagINyTransaksjon(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
                innhentingstidspunkt,
                grunnlagsdata =
                    opprettHentGrunnlagDto()
                        .copy(
                            skattegrunnlagListe = listOf(grunnlagLagret),
                        ).tilSummerteInntekter(behandling.bidragsmottaker!!),
            )

        val aktivereGrunnlagRequest =
            AktivereGrunnlagRequestV2(
                Personident(behandling.bidragsmottaker?.ident!!),
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
            )

        // hvis
        val respons =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/aktivere",
                HttpMethod.PUT,
                HttpEntity(
                    aktivereGrunnlagRequest,
                ),
                AktivereGrunnlagResponseV2::class.java,
            )

        Assertions.assertEquals(HttpStatus.OK, respons.statusCode)

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()

        assertSoftly {
            val oppdatertGrunnlag = oppdatertBehandling.grunnlag
            oppdatertGrunnlag.size shouldBe 2
            oppdatertGrunnlag.filter { it.aktiv == null }.shouldBeEmpty()
        }

        assertSoftly(oppdatertBehandling.inntekter.toList()) {
            this shouldHaveSize 2
            assertSoftly(find { it.type == Inntektsrapportering.LIGNINGSINNTEKT }!!) {
                type shouldBe Inntektsrapportering.LIGNINGSINNTEKT
                belop shouldBe 0.toBigDecimal()
                opprinneligFom shouldBe LocalDate.parse("2025-01-01")
                opprinneligTom shouldBe LocalDate.parse("2025-12-31")
                taMed shouldBe false
                datoFom shouldBe null
                datoTom shouldBe null
                inntektsposter shouldHaveSize 0
            }
            assertSoftly(find { it.type == Inntektsrapportering.KAPITALINNTEKT }!!) {
                type shouldBe Inntektsrapportering.KAPITALINNTEKT
                belop shouldBe 550000.toBigDecimal()
                opprinneligFom shouldBe LocalDate.parse("2025-01-01")
                opprinneligTom shouldBe LocalDate.parse("2025-12-31")
                taMed shouldBe false
                datoTom shouldBe null
                datoFom shouldBe null
                inntektsposter shouldHaveSize 2
            }
        }
    }

    @Test
    fun `skal slette behandling`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling()

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.DELETE,
                null,
                Unit::class.java,
            )

        Assertions.assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        // så
        val slettetBehandlingFraRepository =
            behandlingRepository.findBehandlingById(behandling.id!!)

        assertTrue(
            slettetBehandlingFraRepository.isEmpty,
            "Skal ikke finne behandling i repository etter sletting",
        )

        val sletteBehandling = testdataManager.hentBehandling(behandling.id!!)!!
        withClue("Skal logisk slettet hvor deleted parameter er true") {
            sletteBehandling.deleted shouldBe true
            sletteBehandling.slettetTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
        }
    }

    @Test
    fun `skal ikke slette behandling hvis vedtak er fattet`() {
        // gitt
        val behandling = testdataManager.oppretteBehandling()
        behandling.vedtaksid = 1
        behandlingRepository.save(behandling)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.DELETE,
                null,
                String::class.java,
            )

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, behandlingRes.statusCode)

        behandlingRes.headers
            .get("Warning")
            ?.first() shouldBe "Validering feilet - Kan ikke slette behandling hvor vedtak er fattet"
        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()

        assertNotNull(oppdatertBehandling)
        oppdatertBehandling.deleted shouldBe false
    }
}
