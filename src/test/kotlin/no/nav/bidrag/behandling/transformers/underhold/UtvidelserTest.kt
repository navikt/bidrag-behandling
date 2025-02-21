package no.nav.bidrag.behandling.transformers.underhold

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldNotBeGreaterThan
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.utils.testdata.oppretteBarnetilsynGrunnlagDto
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import java.time.LocalDate
import kotlin.test.Test

class UtvidelserTest {
    @Test
    fun `barn som ikke har nådd skolealder ved inneværende år skal registreres som under skolealder`() {
        // gitt
        val b =
            oppretteTestbehandling(
                setteDatabaseider = true,
                inkludereBp = true,
                behandlingstype = TypeBehandling.BIDRAG,
            )

        val barnetilsynGrunnlagDto = oppretteBarnetilsynGrunnlagDto(b, periodeFraAntallMndTilbake = 13)

        val u = b.underholdskostnader.first()

        u.person.henteFødselsdato!!
            .plusYears(ALDER_VED_SKOLESTART)
            .year shouldBeGreaterThan LocalDate.now().year

        // hvis
        val barnetilsyn = barnetilsynGrunnlagDto.tilBarnetilsyn(u)

        // så
        barnetilsyn.under_skolealder shouldBe true
    }

    @Test
    fun `barn som når skolealder i inneværende år skal registreres som over skolealder`() {
        // gitt
        val b =
            oppretteTestbehandling(
                setteDatabaseider = true,
                inkludereBp = true,
                behandlingstype = TypeBehandling.BIDRAG,
            )

        val idUnderhold = 123L
        val fødselsdato = LocalDate.now().withDayOfYear(365).minusYears(6)
        val rolleBarnSomNårSkolealderIInneværendeÅr = Rolle(b, ident = null, rolletype = Rolletype.BARN, fødselsdato = fødselsdato)

        val personSøknadsbarn = Person(ident = "123", fødselsdato = fødselsdato, rolle = mutableSetOf(rolleBarnSomNårSkolealderIInneværendeÅr))
        b.underholdskostnader.add(
            Underholdskostnad(
                id = idUnderhold,
                behandling = b,
                person = personSøknadsbarn,
            ),
        )

        val u = b.underholdskostnader.first { it.id == idUnderhold }

        u.person.henteFødselsdato!!
            .plusYears(ALDER_VED_SKOLESTART)
            .year shouldNotBeGreaterThan LocalDate.now().year
        val barnetilsynGrunnlagDto = oppretteBarnetilsynGrunnlagDto(b, periodeFraAntallMndTilbake = 13)

        // hvis
        val barnetilsyn = barnetilsynGrunnlagDto.tilBarnetilsyn(u)

        // så
        barnetilsyn.under_skolealder shouldBe false
    }

    @Test
    fun `barn som når skolealder til neste år skal registreres som under skolealder`() {
        // gitt
        val b =
            oppretteTestbehandling(
                setteDatabaseider = true,
                inkludereBp = true,
                behandlingstype = TypeBehandling.BIDRAG,
            )

        val idUnderhold = 123L
        val fødselsdato = LocalDate.now().withDayOfYear(365).minusYears(5)
        val rolleBarnSomNårSkolealderIInneværendeÅr = Rolle(b, ident = null, rolletype = Rolletype.BARN, fødselsdato = fødselsdato)

        val personSøknadsbarn = Person(ident = "123", fødselsdato = LocalDate.now(), rolle = mutableSetOf(rolleBarnSomNårSkolealderIInneværendeÅr))
        b.underholdskostnader.add(
            Underholdskostnad(
                id = idUnderhold,
                behandling = b,
                person = personSøknadsbarn,
            ),
        )

        val u = b.underholdskostnader.first { it.id == idUnderhold }

        u.person.henteFødselsdato!!
            .plusYears(ALDER_VED_SKOLESTART)
            .year shouldBeGreaterThan LocalDate.now().year
        val barnetilsynGrunnlagDto = oppretteBarnetilsynGrunnlagDto(b, periodeFraAntallMndTilbake = 13)

        // hvis
        val barnetilsyn = barnetilsynGrunnlagDto.tilBarnetilsyn(u)

        // så
        barnetilsyn.under_skolealder shouldBe true
    }

    @Test
    fun `skal sette til og med dato til null hvis etter virkning`() {
        // gitt
        val b =
            oppretteTestbehandling(
                setteDatabaseider = true,
                inkludereBp = true,
                behandlingstype = TypeBehandling.BIDRAG,
            )

        val barnetilsynGrunnlagDto =
            oppretteBarnetilsynGrunnlagDto(b, periodeFraAntallMndTilbake = 13)
                .copy(periodeTil = LocalDate.now().plusMonths(2))

        val u = b.underholdskostnader.first()
        b.virkningstidspunkt = LocalDate.now().plusMonths(1).withDayOfMonth(1)

        // hvis
        val barnetilsyn = barnetilsynGrunnlagDto.tilBarnetilsyn(u)

        // så
        barnetilsyn.tom shouldBe null
    }

    @Test
    fun `skal sette til og med dato til null hvis etter dagens dato`() {
        // gitt
        val b =
            oppretteTestbehandling(
                setteDatabaseider = true,
                inkludereBp = true,
                behandlingstype = TypeBehandling.BIDRAG,
            )

        val barnetilsynGrunnlagDto =
            oppretteBarnetilsynGrunnlagDto(b, periodeFraAntallMndTilbake = 13)
                .copy(periodeTil = LocalDate.now().plusMonths(1))

        val u = b.underholdskostnader.first()
        b.virkningstidspunkt = LocalDate.now().minusMonths(3).withDayOfMonth(1)

        // hvis
        val barnetilsyn = barnetilsynGrunnlagDto.tilBarnetilsyn(u)

        // så
        barnetilsyn.tom shouldBe null
    }

    @Test
    fun `skal sette til og med dato hvis før virkning`() {
        // gitt
        val b =
            oppretteTestbehandling(
                setteDatabaseider = true,
                inkludereBp = true,
                behandlingstype = TypeBehandling.BIDRAG,
            )

        val barnetilsynGrunnlagDto =
            oppretteBarnetilsynGrunnlagDto(b, periodeFraAntallMndTilbake = 13)
                .copy(periodeTil = LocalDate.now().minusMonths(4).plusMonths(1))

        val u = b.underholdskostnader.first()
        b.virkningstidspunkt = LocalDate.now().minusMonths(3).withDayOfMonth(1)

        // hvis
        val barnetilsyn = barnetilsynGrunnlagDto.tilBarnetilsyn(u)

        // så
        barnetilsyn.tom shouldBe
            LocalDate
                .now()
                .minusMonths(4)
                .plusMonths(1)
                .minusDays(1)
    }
}
