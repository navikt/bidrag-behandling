package no.nav.bidrag.behandling.controller

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldHaveSize
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
                    Any::class.java,
                )

            // så
            assertSoftly(svar) {
                shouldNotBeNull()
                statusCode shouldBe HttpStatus.CREATED
            }

            assertSoftly(svar.body as LinkedHashMap<*, *>) { b ->
                shouldNotBeNull()
                b shouldHaveSize 6
                b["id"] as Int shouldBeGreaterThan 0
                b["harTilsynsordning"] shouldBe null
                b["begrunnelse"] shouldBe null
                b["faktiskTilsynsutgift"] as ArrayList<*> shouldHaveSize 0
                b["stønadTilBarnetilsyn"] as ArrayList<*> shouldHaveSize 0
                b["tilleggsstønad"] as ArrayList<*> shouldHaveSize 0
            }

            assertSoftly((svar.body as LinkedHashMap<*, *>)["gjelderBarn"] as LinkedHashMap<*, *>) {
                it["id"].shouldNotBeNull()
                it["navn"] as String shouldBe request.navn
                it["fødselsdato"] as String shouldBe request.fødselsdato.toString()
                it["ident"].shouldBeNull()
                it["kilde"] as String shouldBe Kilde.MANUELL.name
                it["medIBehandlingen"] as Boolean shouldBe false
            }

            (svar.body as LinkedHashMap<*, *>)["valideringsfeil"].shouldBeNull()
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
            val eksisterendePerson = Person(ident = "11223312345", fødselsdato = LocalDate.now())
            testdataManager.lagrePersonIEgenTransaksjon(eksisterendePerson)

            testdataManager.lagreBehandlingNewTransaction(behandling)

            val request = BarnDto(personident = Personident(eksisterendePerson.ident!!))

            // hvis
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}/underhold/opprette",
                HttpMethod.POST,
                HttpEntity(request),
                Any::class.java,
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
                    Any::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()

                val b = body as LinkedHashMap<*, *>
                b shouldHaveSize 9
                b["harTilsynsordning"] as Boolean shouldBe oppdatereUnderholdRequest.harTilsynsordning
                b["begrunnelse"] as String shouldBe oppdatereUnderholdRequest.begrunnelse
                assertSoftly(b.get("valideringsfeil") as LinkedHashMap<*, *>) {
                    shouldHaveSize(7)
                    get("manglerPerioderForTilsynsutgifter") as Boolean shouldBe true
                }
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
                    Any::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()

                val b = body as LinkedHashMap<*, *>
                b shouldHaveSize 2
                assertSoftly(b["stønadTilBarnetilsyn"] as LinkedHashMap<*, *>) {
                    shouldHaveSize(5)
                    get("kilde") as String shouldBe Kilde.MANUELL.name
                    get("periode") shouldNotBe null
                    (get("periode") as LinkedHashMap<*, *>)["fom"] as String shouldBe forespørsel.periode.fom.toString()
                    (get("periode") as LinkedHashMap<*, *>)["tom"] shouldBe forespørsel.periode.tom
                    get("skolealder") as String shouldBe forespørsel.skolealder?.name
                    get("tilsynstype") as String shouldBe forespørsel.tilsynstype?.name
                }

                b["underholdskostnad"] as ArrayList<*> shouldHaveSize (3)
                b["valideringsfeil"] shouldBe null
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
                    Any::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()

                val b = body as LinkedHashMap<*, *>
                b shouldHaveSize 2
                b["faktiskTilsynsutgift"] as LinkedHashMap<*, *> shouldHaveSize (6)
                assertSoftly(b["faktiskTilsynsutgift"] as LinkedHashMap<*, *>) {
                    shouldHaveSize(6)
                    get("periode") shouldNotBe null
                    (get("periode") as LinkedHashMap<*, *>)["fom"] as String shouldBe forespørsel.periode.fom.toString()
                    (get("periode") as LinkedHashMap<*, *>)["tom"] shouldBe forespørsel.periode.tom
                    get("utgift") as Int shouldBe forespørsel.utgift.toInt()
                    get("kostpenger") as Int shouldBe forespørsel.kostpenger?.toInt()
                    get("kommentar") as String shouldBe forespørsel.kommentar
                }
                b["stønadTilBarnetilsyn"] shouldBe null
                b["tilleggsstønad"] shouldBe null
                b["underholdskostnad"] as ArrayList<*> shouldHaveSize (3)
                b["valideringsfeil"] shouldBe null
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
                    // TODO: Endre til OppdatereUnderholdResponse
                    Any::class.java,
                )

            // så
            assertSoftly(svar) {
                statusCode shouldBe HttpStatus.CREATED
                body.shouldNotBeNull()

                val b = body as LinkedHashMap<*, *>
                b shouldHaveSize 3
                b["tilleggsstønad"] as LinkedHashMap<*, *> shouldHaveSize (4)
                b["underholdskostnad"] as ArrayList<*> shouldHaveSize (3)

                assertSoftly(b.get("valideringsfeil") as LinkedHashMap<*, *>) {
                    shouldHaveSize(7)
                    get("tilleggsstønadsperioderUtenFaktiskTilsynsutgift") as ArrayList<*> shouldHaveSize 1
                }

                // TODO: Enable etter fikset org.springframework.web.client.UnknownContentTypeException: Could not extract response: no suitable HttpMessageConverter found for response type

                /*
                val b = body as OppdatereUnderholdResponse
                b.stønadTilBarnetilsyn.shouldBeNull()
                b.faktiskTilsynsutgift.shouldBeNull()
                b.tilleggsstønad.shouldNotBeNull()
                b.tilleggsstønad.periode.shouldNotBeNull()
                b.tilleggsstønad.periode.fom shouldBe forespørsel.periode.fom
                b.tilleggsstønad.periode.tom shouldBe forespørsel.periode.tom
                b.tilleggsstønad.dagsats shouldBe forespørsel.dagsats
                 */
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
                    person = Person(navn = navnAnnetBarnBp, fødselsdato = LocalDate.now()),
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
