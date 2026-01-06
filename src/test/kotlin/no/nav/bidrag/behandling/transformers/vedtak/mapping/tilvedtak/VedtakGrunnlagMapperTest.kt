package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.service.BarnebidragGrunnlagInnhenting
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.validerHarGrunnlag
import no.nav.bidrag.behandling.utils.validerHarReferanseTilSjablon
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal

@ExtendWith(MockKExtension::class)
class VedtakGrunnlagMapperTest {
    lateinit var vedtakGrunnlagMapper: VedtakGrunnlagMapper

    @MockK
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    @MockK
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @BeforeEach
    fun init() {
        val personService = PersonService(stubPersonConsumer())
        stubSjablonProvider()
        val behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                behandlingTilGrunnlagMappingV2,
                ValiderBeregning(),
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
    }

    @Test
    fun `skal beregne gebyr`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.inntekter =
            mutableSetOf(
                Inntekt(
                    belop = BigDecimal(2000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    taMed = true,
                    gjelderBarn = behandling.søknadsbarn.first().ident,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    id = 1,
                ),
                Inntekt(
                    belop = BigDecimal(900000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    taMed = true,
                    gjelderBarn = behandling.søknadsbarn.first().ident,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    id = 1,
                ),
            )

        val resultat = vedtakGrunnlagMapper.beregnGebyr(behandling, behandling.bidragsmottaker!!)
        assertSoftly(resultat) {
            ilagtGebyr shouldBe true
            skattepliktigInntekt shouldBe BigDecimal(900000)
            maksBarnetillegg shouldBe BigDecimal(2000)
            beløpGebyrsats shouldBe BigDecimal(1314)
            resultatkode shouldBe Resultatkode.GEBYR_ILAGT
            grunnlagsreferanseListeEngangsbeløp shouldHaveSize 1
            grunnlagsreferanseListeEngangsbeløp shouldContain grunnlagsliste.find { it.type == Grunnlagstype.SLUTTBEREGNING_GEBYR }!!.referanse
            grunnlagsliste shouldHaveSize 5
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.SLUTTBEREGNING_GEBYR)
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.SJABLON_SJABLONTALL, antall = 2)
            grunnlagsliste.validerHarReferanseTilSjablon(SjablonTallNavn.NEDRE_INNTEKTSGRENSE_GEBYR_BELØP)
            grunnlagsliste.validerHarReferanseTilSjablon(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP)
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.DELBEREGNING_INNTEKTSBASERT_GEBYR, antall = 1)
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.DELBEREGNING_SUM_INNTEKT, antall = 1)
        }
    }

    @Test
    fun `skal beregne gebyr ved avslag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.inntekter =
            mutableSetOf(
                Inntekt(
                    belop = BigDecimal(2000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    taMed = true,
                    gjelderBarn = behandling.søknadsbarn.first().ident,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    id = 1,
                ),
                Inntekt(
                    belop = BigDecimal(900000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    taMed = true,
                    gjelderBarn = behandling.søknadsbarn.first().ident,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    id = 1,
                ),
            )

        val resultat = vedtakGrunnlagMapper.beregnGebyr(behandling, behandling.bidragsmottaker!!)
        assertSoftly(resultat) {
            ilagtGebyr shouldBe false
            skattepliktigInntekt shouldBe BigDecimal(900000)
            maksBarnetillegg shouldBe null
            beløpGebyrsats shouldBe BigDecimal(1314)
            resultatkode shouldBe Resultatkode.GEBYR_FRITATT
            grunnlagsreferanseListeEngangsbeløp shouldHaveSize 2
            grunnlagsreferanseListeEngangsbeløp shouldContain grunnlagsliste.find { it.type == Grunnlagstype.SLUTTBEREGNING_GEBYR }!!.referanse
            grunnlagsreferanseListeEngangsbeløp shouldContain grunnlagsliste.find { it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE }!!.referanse
            grunnlagsliste shouldHaveSize 3
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.SLUTTBEREGNING_GEBYR)
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.SJABLON_SJABLONTALL, antall = 1)
            grunnlagsliste.validerHarReferanseTilSjablon(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP)
        }
    }

    @Test
    fun `skal beregne gebyr ved avslag når manuelt gebyr er lagt inn`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.inntekter =
            mutableSetOf(
                Inntekt(
                    belop = BigDecimal(2000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    taMed = true,
                    gjelderBarn = behandling.søknadsbarn.first().ident,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    id = 1,
                ),
                Inntekt(
                    belop = BigDecimal(900000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    taMed = true,
                    gjelderBarn = behandling.søknadsbarn.first().ident,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    id = 1,
                ),
            )

        behandling.bidragsmottaker!!.gebyr = GebyrRolle(true, true, "test")
        val resultat = vedtakGrunnlagMapper.beregnGebyr(behandling, behandling.bidragsmottaker!!)
        assertSoftly(resultat) {
            ilagtGebyr shouldBe false
            skattepliktigInntekt shouldBe BigDecimal(900000)
            maksBarnetillegg shouldBe null
            beløpGebyrsats shouldBe BigDecimal(1314)
            resultatkode shouldBe Resultatkode.GEBYR_FRITATT
            grunnlagsreferanseListeEngangsbeløp shouldHaveSize 2
            grunnlagsreferanseListeEngangsbeløp shouldContain grunnlagsliste.find { it.type == Grunnlagstype.SLUTTBEREGNING_GEBYR }!!.referanse
            grunnlagsreferanseListeEngangsbeløp shouldContain grunnlagsliste.find { it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE }!!.referanse
            grunnlagsliste shouldHaveSize 3
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.SLUTTBEREGNING_GEBYR)
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            grunnlagsliste.validerHarGrunnlag(Grunnlagstype.SJABLON_SJABLONTALL, antall = 1)
            grunnlagsliste.validerHarReferanseTilSjablon(SjablonTallNavn.FASTSETTELSESGEBYR_BELØP)
        }
    }
}
