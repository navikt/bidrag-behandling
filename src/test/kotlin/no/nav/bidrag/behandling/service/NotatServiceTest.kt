package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

class NotatServiceTest {
    private val notatService = NotatService()

    @Nested
    open inner class OppdatereBegrunnelse {
        @Test
        fun `skal opprette nytt notat`() {
            // gitt
            val innhold = "Notattekst"
            val behandling = oppretteTestbehandling(true, setteDatabaseider = true)

            // hvis
            notatService.oppdatereNotat(behandling, Notattype.BOFORHOLD, innhold, behandling.bidragsmottaker!!.id!!)

            // så
            assertSoftly(behandling.notater) { n ->
                n shouldHaveSize 1
                n.first().type shouldBe Notattype.BOFORHOLD
                n.first().rolle.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
                n.first().innhold shouldBe innhold
                n.first().sistOppdatert.toLocalDate() shouldBe LocalDate.now()
            }
        }

        @Test
        fun `skal oppdatere eksisterende notat`() {
            // gitt
            val eksisterendeInnhold = "Eksisterende notattekst"
            val nyttInnhold = "Ny nottattekst"
            val behandling = oppretteTestbehandling(true, setteDatabaseider = true)
            val rolle = behandling.bidragsmottaker!!
            val notattype = Notattype.UTGIFTER

            behandling.notater.add(
                Notat(
                    behandling = behandling,
                    innhold = eksisterendeInnhold,
                    rolle = rolle,
                    type = notattype,
                ),
            )

            behandling.notater shouldHaveSize 1

            // hvis
            notatService.oppdatereNotat(behandling, notattype, nyttInnhold, rolle.id!!)

            // så
            assertSoftly(behandling.notater) { n ->
                n shouldHaveSize 1
                n.first().type shouldBe notattype
                n.first().rolle.rolletype shouldBe rolle.rolletype
                n.first().innhold shouldBe nyttInnhold
                n.first().sistOppdatert.toLocalDate() shouldBe LocalDate.now()
            }
        }

        @Test
        fun `skal ikke tillate oppdatering av notat for rolle som ikke er med i behandlingen`() {
            // gitt
            val innhold = "Notattekst"
            val behandling = oppretteTestbehandling(true, setteDatabaseider = true)
            val idTilRolleidSomIkkeFinnesIBehandling = 100L

            // hvis, så
            assertThrows(HttpClientErrorException::class.java) {
                notatService.oppdatereNotat(
                    behandling,
                    Notattype.BOFORHOLD,
                    innhold,
                    idTilRolleidSomIkkeFinnesIBehandling,
                )
            }
        }
    }
}
