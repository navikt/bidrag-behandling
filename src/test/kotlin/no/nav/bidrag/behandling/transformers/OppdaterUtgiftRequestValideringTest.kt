package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import org.junit.jupiter.api.Test
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate

class OppdaterUtgiftRequestValideringTest {
    fun opprettBehandlingSærligeUtgifter(): Behandling {
        val behandling = oppretteBehandling(1)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        return behandling
    }

    @Test
    fun `skal ikke kunne oppdatere utgift for behandling som ikke er særbidrag`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.engangsbeloptype = null
        behandling.stonadstype = Stønadstype.FORSKUDD
        val request =
            OppdatereUtgiftRequest()

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain
            "Kan ikke oppdatere utgift for behandling som ikke er av typen SÆRBIDRAG"
    }

    @Test
    fun `skal validere at utgiftstype er gyldig for konfirmasjon`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = Utgiftstype.OPTIKK.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Type OPTIKK er ikke gyldig for behandling med kategori KONFIRMASJON"
    }

    @Test
    fun `skal kunne sette utgiftstype for kategori KONFIRMASJON`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        shouldNotThrow<HttpClientErrorException> { request.valider(behandling) }
    }

    @Test
    fun `skal kunne sette utgiftstype for kategori ANNET`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.ANNET.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = "Utgift batteri",
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        shouldNotThrow<HttpClientErrorException> { request.valider(behandling) }
    }

    @Test
    fun `skal validere at utgiftstype er ikke kan settes for kategori OPTIKK`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.OPTIKK.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = Utgiftstype.OPTIKK.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Type kan ikke settes hvis behandling har kategori OPTIKK"
    }

    @Test
    fun `skal validere at utgiftstype er ikke kan settes for kategori TANNREGULERING`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.TANNREGULERING.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = Utgiftstype.TANNREGULERING.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Type kan ikke settes hvis behandling har kategori TANNREGULERING"
    }

    @Test
    fun `skal ikke kunne oppdatere utgift hvis avslag er satt`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.avslag = Resultatkode.PRIVAT_AVTALE_OM_SÆRBIDRAG
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.parse("2021-01-01"),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Kan ikke oppdatere eller opprette utgift hvis avslag er satt"
    }

    @Test
    fun `skal ikke kunne legge til utgift med dato senere enn dagens dato`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().plusDays(1),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Dato for utgift kan ikke være senere enn eller lik dagens dato"
    }

    @Test
    fun `skal ikke kunne legge til utgift med dato lik dagens dato`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now(),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Dato for utgift kan ikke være senere enn eller lik dagens dato"
    }

    @Test
    fun `skal ikke kunne oppdatere utgift med godkjent beløp høyere enn kravbeløp`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.parse("2021-01-01"),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(2500),
                        begrunnelse = "Test",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Godkjent beløp kan ikke være høyere enn kravbeløp"
    }

    @Test
    fun `skal ikke kunne oppdatere utgift med ulik kravbeløp og godkjent beløp uten begrunnelse`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Begrunnelse må settes hvis kravbeløp er ulik godkjent beløp"
    }

    @Test
    fun `skal ikke kunne legge til utgift for bp hvis kategori ikke er konfirmasjon`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.OPTIKK.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        betaltAvBp = true,
                        begrunnelse = "Begrunnelse",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain
            "Kan ikke legge til utgift betalt av BP for særbidrag behandling som ikke har kategori KONFIRMASJON"
    }

    @Test
    fun `skal ikke kunne oppdatere utgift som ikke finnes`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.parse("2021-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        behandling.kategori = Særbidragskategori.OPTIKK.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        id = 2,
                        dato = LocalDate.parse("2022-01-01"),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "asd",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Utgiftspost med id 2 finnes ikke i behandling 1"
    }

    @Test
    fun `skal ikke kunne legge til utgift med beløp hvis foreldet`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.mottattdato = LocalDate.parse("2024-01-01")
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.parse("2022-01-01"),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Begrunnelse",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Godkjent beløp må være 0 når dato på utgiften er 1 år etter mottatt dato"
    }

    @Test
    fun `skal ikke kunne legge til utgift med beløp hvis foreldet basert på opprinnelig mottatt dato`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.opprinneligMottattdato = LocalDate.parse("2024-01-01")
        behandling.mottattdato = LocalDate.parse("2022-05-05")
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.parse("2022-01-01"),
                        type = Utgiftstype.KONFIRMASJONSLEIR.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Begrunnelse",
                    ),
            )

        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Godkjent beløp må være 0 når dato på utgiften er 1 år etter mottatt dato"
    }

    @Test
    fun `skal validere at utgiftstype er satt for kategori ANNET`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.ANNET.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = "",
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "asdsad",
                    ),
            )
        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Type må settes hvis behandling har kategori ANNET"
    }

    @Test
    fun `skal validere at utgiftstype er satt for kategori KONFIRMASJON`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        val request =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(2),
                        type = "",
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "asdsad",
                    ),
            )
        val exception = shouldThrow<HttpClientErrorException> { request.valider(behandling) }

        exception.message shouldContain "Type må settes hvis behandling har kategori KONFIRMASJON"
    }
}
