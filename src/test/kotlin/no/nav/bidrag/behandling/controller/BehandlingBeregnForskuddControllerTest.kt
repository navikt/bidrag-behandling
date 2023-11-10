package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarnPeriode
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.beregning.ForskuddBeregningRespons
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.oppretteBehandlingRoller
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.*

class BehandlingBeregnForskuddControllerTest : KontrollerTestRunner() {

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
    }


    @Test
    fun `skal beregne forskudd for behandling`() {

        // given
        var behandling = lagreBehandlingMedRoller()

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ForskuddBeregningRespons::class.java,)

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.OK
            returnert.body shouldNotBe null
        }
    }


    private fun lagreBehandlingMedRoller(): Behandling {
        var behandling = oppretteBehandling()
        behandling.roller = oppretteBehandlingRoller(behandling)
        return behandlingRepository.save(behandling)
    }

    private fun prepareHusstandsBarnPeriode(
        b: HusstandsBarn,
        fraDato: Date,
        tilDao: Date,
    ) = HusstandsBarnPeriode(
        b,
        fraDato,
        tilDao,
        BoStatusType.REGISTRERT_PA_ADRESSE,
        "",
    )

}
