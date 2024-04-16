package no.nav.bidrag.behandling.controller.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.service.opprettHentGrunnlagDto
import no.nav.bidrag.behandling.service.tilSummerInntekt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagspostDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `skal oppdatere behandling for API v2`() {
        // gitt
        val b = testdataManager.opprettBehandling(true)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(
                    OppdaterBehandlingRequestV2(
                        virkningstidspunkt =
                            OppdaterVirkningstidspunkt(
                                virkningstidspunkt = LocalDate.parse("2024-03-02"),
                            ),
                    ),
                ),
                BehandlingDtoV2::class.java,
            )
        Assertions.assertEquals(HttpStatus.CREATED, behandlingRes.statusCode)
        val responseBody = behandlingRes.body!!
        responseBody.inntekter.beregnetInntekter shouldHaveSize 3
        responseBody.virkningstidspunkt.virkningstidspunkt shouldBe LocalDate.parse("2024-03-02")

        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(b.id!!)

        assertNotNull(oppdatertBehandling)
        oppdatertBehandling.get().virkningstidspunkt shouldBe LocalDate.parse("2024-03-02")
    }

    @Test
    @Transactional
    fun `skal aktivere grunnlag`() {
        // gitt
        var behandling = testdataManager.opprettBehandlingNewTransacion(false)
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
                periodeFra = YearMonth.now().minusYears(1).withMonth(1).atDay(1),
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
            testdataManager.oppretteOgLagreGrunnlagNewTransaction(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, false),
                innhentingstidspunkt,
                grunnlagsdata = SkattepliktigeInntekter(skattegrunnlag = listOf(grunnlagLagret)),
            )
        behandling =
            testdataManager.oppretteOgLagreGrunnlagNewTransaction(
                behandling,
                Grunnlagstype(Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER, true),
                innhentingstidspunkt,
                grunnlagsdata =
                    opprettHentGrunnlagDto().copy(
                        skattegrunnlagListe = listOf(grunnlagLagret),
                    ).tilSummerInntekt(behandling),
            )

        val aktivereGrunnlagRequest =
            AktivereGrunnlagRequestV2(
                Personident(behandling.bidragsmottaker?.ident!!),
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
            )

        // hvis
        val respons =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/grunnlag/aktiver",
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
                opprinneligFom shouldBe LocalDate.parse("2023-01-01")
                opprinneligTom shouldBe LocalDate.parse("2023-12-31")
                taMed shouldBe true
                datoFom shouldBe LocalDate.parse("2023-01-01")
                datoTom shouldBe LocalDate.parse("2023-12-31")
                inntektsposter shouldHaveSize 0
            }
            assertSoftly(find { it.type == Inntektsrapportering.KAPITALINNTEKT }!!) {
                type shouldBe Inntektsrapportering.KAPITALINNTEKT
                belop shouldBe 550000.toBigDecimal()
                opprinneligFom shouldBe LocalDate.parse("2023-01-01")
                opprinneligTom shouldBe LocalDate.parse("2023-12-31")
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
        val behandling = testdataManager.opprettBehandling()

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
        val behandling = testdataManager.opprettBehandling()
        behandling.vedtaksid = 1
        behandlingRepository.save(behandling)

        // hvis
        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/" + behandling.id,
                HttpMethod.DELETE,
                null,
                Unit::class.java,
            )

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, behandlingRes.statusCode)

        behandlingRes.headers.get("Warning")
            ?.first() shouldBe "Validering feilet - Kan ikke slette behandling hvor vedtak er fattet"
        // så
        val oppdatertBehandling = behandlingRepository.findBehandlingById(behandling.id!!).get()

        assertNotNull(oppdatertBehandling)
        oppdatertBehandling.deleted shouldBe false
    }
}
