package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.transformers.utgift.tilUtgiftDto
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteUtgift
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import stubPersonConsumer
import stubSaksbehandlernavnProvider
import java.math.BigDecimal
import java.time.LocalDate

class BehandlingDtoMappingTest {
    @BeforeEach
    fun initMocks() {
        stubPersonConsumer()
        stubKodeverkProvider()
        stubSjablonProvider()
        stubSaksbehandlernavnProvider()
    }

    @Test
    fun `skal mappe BehandlingDto for SÆRBIDRAG behandling med utgifter`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.ANNET.name
        behandling.kategoriBeskrivelse = "Høreapparat"
        behandling.utgiftsbegrunnelseKunINotat = "Notat utgift"

        behandling.utgift = oppretteUtgift(behandling, "")
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal.valueOf(1000)
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.now().minusDays(4),
                    type = "Batteri",
                    kravbeløp = BigDecimal(100),
                    godkjentBeløp = BigDecimal(100),
                    begrunnelse = null,
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusDays(10),
                    type = "Ny mikrofon for skolelærer",
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    begrunnelse = "Tar ikke med fraktkostnader",
                    utgift = behandling.utgift!!,
                ),
            )

        val utgiftDto = behandling.tilUtgiftDto()

        assertSoftly(utgiftDto!!.beregning!!) {
            beløpDirekteBetaltAvBp shouldBe BigDecimal(1000)
            totalBeløpBetaltAvBp shouldBe BigDecimal(1000)
            totalGodkjentBeløp shouldBe BigDecimal(2100)
            totalGodkjentBeløpBp shouldBe null
        }
        assertSoftly(utgiftDto) {
            avslag shouldBe null
            kategori.kategori shouldBe Særbidragskategori.ANNET
            kategori.beskrivelse shouldBe "Høreapparat"
            notat.kunINotat shouldBe "Notat utgift"
            utgifter shouldHaveSize 2
            utgifter[0].type shouldBe "Ny mikrofon for skolelærer"
            utgifter[1].type shouldBe "Batteri"
        }
    }

    @Test
    fun `skal mappe BehandlingDto for SÆRBIDRAG behandling med utgifter og kategori KONFIRMASJON`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.utgiftsbegrunnelseKunINotat = "Notat utgift"

        behandling.utgift = oppretteUtgift(behandling, "")
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal.valueOf(1000)
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.now().minusDays(4),
                    type = Utgiftstype.KLÆR.name,
                    kravbeløp = BigDecimal(100),
                    godkjentBeløp = BigDecimal(100),
                    begrunnelse = null,
                    betaltAvBp = true,
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    betaltAvBp = true,
                    begrunnelse = "Inkluderer ikke alkohol",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusDays(10),
                    type = Utgiftstype.REISEUTGIFT.name,
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    begrunnelse = "Tar ikke med bompenger",
                    utgift = behandling.utgift!!,
                ),
            )

        val utgiftDto = behandling.tilUtgiftDto()

        assertSoftly(utgiftDto!!.beregning!!) {
            beløpDirekteBetaltAvBp shouldBe BigDecimal(1000)
            totalBeløpBetaltAvBp shouldBe BigDecimal(3100)
            totalGodkjentBeløp shouldBe BigDecimal(4100)
            totalGodkjentBeløpBp shouldBe BigDecimal(2100)
        }
        assertSoftly(utgiftDto) {
            avslag shouldBe null
            kategori.kategori shouldBe Særbidragskategori.KONFIRMASJON
            notat.kunINotat shouldBe "Notat utgift"
            utgifter shouldHaveSize 3
            utgifter[0].type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
            utgifter[1].type shouldBe Utgiftstype.REISEUTGIFT.name
            utgifter[2].type shouldBe Utgiftstype.KLÆR.name
        }
    }

    @Test
    fun `skal mappe BehandlingDto for SÆRBIDRAG med avslag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.avslag = Resultatkode.PRIVAT_AVTALE_OM_SÆRBIDRAG
        behandling.kategori = Særbidragskategori.ANNET.name
        behandling.kategoriBeskrivelse = "Høreapparat"
        behandling.utgiftsbegrunnelseKunINotat = "Notat utgift"

        behandling.utgift = oppretteUtgift(behandling, "")
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal.valueOf(1000)
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.now().minusDays(4),
                    type = "Batteri",
                    kravbeløp = BigDecimal(100),
                    godkjentBeløp = BigDecimal(100),
                    begrunnelse = null,
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusDays(10),
                    type = "Ny mikrofon for skolelærer",
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    begrunnelse = "Tar ikke med fraktkostnader",
                    utgift = behandling.utgift!!,
                ),
            )

        val utgiftDto = behandling.tilUtgiftDto()

        assertSoftly(utgiftDto!!) {
            beregning shouldBe null
            avslag shouldBe Resultatkode.PRIVAT_AVTALE_OM_SÆRBIDRAG
            kategori.kategori shouldBe Særbidragskategori.ANNET
            kategori.beskrivelse shouldBe "Høreapparat"
            notat.kunINotat shouldBe "Notat utgift"
            utgifter shouldHaveSize 0
        }
    }

    @Test
    fun `mappe behandlingDto for særbidrag  med andre voksne i husstanden`() {
        // gitt
        val behandling = oppretteBehandling(false, false, true, true, TypeBehandling.SÆRBIDRAG, true, true)

        // hvis
        val behandlingDto = behandling.tilBehandlingDtoV2(behandling.grunnlagListe.toSet().hentSisteAktiv())

        // så
        assertSoftly(behandlingDto) { b ->
            b.aktiveGrunnlagsdata.andreVoksneIHusstanden?.perioder?.shouldHaveSize(3)
        }
    }
}
