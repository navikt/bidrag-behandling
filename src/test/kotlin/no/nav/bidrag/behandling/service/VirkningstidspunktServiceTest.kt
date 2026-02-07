package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.opphørSisteTilDato
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.opprettInntektsposter
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional

class VirkningstidspunktServiceTest : CommonMockServiceTest() {
    @Test
    fun `skal oppdatere avslag`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val barn1 = behandling.søknadsbarn.first()
        val barn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 55,
            )

        behandling.roller.add(barn2)
        barn1.virkningstidspunkt = LocalDate.parse("2025-01-01")
        barn2.virkningstidspunkt = LocalDate.parse("2025-02-01")
        behandling.virkningstidspunkt = LocalDate.parse("2025-03-01")
        barn1.avslag = null
        barn2.avslag = null
        behandling.avslag = null
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = barn2.id, avslag = Resultatkode.AVSLAG))
        behandling.avslag shouldBe null
        barn1.avslag shouldBe null
        barn2.avslag shouldBe Resultatkode.AVSLAG

        barn1.avslag = null
        barn2.avslag = null
        behandling.avslag = null

        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = null, avslag = Resultatkode.AVSLAG))
        behandling.avslag shouldBe Resultatkode.AVSLAG
        barn1.avslag shouldBe Resultatkode.AVSLAG
        barn2.avslag shouldBe Resultatkode.AVSLAG
    }

    @Test
    fun `skal oppdatere avslag når alle barn har avslag`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val barn1 = behandling.søknadsbarn.first()
        val barn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 55,
            )

        behandling.roller.add(barn2)
        barn1.virkningstidspunkt = LocalDate.parse("2025-01-01")
        barn2.virkningstidspunkt = LocalDate.parse("2025-02-01")
        behandling.virkningstidspunkt = LocalDate.parse("2025-03-01")
        barn1.avslag = null
        barn2.avslag = null
        behandling.avslag = null
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = barn2.id, avslag = Resultatkode.AVSLAG))
        behandling.avslag shouldBe null
        barn1.avslag shouldBe null
        barn2.avslag shouldBe Resultatkode.AVSLAG

        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = barn1.id, avslag = Resultatkode.AVSLAG))
        behandling.avslag shouldBe Resultatkode.AVSLAG
        barn1.avslag shouldBe Resultatkode.AVSLAG
        barn2.avslag shouldBe Resultatkode.AVSLAG
    }

    @Test
    fun `skal oppdatere virkningstidspunkt hvis barn virkningstidspunkt er null`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val barn1 = behandling.søknadsbarn.first()
        val barn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 55,
            )

        behandling.roller.add(barn2)
        barn1.virkningstidspunkt = null
        barn2.virkningstidspunkt = null
        behandling.virkningstidspunkt = LocalDate.parse("2025-01-01")
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = null, virkningstidspunkt = LocalDate.parse("2025-10-01")))
        behandling.virkningstidspunkt shouldBe LocalDate.parse("2025-10-01")
        barn1.virkningstidspunkt shouldBe LocalDate.parse("2025-10-01")
        barn2.virkningstidspunkt shouldBe LocalDate.parse("2025-10-01")
    }

    @Test
    fun `skal oppdatere virkningstidspunkt`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val barn1 = behandling.søknadsbarn.first()
        val barn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 55,
            )

        behandling.roller.add(barn2)
        barn1.virkningstidspunkt = LocalDate.parse("2025-01-01")
        barn2.virkningstidspunkt = LocalDate.parse("2025-02-01")
        behandling.virkningstidspunkt = LocalDate.parse("2025-01-01")

        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = barn2.id, virkningstidspunkt = LocalDate.parse("2025-02-01")))
        behandling.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
        barn1.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
        barn2.virkningstidspunkt shouldBe LocalDate.parse("2025-02-01")

        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = barn2.id, virkningstidspunkt = LocalDate.parse("2025-05-01")))
        behandling.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
        barn1.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
        barn2.virkningstidspunkt shouldBe LocalDate.parse("2025-05-01")

        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = barn1.id, virkningstidspunkt = LocalDate.parse("2025-03-01")))
        behandling.virkningstidspunkt shouldBe LocalDate.parse("2025-03-01")
        barn1.virkningstidspunkt shouldBe LocalDate.parse("2025-03-01")
        barn2.virkningstidspunkt shouldBe LocalDate.parse("2025-05-01")

        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = null, virkningstidspunkt = LocalDate.parse("2025-10-01")))
        behandling.virkningstidspunkt shouldBe LocalDate.parse("2025-10-01")
        barn1.virkningstidspunkt shouldBe LocalDate.parse("2025-10-01")
        barn2.virkningstidspunkt shouldBe LocalDate.parse("2025-10-01")
    }

    @Test
    fun `skal oppdatere virkningstidspunkt og årsak for ett barn`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val barn1 = behandling.søknadsbarn.first()
        val barn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 55,
            )

        behandling.roller.add(barn2)
        barn1.virkningstidspunkt = null
        barn1.årsak = null
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        behandling.søktFomDato = LocalDate.parse("2024-02-01")
        behandling.virkningstidspunkt = LocalDate.parse("2024-02-01")
        behandling.søknadsbarn.first().opphørsdato = null
        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = 55, virkningstidspunkt = LocalDate.parse("2025-01-01"), årsak = VirkningstidspunktÅrsakstype.ENDRING_3_MÅNEDER_TILBAKE))
        behandling.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
        behandling.årsak shouldBe VirkningstidspunktÅrsakstype.ENDRING_3_MÅNEDER_TILBAKE
        barn1.årsak shouldBe null
        barn1.virkningstidspunkt shouldBe null
        barn2.årsak shouldBe VirkningstidspunktÅrsakstype.ENDRING_3_MÅNEDER_TILBAKE
        barn2.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
    }

    @Test
    fun `skal oppdatere virkningstidspunkt og årsak for alle barn`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        val barn1 = behandling.søknadsbarn.first()
        val barn2 =
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = 55,
            )

        behandling.roller.add(barn2)
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        behandling.søktFomDato = LocalDate.parse("2024-02-01")
        behandling.virkningstidspunkt = LocalDate.parse("2024-02-01")
        behandling.søknadsbarn.first().opphørsdato = null
        virkningstidspunktService.oppdatereVirkningstidspunkt(behandling.id!!, OppdatereVirkningstidspunkt(rolleId = null, virkningstidspunkt = LocalDate.parse("2025-01-01"), årsak = VirkningstidspunktÅrsakstype.ENDRING_3_MÅNEDER_TILBAKE))
        behandling.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
        behandling.årsak shouldBe VirkningstidspunktÅrsakstype.ENDRING_3_MÅNEDER_TILBAKE
        barn1.årsak shouldBe VirkningstidspunktÅrsakstype.ENDRING_3_MÅNEDER_TILBAKE
        barn1.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
        barn2.årsak shouldBe VirkningstidspunktÅrsakstype.ENDRING_3_MÅNEDER_TILBAKE
        barn2.virkningstidspunkt shouldBe LocalDate.parse("2025-01-01")
    }

    @Test
    fun `skal oppdatere virkningstidspunkt og oppdatere opphørsdato hvis det blir satt til avslag`() {
        Optional.ofNullable<List<String>>(null).flatMap { it.stream().findFirst() }
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG_18_ÅR)
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        behandling.søktFomDato = LocalDate.parse("2024-02-01")
        behandling.virkningstidspunkt = LocalDate.parse("2024-02-01")
        behandling.søknadsbarn.first().opphørsdato = null
        virkningstidspunktService.oppdaterAvslagÅrsak(behandling, OppdatereVirkningstidspunkt(avslag = Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG))
        behandling.søknadsbarn.first().opphørsdato shouldBe null
        behandling.søknadsbarn.first().avslag shouldBe Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG

        // Skal ikke oppdatere opphørsdato hvis det allerede er satt
        behandling.søknadsbarn.first().opphørsdato = LocalDate.parse("2024-12-01")
        virkningstidspunktService.oppdaterAvslagÅrsak(behandling, OppdatereVirkningstidspunkt(avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD))
        behandling.søknadsbarn.first().opphørsdato shouldBe null
        behandling.søknadsbarn.first().avslag shouldBe Resultatkode.BIDRAGSPLIKTIG_ER_DØD
    }

    @Test
    fun `skal ikke oppdatere virkningstidspunkt og oppdatere opphørsdato hvis det blir satt til avslag hvis det er bidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        behandling.søktFomDato = LocalDate.parse("2024-02-01")
        behandling.virkningstidspunkt = LocalDate.parse("2024-02-01")
        behandling.søknadsbarn.first().opphørsdato = null
        virkningstidspunktService.oppdaterAvslagÅrsak(behandling, OppdatereVirkningstidspunkt(avslag = Resultatkode.IKKE_DOKUMENTERT_SKOLEGANG))
        behandling.søknadsbarn.first().opphørsdato shouldBe null

        // Skal ikke oppdatere opphørsdato hvis det allerede er satt
        behandling.søknadsbarn.first().opphørsdato = LocalDate.parse("2024-12-01")
        virkningstidspunktService.oppdaterAvslagÅrsak(behandling, OppdatereVirkningstidspunkt(avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD))
        behandling.søknadsbarn.first().opphørsdato shouldBe null
    }

    @Nested
    inner class BoforholdTest {
        @Test
        fun `skal oppdatere opphør`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                )
            behandling.roller.add(søknadsbarn2)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            behandling.søknadsbarn.first().opphørsdato = null
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = LocalDate.parse("2024-01-01")))
            assertSoftly {
                søknadsbarn.opphørsdato shouldBe LocalDate.parse("2024-01-01")
                søknadsbarn.opphørTilDato shouldBe LocalDate.parse("2023-12-31")
                søknadsbarn.opphørSistePeriode shouldBe true

                søknadsbarn2.opphørsdato shouldBe null
                søknadsbarn2.opphørTilDato shouldBe null
                søknadsbarn2.opphørSistePeriode shouldBe false

                behandling.globalOpphørsdato shouldBe null
                behandling.opphørTilDato shouldBe null
                behandling.opphørSistePeriode shouldBe false
            }
        }

        @Test
        fun `skal oppdatere opphør for to barn`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                )
            behandling.roller.add(søknadsbarn2)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            behandling.søknadsbarn.first().opphørsdato = null
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = LocalDate.parse("2024-01-01")))
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn2.id!!, opphørsdato = LocalDate.parse("2025-01-01")))

            assertSoftly {
                søknadsbarn.opphørsdato shouldBe LocalDate.parse("2024-01-01")
                søknadsbarn.opphørTilDato shouldBe LocalDate.parse("2023-12-31")
                søknadsbarn.opphørSistePeriode shouldBe true

                søknadsbarn2.opphørsdato shouldBe LocalDate.parse("2025-01-01")
                søknadsbarn2.opphørTilDato shouldBe LocalDate.parse("2024-12-31")
                søknadsbarn2.opphørSistePeriode shouldBe true

                behandling.globalOpphørsdato shouldBe LocalDate.parse("2025-01-01")
                behandling.opphørTilDato shouldBe LocalDate.parse("2024-12-31")
                behandling.opphørSistePeriode shouldBe true
            }
        }

        @Test
        fun `skal oppdatere opphørsdato boforhold bakover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            val opphørsdato = LocalDate.parse("2024-07-01")

            val husstandsmedlem =
                Husstandsmedlem(
                    behandling = behandling,
                    kilde = Kilde.OFFENTLIG,
                    ident = testdataBarn1.ident,
                    navn = testdataBarn1.navn,
                    fødselsdato = testdataBarn1.fødselsdato,
                    id = 1,
                    rolle = testdataBarn1.tilRolle(behandling),
                )
            husstandsmedlem.perioder =
                mutableSetOf(
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = behandling.virkningstidspunkt,
                        datoTom = LocalDate.parse("2024-10-31"),
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = 1,
                    ),
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = LocalDate.parse("2024-11-01"),
                        datoTom = null,
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = 2,
                    ),
                )
            behandling.husstandsmedlem = mutableSetOf(husstandsmedlem)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(behandling.søknadsbarn.first().id!!, opphørsdato = opphørsdato))

            assertSoftly(
                behandling.husstandsmedlem.first(),
            ) {
                perioder shouldHaveSize 1
                perioder.first().datoTom shouldBe opphørsdato.opphørSisteTilDato()
            }
        }

        @Test
        fun `skal oppdatere opphørsdato boforhold inneværende måned`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            val opphørsdato = LocalDate.now().withDayOfMonth(1)

            val husstandsmedlem =
                Husstandsmedlem(
                    behandling = behandling,
                    kilde = Kilde.OFFENTLIG,
                    ident = testdataBarn1.ident,
                    navn = testdataBarn1.navn,
                    fødselsdato = LocalDate.parse("2025-01-01").minusYears(18),
                    id = 1,
                    rolle = testdataBarn1.tilRolle(behandling),
                )
            husstandsmedlem.perioder =
                mutableSetOf(
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = behandling.virkningstidspunkt,
                        datoTom = LocalDate.parse("2024-10-31"),
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = 1,
                    ),
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = LocalDate.parse("2024-11-01"),
                        datoTom = null,
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = 2,
                    ),
                )
            behandling.husstandsmedlem = mutableSetOf(husstandsmedlem)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(behandling.søknadsbarn.first().id!!, opphørsdato = opphørsdato))

            assertSoftly(
                behandling.husstandsmedlem.first(),
            ) {
                perioder shouldHaveSize 2
                perioder.first().datoTom shouldBe LocalDate.parse("2024-10-31")
                perioder.last().datoTom shouldBe opphørsdato.opphørSisteTilDato()
            }
        }

        @Test
        fun `skal oppdatere opphørsdato boforhold framover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            val opphørsdato = LocalDate.now().plusMonths(3).withDayOfMonth(1)

            val husstandsmedlem =
                Husstandsmedlem(
                    behandling = behandling,
                    kilde = Kilde.OFFENTLIG,
                    ident = testdataBarn1.ident,
                    navn = testdataBarn1.navn,
                    fødselsdato = LocalDate.parse("2025-01-01").minusYears(18),
                    id = 1,
                    rolle = testdataBarn1.tilRolle(behandling),
                )
            husstandsmedlem.perioder =
                mutableSetOf(
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = behandling.virkningstidspunkt,
                        datoTom = LocalDate.parse("2024-10-31"),
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = 1,
                    ),
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = LocalDate.parse("2024-11-01"),
                        datoTom = null,
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = 2,
                    ),
                )
            behandling.husstandsmedlem = mutableSetOf(husstandsmedlem)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(behandling.søknadsbarn.first().id!!, opphørsdato = opphørsdato))

            assertSoftly(
                behandling.husstandsmedlem.first(),
            ) {
                perioder shouldHaveSize 2
                perioder.first().datoTom shouldBe LocalDate.parse("2024-10-31")
                perioder.last().datoTom shouldBe null
            }
        }
    }

    @Nested
    inner class InntektTest {
        @Test
        fun `skal oppdatere opphørsdato inntekt bakover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                    opphørsdato = LocalDate.parse("2024-01-01"),
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            val opphørsdato = LocalDate.parse("2024-07-01")

            val inntektBM =

                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    opprinneligFom = YearMonth.parse("2023-02"),
                    opprinneligTom = YearMonth.parse("2024-01"),
                    gjelderRolle = behandling.bidragsmottaker!!,
                    taMed = true,
                    kilde = Kilde.OFFENTLIG,
                    behandling = behandling,
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    medId = true,
                )
            inntektBM.inntektsposter.addAll(opprettInntektsposter(inntektBM))
            val inntektBP1 =
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = YearMonth.parse("2024-09"),
                    gjelderRolle = behandling.bidragspliktig!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBP2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-10"),
                    datoTom = YearMonth.parse("2024-12"),
                    gjelderRolle = behandling.bidragspliktig!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBarnetillegg =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-12"),
                    opprinneligFom = YearMonth.parse("2024-05"),
                    opprinneligTom = YearMonth.parse("2024-12"),
                    taMed = true,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    kilde = Kilde.OFFENTLIG,
                    gjelderBarn = søknadsbarn.ident,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    medId = true,
                )
            val inntektBarnetillegg2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = YearMonth.parse("2024-06"),
                    opprinneligFom = YearMonth.parse("2024-01"),
                    opprinneligTom = YearMonth.parse("2024-06"),
                    taMed = true,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    kilde = Kilde.OFFENTLIG,
                    gjelderBarn = søknadsbarn.ident,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    medId = true,
                )
            val inntektBarnetilleggBarn2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2023-05"),
                    datoTom = YearMonth.parse("2024-02"),
                    opprinneligFom = YearMonth.parse("2023-05"),
                    opprinneligTom = YearMonth.parse("2024-02"),
                    taMed = true,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    kilde = Kilde.OFFENTLIG,
                    gjelderBarn = søknadsbarn2.ident,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    medId = true,
                )
            val inntektBarnetillegg2Barn2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-02"),
                    datoTom = YearMonth.parse("2024-06"),
                    opprinneligFom = YearMonth.parse("2024-02"),
                    opprinneligTom = YearMonth.parse("2024-06"),
                    taMed = true,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    kilde = Kilde.OFFENTLIG,
                    gjelderBarn = søknadsbarn2.ident,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    medId = true,
                )
            behandling.inntekter = mutableSetOf(inntektBM, inntektBarnetillegg, inntektBP1, inntektBP2, inntektBarnetillegg2, inntektBarnetilleggBarn2, inntektBarnetillegg2Barn2)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))
            assertSoftly(inntektBP1) {
                taMed shouldBe true
                datoTom shouldBe opphørsdato.opphørSisteTilDato()
                datoFom shouldBe behandling.virkningstidspunkt
            }
            assertSoftly(inntektBP2) {
                taMed shouldBe false
                datoTom shouldBe null
                datoFom shouldBe null
            }
            assertSoftly(inntektBM) {
                taMed shouldBe true
                datoFom shouldBe behandling.virkningstidspunkt
                datoTom shouldBe opphørsdato.opphørSisteTilDato()
            }
            assertSoftly(inntektBarnetillegg) {
                taMed shouldBe true
                datoFom shouldBe LocalDate.parse("2024-05-01")
                datoTom shouldBe LocalDate.parse("2024-06-30")
            }
            assertSoftly(inntektBarnetillegg2) {
                taMed shouldBe true
                datoFom shouldBe LocalDate.parse("2024-01-01")
                datoTom shouldBe LocalDate.parse("2024-06-30")
            }

            assertSoftly(inntektBarnetillegg2Barn2) {
                taMed shouldBe false
                datoTom shouldBe null
                datoFom shouldBe null
            }
            assertSoftly(inntektBarnetilleggBarn2) {
                taMed shouldBe true
                datoFom shouldBe YearMonth.parse("2023-05").atDay(1)
                datoTom shouldBe LocalDate.parse("2023-12-31")
            }
        }

        @Test
        fun `skal oppdatere opphørsdato inntekt framover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                    opphørsdato = LocalDate.parse("2024-01-01"),
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            val opphørsdato = LocalDate.now().plusMonths(5).withDayOfMonth(1)

            val inntektBM =
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    opprinneligFom = YearMonth.parse("2023-02"),
                    opprinneligTom = YearMonth.parse("2024-01"),
                    gjelderRolle = behandling.bidragsmottaker!!,
                    taMed = true,
                    kilde = Kilde.OFFENTLIG,
                    behandling = behandling,
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                    medId = true,
                )
            val inntektBP1 =
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = YearMonth.parse("2024-09"),
                    gjelderRolle = behandling.bidragspliktig!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBP2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-10"),
                    datoTom = null,
                    gjelderRolle = behandling.bidragspliktig!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBarnetillegg =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-05"),
                    datoTom = YearMonth.parse("2024-12"),
                    opprinneligFom = YearMonth.parse("2024-05"),
                    opprinneligTom = YearMonth.parse("2024-12"),
                    taMed = true,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    kilde = Kilde.OFFENTLIG,
                    gjelderBarn = søknadsbarn.ident,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    medId = true,
                )
            val inntektBarnetillegg2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = YearMonth.parse("2024-06"),
                    opprinneligFom = YearMonth.parse("2024-01"),
                    opprinneligTom = YearMonth.parse("2024-06"),
                    taMed = true,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    kilde = Kilde.OFFENTLIG,
                    gjelderBarn = søknadsbarn.ident,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    medId = true,
                )

            behandling.inntekter = mutableSetOf(inntektBM, inntektBarnetillegg, inntektBP1, inntektBP2, inntektBarnetillegg2)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))
            assertSoftly(inntektBP1) {
                taMed shouldBe true
                datoTom shouldBe YearMonth.parse("2024-09").atEndOfMonth()
                datoFom shouldBe behandling.virkningstidspunkt
            }
            assertSoftly(inntektBP2) {
                taMed shouldBe true
                datoTom shouldBe null
                datoFom shouldBe YearMonth.parse("2024-10").atDay(1)
            }
            assertSoftly(inntektBM) {
                taMed shouldBe true
                datoFom shouldBe behandling.virkningstidspunkt
                datoTom shouldBe null
            }
            assertSoftly(inntektBarnetillegg) {
                taMed shouldBe true
                datoFom shouldBe LocalDate.parse("2024-05-01")
                datoTom shouldBe LocalDate.parse("2024-12-31")
            }
            assertSoftly(inntektBarnetillegg2) {
                taMed shouldBe true
                datoFom shouldBe LocalDate.parse("2024-01-01")
                datoTom shouldBe LocalDate.parse("2024-06-30")
            }
        }

        @Test
        fun `skal oppdatere opphørsdato inntekt framover i tid hvis opphør fra før`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                    opphørsdato = LocalDate.parse("2024-01-01"),
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            søknadsbarn.opphørsdato = LocalDate.parse("2024-05-01")
            val opphørsdato = LocalDate.now().plusMonths(5).withDayOfMonth(1)

            val inntektBM =
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = YearMonth.parse("2024-01"),
                    opprinneligFom = YearMonth.parse("2023-02"),
                    opprinneligTom = YearMonth.parse("2024-01"),
                    gjelderRolle = behandling.bidragsmottaker!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBM2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-02"),
                    datoTom = YearMonth.parse("2024-04"),
                    gjelderRolle = behandling.bidragsmottaker!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBP1 =
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = YearMonth.parse("2024-09"),
                    gjelderRolle = behandling.bidragspliktig!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBP2 =
                opprettInntekt(
                    datoFom = YearMonth.parse("2024-10"),
                    datoTom = null,
                    gjelderRolle = behandling.bidragspliktig!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )

            behandling.inntekter = mutableSetOf(inntektBM, inntektBP1, inntektBP2, inntektBM2)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))
            assertSoftly(inntektBP1) {
                taMed shouldBe true
                datoFom shouldBe behandling.virkningstidspunkt
                datoTom shouldBe YearMonth.parse("2024-09").atEndOfMonth()
            }
            assertSoftly(inntektBP2) {
                taMed shouldBe true
                datoFom shouldBe YearMonth.parse("2024-10").atDay(1)
                datoTom shouldBe null
            }
            assertSoftly(inntektBM) {
                taMed shouldBe true
                datoFom shouldBe behandling.virkningstidspunkt
                datoTom shouldBe YearMonth.parse("2024-01").atEndOfMonth()
            }
            assertSoftly(inntektBM2) {
                taMed shouldBe true
                datoFom shouldBe YearMonth.parse("2024-02").atDay(1)
                datoTom shouldBe null
            }
        }

        @Test
        fun `skal oppdatere opphørsdato og virkningstidspunkt når begge er fram i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                    opphørsdato = LocalDate.parse("2024-01-01"),
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.oppdaterVirkningstidspunktForAlle(LocalDate.now().plusMonths(6).withDayOfMonth(1))

            søknadsbarn.opphørsdato = LocalDate.now().plusMonths(9).withDayOfMonth(1)
            val opphørsdato = LocalDate.now().plusMonths(8).withDayOfMonth(1)

            val inntektBM =
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    gjelderRolle = behandling.bidragsmottaker!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            val inntektBP1 =
                opprettInntekt(
                    datoFom = YearMonth.from(behandling.virkningstidspunkt),
                    datoTom = null,
                    gjelderRolle = behandling.bidragspliktig!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    medId = true,
                )
            behandling.inntekter = mutableSetOf(inntektBM, inntektBP1)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))
            assertSoftly(inntektBP1) {
                taMed shouldBe true
                datoFom shouldBe behandling.virkningstidspunkt
                datoTom shouldBe null
            }

            assertSoftly(inntektBM) {
                taMed shouldBe true
                datoFom shouldBe behandling.virkningstidspunkt
                datoTom shouldBe null
            }
            val nyVirkningstidspunkt = LocalDate.now().plusMonths(2).withDayOfMonth(1)

            virkningstidspunktService.oppdatereVirkningstidspunkt(1, OppdatereVirkningstidspunkt(virkningstidspunkt = nyVirkningstidspunkt))
            assertSoftly(inntektBP1) {
                taMed shouldBe true
                datoFom shouldBe nyVirkningstidspunkt
                datoTom shouldBe null
            }

            assertSoftly(inntektBM) {
                taMed shouldBe true
                datoFom shouldBe nyVirkningstidspunkt
                datoTom shouldBe null
            }
        }
    }

    @Nested
    inner class SamværTest {
        @Test
        fun `skal oppdatere virkningstidspunkt og opphørsdato når begge er fram i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            behandling.virkningstidspunkt = LocalDate.now().plusMonths(4).withDayOfMonth(1)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    virkningstidspunkt = behandling.virkningstidspunkt,
                    id = 5,
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            søknadsbarn.virkningstidspunkt = behandling.virkningstidspunkt
            behandling.roller.add(søknadsbarn2)

            val samværBarn1 =
                Samvær(
                    behandling = behandling,
                    id = 1,
                    rolle = søknadsbarn,
                )
            samværBarn1.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = behandling.virkningstidspunkt!!,
                    tom = null,
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )

            val samværBarn2 =
                Samvær(
                    behandling = behandling,
                    id = 1,
                    rolle = søknadsbarn2,
                )
            samværBarn2.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = behandling.virkningstidspunkt!!,
                    tom = null,
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )

            val opphørsdato = LocalDate.now().plusMonths(5)
            behandling.samvær = mutableSetOf(samværBarn1, samværBarn2)

            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))
            assertSoftly {
                val sistePeriode = samværBarn2.perioder.maxBy { it.fom }
                sistePeriode.tom shouldBe null
            }
            assertSoftly {
                val sistePeriode = samværBarn1.perioder.maxBy { it.fom }
                samværBarn1.perioder.shouldHaveSize(1)
                sistePeriode.tom shouldBe null
            }
            val nyVirkningstidspunkt = LocalDate.now().plusMonths(2)
            virkningstidspunktService.oppdatereVirkningstidspunkt(1, OppdatereVirkningstidspunkt(virkningstidspunkt = nyVirkningstidspunkt))
            assertSoftly {
                val førstePeriode = samværBarn2.perioder.minBy { it.fom }
                førstePeriode.fom shouldBe LocalDate.now().plusMonths(2).withDayOfMonth(1)
            }
            assertSoftly {
                val førstePeriode = samværBarn1.perioder.minBy { it.fom }
                førstePeriode.fom shouldBe LocalDate.now().plusMonths(2).withDayOfMonth(1)
            }
        }

        @Test
        fun `skal oppdatere opphør og virkningstidspunkt samvær bakover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.virkningstidspunkt = LocalDate.now().minusMonths(7).withDayOfMonth(1)

            val samværBarn1 =
                Samvær(
                    behandling = behandling,
                    id = 1,
                    rolle = søknadsbarn,
                )
            samværBarn1.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = behandling.virkningstidspunkt!!,
                    tom = YearMonth.now().minusMonths(5).atEndOfMonth(),
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )
            samværBarn1.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = YearMonth.now().minusMonths(4).atEndOfMonth(),
                    tom = YearMonth.now().minusMonths(3).atEndOfMonth(),
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )
            samværBarn1.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                    tom = null,
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )

            val samværBarn2 =
                Samvær(
                    behandling = behandling,
                    id = 1,
                    rolle = søknadsbarn2,
                )
            samværBarn2.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn2,
                    fom = behandling.virkningstidspunkt!!,
                    tom = YearMonth.now().minusMonths(3).atEndOfMonth(),
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )
            samværBarn2.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn2,
                    fom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                    tom = null,
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )
            val opphørsdato = LocalDate.now().minusMonths(3).withDayOfMonth(1)
            behandling.samvær = mutableSetOf(samværBarn1, samværBarn2)

            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn2.id!!, opphørsdato = opphørsdato))
            assertSoftly {
                samværBarn2.perioder shouldHaveSize 1
                val sistePeriode = samværBarn2.perioder.maxBy { it.fom }
                sistePeriode.tom shouldBe opphørsdato.opphørSisteTilDato()
            }
            assertSoftly {
                val sistePeriode = samværBarn1.perioder.maxBy { it.fom }
                sistePeriode.tom shouldBe null
            }
        }

        @Test
        fun `skal oppdatere opphørsdato samvær bakover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")

            val samværBarn1 =
                Samvær(
                    behandling = behandling,
                    id = 1,
                    rolle = søknadsbarn,
                )
            samværBarn1.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = behandling.virkningstidspunkt!!,
                    tom = YearMonth.parse("2024-04").atEndOfMonth(),
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )
            samværBarn1.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = YearMonth.parse("2024-05").atDay(1),
                    tom = null,
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )

            val samværBarn2 =
                Samvær(
                    behandling = behandling,
                    id = 1,
                    rolle = søknadsbarn2,
                )
            samværBarn2.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = behandling.virkningstidspunkt!!,
                    tom = YearMonth.parse("2024-10").atEndOfMonth(),
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )
            samværBarn2.perioder.add(
                Samværsperiode(
                    id = 1,
                    samvær = samværBarn1,
                    fom = YearMonth.parse("2024-11").atDay(1),
                    tom = null,
                    samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                ),
            )
            val opphørsdato = LocalDate.parse("2024-07-01")
            behandling.samvær = mutableSetOf(samværBarn1, samværBarn2)

            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))
            assertSoftly {
                val sistePeriode = samværBarn2.perioder.maxBy { it.fom }
                sistePeriode.tom shouldBe null
            }
            assertSoftly {
                val sistePeriode = samværBarn1.perioder.maxBy { it.fom }
                sistePeriode.tom shouldBe opphørsdato.opphørSisteTilDato()
            }
        }
    }

    @Nested
    inner class UnderholdskostnadTest {
        @Test
        fun `skal oppdatere opphørsdato underholdskostnad bakover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                    opphørsdato = LocalDate.now().minusMonths(10),
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.virkningstidspunkt = YearMonth.now().minusMonths(8).atDay(1)

            val underholdskostnadBarn1 =
                Underholdskostnad(
                    behandling = behandling,
                    id = 1,
                    person =
                        Person(
                            id = 1,
                            rolle = mutableSetOf(søknadsbarn),
                            ident = testdataBarn1.ident,
                            navn = testdataBarn1.navn,
                            fødselsdato = testdataBarn1.fødselsdato,
                        ),
                )
            underholdskostnadBarn1.faktiskeTilsynsutgifter =
                mutableSetOf(
                    FaktiskTilsynsutgift(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = YearMonth.now().minusMonths(7).atEndOfMonth(),
                        tilsynsutgift = BigDecimal(1000),
                    ),
                    FaktiskTilsynsutgift(
                        id = 2,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = YearMonth.now().minusMonths(6).atDay(1),
                        tom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                        tilsynsutgift = BigDecimal(1000),
                    ),
                )
            underholdskostnadBarn1.barnetilsyn =
                mutableSetOf(
                    Barnetilsyn(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = YearMonth.now().minusMonths(7).atEndOfMonth(),
                        under_skolealder = true,
                        omfang = Tilsynstype.DELTID,
                        kilde = Kilde.MANUELL,
                    ),
                    Barnetilsyn(
                        id = 2,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = YearMonth.now().minusMonths(6).atDay(1),
                        tom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                        under_skolealder = true,
                        omfang = Tilsynstype.DELTID,
                        kilde = Kilde.MANUELL,
                    ),
                )
            underholdskostnadBarn1.tilleggsstønad =
                mutableSetOf(
                    Tilleggsstønad(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = YearMonth.now().minusMonths(7).atEndOfMonth(),
                        `beløp` = BigDecimal(1000),
                    ),
                    Tilleggsstønad(
                        id = 2,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = YearMonth.now().minusMonths(6).atDay(1),
                        tom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                        `beløp` = BigDecimal(1000),
                    ),
                )
            val opphørsdato = LocalDate.now().minusMonths(3)

            behandling.underholdskostnader = mutableSetOf(underholdskostnadBarn1)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))

            assertSoftly(underholdskostnadBarn1.tilleggsstønad) {
                shouldHaveSize(2)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe opphørsdato.opphørSisteTilDato()
            }
            assertSoftly(underholdskostnadBarn1.faktiskeTilsynsutgifter) {
                shouldHaveSize(2)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe opphørsdato.opphørSisteTilDato()
            }
            assertSoftly(underholdskostnadBarn1.barnetilsyn) {
                shouldHaveSize(2)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe opphørsdato.opphørSisteTilDato()
            }
        }

        @Test
        fun `skal oppdatere opphørsdato underholdskostnad framover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                    opphørsdato = LocalDate.now().minusMonths(10),
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.virkningstidspunkt = YearMonth.now().minusMonths(8).atDay(1)

            val underholdskostnadBarn1 =
                Underholdskostnad(
                    behandling = behandling,
                    id = 1,
                    person =
                        Person(
                            id = 1,
                            rolle = mutableSetOf(søknadsbarn),
                            ident = testdataBarn1.ident,
                            navn = testdataBarn1.navn,
                            fødselsdato = testdataBarn1.fødselsdato,
                        ),
                )
            underholdskostnadBarn1.faktiskeTilsynsutgifter =
                mutableSetOf(
                    FaktiskTilsynsutgift(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = YearMonth.now().minusMonths(7).atEndOfMonth(),
                        tilsynsutgift = BigDecimal(1000),
                    ),
                    FaktiskTilsynsutgift(
                        id = 2,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = YearMonth.now().minusMonths(6).atDay(1),
                        tom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                        tilsynsutgift = BigDecimal(1000),
                    ),
                )
            underholdskostnadBarn1.barnetilsyn =
                mutableSetOf(
                    Barnetilsyn(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = YearMonth.now().minusMonths(7).atEndOfMonth(),
                        under_skolealder = true,
                        omfang = Tilsynstype.DELTID,
                        kilde = Kilde.MANUELL,
                    ),
                    Barnetilsyn(
                        id = 2,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = YearMonth.now().minusMonths(6).atDay(1),
                        tom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                        under_skolealder = true,
                        omfang = Tilsynstype.DELTID,
                        kilde = Kilde.MANUELL,
                    ),
                )
            underholdskostnadBarn1.tilleggsstønad =
                mutableSetOf(
                    Tilleggsstønad(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = YearMonth.now().minusMonths(7).atEndOfMonth(),
                        `beløp` = BigDecimal(1000),
                    ),
                    Tilleggsstønad(
                        id = 2,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = YearMonth.now().minusMonths(6).atDay(1),
                        tom = YearMonth.now().minusMonths(2).atEndOfMonth(),
                        `beløp` = BigDecimal(1000),
                    ),
                )
            val opphørsdato = LocalDate.now().plusMonths(3)

            behandling.underholdskostnader = mutableSetOf(underholdskostnadBarn1)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))

            assertSoftly(underholdskostnadBarn1.tilleggsstønad) {
                shouldHaveSize(2)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe YearMonth.now().minusMonths(2).atEndOfMonth()
            }
            assertSoftly(underholdskostnadBarn1.faktiskeTilsynsutgifter) {
                shouldHaveSize(2)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe YearMonth.now().minusMonths(2).atEndOfMonth()
            }
            assertSoftly(underholdskostnadBarn1.barnetilsyn) {
                shouldHaveSize(2)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe YearMonth.now().minusMonths(2).atEndOfMonth()
            }
        }

        @Test
        fun `skal oppdatere opphørsdato og virkningstidspunkt underholdskostnad framover i tid`() {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
            val søknadsbarn2 =
                Rolle(
                    ident = testdataBarn2.ident,
                    rolletype = Rolletype.BARN,
                    behandling = behandling,
                    fødselsdato = testdataBarn2.fødselsdato,
                    id = 5,
                    opphørsdato = LocalDate.now().plusMonths(10),
                )
            val søknadsbarn = behandling.søknadsbarn.first()
            behandling.roller.add(søknadsbarn2)
            behandling.oppdaterVirkningstidspunktForAlle(YearMonth.now().plusMonths(8).atDay(1))

            val underholdskostnadBarn1 =
                Underholdskostnad(
                    behandling = behandling,
                    id = 1,
                    person =
                        Person(
                            id = 1,
                            rolle = mutableSetOf(søknadsbarn),
                            ident = testdataBarn1.ident,
                            navn = testdataBarn1.navn,
                            fødselsdato = testdataBarn1.fødselsdato,
                        ),
                )
            underholdskostnadBarn1.faktiskeTilsynsutgifter =
                mutableSetOf(
                    FaktiskTilsynsutgift(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = null,
                        tilsynsutgift = BigDecimal(1000),
                    ),
                )
            underholdskostnadBarn1.barnetilsyn =
                mutableSetOf(
                    Barnetilsyn(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = null,
                        under_skolealder = true,
                        omfang = Tilsynstype.DELTID,
                        kilde = Kilde.MANUELL,
                    ),
                )
            underholdskostnadBarn1.tilleggsstønad =
                mutableSetOf(
                    Tilleggsstønad(
                        id = 1,
                        underholdskostnad = underholdskostnadBarn1,
                        fom = behandling.virkningstidspunkt!!,
                        tom = null,
                        `beløp` = BigDecimal(1000),
                    ),
                )
            val opphørsdato = LocalDate.now().plusMonths(10)

            behandling.underholdskostnader = mutableSetOf(underholdskostnadBarn1)
            every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
            virkningstidspunktService.oppdaterOpphørsdato(1, OppdaterOpphørsdatoRequestDto(søknadsbarn.id!!, opphørsdato = opphørsdato))

            assertSoftly(underholdskostnadBarn1.tilleggsstønad) {
                shouldHaveSize(1)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe null
            }
            assertSoftly(underholdskostnadBarn1.faktiskeTilsynsutgifter) {
                shouldHaveSize(1)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe null
            }
            assertSoftly(underholdskostnadBarn1.barnetilsyn) {
                shouldHaveSize(1)
                val sistePeriode = maxByOrNull { it.fom }
                sistePeriode!!.tom shouldBe null
            }
            val nyVirkningstidspunkt = LocalDate.now().plusMonths(2).withDayOfMonth(1)

            virkningstidspunktService.oppdatereVirkningstidspunkt(1, OppdatereVirkningstidspunkt(virkningstidspunkt = nyVirkningstidspunkt))
            assertSoftly(underholdskostnadBarn1.tilleggsstønad) {
                shouldHaveSize(1)
                val periode = maxByOrNull { it.fom }
                periode!!.fom shouldBe nyVirkningstidspunkt
            }
            assertSoftly(underholdskostnadBarn1.faktiskeTilsynsutgifter) {
                shouldHaveSize(1)
                val periode = maxByOrNull { it.fom }
                periode!!.fom shouldBe nyVirkningstidspunkt
            }
            assertSoftly(underholdskostnadBarn1.barnetilsyn) {
                shouldHaveSize(1)
                val periode = maxByOrNull { it.fom }
                periode!!.fom shouldBe nyVirkningstidspunkt
            }
        }
    }
}
