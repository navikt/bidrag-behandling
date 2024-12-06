package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdResponse
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFailsWith

@RunWith(Enclosed::class)
class UnderholdControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @Autowired
    lateinit var personRepository: PersonRepository

    @Nested
    open inner class Opprette {
        @Test
        open fun `skal opprette underhold for barn`() {
            // gitt
            val navnAnnetBarnBp = "Stig E. Spill"
            val fødselsdatoAnnetBarn = LocalDate.now().minusMonths(143)
            val behandling = oppretteTestbehandling(inkludereBp = true, behandlingstype = TypeBehandling.BIDRAG)

            testdataManager.lagreBehandlingNewTransaction(behandling)

            val request = BarnDto(navn = navnAnnetBarnBp, fødselsdato = fødselsdatoAnnetBarn)

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
                faktiskTilsynsutgift.shouldBeEmpty()
                stønadTilBarnetilsyn.shouldBeEmpty()
                tilleggsstønad.shouldBeEmpty()
            }

            assertSoftly(svar.body!!.gjelderBarn) {
                id.shouldNotBeNull()
                navn shouldBe request.navn
                fødselsdato shouldBe request.fødselsdato
                ident.shouldBeNull()
                kilde shouldBe Kilde.MANUELL
                medIBehandlingen shouldBe false
            }
        }

        @Test
        open fun `skal ikke opprette underhold for annet barn uten personident hvis fødselsdato mangler`() {
            // gitt
            val navnAnnetBarnBp = "Stig E. Spill"
            val behandling = oppretteTestbehandling(inkludereBp = true, behandlingstype = TypeBehandling.BIDRAG)

            testdataManager.lagreBehandlingNewTransaction(behandling)

            val request = BarnDto(navn = navnAnnetBarnBp)

            // hvis, så
            assertFailsWith<RestClientException> {
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/opprette",
                    HttpMethod.POST,
                    HttpEntity(request),
                    UnderholdDto::class.java,
                )
            }
        }

        @Test
        @Transactional
        open fun `skal ikke opprette ny person for annet barn oppgitt med personident dersom person med samme ident allerede eksisterer`() {
            // gitt
            val behandling = oppretteTestbehandling(inkludereBp = true, behandlingstype = TypeBehandling.BIDRAG)
            val eksisterendePerson = Person(ident = "11223312345")
            testdataManager.lagrePersonIEgenTransaksjon(eksisterendePerson)

            testdataManager.lagreBehandlingNewTransaction(behandling)

            val request = BarnDto(personident = Personident(eksisterendePerson.ident!!))

            // hvis
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/underhold/opprette",
                HttpMethod.POST,
                HttpEntity(request),
                UnderholdDto::class.java,
            )

            // så
            val p = personRepository.findAll()
            p.filter { it.ident == eksisterendePerson.ident } shouldHaveSize 1

            assertSoftly(personRepository.findAll().filter { it.ident == eksisterendePerson.ident }) {
                shouldHaveSize(1)
                it.first().underholdskostnad shouldHaveSize 1
                it.first().rolle shouldHaveSize 0
            }
        }
    }

    @Nested
    open inner class Oppdatere {
        @Test
        @Transactional
        open fun `skal sette tilsynsordning`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val lagretBehandling = testdataManager.lagreBehandlingNewTransaction(behandling)

            val underholdsid = behandling.underholdskostnader.first().id!!
            lagretBehandling.underholdskostnader.find { it.id == underholdsid }?.harTilsynsordning shouldNotBe true

            val komponentbygger =
                UriComponentsBuilder
                    .fromUriString("${rootUriV2()}/behandling/${behandling.id}/underhold/$underholdsid/tilsynsordning")
                    .queryParam("harTilsynsordning", true)

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    komponentbygger.build().encode().toUri(),
                    HttpMethod.PUT,
                    null,
                    Object::class.java,
                )

            // så
            svar.shouldNotBeNull()
            svar.statusCode shouldBe HttpStatus.CREATED

            val oppdatertBehandling = behandlingRepository.findBehandlingById(lagretBehandling.id!!)
            oppdatertBehandling.shouldNotBeNull()

            oppdatertBehandling
                .get()
                .underholdskostnader
                .find { it.id == underholdsid }
                ?.harTilsynsordning shouldBe true
        }

        @Test
        @Transactional
        open fun `skal oppdatere begrunnelse for søknadsbarn`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val lagretBehandling = testdataManager.lagreBehandlingNewTransaction(behandling)

            val underholdsid = behandling.underholdskostnader.first().id!!
            lagretBehandling.underholdskostnader.find { it.id == underholdsid }?.harTilsynsordning shouldNotBe true

            val request = OppdatereBegrunnelseRequest(underholdsid, "Oppretter begrunnelse for søknadsbarn")

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/begrunnelse",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    Object::class.java,
                )

            // så
            svar.shouldNotBeNull()
            svar.statusCode shouldBe HttpStatus.CREATED

            val oppdatertBehandling = behandlingRepository.findBehandlingById(lagretBehandling.id!!)
            oppdatertBehandling.shouldNotBeNull()

            assertSoftly(
                oppdatertBehandling.get().notater.find {
                    behandling.underholdskostnader
                        .first()
                        .barnetsRolleIBehandlingen
                        ?.id == it.rolle.id &&
                        NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD == it.type
                },
            ) {
                shouldNotBeNull()
                innhold shouldBe "Oppretter begrunnelse for søknadsbarn"
            }
        }

        @Test
        open fun `skal angi tilsynsordning og oppdatere begrunnelse`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnader.first().id!!

            val oppdatereUnderholdRequest = OppdatereUnderholdRequest(true, "En grundig begrunnelse")

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/$underholdsid/oppdatere",
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
                oppretteTestbehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnader.first().id!!

            val forespørsel =
                StønadTilBarnetilsynDto(
                    periode = DatoperiodeDto(behandling.virkningstidspunktEllerSøktFomDato, null),
                    skolealder = Skolealder.OVER,
                    tilsynstype = Tilsynstype.DELTID,
                )

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/$underholdsid/barnetilsyn",
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
        @Transactional
        open fun `skal oppdatere faktiske tilsynsutgifter`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnader.first().id!!

            val forespørsel =
                OppdatereFaktiskTilsynsutgiftRequest(
                    periode = DatoperiodeDto(behandling.virkningstidspunktEllerSøktFomDato, null),
                    utgift = BigDecimal(6000),
                    kostpenger = BigDecimal(1000),
                    kommentar = "Gjelder frokost",
                )

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/$underholdsid/faktisk_tilsynsutgift",
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
                oppretteTestbehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            testdataManager.lagreBehandlingNewTransaction(behandling)
            val underholdsid = behandling.underholdskostnader.first().id!!

            val forespørsel =
                OppdatereTilleggsstønadRequest(
                    periode = DatoperiodeDto(behandling.virkningstidspunktEllerSøktFomDato, null),
                    dagsats = BigDecimal(365),
                )

            // hvis
            val svar =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/underhold/$underholdsid/tilleggsstonad",
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
                oppretteTestbehandling(
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            behandling.underholdskostnader.add(
                Underholdskostnad(
                    behandling = behandling,
                    person = Person(navn = navnAnnetBarnBp),
                ),
            )
            val lagretBehandling = testdataManager.lagreBehandlingNewTransaction(behandling)
            lagretBehandling.underholdskostnader shouldHaveSize 3

            val u =
                lagretBehandling.underholdskostnader.find { it.barnetsRolleIBehandlingen == null && it.person.navn == navnAnnetBarnBp }

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

            oppdatertBehandling.underholdskostnader shouldHaveSize 2

            assertSoftly(oppdatertBehandling.underholdskostnader) {
                it shouldHaveSize 2
                it.filter { it.person.navn == u.person.navn } shouldHaveSize 0
            }
        }
    }
}
