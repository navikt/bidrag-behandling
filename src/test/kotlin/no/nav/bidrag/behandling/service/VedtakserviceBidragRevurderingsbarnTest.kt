package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.extensions.BehandlingMetadataDo.Companion.FATTE_VEDTAK_REVURDERINGSBARN_INFORMASJON
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.utils.testdata.erstattVariablerITestFil
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetillegg
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggTilFaktiskTilsynsutgift
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.leggTilTillegsstønad
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BehandlingDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.FatteVedtakRevurderingsbarn
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.Test

class VedtakserviceBidragRevurderingsbarnTest : CommonVedtakTilBehandlingTest() {
    @Test
    fun `skal sette beslutningstype AVVIST for revurderingsbarn nar skalFatteVedtakForRevurderingsbarn er false`() {
        val behandling = opprettBehandlingMedRevurderingsbarn()
        val fatteVedtakRevurderingsbarn =
            mockk<FatteVedtakRevurderingsbarn> {
                every { skalFatteVedtakForRevurderingsbarn } returns false
                every { foreslåttFatteVedtak } returns false
                every { manueltOverstyrtForslagBegrunnelse } returns "Manuell overstyring av forslag"
            }
        val request = FatteVedtakRequestDto(fatteVedtakRevurderingsbarn = fatteVedtakRevurderingsbarn)

        every { behandlingService.hentBehandlingById(behandling.id!!) } returns behandling
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns OpprettVedtakResponseDto(1, emptyList())

        vedtakService.fatteVedtak(behandling.id!!, request)

        behandling.metadata?.shouldContainKey(FATTE_VEDTAK_REVURDERINGSBARN_INFORMASJON)

        assertSoftly(opprettVedtakSlot.captured.stønadsendringListe) {
            shouldHaveSize(2)
            val seSøknadsbarn = it.find { it.kravhaver.verdi == testdataBarn1.ident }!!
            seSøknadsbarn.beslutning shouldBe Beslutningstype.ENDRING
            val seRevurderingsbarn = it.find { it.kravhaver.verdi == testdataBarn2.ident }!!
            seRevurderingsbarn.beslutning shouldBe Beslutningstype.ENDRING
        }
    }

    @Test
    fun `skal sette beslutningstype ENDRING for revurderingsbarn nar skalFatteVedtakForRevurderingsbarn er true`() {
        val behandling = opprettBehandlingMedRevurderingsbarn()
        val fatteVedtakRevurderingsbarn =
            mockk<FatteVedtakRevurderingsbarn> {
                every { skalFatteVedtakForRevurderingsbarn } returns true
                every { foreslåttFatteVedtak } returns true
                every { manueltOverstyrtForslagBegrunnelse } returns "Manuell overstyring av forslag"
            }
        val request = FatteVedtakRequestDto(fatteVedtakRevurderingsbarn = fatteVedtakRevurderingsbarn)

        every { behandlingService.hentBehandlingById(behandling.id!!) } returns behandling
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns OpprettVedtakResponseDto(1, emptyList())

        vedtakService.fatteVedtak(behandling.id!!, request)

        assertSoftly(opprettVedtakSlot.captured.stønadsendringListe) {
            shouldHaveSize(2)
            first().beslutning shouldBe Beslutningstype.ENDRING
        }
    }

    @Test
    fun `skal sette beslutningstype ENDRING nar foreslattFatteVedtak er false og manueltOverstyrtForslagBegrunnelse er satt`() {
        val behandling = opprettBehandlingMedRevurderingsbarn()
        val overstyringsbegrunnelse = "Manuell overstyring av forslag"
        val fatteVedtakRevurderingsbarn =
            mockk<FatteVedtakRevurderingsbarn> {
                every { skalFatteVedtakForRevurderingsbarn } returns true
                every { foreslåttFatteVedtak } returns false
                every { manueltOverstyrtForslagBegrunnelse } returns overstyringsbegrunnelse
            }
        val request = FatteVedtakRequestDto(fatteVedtakRevurderingsbarn = fatteVedtakRevurderingsbarn)

        every { behandlingService.hentBehandlingById(behandling.id!!) } returns behandling
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns OpprettVedtakResponseDto(1, emptyList())

        vedtakService.fatteVedtak(behandling.id!!, request)

        assertSoftly(opprettVedtakSlot.captured.stønadsendringListe) {
            shouldHaveSize(2)
            first().beslutning shouldBe Beslutningstype.ENDRING
        }

        val behandlingDetaljerGrunnlag =
            opprettVedtakSlot.captured.grunnlagListe
                .filtrerOgKonverterBasertPåEgenReferanse<BehandlingDetaljerGrunnlag>(Grunnlagstype.BEHANDLING_DETALJER)

        assertSoftly(behandlingDetaljerGrunnlag) {
            shouldHaveSize(1)
            first().innhold.fatteVedtakRevurderingsbarn?.foreslåttFatteVedtak shouldBe false
            first().innhold.fatteVedtakRevurderingsbarn?.manueltOverstyrtForslagBegrunnelse shouldBe overstyringsbegrunnelse
        }
    }

