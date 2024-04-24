package no.nav.bidrag.behandling.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class BidragGrunnlagConsumerTest : TestContainerRunner() {
    @Autowired
    lateinit var bidragGrunnlagConsumer: BidragGrunnlagConsumer

    @Autowired
    lateinit var testdataManager: TestdataManager

    @BeforeEach
    internal fun oppsett() {
        MockKAnnotations.init(this)
        clearAllMocks()
    }

    @Test
    fun `skal hente grunnlag for behandling`() {
        // given
        val personidentBmFraStub = Personident("99057812345")
        val behandling = testdataManager.opprettBehandling(false)
        val grunnlagRequestobjekter = bidragGrunnlagConsumer.henteGrunnlagRequestobjekterForBehandling(behandling)

        stubUtils.stubHenteGrunnlagOk()

        grunnlagRequestobjekter.forEach {
            // when
            val returnertGrunnlag = bidragGrunnlagConsumer.henteGrunnlag(it.value)

            // then
            assertSoftly {
                returnertGrunnlag.arbeidsforholdListe.size shouldBe 3
                returnertGrunnlag.arbeidsforholdListe[0].partPersonId shouldBe personidentBmFraStub.verdi
            }
        }
    }

    @Test
    fun `skal angi riktig periode i requestobjekter for henting av grunnlag`() {
        // gitt
        val behandling = testdataManager.opprettBehandling(false)

        // hvis
        val grunnlagRequest = bidragGrunnlagConsumer.henteGrunnlagRequestobjekterForBehandling(behandling)

        // så
        val periodeFra =
            setOf(
                behandling.virkningstidspunktEllerSøktFomDato,
                LocalDate.now(),
            ).min()

        assertSoftly {
            grunnlagRequest.entries.forEach {
                it.value.forEach { request ->
                    request.periodeTil shouldBe LocalDate.now().plusDays(1)
                    if (GrunnlagRequestType.AINNTEKT == request.type) {
                        request.periodeFra shouldBe periodeFra.minusYears(1).withMonth(1).withDayOfMonth(1)
                    } else if (GrunnlagRequestType.SKATTEGRUNNLAG == request.type) {
                        request.periodeFra shouldBe periodeFra.minusYears(3).withMonth(1).withDayOfMonth(1)
                    } else {
                        request.periodeFra shouldBe periodeFra
                    }
                }
            }
        }
    }
}
