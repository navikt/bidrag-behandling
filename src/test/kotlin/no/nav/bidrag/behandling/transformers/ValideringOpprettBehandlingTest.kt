package no.nav.bidrag.behandling.transformers

import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettKategoriRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpStatusCodeException
import java.time.LocalDate

class ValideringOpprettBehandlingTest {
    private fun opprettOpprettBehandlingRequest() =
        OpprettBehandlingRequest(
            vedtakstype = Vedtakstype.FASTSETTELSE,
            engangsbeløpstype = Engangsbeløptype.SÆRBIDRAG,
            søktFomDato = LocalDate.now().minusMonths(4),
            mottattdato = LocalDate.now(),
            søknadFra = SøktAvType.BIDRAGSMOTTAKER,
            saksnummer = SAKSNUMMER,
            behandlerenhet = "4806",
            roller =
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident(testdataBarn1.ident),
                        fødselsdato = LocalDate.now().minusMonths(136),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSMOTTAKER,
                        Personident(testdataBM.ident),
                        fødselsdato = LocalDate.now().minusMonths(555),
                    ),
                    OpprettRolleDto(
                        Rolletype.BIDRAGSPLIKTIG,
                        Personident(testdataBP.ident),
                        fødselsdato = LocalDate.now().minusMonths(555),
                    ),
                ),
            søknadsid = 123213,
        )

    @Test
    fun `Skal BP er satt for særbidrag`() {
        val request =
            opprettOpprettBehandlingRequest().copy(
                kategori =
                    OpprettKategoriRequestDto(
                        kategori = Særbidragskategori.KONFIRMASJON.name,
                        beskrivelse = null,
                    ),
                roller =
                    setOf(
                        OpprettRolleDto(
                            Rolletype.BARN,
                            Personident(testdataBarn1.ident),
                            fødselsdato = LocalDate.now().minusMonths(136),
                        ),
                        OpprettRolleDto(
                            Rolletype.BIDRAGSMOTTAKER,
                            Personident(testdataBM.ident),
                            fødselsdato = LocalDate.now().minusMonths(555),
                        ),
                    ),
            )

        val exception = assertThrows<HttpStatusCodeException> { request.valider() }
        exception.message shouldContain "Behandling av typen SÆRBIDRAG må ha en rolle av typen BIDRAGSPLIKTIG"
    }

    @Test
    fun `Skal validere at kategorien er satt for særbidrag`() {
        val request =
            opprettOpprettBehandlingRequest().copy(
                kategori = null,
            )

        val exception = assertThrows<HttpStatusCodeException> { request.valider() }
        exception.message shouldContain "Kategori må settes for SÆRBIDRAG"
    }

    @Test
    fun `Skal validere at kategori beskrivelse er satt for kataegori ANNET`() {
        val request =
            opprettOpprettBehandlingRequest().copy(
                kategori =
                    OpprettKategoriRequestDto(
                        kategori = Særbidragskategori.ANNET.name,
                        beskrivelse = null,
                    ),
            )

        val exception = assertThrows<HttpStatusCodeException> { request.valider() }
        exception.message shouldContain "Beskrivelse må settes hvis kategori er ANNET"
    }

    @Test
    fun `Skal validere at kategori er ugyldig`() {
        val request =
            opprettOpprettBehandlingRequest().copy(
                kategori =
                    OpprettKategoriRequestDto(
                        kategori = "ADasdsad",
                        beskrivelse = null,
                    ),
            )

        val exception = assertThrows<HttpStatusCodeException> { request.valider() }
        exception.message shouldContain "Kategori ADasdsad er ikke en gyldig særbidrag kategori"
    }
}