    @Test
    fun `skal lagre BEHANDLING_DETALJER grunnlag med manuelt overstyrt forslag begrunnelse og foreslatt vedtak`() {
        val behandling = opprettBehandlingMedRevurderingsbarn()
        val overstyringsbegrunnelse = "Manuell overstyring av forslag"
        val fatteVedtakRevurderingsbarn =
            mockk<FatteVedtakRevurderingsbarn> {
                every { skalFatteVedtakForRevurderingsbarn } returns true
                every { foreslåttFatteVedtak } returns true
                every { manueltOverstyrtForslagBegrunnelse } returns overstyringsbegrunnelse
            }
        val request = FatteVedtakRequestDto(fatteVedtakRevurderingsbarn = fatteVedtakRevurderingsbarn)

        every { behandlingService.hentBehandlingById(behandling.id!!) } returns behandling
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns OpprettVedtakResponseDto(1, emptyList())

        vedtakService.fatteVedtak(behandling.id!!, request)

        val behandlingDetaljerGrunnlag =
            opprettVedtakSlot.captured.grunnlagListe
                .filtrerOgKonverterBasertPåEgenReferanse<BehandlingDetaljerGrunnlag>(Grunnlagstype.BEHANDLING_DETALJER)

        assertSoftly(behandlingDetaljerGrunnlag) {
            shouldHaveSize(1)
            first().innhold.fatteVedtakRevurderingsbarn?.foreslåttFatteVedtak shouldBe true
            first().innhold.fatteVedtakRevurderingsbarn?.manueltOverstyrtForslagBegrunnelse shouldBe overstyringsbegrunnelse
        }
    }

    private fun opprettBehandlingMedRevurderingsbarn(): Behandling {
        val behandling =
            opprettGyldigBehandlingForBeregningOgVedtak(
                generateId = true,
                typeBehandling = TypeBehandling.BIDRAG,
                andreBarn =
                    listOf(
                        testdataBarn2,
                    ),
            )
        val revurderingsbarn =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 6,
            )
        behandling.roller.add(revurderingsbarn)
        behandling.leggTilSamvær(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)),
            samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1,
            medId = true,
        )
        behandling.leggTilSamvær(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null),
            medId = true,
        )
        behandling.leggTilSamvær(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)),
            samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1,
            barn = testdataBarn2,
            medId = true,
        )
        behandling.leggTilSamvær(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null),
            medId = true,
            barn = testdataBarn2,
        )
        behandling.leggTilTillegsstønad(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(4), null), medId = true)
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), testdataHusstandsmedlem1, medId = true)
        behandling.leggTilFaktiskTilsynsutgift(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null),
            testdataBarnBm,
            medId = true,
        )
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!, null), medId = true)
        behandling.leggTilBarnetilsyn(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), generateId = true)
        behandling.leggTilBarnetilsyn(
            ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)),
            generateId = true,
            tilsynstype = Tilsynstype.HELTID,
            under_skolealder = true,
            kilde = Kilde.OFFENTLIG,
        )
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!, medId = true)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!, medId = true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatType.INNTEKT,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatType.VIRKNINGSTIDSPUNKT,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatType.BOFORHOLD,
            behandling.bidragspliktig,
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn[1],
        )
        behandling.leggTilNotat(
            "Underhold barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Underhold andre barn",
            NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Privat avtale",
            NotatType.PRIVAT_AVTALE,
            behandling.søknadsbarn.first(),
            erDelAvBehandlingen = true,
        )

        revurderingsbarn.forholdsmessigFordeling =
            ForholdsmessigFordelingRolle(
                tilhørerSak = behandling.saksnummer,
                behandlerenhet = behandling.behandlerEnhet,
                delAvOpprinneligBehandling = false,
                erRevurdering = true,
                bidragsmottaker = behandling.bidragsmottaker?.ident,
            )
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                erstattVariablerITestFil("grunnlagresponse_bp_bm"),
            )
        return behandling
    }
}
