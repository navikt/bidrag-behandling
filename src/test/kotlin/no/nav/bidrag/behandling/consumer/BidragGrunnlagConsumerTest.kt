package no.nav.bidrag.behandling.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BidragGrunnlagConsumerTest : TestContainerRunner() {
    @Autowired
    lateinit var bidragGrunnlagConsumer: BidragGrunnlagConsumer

    @BeforeEach
    internal fun oppsett() {
        MockKAnnotations.init(this)
        clearAllMocks()
    }

    @Test
    fun `skal hente grunnlag for behandling`() {
        // given
        val personidentBm = Personident("99057812345")
        val personidentBarn = Personident("12345678910")

        stubUtils.stubHenteGrunnlagOk(personidentBm, setOf(personidentBarn))

        // when
        val returnertGrunnlag =
            bidragGrunnlagConsumer.henteGrunnlagForBmOgBarnIBehandling(
                personidentBm,
                listOf(personidentBarn),
            )

        // then
        assertSoftly {
            returnertGrunnlag.arbeidsforholdListe.size shouldBe 3
            returnertGrunnlag.arbeidsforholdListe[0].partPersonId shouldBe personidentBm.verdi
        }
    }
}
