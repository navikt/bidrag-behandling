package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.minified.BehandlingSimple
import no.nav.bidrag.behandling.database.datamodell.minified.RolleSimple
import no.nav.bidrag.behandling.service.hentAlleSaker
import no.nav.bidrag.behandling.service.hentLøpendeBidrag
import no.nav.bidrag.behandling.service.hentSak
import no.nav.bidrag.behandling.utils.disableUnleashFeature
import no.nav.bidrag.behandling.utils.enableUnleashFeature
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.commons.unleash.UnleashFeaturesProvider
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.sak.Bidragssakstatus
import no.nav.bidrag.domene.enums.sak.Sakskategori
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssakerResponse
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class KanFatteVedtakTest {
    @BeforeEach
    fun setup() {
        mockkObject(UnleashFeaturesProvider)
        mockkStatic("no.nav.bidrag.behandling.service.SakServiceKt")
        mockkStatic("no.nav.bidrag.behandling.service.PersonServiceKt")

        disableUnleashFeature(UnleashFeatures.BEHANDLE_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_SAKER)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_UTENLANDSK_VALUTA)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_OPPFOSTRINGSBIDRAG)

        every { hentAlleSaker(any()) } returns emptyList()
        every { hentSak(any()) } returns opprettSak(testdataBarn1.ident)
        every { hentLøpendeBidrag(any()) } returns opprettLøpendeBidragssakerResponse(opprettLøpendeBidragssak(SAKSNUMMER, NORSK_VALUTA))
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `skal kunne fatte vedtak for behandling som ikke er bidrag`() {
        opprettBehandlingSimple(stønadstype = Stønadstype.FORSKUDD, medBidragspliktig = false, barn = emptyList())
            .kanFatteVedtakBegrunnelse() shouldBe null
    }

    @Test
    fun `skal kunne fatte vedtak for aldersjustering`() {
        opprettBehandlingSimple(vedtakstype = Vedtakstype.ALDERSJUSTERING)
            .kanFatteVedtakBegrunnelse() shouldBe null
    }

    @Test
    fun `skal kunne fatte vedtak for innkreving`() {
        opprettBehandlingSimple(vedtakstype = Vedtakstype.INNKREVING)
            .kanFatteVedtakBegrunnelse() shouldBe null
    }

    @Test
    fun `skal kunne fatte vedtak for opphør`() {
        opprettBehandlingSimple(vedtakstype = Vedtakstype.OPPHØR)
            .kanFatteVedtakBegrunnelse() shouldBe null
    }

    @Test
    fun `skal ikke kunne fatte vedtak for bidrag uten bidragspliktig`() {
        opprettBehandlingSimple(
            stønadstype = Stønadstype.BIDRAG,
            medBidragspliktig = false,
            barn = listOf(testdataBarn1.ident to VIRKNINGSTIDSPUNKT),
        ).kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_MANGLER_BIDRAGSPLIKTIG
    }

    @Test
    fun `skal ikke kunne fatte vedtak når søknadsbarn har ulike virkningstidspunkt`() {
        val behandling =
            opprettBehandlingSimple(
                barn =
                    listOf(
                        testdataBarn1.ident to VIRKNINGSTIDSPUNKT,
                        testdataBarn2.ident to ANNEN_VIRKNINGSTIDSPUNKT,
                    ),
            )

        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_ULIKE_VIRKNINGSTIDSPUNKT
    }

    @Test
    fun `skal ikke kunne fatte vedtak for behandling som mangler barn i saken`() {
        val behandling = opprettBehandlingSimple()

        disableUnleashFeature(UnleashFeatures.BEHANDLE_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        every { hentSak(SAKSNUMMER) } returns opprettSak()

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_MANGLER_BARN_I_SAKEN
    }

    @Test
    fun `skal ikke kunne fatte vedtak for behandling som mangler barn i saken men skal kunne behandle`() {
        val behandling = opprettBehandlingSimple()

        enableUnleashFeature(UnleashFeatures.BEHANDLE_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        every { hentSak(SAKSNUMMER) } returns opprettSak()

        behandling.kanFatteVedtakBegrunnelse(true) shouldBe null
        behandling.kanFatteVedtakBegrunnelse(false) shouldBe BEGRUNNELSE_MANGLER_BARN_I_SAKEN
    }

    @Test
    fun `skal ikke fatte vedtak når ikke alle barn saken er inkludert i behandlingen`() {
        val behandling = opprettBehandlingSimple()

        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        every { hentSak(SAKSNUMMER) } returns null

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_MANGLER_BARN_I_SAKEN
    }

    @Test
    fun `skal ikke kunne fatte vedtak når behandlingen har privat avtale for andre barn`() {
        val behandling =
            opprettBehandlingSimple(
                harPrivatAvtaleAndreBarn = true,
            )

        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        every { hentSak(SAKSNUMMER) } returns opprettSak(testdataBarn1.ident)

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_PRIVAT_AVTALE_ANDRE_BARN
    }

    @Test
    fun `skal ikke kunne fatte vedtak når BP har flere saker`() {
        val behandling = opprettBehandlingSimple()

        enableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_SAKER)
        every {
            hentAlleSaker(testdataBP.ident)
        } returns
            listOf(
                opprettSak(testdataBarn1.ident, testdataBarn2.ident, saksnummer = ANDRE_SAKSNUMMER),
            )
        every { hentLøpendeBidrag(any()) } returns
            opprettLøpendeBidragssakerResponse(
                opprettLøpendeBidragssak(SAKSNUMMER, NORSK_VALUTA),
                opprettLøpendeBidragssak(ANDRE_SAKSNUMMER, NORSK_VALUTA),
            )

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_FLERE_SAKER
    }

    @Test
    fun `skal ikke kunne fatte vedtak når det finnes privat avtale for barn i andre saker`() {
        val behandling =
            opprettBehandlingSimple(
                privatAvtaleAndreBarnIdenter = listOf(testdataBarn1.ident),
            )

        enableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_SAKER)
        every {
            hentAlleSaker(testdataBP.ident)
        } returns
            listOf(
                opprettSak(testdataBarn1.ident, saksnummer = ANDRE_SAKSNUMMER),
            )
        every { hentLøpendeBidrag(any()) } returns
            opprettLøpendeBidragssakerResponse(
                opprettLøpendeBidragssak(SAKSNUMMER, NORSK_VALUTA),
            )

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_PRIVAT_AVTALE_ANDRE_SAKER
    }

    @Test
    fun `skal ikke kunne fatte vedtak når det løper bidrag med utenlandsk valuta`() {
        val behandling = opprettBehandlingSimple()

        every { hentLøpendeBidrag(any()) } returns opprettLøpendeBidragssakerResponse(opprettLøpendeBidragssak(SAKSNUMMER, UTENLANDSK_VALUTA))

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_UTENLANDSK_VALUTA
    }

    @Test
    fun `skal ikke kunne fatte vedtak når det løper oppfostringsbidrag`() {
        val behandling = opprettBehandlingSimple()

        every { hentLøpendeBidrag(any()) } returns opprettLøpendeBidragssakerResponse(opprettLøpendeBidragssak(SAKSNUMMER, "NOK", Stønadstype.OPPFOSTRINGSBIDRAG))

        enableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_UTENLANDSK_VALUTA)
        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_OPPFOSTRINGSBIDRAG)

        behandling.kanFatteVedtakBegrunnelse() shouldBe BEGRUNNELSE_OPPFOSTRINGSBIDRAG
    }

    @Test
    fun `skal kunne fatte vedtak for behandling som oppfyller alle vilkår`() {
        val behandling = opprettBehandlingSimple()

        every { hentSak(SAKSNUMMER) } returns opprettSak(testdataBarn1.ident)

        behandling.kanFatteVedtakBegrunnelse() shouldBe null
    }

    private fun opprettBehandlingSimple(
        vedtakstype: Vedtakstype = Vedtakstype.FASTSETTELSE,
        stønadstype: Stønadstype? = Stønadstype.BIDRAG,
        harPrivatAvtaleAndreBarn: Boolean = false,
        privatAvtaleAndreBarnIdenter: List<String> = emptyList(),
        barn: List<Pair<String, LocalDate?>> = listOf(testdataBarn1.ident to VIRKNINGSTIDSPUNKT),
        medBidragspliktig: Boolean = true,
        virkningstidspunkt: LocalDate = VIRKNINGSTIDSPUNKT,
    ) = BehandlingSimple(
        id = 1L,
        virkningstidspunkt = virkningstidspunkt,
        søktFomDato = SØKT_FOM_DATO,
        mottattdato = MOTTATT_DATO,
        saksnummer = SAKSNUMMER,
        harPrivatAvtaleAndreBarn = harPrivatAvtaleAndreBarn,
        vedtakstype = vedtakstype,
        søknadstype = null,
        omgjøringsdetaljer = null,
        stønadstype = stønadstype,
        engangsbeløptype = null,
        forholdsmessigFordeling = null,
        privatAvtaleAndreBarnIdenter = privatAvtaleAndreBarnIdenter,
        roller =
            buildList {
                if (medBidragspliktig) {
                    add(RolleSimple(Rolletype.BIDRAGSPLIKTIG, testdataBP.ident, virkningstidspunkt))
                }
                barn.forEach { (ident, barnVirkningstidspunkt) ->
                    add(RolleSimple(Rolletype.BARN, ident, barnVirkningstidspunkt))
                }
            },
    )

    private fun opprettSak(
        vararg barnIdenter: String,
        saksnummer: String = SAKSNUMMER,
    ) = BidragssakDto(
        eierfogd = Enhetsnummer(BEHANDLER_ENHET),
        saksnummer =
            no.nav.bidrag.domene.sak
                .Saksnummer(saksnummer),
        saksstatus = Bidragssakstatus.IN,
        kategori = Sakskategori.NASJONAL,
        opprettetDato = SAK_OPPRETTET_DATO,
        levdeAdskilt = false,
        ukjentPart = false,
        roller =
            buildList {
                add(
                    RolleDto(
                        fødselsnummer = Personident(testdataBP.ident),
                        type = Rolletype.BIDRAGSPLIKTIG,
                        rollehistorikk = emptyList(),
                    ),
                )
                barnIdenter.forEach { ident ->
                    add(
                        RolleDto(
                            fødselsnummer = Personident(ident),
                            type = Rolletype.BARN,
                            rollehistorikk = emptyList(),
                        ),
                    )
                }
            },
    )

    private fun opprettLøpendeBidragssakerResponse(vararg bidragssaker: LøpendeBidragssak) =
        LøpendeBidragssakerResponse(
            bidragssakerListe = bidragssaker.toList(),
        )

    private fun opprettLøpendeBidragssak(
        saksnummer: String,
        valutakode: String,
        stønadstype: Stønadstype = Stønadstype.BIDRAG,
    ) = LøpendeBidragssak(
        valutakode = valutakode,
        sak =
            no.nav.bidrag.domene.sak
                .Saksnummer(saksnummer),
        kravhaver = Personident(testdataBP.ident),
        type = stønadstype,
        løpendeBeløp = BigDecimal.ONE,
    )

    companion object {
        private val SØKT_FOM_DATO = LocalDate.parse("2022-02-28")
        private val MOTTATT_DATO = LocalDate.parse("2023-03-15")
        private val VIRKNINGSTIDSPUNKT = LocalDate.parse("2023-02-01")
        private val ANNEN_VIRKNINGSTIDSPUNKT = LocalDate.parse("2023-03-01")
        private val SAK_OPPRETTET_DATO = LocalDate.parse("2024-01-01")
        private const val BEHANDLER_ENHET = "4806"
        private const val ANDRE_SAKSNUMMER = "9999999"
        private const val NORSK_VALUTA = "NOK"
        private const val UTENLANDSK_VALUTA = "USD"

        private const val BEGRUNNELSE_MANGLER_BIDRAGSPLIKTIG = "Kan ikke behandle søknad for bidrag uten bidragspliktig"
        private const val BEGRUNNELSE_ULIKE_VIRKNINGSTIDSPUNKT = "Kan ikke fatte vedtak når søknadsbarna har ulike virkningstidspunkt"
        private const val BEGRUNNELSE_MANGLER_BARN_I_SAKEN = "Kan ikke fatte vedtak for behandling som ikke inneholder alle barna i saken"
        private const val BEGRUNNELSE_PRIVAT_AVTALE_ANDRE_BARN = "Kan ikke fatte vedtak når det er lagt inn privat avtale for andre barn"
        private const val BEGRUNNELSE_FLERE_SAKER = "Kan ikke fatte vedtak når BP har flere saker"
        private const val BEGRUNNELSE_PRIVAT_AVTALE_ANDRE_SAKER = "Kan ikke fatte vedtak når BP har privat avtale for barn i andre saker"
        private const val BEGRUNNELSE_UTENLANDSK_VALUTA = "Kan ikke fatte vedtak hvor BP har løpende bidrag med utenlandsk valuta"
        private const val BEGRUNNELSE_OPPFOSTRINGSBIDRAG = "Kan ikke fatte vedtak hvor BP har løpende oppfostringsbidrag"
    }
}
