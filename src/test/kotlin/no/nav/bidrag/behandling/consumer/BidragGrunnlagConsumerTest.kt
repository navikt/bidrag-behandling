package no.nav.bidrag.behandling.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

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
}
