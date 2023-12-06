package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.beregning.Forskuddsberegningrespons
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.oppretteBehandlingRoller
import no.nav.bidrag.domene.enums.person.Bostatuskode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
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
        var behandling = oppretteBehandling()
        behandling.roller = oppretteBehandlingRoller(behandling)
        var husstandsbarn =
            Husstandsbarn(
                behandling = behandling,
                medISaken = true,
                ident = behandling.getSøknadsbarn().first().ident,
                navn = "Lavransdottir",
                foedselsdato = LocalDate.now().minusMonths(140),
            )
        var husstandsbarnperiode =
            Husstandsbarnperiode(
                husstandsbarn = husstandsbarn,
                datoFom = LocalDate.now().minusMonths(5),
                datoTom = LocalDate.now().plusMonths(3),
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
            )
        husstandsbarn.perioder = mutableSetOf(husstandsbarnperiode)
        behandling.husstandsbarn = mutableSetOf(husstandsbarn)
        behandlingRepository.save(behandling)

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                Forskuddsberegningrespons::class.java,
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
                Forskuddsberegningrespons::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldBe null
            returnert.headers["Warning"]?.get(0) shouldBe "Validering feilet - [Husstandsbarn mangler i behandling]"
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
                Forskuddsberegningrespons::class.java,
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
