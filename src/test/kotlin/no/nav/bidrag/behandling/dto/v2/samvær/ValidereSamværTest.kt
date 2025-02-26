package no.nav.bidrag.behandling.dto.v2.samvær

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.transformers.validering.opprettRolle
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test

class ValidereSamværTest {
    @Test
    fun `skal validere samvær hvis har opphørsdato`() {
        val bmIdent = "31233123"
        val barn1Ident = "123123213213"
        val barn2Ident = "44444"

        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.now().minusMonths(5).atDay(1)
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barn1Ident, Rolletype.BARN, LocalDate.now().plusMonths(1).withDayOfMonth(1), behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().minusMonths(1).withDayOfMonth(1), behandling),
            )
        behandling.roller = roller
        behandling.leggTilNotat("dd", NotatGrunnlag.NotatType.SAMVÆR, behandling.søknadsbarn[0])
        behandling.leggTilNotat("dd", NotatGrunnlag.NotatType.SAMVÆR, behandling.søknadsbarn[1])

        val samværBarn1 =
            opprettSamvær(
                behandling = behandling,
                gjelderBarn = barn1Ident,
                perioder =
                    listOf(
                        ÅrMånedsperiode(YearMonth.now().minusMonths(5), YearMonth.now().minusMonths(4)),
                        ÅrMånedsperiode(YearMonth.now().minusMonths(3), YearMonth.now().minusMonths(2)),
                    ),
            )
        val samværBarn2 =
            opprettSamvær(
                behandling = behandling,
                gjelderBarn = barn2Ident,
                perioder =
                    listOf(
                        ÅrMånedsperiode(YearMonth.now().minusMonths(5), YearMonth.now().minusMonths(4)),
                        ÅrMånedsperiode(YearMonth.now().minusMonths(3), YearMonth.now().minusMonths(2)),
                    ),
            )
        assertSoftly(samværBarn1) {
            val valideringsfeil = it.mapValideringsfeil()
            valideringsfeil.harFeil shouldBe true
            valideringsfeil.ingenLøpendeSamvær shouldBe true
        }

        assertSoftly(samværBarn2) {
            val valideringsfeil = it.mapValideringsfeil()
            valideringsfeil.harFeil shouldBe false
        }
    }

    @Test
    fun `skal validere samvær hvis har opphørsdato inneværende måned`() {
        val bmIdent = "31233123"
        val barn1Ident = "123123213213"
        val barn2Ident = "44444"

        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.now().minusMonths(5).atDay(1)
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barn1Ident, Rolletype.BARN, LocalDate.now().withDayOfMonth(1), behandling),
            )
        behandling.roller = roller
        behandling.leggTilNotat("dd", NotatGrunnlag.NotatType.SAMVÆR, behandling.søknadsbarn[0])

        val samværBarn1 =
            opprettSamvær(
                behandling = behandling,
                gjelderBarn = barn1Ident,
                perioder =
                    listOf(
                        ÅrMånedsperiode(behandling.virkningstidspunkt!!.withDayOfMonth(1), YearMonth.now().minusMonths(4).atEndOfMonth()),
                        ÅrMånedsperiode(YearMonth.now().minusMonths(3).atDay(1), YearMonth.now().atDay(1).minusDays(1)),
                    ),
            )

        assertSoftly(samværBarn1) {
            val valideringsfeil = it.mapValideringsfeil()
            valideringsfeil.harFeil shouldBe false
            valideringsfeil.ingenLøpendeSamvær shouldBe false
        }
    }

    @Test
    fun `skal validere samvær hvis har opphørsdato med ugyldig sluttperiode`() {
        val bmIdent = "31233123"
        val barn1Ident = "123123213213"
        val barn2Ident = "44444"

        val behandling = oppretteBehandling()
        behandling.virkningstidspunkt = YearMonth.now().minusMonths(5).atDay(1)
        val roller =
            mutableSetOf(
                opprettRolle(bmIdent, Rolletype.BIDRAGSMOTTAKER, behandling = behandling),
                opprettRolle(barn1Ident, Rolletype.BARN, LocalDate.now().minusMonths(2).withDayOfMonth(1), behandling),
                opprettRolle(barn2Ident, Rolletype.BARN, LocalDate.now().minusMonths(1).withDayOfMonth(1), behandling),
            )
        behandling.roller = roller
        behandling.leggTilNotat("dd", NotatGrunnlag.NotatType.SAMVÆR, behandling.søknadsbarn[0])
        behandling.leggTilNotat("dd", NotatGrunnlag.NotatType.SAMVÆR, behandling.søknadsbarn[1])

        val samværBarn1 =
            opprettSamvær(
                behandling = behandling,
                gjelderBarn = barn1Ident,
                perioder =
                    listOf(
                        ÅrMånedsperiode(YearMonth.now().minusMonths(5), YearMonth.now().minusMonths(4)),
                        ÅrMånedsperiode(YearMonth.now().minusMonths(3), YearMonth.now().minusMonths(2)),
                    ),
            )
        assertSoftly(samværBarn1) {
            val valideringsfeil = it.mapValideringsfeil()
            valideringsfeil.harFeil shouldBe true
            valideringsfeil.ingenLøpendeSamvær shouldBe false
            valideringsfeil.ugyldigSluttperiode shouldBe true
        }
    }
}

fun opprettSamvær(
    gjelderBarn: String? = null,
    perioder: List<ÅrMånedsperiode>,
    behandling: Behandling = oppretteBehandling(),
): Samvær {
    val samvær =
        Samvær(
            id = 1,
            behandling = behandling,
            rolle = behandling.roller.find { it.ident == gjelderBarn }!!,
        )

    perioder.forEach {
        samvær.perioder.add(
            Samværsperiode(
                id = 1,
                fom = it.fom.atDay(1),
                tom = it.til?.atEndOfMonth(),
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1,
                samvær = samvær,
            ),
        )
    }
    return samvær
}
