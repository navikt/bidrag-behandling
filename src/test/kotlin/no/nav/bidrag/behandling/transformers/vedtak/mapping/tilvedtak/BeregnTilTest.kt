package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagEtterfølgendeVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettPeriode
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import java.time.LocalDate
import kotlin.test.Test

class BeregnTilTest {
    @Test
    fun `Skal lage beregn til dato til neste måned for særbidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()

        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.beregnTil = BeregnTil.INNEVÆRENDE_MÅNED
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe søknadsbarn.virkningstidspunkt!!.plusMonths(1)
    }

    @Test
    fun `Skal lage beregn til dato til neste måned for forskudd`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.FORSKUDD)
        val søknadsbarn = behandling.søknadsbarn.first()

        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.beregnTil = BeregnTil.ETTERFØLGENDE_MANUELL_VEDTAK
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe LocalDate.now().plusMonths(1).withDayOfMonth(1)
    }

    @Test
    fun `Skal lage beregn til dato for OPPRINNELIG_VEDTAKSTIDSPUNKT til opprinnelig vedtakstidspunkt`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe LocalDate.parse("2025-02-01")
    }

    @Test
    fun `Skal lage beregn til dato for OPPRINNELIG_VEDTAKSTIDSPUNKT til opprinnelig vedtakstidspunkt med opphørsdato`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opphørsdato = LocalDate.parse("2024-12-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe søknadsbarn.opphørsdato
    }

    @Test
    fun `Skal lage beregn til dato for OPPRINNELIG_VEDTAKSTIDSPUNKT til opprinnelig vedtakstidspunkt med opphørsdato når opphør er senere`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opphørsdato = LocalDate.parse("2025-12-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe LocalDate.parse("2025-02-01")
    }

    @Test
    fun `Skal lage beregn til dato for OPPRINNELIG_VEDTAKSTIDSPUNKT til en måned etter virkning hvis virkning er etter opprinnelig`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-04-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe LocalDate.parse("2025-05-01")
    }

    @Test
    fun `Skal lage beregn til dato for OPPRINNELIG_VEDTAKSTIDSPUNKT til en måned etter virkning hvis virkning er etter opprinnelig med opphørsdato`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-04-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe LocalDate.parse("2025-05-01")
    }

    @Test
    fun `Skal lage beregn til dato for INNEVÆRENDE_MÅNED`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-04-01")
        søknadsbarn.beregnTil = BeregnTil.INNEVÆRENDE_MÅNED
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe LocalDate.now().plusMonths(1).withDayOfMonth(1)
    }

    @Test
    fun `Skal lage beregn til dato for INNEVÆRENDE_MÅNED med opphørsdato`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-04-01")
        søknadsbarn.beregnTil = BeregnTil.INNEVÆRENDE_MÅNED
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opphørsdato = LocalDate.parse("2025-05-01")
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe søknadsbarn.opphørsdato
    }

    @Test
    fun `Skal lage beregn til dato for ETTERFØLGENDE_MANUELL_VEDTAK`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-04-01")
        søknadsbarn.beregnTil = BeregnTil.ETTERFØLGENDE_MANUELL_VEDTAK
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2024-12-01").atStartOfDay()),
            )

        behandling.leggTilGrunnlagEtterfølgendeVedtak(
            listOf(
                opprettPeriode(ÅrMånedsperiode(LocalDate.parse("2025-02-01"), null)).copy(
                    valutakode = "NOK",
                ),
            ),
        )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe LocalDate.parse("2025-02-01")
    }

    @Test
    fun `Skal lage beregn til dato for ETTERFØLGENDE_MANUELL_VEDTAK med opphør`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-04-01")
        søknadsbarn.beregnTil = BeregnTil.ETTERFØLGENDE_MANUELL_VEDTAK
        søknadsbarn.opphørsdato = LocalDate.parse("2025-05-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                opprinneligVedtakId = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-07-01").atStartOfDay()),
            )

        behandling.leggTilGrunnlagEtterfølgendeVedtak(
            listOf(
                opprettPeriode(ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null)).copy(
                    valutakode = "NOK",
                ),
            ),
        )

        behandling.finnBeregnTilDatoBehandling(søknadsbarn) shouldBe søknadsbarn.opphørsdato
    }
}
