package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.Test

class BehandlingDtoMappingMockTest {
    @Test
    fun `skal mappe notattittel annet`() {
        val behandling = oppretteBehandling()
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.ANNET.name
        behandling.kategoriBeskrivelse = "Høreapparat"

        behandling.notatTittel() shouldBe "Særbidrag Høreapparat, Saksbehandlingsnotat"
    }

    @Test
    fun `skal mappe notattittel konfirmasjon`() {
        val behandling = oppretteBehandling()
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name

        behandling.notatTittel() shouldBe "Særbidrag konfirmasjon, Saksbehandlingsnotat"
    }

    @Test
    fun `skal mappe notattittel tannregulering`() {
        val behandling = oppretteBehandling()
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.TANNREGULERING.name

        behandling.notatTittel() shouldBe "Særbidrag tannregulering, Saksbehandlingsnotat"
    }

    @Test
    fun `skal mappe notattittel optikk`() {
        val behandling = oppretteBehandling()
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.OPTIKK.name

        behandling.notatTittel() shouldBe "Særbidrag optikk, Saksbehandlingsnotat"
    }
}
