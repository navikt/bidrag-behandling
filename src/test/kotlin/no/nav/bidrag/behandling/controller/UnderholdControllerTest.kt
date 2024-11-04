package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdResponse
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@RunWith(Enclosed::class)
class UnderholdControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @Nested
    open inner class Opprette {

        @Test
        open fun `skal opprette underhold for barn`() {

            // gitt
            val navnAnnetBarnBp = "Stig E. Spill"
            val behandling =
                oppretteBehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)

            val request = BarnDto(navn = navnAnnetBarnBp)

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/opprette",
                    HttpMethod.POST,
                    HttpEntity(request),
                    UnderholdDto::class.java,
                )

            // så
            assertSoftly(svar) {
                shouldNotBeNull()
                statusCode shouldBe HttpStatus.CREATED
            }

            assertSoftly(svar.body) {
                shouldNotBeNull()
                id shouldBeGreaterThan 0L
                harTilsynsordning.shouldBeNull()
                begrunnelse.shouldBeNull()
                faktiskeTilsynsutgifter.shouldBeEmpty()
                stønadTilBarnetilsyn.shouldBeEmpty()
                tilleggsstønad.shouldBeEmpty()
            }

            assertSoftly(svar.body!!.gjelderBarn) {
                id.shouldNotBeNull()
                navn shouldBe request.navn
                ident.shouldBeNull()
                kilde shouldBe Kilde.MANUELL
                fødselsdato.shouldBeNull()
                medIBehandlingen shouldBe false
            }
        }

    }

    @Nested
    open inner class Oppdatere {

        @Test
        open fun `skal angi tilsynsordning og oppdatere begrunnelse`() {

            // gitt
            val behandling =
                oppretteBehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnad.first().id!!

            val oppdatereUnderholdRequest = OppdatereUnderholdRequest(true, "En grundig begrunnelse")

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/${underholdsid}/oppdatere",
                    HttpMethod.PUT,
                    HttpEntity(oppdatereUnderholdRequest),
                    UnderholdDto::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()
                body!!.id shouldBe underholdsid
                body!!.harTilsynsordning shouldBe oppdatereUnderholdRequest.harTilsynsordning
                body!!.begrunnelse shouldBe oppdatereUnderholdRequest.begrunnelse
            }

            assertSoftly(underholdskostnadRepository.findById(underholdsid)) {
                it.shouldNotBeNull()
                it.get().harTilsynsordning.shouldNotBeNull()
                it.get().harTilsynsordning shouldBe oppdatereUnderholdRequest.harTilsynsordning
            }
        }

        @Test
        open fun `skal oppdatere stønad til barnetilsyn`() {

            // gitt
            val behandling =
                oppretteBehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnad.first().id!!

            val forespørsel = StønadTilBarnetilsynDto(
                periode = DatoperiodeDto(behandling.virkningstidspunktEllerSøktFomDato, null),
                skolealder = Skolealder.OVER,
                tilsynstype = Tilsynstype.DELTID
            )

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/${underholdsid}/barnetilsyn",
                    HttpMethod.PUT,
                    HttpEntity(forespørsel),
                    OppdatereUnderholdResponse::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()
                body!!.faktiskTilsynsutgift.shouldBeNull()
                body!!.tilleggsstønad.shouldBeNull()
                body!!.stønadTilBarnetilsyn.shouldNotBeNull()
                body!!.stønadTilBarnetilsyn!!.kilde shouldBe Kilde.MANUELL
                body!!.stønadTilBarnetilsyn!!.periode.shouldNotBeNull()
                body!!.stønadTilBarnetilsyn!!.periode.fom shouldBe forespørsel.periode.fom
                body!!.stønadTilBarnetilsyn!!.periode.tom shouldBe forespørsel.periode.tom
                body!!.stønadTilBarnetilsyn!!.skolealder shouldBe forespørsel.skolealder
                body!!.stønadTilBarnetilsyn!!.tilsynstype shouldBe forespørsel.tilsynstype
            }
        }

        @Test
        open fun `skal oppdatere faktiske tilsynsutgifter`() {

            // gitt
            val behandling =
                oppretteBehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnad.first().id!!

            val forespørsel = FaktiskTilsynsutgiftDto(
                periode = DatoperiodeDto(behandling.virkningstidspunktEllerSøktFomDato, null),
                utgift = BigDecimal(6000),
                kostpenger = BigDecimal(1000),
                kommentar = "Gjelder frokost",
            )

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/${underholdsid}/faktisk_tilsynsutgift",
                    HttpMethod.PUT,
                    HttpEntity(forespørsel),
                    OppdatereUnderholdResponse::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()
                body!!.stønadTilBarnetilsyn.shouldBeNull()
                body!!.tilleggsstønad.shouldBeNull()
                body!!.faktiskTilsynsutgift.shouldNotBeNull()
                body!!.faktiskTilsynsutgift!!.periode.shouldNotBeNull()
                body!!.faktiskTilsynsutgift!!.periode.fom shouldBe forespørsel.periode.fom
                body!!.faktiskTilsynsutgift!!.periode.tom shouldBe forespørsel.periode.tom
                body!!.faktiskTilsynsutgift!!.utgift shouldBe forespørsel.utgift
                body!!.faktiskTilsynsutgift!!.kostpenger shouldBe forespørsel.kostpenger
                body!!.faktiskTilsynsutgift!!.kommentar shouldBe forespørsel.kommentar
            }
        }

        @Test
        open fun `skal oppdatere tilleggsstønad`() {

            // gitt
            val behandling =
                oppretteBehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnad.first().id!!

            val forespørsel = TilleggsstønadDto(
                periode = DatoperiodeDto(behandling.virkningstidspunktEllerSøktFomDato, null),
                dagsats = BigDecimal(365)
            )

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/${underholdsid}/tilleggsstonad",
                    HttpMethod.PUT,
                    HttpEntity(forespørsel),
                    OppdatereUnderholdResponse::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()
                body!!.stønadTilBarnetilsyn.shouldBeNull()
                body!!.faktiskTilsynsutgift.shouldBeNull()
                body!!.tilleggsstønad.shouldNotBeNull()
                body!!.tilleggsstønad!!.periode.shouldNotBeNull()
                body!!.tilleggsstønad!!.periode.fom shouldBe forespørsel.periode.fom
                body!!.tilleggsstønad!!.periode.tom shouldBe forespørsel.periode.tom
                body!!.tilleggsstønad!!.dagsats shouldBe forespørsel.dagsats
            }
        }
    }

    @Nested
    open inner class Slette {

        @Test
        @Transactional
        open fun `skal slette underhold og barn`() {

            // gitt
            val navnAnnetBarnBp = "Stig E. Spill"
            val behandling =
                oppretteBehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            behandling.underholdskostnad.add(
                Underholdskostnad(
                    behandling = behandling,
                    person = Person(navn = navnAnnetBarnBp)
                )
            )
            val lagretBehandling = testdataManager.lagreBehandlingNewTransaction(behandling)
            lagretBehandling.underholdskostnad shouldHaveSize 3

            val u =
                lagretBehandling.underholdskostnad.find { it.person.rolle.isEmpty() && it.person.navn == navnAnnetBarnBp }

            val sletteUnderholdselement = SletteUnderholdselement(u?.id!!, u.person.id!!, Underholdselement.BARN)

            assertSoftly(u) {
                id.shouldNotBeNull()
                person.id.shouldNotBeNull()
            }

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold",
                    HttpMethod.DELETE,
                    HttpEntity(sletteUnderholdselement),
                    Any::class.java,
                )

            // så
            assertSoftly(svar) {
                shouldNotBeNull()
                statusCode shouldBe HttpStatus.ACCEPTED
                body.shouldBeNull()
            }
            val oppdatertBehandling = behandlingRepository.findBehandlingById(lagretBehandling.id!!).get()

            oppdatertBehandling.underholdskostnad shouldHaveSize 2

            assertSoftly(oppdatertBehandling.underholdskostnad) {
                it shouldHaveSize 2
                it.filter { it.person.navn == u.person.navn } shouldHaveSize 0
            }
        }
    }

}
