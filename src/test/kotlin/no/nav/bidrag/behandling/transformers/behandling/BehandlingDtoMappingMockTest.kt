package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.transformers.skalInnkrevingKunneUtsettes
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.junit.jupiter.api.Test

class BehandlingDtoMappingMockTest {
    @Test
    fun `skal kunne utsette innkreving`() {
        val behandling = oppretteBehandling()
        behandling.vedtakstype = Vedtakstype.ENDRING
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.skalInnkrevingKunneUtsettes() shouldBe true

        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.skalInnkrevingKunneUtsettes() shouldBe true

        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.stonadstype = Stønadstype.FORSKUDD
        behandling.skalInnkrevingKunneUtsettes() shouldBe false

        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.stonadstype = null
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.skalInnkrevingKunneUtsettes() shouldBe false

        behandling.vedtakstype = Vedtakstype.ALDERSJUSTERING
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.skalInnkrevingKunneUtsettes() shouldBe false

        behandling.vedtakstype = Vedtakstype.OPPHØR
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.skalInnkrevingKunneUtsettes() shouldBe false

        behandling.vedtakstype = Vedtakstype.ENDRING
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.omgjøringsdetaljer = Omgjøringsdetaljer(omgjørVedtakId = 1)
        behandling.skalInnkrevingKunneUtsettes() shouldBe false

        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.omgjøringsdetaljer = Omgjøringsdetaljer(omgjørVedtakId = 1)
        behandling.skalInnkrevingKunneUtsettes() shouldBe false

        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.omgjøringsdetaljer = null
        behandling.skalInnkrevingKunneUtsettes() shouldBe false
    }

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
