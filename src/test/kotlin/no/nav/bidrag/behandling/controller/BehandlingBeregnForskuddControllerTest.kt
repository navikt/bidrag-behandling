package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.beregning.ResultatForskuddsberegning
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.oppretteBehandlingRoller
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate

class BehandlingBeregnForskuddControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
    }

    @Test
    fun `skal beregne forskudd for validert behandling`() {
        // given
        val behandling = oppretteBehandling()
        behandling.roller = oppretteBehandlingRoller(behandling)
        val husstandsbarn =
            Husstandsbarn(
                behandling = behandling,
                medISaken = true,
                ident = behandling.getSøknadsbarn().first().ident,
                navn = "Lavransdottir",
                foedselsdato = LocalDate.now().minusMonths(140),
            )
        val husstandsbarnperiode =
            Husstandsbarnperiode(
                husstandsbarn = husstandsbarn,
                datoFom = LocalDate.now().minusMonths(5),
                datoTom = LocalDate.now().plusMonths(3),
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
            )
        val sivilstand =
            Sivilstand(
                sivilstand = Sivilstandskode.BOR_ALENE_MED_BARN,
                behandling = behandling,
                datoFom = LocalDate.now().minusMonths(12),
                datoTom = null,
                kilde = Kilde.OFFENTLIG,
            )
        val inntekter =
            Inntekt(
                belop = BigDecimal(1000),
                datoTom = null,
                datoFom = LocalDate.now().minusMonths(12),
                ident = behandling.getBidragsmottaker()!!.ident!!,
                taMed = true,
                fraGrunnlag = false,
                behandling = behandling,
                inntektstype = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
            )
        husstandsbarn.perioder = mutableSetOf(husstandsbarnperiode)
        behandling.husstandsbarn = mutableSetOf(husstandsbarn)
        behandling.inntekter = mutableSetOf(inntekter)
        behandling.sivilstand = mutableSetOf(sivilstand)
        behandlingRepository.save(behandling)

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ResultatForskuddsberegning::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.OK
            returnert.body shouldNotBe null
        }
    }

    @Test
    fun `skal returnere httpkode 400 dersom behandling mangler informasjon om husstandsbarn`() {
        // given
        var behandling = lagreBehandlingMedRoller()

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ResultatForskuddsberegning::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldBe null
            returnert.headers["Warning"]?.shouldBe(
                listOf(
                    "Sivilstand mangler i behandling",
                    "Mangler inntekter for bidragsmottaker",
                    "Husstandsbarn mangler",
                ),
            )
        }
    }

    @Test
    @Disabled
    fun `skal videreføre BAD_REQUEST fra bidrag-beregn-forskudd-rest`() {
        // given
        stubUtils.stubBeregneForskudd(HttpStatus.BAD_REQUEST)
        var behandling = lagreBehandlingMedRoller()

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ResultatForskuddsberegning::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldNotBe null
        }
    }

    private fun lagreBehandlingMedRoller(): Behandling {
        var behandling = oppretteBehandling()
        behandling.roller = oppretteBehandlingRoller(behandling)
        return behandlingRepository.save(behandling)
    }
}
