package no.nav.bidrag.behandling.transformers.vedtak

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.ident.ReellMottaker
import no.nav.bidrag.transport.sak.ReellMottakerDto
import no.nav.bidrag.transport.sak.RolleDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StønadsendringBidragsmottakerTest {
    @BeforeEach
    fun setup() {
        mockkStatic("no.nav.bidrag.behandling.service.PersonServiceKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `skal returnere reell mottaker når den finnes`() {
        val reellMottakerIdent = "12345678910"
        val rolleDto =
            RolleDto(
                fødselsnummer = Personident("11111111111"),
                type = Rolletype.BARN,
                reellMottaker = ReellMottakerDto(ReellMottaker(reellMottakerIdent)),
            )

        val roller = setOf<Rolle>()

        val result = roller.reelMottakerEllerBidragsmottaker(rolleDto)

        result shouldBe Personident(reellMottakerIdent)
    }

    @Test
    fun `skal returnere bidragsmottaker når reell mottaker ikke finnes`() {
        val bidragsmottakerIdent = "22222222222"
        val nyesteIdent = Personident("33333333333")

        val rolleDto =
            RolleDto(
                fødselsnummer = Personident("11111111111"),
                type = Rolletype.BARN,
                reellMottaker = null,
            )

        val bidragsmottaker =
            Rolle(
                ident = bidragsmottakerIdent,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = oppretteBehandling(),
                fødselsdato = LocalDate.of(1990, 1, 1),
            )

        val roller = setOf(bidragsmottaker)

        every { hentNyesteIdent(bidragsmottakerIdent) } returns nyesteIdent

        val result = roller.reelMottakerEllerBidragsmottaker(rolleDto)

        result shouldBe nyesteIdent
    }

    @Test
    fun `skal returnere oppdatert ident for bidragsmottaker`() {
        val gammelIdent = "44444444444"
        val nyIdent = Personident("55555555555")

        val rolleDto =
            RolleDto(
                fødselsnummer = Personident("11111111111"),
                type = Rolletype.BARN,
                reellMottaker = null,
            )

        val bidragsmottaker =
            Rolle(
                ident = gammelIdent,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = oppretteBehandling(),
                fødselsdato = LocalDate.of(1990, 1, 1),
            )

        val roller = setOf(bidragsmottaker)

        every { hentNyesteIdent(gammelIdent) } returns nyIdent

        val result = roller.reelMottakerEllerBidragsmottaker(rolleDto)

        result shouldBe nyIdent
    }

    @Test
    fun `skal håndtere reell mottaker med verdi selv når det finnes bidragsmottaker`() {
        val reellMottakerIdent = "66666666666"
        val bidragsmottakerIdent = "77777777777"

        val rolleDto =
            RolleDto(
                fødselsnummer = Personident("11111111111"),
                type = Rolletype.BARN,
                reellMottaker =
                    ReellMottakerDto(
                        ident = ReellMottaker(reellMottakerIdent),
                    ),
            )

        val bidragsmottaker =
            Rolle(
                ident = bidragsmottakerIdent,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = oppretteBehandling(),
                fødselsdato = LocalDate.of(1990, 1, 1),
            )

        val roller = setOf(bidragsmottaker)

        val result = roller.reelMottakerEllerBidragsmottaker(rolleDto)

        result shouldBe Personident(reellMottakerIdent)
    }

    @Test
    fun `skal returnere bidragsmottaker når det finnes flere roller`() {
        val bidragsmottakerIdent = "88888888888"
        val nyesteIdent = Personident("99999999999")

        val rolleDto =
            RolleDto(
                fødselsnummer = Personident("11111111111"),
                type = Rolletype.BARN,
                reellMottaker = null,
            )

        val bidragsmottaker =
            Rolle(
                ident = bidragsmottakerIdent,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = oppretteBehandling(),
                fødselsdato = LocalDate.of(1990, 1, 1),
            )

        val bidragspliktig =
            Rolle(
                ident = "10101010101",
                rolletype = Rolletype.BIDRAGSPLIKTIG,
                behandling = oppretteBehandling(),
                fødselsdato = LocalDate.of(1985, 5, 15),
            )

        val barn =
            Rolle(
                ident = "20202020202",
                rolletype = Rolletype.BARN,
                behandling = oppretteBehandling(),
                fødselsdato = LocalDate.of(2010, 3, 20),
            )

        val roller = setOf(bidragsmottaker, bidragspliktig, barn)

        every { hentNyesteIdent(bidragsmottakerIdent) } returns nyesteIdent

        val result = roller.reelMottakerEllerBidragsmottaker(rolleDto)

        result shouldBe nyesteIdent
    }

    @Test
    fun `skal returnere riktig bidragsmottaker når det finnes flere roller`() {
        val bidragsmottakerIdent = "88888888888"
        val bidragsmottakerIdent2 = "882888888888"

        val behandling = oppretteBehandling()
        val bidragsmottaker =
            Rolle(
                ident = bidragsmottakerIdent,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = behandling,
                fødselsdato = LocalDate.of(1990, 1, 1),
            )
        val bidragsmottaker2 =
            Rolle(
                ident = bidragsmottakerIdent2,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = behandling,
                fødselsdato = LocalDate.of(1990, 1, 1),
            )

        val bidragspliktig =
            Rolle(
                ident = "10101010101",
                rolletype = Rolletype.BIDRAGSPLIKTIG,
                behandling = behandling,
                fødselsdato = LocalDate.of(1985, 5, 15),
            )

        val barn =
            Rolle(
                ident = "20202020202",
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = LocalDate.of(2010, 3, 20),
                forholdsmessigFordeling =
                    ForholdsmessigFordelingRolle(
                        tilhørerSak = "123",
                        behandlerenhet = "13",
                        delAvOpprinneligBehandling = true,
                        erRevurdering = false,
                        bidragsmottaker = bidragsmottakerIdent,
                    ),
            )
        val barn2 =
            Rolle(
                ident = "2020202330202",
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = LocalDate.of(2010, 3, 20),
                forholdsmessigFordeling =
                    ForholdsmessigFordelingRolle(
                        tilhørerSak = "123",
                        behandlerenhet = "13",
                        delAvOpprinneligBehandling = true,
                        erRevurdering = false,
                        bidragsmottaker = bidragsmottakerIdent2,
                    ),
            )
        val roller = setOf(bidragsmottaker, bidragspliktig, barn, bidragsmottaker2, barn2)
        behandling.roller = roller.toMutableSet()
        every { hentNyesteIdent(bidragsmottakerIdent) } returns Personident(bidragsmottakerIdent)
        every { hentNyesteIdent(bidragsmottakerIdent2) } returns Personident(bidragsmottakerIdent2)
        val rolleDto =
            RolleDto(
                fødselsnummer = Personident("20202020202"),
                type = Rolletype.BARN,
                reellMottaker = null,
            )
        val rolleDto2 =
            RolleDto(
                fødselsnummer = Personident("2020202330202"),
                type = Rolletype.BARN,
                reellMottaker = null,
            )
        roller.reelMottakerEllerBidragsmottaker(rolleDto).verdi shouldBe bidragsmottakerIdent
        roller.reelMottakerEllerBidragsmottaker(rolleDto2).verdi shouldBe bidragsmottakerIdent2
    }
}
