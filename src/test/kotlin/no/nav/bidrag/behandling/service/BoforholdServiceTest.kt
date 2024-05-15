package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.finnHusstandsbarnperiode
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdaterHusstandsmedlemPeriode
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.dto.v2.boforhold.OpprettHusstandsstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.Sivilstandsperiode
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.opprettBoforholdBearbeidetGrunnlagForHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.opprettHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.opprettHusstandsbarnMedOffentligePerioder
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertFailsWith

@RunWith(Enclosed::class)
class BoforholdServiceTest : TestContainerRunner() {
    @MockBean
    lateinit var bidragPersonConsumer: BidragPersonConsumer

    @Autowired
    lateinit var boforholdService: BoforholdService

    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var entityManager: EntityManager

    @Nested
    open inner class Husstandsbarnstester {

        @Nested
        open inner class Førstegangsinnhenting {
            @Test
            @Transactional
            open fun `skal lagre periodisert boforhold basert på førstegangsinnhenting av grunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                // Fjerner eksisterende barn for å simulere førstegangsinnhenting av grunnlag
                behandling.husstandsbarn.removeAll(behandling.husstandsbarn)
                entityManager.persist(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()

                val periode1Til = testdataBarn2.fødselsdato.plusMonths(19)
                val periode2Fra = testdataBarn2.fødselsdato.plusMonths(44)

                val grunnlagBoforhold =
                    listOf(
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn1.ident,
                            fødselsdato = testdataBarn1.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn1.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn1.fødselsdato,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn2.ident,
                            fødselsdato = testdataBarn2.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn2.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn2.fødselsdato,
                                    periodeTil = periode1Til,
                                ),
                                BorISammeHusstandDto(
                                    periodeFra = periode2Fra,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                    )

                val periodisertBoforhold =
                    BoforholdApi.beregnV2(
                        testdataBarn2.fødselsdato,
                        grunnlagBoforhold.tilBoforholdRequest(testdataBarn2.fødselsdato),
                    )

                // hvis
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(
                    behandling,
                    periodisertBoforhold,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsbarn) {
                    it.size shouldBe 2
                    it.first { barn -> testdataBarn2.ident == barn.ident }.perioder.size shouldBe 3
                }
            }

            @Test
            @Transactional
            open fun `skal erstatte lagrede offentlige husstandsbarn med nye fra grunnlag ved førstegangsinnhenting `() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                stubbeHentingAvPersoninfoForTestpersoner()

                val grunnlagBoforhold =
                    listOf(
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = behandling.søknadsbarn.first().ident,
                            fødselsdato = behandling.søknadsbarn.first().foedselsdato,
                            erBarnAvBmBp = true,
                            navn = behandling.søknadsbarn.first().navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = behandling.søknadsbarn.first().foedselsdato,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                    )

                val periodisertBoforhold =
                    BoforholdApi.beregnV2(LocalDate.now(), grunnlagBoforhold.tilBoforholdRequest(LocalDate.now()))

                behandling.husstandsbarn.size shouldBe 2

                // hvis
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(
                    behandling,
                    periodisertBoforhold,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly {
                    behandling.husstandsbarn.size shouldBe 1
                }
            }
        }

        @Nested
        open inner class OppdatereAutomatisk {
            @Test
            @Transactional
            open fun `skal oppdatere automatisk innhenta husstandsbarn og overskrive manuell informasjon`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                stubbeHentingAvPersoninfoForTestpersoner()
                val periode1Til = testdataBarn2.fødselsdato.plusMonths(19)
                val periode2Fra = testdataBarn2.fødselsdato.plusMonths(44)

                val grunnlagBoforhold =
                    listOf(
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn1.ident,
                            fødselsdato = testdataBarn1.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn1.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn1.fødselsdato,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn2.ident,
                            fødselsdato = testdataBarn2.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn2.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn2.fødselsdato,
                                    periodeTil = periode1Til,
                                ),
                                BorISammeHusstandDto(
                                    periodeFra = periode2Fra,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                    )

                assertSoftly(behandling.husstandsbarn) { hb ->
                    hb.size shouldBe 2
                    hb.find { testdataBarn1.ident == it.ident } shouldNotBe null
                    hb.find { testdataBarn1.ident == it.ident }?.perioder shouldNotBe emptySet<Husstandsbarnperiode>()
                }
                assertSoftly(behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }?.perioder) { p ->
                    p?.size shouldBe 3
                    p?.filter { Kilde.MANUELL == it.kilde }?.size shouldBe 1
                }

                val periodeFom = minOf(testdataBarn1.fødselsdato, testdataBarn2.fødselsdato)
                val periodisertBoforhold =
                    BoforholdApi.beregnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdRequest(periodeFom),
                    )

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaBoforhold(
                    behandling,
                    periodisertBoforhold,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsbarn) { husstandsbarn ->
                    husstandsbarn.size shouldBe 2
                }

                assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }) { barn1 ->
                    barn1 shouldNotBe null
                    barn1!!.perioder.size shouldBe 1
                    barn1.perioder.filter { Kilde.MANUELL == it.kilde } shouldBe emptyList()
                }

                assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn2.ident }) { barn2 ->
                    barn2 shouldNotBe null
                    barn2!!.perioder.size shouldBe 3
                    barn2.perioder.filter { Kilde.MANUELL == it.kilde } shouldBe emptyList()
                }
            }

            @Test
            @Transactional
            open fun `skal oppdatere automatisk innhenta husstandsbarn og flette inn manuell informasjon`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()
                behandling.virkningstidspunkt = testdataBarn1.fødselsdato
                behandling.husstandsbarn.forEach {
                    it.perioder.forEach {
                        it.kilde = Kilde.MANUELL
                    }
                }

                stubbeHentingAvPersoninfoForTestpersoner()
                val periode1Til = testdataBarn2.fødselsdato.plusMonths(19)
                val periode2Fra = testdataBarn2.fødselsdato.plusMonths(44)

                val grunnlagBoforhold =
                    listOf(
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn1.ident,
                            fødselsdato = testdataBarn1.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn1.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn1.fødselsdato,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn2.ident,
                            fødselsdato = testdataBarn2.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn2.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn2.fødselsdato,
                                    periodeTil = periode1Til,
                                ),
                                BorISammeHusstandDto(
                                    periodeFra = periode2Fra,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                    )

                val periodeFom = minOf(testdataBarn1.fødselsdato, testdataBarn2.fødselsdato)
                val periodisertBoforhold =
                    BoforholdApi.beregnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdRequest(periodeFom),
                    )

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaBoforhold(
                    behandling,
                    periodisertBoforhold,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    false,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsbarn) { husstandsbarn ->
                    husstandsbarn.size shouldBe 2
                }

                assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }) { barn1 ->
                    barn1 shouldNotBe null
                    barn1!!.perioder.size shouldBe 4
                    barn1.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 3
                    barn1.perioder.last().kilde shouldBe Kilde.MANUELL
                    barn1.perioder.last().datoFom shouldBe LocalDate.of(2023, 6, 1)
                    barn1.perioder.last().datoTom shouldBe null
                    barn1.perioder.last().bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                }

                assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn2.ident }) { barn2 ->
                    barn2 shouldNotBe null
                    barn2!!.perioder.size shouldBe 3
                    barn2.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 3
                    barn2.perioder.last().kilde shouldBe Kilde.MANUELL
                    barn2.perioder.last().datoFom shouldBe LocalDate.of(2023, 6, 1)
                    barn2.perioder.last().datoTom shouldBe null
                    barn2.perioder.last().bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                }
            }

            @Test
            @Transactional
            open fun `skal endre kilde til manuell for offentlig husstandsbarn som ikke finnes i nyeste grunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()
                behandling.virkningstidspunkt = testdataBarn1.fødselsdato
                behandling.husstandsbarn.forEach {
                    it.perioder.forEach {
                        it.kilde = Kilde.MANUELL
                    }
                }

                assertSoftly(behandling.husstandsbarn) { hb ->
                    hb shouldHaveSize 2
                    hb.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                }

                stubbeHentingAvPersoninfoForTestpersoner()

                val grunnlagBoforhold =
                    listOf(
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn1.ident,
                            fødselsdato = testdataBarn1.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn1.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn1.fødselsdato,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                    )

                val periodeFom = minOf(testdataBarn1.fødselsdato, testdataBarn2.fødselsdato)
                val periodisertBoforhold =
                    BoforholdApi.beregnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdRequest(periodeFom),
                    )

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaBoforhold(
                    behandling,
                    periodisertBoforhold,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    false,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsbarn) { husstandsbarn ->
                    husstandsbarn.size shouldBe 2
                }

                assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }) { barn1 ->
                    barn1 shouldNotBe null
                    barn1!!.kilde shouldBe Kilde.OFFENTLIG
                    barn1.perioder.size shouldBe 4
                    barn1.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 3
                    barn1.perioder.last().kilde shouldBe Kilde.MANUELL
                    barn1.perioder.last().datoFom shouldBe LocalDate.of(2023, 6, 1)
                    barn1.perioder.last().datoTom shouldBe null
                    barn1.perioder.last().bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                }

                assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn2.ident }) { barn2 ->
                    barn2 shouldNotBe null
                    barn2!!.kilde shouldBe Kilde.MANUELL
                    barn2.perioder.size shouldBe 3
                    barn2.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 3
                    barn2.perioder.last().kilde shouldBe Kilde.MANUELL
                    barn2.perioder.last().datoFom shouldBe LocalDate.of(2023, 6, 1)
                    barn2.perioder.last().datoTom shouldBe null
                    barn2.perioder.last().bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                }
            }

            @Test
            @Transactional
            open fun `skal slette offentlige husstandsbarn som ikke finnes i nyeste grunnlag hvis overskriving er valgt`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()
                behandling.virkningstidspunkt = testdataBarn1.fødselsdato
                behandling.husstandsbarn.forEach {
                    it.perioder.forEach {
                        it.kilde = Kilde.MANUELL
                    }
                }

                assertSoftly(behandling.husstandsbarn) { hb ->
                    hb shouldHaveSize 2
                    hb.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                }

                stubbeHentingAvPersoninfoForTestpersoner()

                val grunnlagBoforhold =
                    listOf(
                        RelatertPersonGrunnlagDto(
                            partPersonId = behandling.bidragsmottaker!!.ident!!,
                            relatertPersonPersonId = testdataBarn1.ident,
                            fødselsdato = testdataBarn1.fødselsdato,
                            erBarnAvBmBp = true,
                            navn = testdataBarn1.navn,
                            borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn1.fødselsdato,
                                    periodeTil = null,
                                ),
                            ),
                        ),
                    )

                val periodeFom = minOf(testdataBarn1.fødselsdato, testdataBarn2.fødselsdato)
                val periodisertBoforhold =
                    BoforholdApi.beregnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdRequest(periodeFom),
                    )

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaBoforhold(
                    behandling,
                    periodisertBoforhold,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsbarn) { husstandsbarn ->
                    husstandsbarn.size shouldBe 1
                }

                assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }) { barn1 ->
                    barn1 shouldNotBe null
                    barn1!!.kilde shouldBe Kilde.OFFENTLIG
                    barn1.perioder.size shouldBe 1
                    barn1.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 0
                    barn1.perioder.last().kilde shouldBe Kilde.OFFENTLIG
                    barn1.perioder.last().datoFom shouldBe testdataBarn1.fødselsdato
                    barn1.perioder.last().datoTom shouldBe null
                    barn1.perioder.last().bostatus shouldBe Bostatuskode.MED_FORELDER
                }
            }
        }

        @Nested
        open inner class OppdatereManuelt {


            @Test
            @Transactional
            open fun `skal få slette både manuelle og offentlige husstandsbarnperioder`() {

                // gitt
                val behandling = opprettBehandlingForBoforholdTest()
                stubbeHentingAvPersoninfoForTestpersoner()

                val husstandsbarnperiodeSomSkalSlettes = behandling.husstandsbarn.first().perioder.first()
                assertSoftly {
                    husstandsbarnperiodeSomSkalSlettes shouldNotBe null
                    behandling.finnHusstandsbarnperiode(husstandsbarnperiodeSomSkalSlettes.id).shouldNotBeNull()
                }

                // hvis
                val oppdatereBoforholdResponse =
                    boforholdService.oppdatereHusstandsbarnManuelt(
                        behandling.id!!,
                        OppdatereHusstandsmedlem(slettPeriode = husstandsbarnperiodeSomSkalSlettes.id!!),
                    )

                // så
                assertSoftly(oppdatereBoforholdResponse) {
                    it.oppdatertHusstandsbarn shouldNotBe null
                    it.oppdatertHusstandsbarn!!.ident shouldBe husstandsbarnperiodeSomSkalSlettes.husstandsbarn.ident
                }
                assertSoftly(behandling.husstandsbarn.find { it == husstandsbarnperiodeSomSkalSlettes.husstandsbarn }) {
                    it.shouldNotBeNull()
                    it.perioder.find { it == husstandsbarnperiodeSomSkalSlettes } shouldBe null
                    behandling.finnHusstandsbarnperiode(husstandsbarnperiodeSomSkalSlettes.id).shouldBeNull()
                }
            }

            @Test
            @Transactional
            open fun `skal ikke få slette husstandsbarn med kilde offentlig`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                // hvis, så
                assertFailsWith<HttpClientErrorException> {
                    boforholdService.oppdatereHusstandsbarnManuelt(
                        behandling.id!!,
                        OppdatereHusstandsmedlem(
                            slettHusstandsmedlem = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }!!.id,
                        ),
                    )
                }
            }

            @Test
            @Transactional
            open fun `skal kunne slette manuelt husstandsbarn`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                val husstandsbarn = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }
                husstandsbarn?.kilde = Kilde.MANUELL

                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(slettHusstandsmedlem = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }!!.id),
                )

                assertSoftly {
                    behandling.husstandsbarn.find { testdataBarn1.ident == it.ident } shouldBe null
                }
            }

            @Test
            @Transactional
            open fun `skal tilbakestille til offentlig opplysninger`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                val husstandsbarn = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }!!
                husstandsbarn.perioder.shouldHaveSize(3)
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(tilbakestillPerioderForHusstandsmedlem = husstandsbarn.id),
                )

                assertSoftly {
                    husstandsbarn.perioder.shouldHaveSize(2)
                    husstandsbarn.perioder.filter { it.kilde == Kilde.MANUELL }.shouldBeEmpty()
                    husstandsbarn.forrigePerioder.shouldNotBeEmpty()
                }
            }

            @Test
            @Transactional
            open fun `skal slette periode`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                val husstandsbarn = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }!!
                husstandsbarn.perioder.shouldHaveSize(3)
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(slettPeriode = husstandsbarn.perioder.find { it.kilde == Kilde.MANUELL }!!.id),
                )
                husstandsbarn.perioder.shouldHaveSize(2)
            }

            @Test
            open fun `skal ikke slette periode hvis det er bare en igjen`() {
                // gitt
                var behandling = opprettBehandlingForBoforholdTest()

                val fødselsdato = LocalDate.now().minusYears(5)
                val ident = "213123"
                val husstandsbarn =
                    Husstandsbarn(
                        behandling = behandling,
                        kilde = Kilde.MANUELL,
                        perioder = mutableSetOf(),
                        ident = ident,
                        fødselsdato = fødselsdato,
                    )
                husstandsbarn.perioder.add(
                    Husstandsbarnperiode(
                        husstandsbarn = husstandsbarn,
                        datoFom = LocalDate.parse("2020-01-01"),
                        datoTom = null,
                        kilde = Kilde.MANUELL,
                        bostatus = Bostatuskode.MED_FORELDER,
                    ),
                )
                behandling.husstandsbarn.add(husstandsbarn)
                behandling = testdataManager.lagreBehandling(behandling)
                val husstandsmedlemPersisted = behandling.husstandsbarn.find { it.ident == ident }

                val exception =
                    assertThrows<HttpClientErrorException> {
                        boforholdService.oppdatereHusstandsbarnManuelt(
                            behandling.id!!,
                            OppdatereHusstandsmedlem(
                                slettPeriode = husstandsmedlemPersisted!!.perioder.first().id,
                            ),
                        )
                    }

                exception.message shouldContain "Kan ikke slette alle perioder fra husstandsmedlem"
                exception.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            @Test
            @Transactional
            open fun `skal angre forrige endring`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                val husstandsbarn = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }!!
                val periodeSomSkalOppdateres = husstandsbarn.perioder.maxByOrNull { it.datoFom!! }!!
                husstandsbarn.perioder.shouldHaveSize(3)

                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        oppdaterPeriode =
                        OppdaterHusstandsmedlemPeriode(
                            idHusstandsbarn = husstandsbarn.id!!,
                            idPeriode = periodeSomSkalOppdateres.id,
                            datoFom = LocalDate.parse("2024-02-01"),
                            datoTom = null,
                            bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        ),
                    ),
                )

                assertSoftly("Resultat etter første oppdatering") {
                    husstandsbarn.perioder.shouldHaveSize(2)
                    husstandsbarn.forrigePerioder.shouldNotBeEmpty()
                    val førstePeriode = husstandsbarn.perioder.minBy { it.datoFom!! }
                    val andrePeriode = husstandsbarn.perioder.maxBy { it.datoFom!! }
                    førstePeriode!!.datoFom shouldBe LocalDate.parse("2023-01-01")
                    andrePeriode!!.datoFom shouldBe LocalDate.parse("2023-06-01")
                }

                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        angreSisteStegForHusstandsmedlem = husstandsbarn.id,
                    ),
                )

                assertSoftly("Resultat etter angre forrige steg") {
                    husstandsbarn.perioder.shouldHaveSize(3)
                    husstandsbarn.forrigePerioder.shouldNotBeEmpty()
                    val førstePeriode = husstandsbarn.perioder.minBy { it.datoFom!! }
                    val sistePeriode = husstandsbarn.perioder.maxBy { it.datoFom!! }
                    førstePeriode.datoFom shouldBe LocalDate.parse("2023-01-01")
                    sistePeriode.datoFom shouldBe LocalDate.parse("2024-01-01")
                    sistePeriode.kilde shouldBe Kilde.MANUELL
                }

                // hvis
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        angreSisteStegForHusstandsmedlem = husstandsbarn.id,
                    ),
                )

                // så
                husstandsbarn.perioder.shouldHaveSize(2)
            }

            @Test
            @Transactional
            open fun `skal opprette husstandsmedlem`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                behandling.husstandsbarn.shouldHaveSize(1)
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        opprettHusstandsmedlem =
                        OpprettHusstandsstandsmedlem(
                            personident = Personident("213123"),
                            fødselsdato = LocalDate.parse("2020-02-01"),
                            navn = "Navn Navnesen",
                        ),
                    ),
                )

                assertSoftly(behandling.husstandsbarn.find { it.ident == "213123" }!!) {
                    it.perioder.shouldHaveSize(1)
                    it.navn shouldBe "Navn Navnesen"
                    it.fødselsdato shouldBe LocalDate.parse("2020-02-01")
                    val periode = it.perioder.first()
                    periode.kilde shouldBe Kilde.MANUELL
                    periode.datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                    periode.datoTom shouldBe null
                    periode.bostatus shouldBe Bostatuskode.MED_FORELDER
                }
            }

            @Test
            @Transactional
            open fun `skal slette husstandsmedlem`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                behandling.husstandsbarn.shouldHaveSize(1)
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        opprettHusstandsmedlem =
                        OpprettHusstandsstandsmedlem(
                            personident = Personident("213123"),
                            fødselsdato = LocalDate.parse("2020-02-01"),
                            navn = "Navn Navnesen",
                        ),
                    ),
                )
                behandling.husstandsbarn.shouldHaveSize(2)
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        slettHusstandsmedlem = behandling.husstandsbarn.find { it.ident == "213123" }!!.id,
                    ),
                )
                behandling.husstandsbarn.shouldHaveSize(1)
            }

            @Test
            @Transactional
            @Disabled("Magnus skal fikse Boforhold api slik at oppføreselen blir riktig")
            open fun `skal opprette husstandsmedlem over 18`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                behandling.husstandsbarn.shouldHaveSize(1)
                val fødselsdato = LocalDate.now().minusYears(17).minusMonths(7)
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        opprettHusstandsmedlem =
                        OpprettHusstandsstandsmedlem(
                            personident = Personident("213123"),
                            fødselsdato = fødselsdato,
                            navn = "Navn Navnesen",
                        ),
                    ),
                )

                assertSoftly(behandling.husstandsbarn.find { it.ident == "213123" }!!) {
                    it.perioder.shouldHaveSize(2)
                    it.navn shouldBe "Navn Navnesen"
                    it.fødselsdato shouldBe fødselsdato
                    val periode = it.perioder.first()
                    periode.kilde shouldBe Kilde.MANUELL
                    periode.datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                    periode.datoTom shouldBe YearMonth.now().minusYears(18).atEndOfMonth()
                    periode.bostatus shouldBe Bostatuskode.MED_FORELDER

                    val periode2 = it.perioder.toList()[1]
                    periode2.kilde shouldBe Kilde.MANUELL
                    periode2.datoFom shouldBe LocalDate.now().minusYears(18).plusMonths(1).withDayOfMonth(1)
                    periode2.datoTom shouldBe null
                    periode2.bostatus shouldBe Bostatuskode.REGNES_IKKE_SOM_BARN
                }
            }

            @Test
            @Transactional
            open fun `skal ikke opprette husstandsmedlem som finnes fra før med samme ident`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()
                val fødselsdato = LocalDate.now().minusYears(5)

                val ident = "213123"
                behandling.husstandsbarn.add(
                    Husstandsbarn(
                        behandling = behandling,
                        kilde = Kilde.MANUELL,
                        perioder = mutableSetOf(),
                        ident = ident,
                        fødselsdato = fødselsdato,
                    ),
                )
                val exception =
                    assertThrows<HttpClientErrorException> {
                        boforholdService.oppdatereHusstandsbarnManuelt(
                            behandling.id!!,
                            OppdatereHusstandsmedlem(
                                opprettHusstandsmedlem =
                                OpprettHusstandsstandsmedlem(
                                    personident = Personident(ident),
                                    fødselsdato = fødselsdato,
                                    navn = "Navn Navnesen",
                                ),
                            ),
                        )
                    }

                exception.message shouldContain "Forsøk på å oppdatere behandling ${behandling.id} feilet pga duplikate data."
                exception.statusCode shouldBe HttpStatus.CONFLICT
            }
        }

        private fun opprettBehandlingForBoforholdTest(): Behandling {
            val behandling = testdataManager.oppretteBehandling()
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            behandling.husstandsbarn.clear()
            behandling.husstandsbarn.addAll(
                setOf(
                    opprettHusstandsbarn(behandling, testdataBarn1).let {
                        it.perioder =
                            mutableSetOf(
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2023-01-01"),
                                    datoTom = LocalDate.parse("2023-05-31"),
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsbarn = it,
                                ),
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2023-06-01"),
                                    datoTom = null,
                                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsbarn = it,
                                ),
                                Husstandsbarnperiode(
                                    datoFom = LocalDate.parse("2024-01-01"),
                                    datoTom = null,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    kilde = Kilde.MANUELL,
                                    husstandsbarn = it,
                                ),
                            )
                        it
                    },
                ),
            )
            behandling.grunnlag.addAll(
                opprettBoforholdBearbeidetGrunnlagForHusstandsbarn(
                    opprettHusstandsbarnMedOffentligePerioder(behandling),
                ),
            )
            return testdataManager.lagreBehandling(behandling)
        }

        @Test
        @Transactional
        open fun `skal returnere riktig kilde på husstandsbarnperiode`() {
            // gitt
            val grunnlagBoforhold =
                listOf(
                    RelatertPersonGrunnlagDto(
                        partPersonId = testdataBM.ident,
                        relatertPersonPersonId = testdataBarn2.ident,
                        fødselsdato = testdataBarn2.fødselsdato,
                        erBarnAvBmBp = true,
                        navn = testdataBarn2.navn,
                        borISammeHusstandDtoListe =
                        listOf(
                            BorISammeHusstandDto(
                                periodeFra = testdataBarn2.fødselsdato,
                                periodeTil = null,
                            ),
                        ),
                    ),
                )

            val boforholdsrequest = grunnlagBoforhold.tilBoforholdRequest(testdataBarn2.fødselsdato)
            val manuellPeriode =
                Bostatus(
                    periodeFom = LocalDate.now().minusMonths(5),
                    periodeTom = null,
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    kilde = Kilde.MANUELL,
                )
            val offentligeBostatuser =
                boforholdsrequest.find { it.relatertPersonPersonId == testdataBarn2.ident }!!.bostatusListe
            val request =
                boforholdsrequest.find { it.relatertPersonPersonId == testdataBarn2.ident }!!
                    .copy(bostatusListe = offentligeBostatuser + manuellPeriode)

            // hvis
            val periodisertBoforhold = BoforholdApi.beregnV2(testdataBarn2.fødselsdato, listOf(request))

            // så
            assertSoftly(periodisertBoforhold) { p ->
                p shouldNotBe emptyList<BoforholdResponse>()
                p.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
            }
        }
    }

    @Nested
    open inner class Sivilstandstester {

        @Nested
        open inner class Førstegangsinnhenting {
            @Test
            @Transactional
            open fun `skal lagre periodisert sivilstand basert på førstegangsinnhenting av grunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                // Fjerner eksisterende barn for å simulere førstegangsinnhenting av grunnlag
                behandling.sivilstand.removeAll(behandling.sivilstand)
                entityManager.persist(behandling)

                stubbeHentingAvPersoninfoForTestpersoner()

                val separeringstidspunkt = LocalDateTime.now().minusMonths(125)

                val grunnlagSivilstand =
                    listOf(
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.SKILT,
                            registrert = separeringstidspunkt.plusMonths(24),
                            gyldigFom = separeringstidspunkt.plusMonths(24).toLocalDate(),
                            historisk = false,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.SEPARERT,
                            registrert = separeringstidspunkt,
                            gyldigFom = separeringstidspunkt.toLocalDate(),
                            historisk = true,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.GIFT,
                            registrert = separeringstidspunkt.minusMonths(75),
                            gyldigFom = separeringstidspunkt.minusMonths(75).toLocalDate(),
                            historisk = true,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                    )

                val periodisertSivilstand =
                    SivilstandApi.beregnV1(behandling.virkningstidspunktEllerSøktFomDato, grunnlagSivilstand)

                // hvis
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertSivilstand(
                    behandling,
                    Personident(behandling.bidragsmottaker!!.ident!!),
                    periodisertSivilstand,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.sivilstand) { s ->
                    s.size shouldBe 1
                    s.first { Sivilstandskode.BOR_ALENE_MED_BARN == it.sivilstand } shouldNotBe null
                    s.first { behandling.virkningstidspunktEllerSøktFomDato == it.datoFom } shouldNotBe null
                }
            }
        }

        @Nested
        open inner class OppdatereAutomatisk {

            @Test
            @Transactional
            open fun `skal oppdatere automatisk innhenta sivilstand uten å ta hensyn til manuelle perioder`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()
                val separeringstidspunkt = LocalDateTime.now().minusMonths(125)
                val grunnlagsdataSivilstand =
                    setOf(
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.SKILT,
                            registrert = separeringstidspunkt.plusMonths(24),
                            gyldigFom = separeringstidspunkt.plusMonths(24).toLocalDate(),
                            historisk = false,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.SEPARERT,
                            registrert = separeringstidspunkt,
                            gyldigFom = separeringstidspunkt.toLocalDate(),
                            historisk = true,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.GIFT,
                            registrert = separeringstidspunkt.minusMonths(75),
                            gyldigFom = separeringstidspunkt.minusMonths(75).toLocalDate(),
                            historisk = true,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
                    grunnlagsdata = grunnlagsdataSivilstand,
                )

                val periodisertSivilstand =
                    SivilstandApi.beregnV2(
                        minOf(behandling.virkningstidspunktEllerSøktFomDato),
                        grunnlagsdataSivilstand.tilSivilstandRequest(),
                    )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                    grunnlagsdata = periodisertSivilstand,
                )

                behandling.sivilstand.add(
                    Sivilstand(
                        behandling,
                        datoFom = behandling.virkningstidspunkt,
                        datoTom = null,
                        Sivilstandskode.BOR_ALENE_MED_BARN,
                        Kilde.MANUELL,
                    ),
                )

                entityManager.flush()

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 3
                    s.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                }

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaSivilstand(behandling, true)

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 1
                    s.first { Sivilstandskode.BOR_ALENE_MED_BARN == it.sivilstand } shouldNotBe null
                    s.first { behandling.virkningstidspunktEllerSøktFomDato == it.datoFom } shouldNotBe null
                    s.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 0
                }

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 2
                    g.filter { it.aktiv != null } shouldHaveSize 2
                }
            }

            @Test
            @Transactional
            open fun `skal oppdatere automatisk innhenta sivilstand uten å slette manuelleperioder`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                stubbeHentingAvPersoninfoForTestpersoner()
                val separeringstidspunkt = LocalDateTime.now().minusMonths(125)
                val grunnlagsdataSivilstand =
                    setOf(
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.SKILT,
                            registrert = separeringstidspunkt.plusMonths(24),
                            gyldigFom = separeringstidspunkt.plusMonths(24).toLocalDate(),
                            historisk = false,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.SEPARERT,
                            registrert = separeringstidspunkt,
                            gyldigFom = separeringstidspunkt.toLocalDate(),
                            historisk = true,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                        SivilstandGrunnlagDto(
                            personId = behandling.bidragsmottaker!!.ident!!,
                            type = SivilstandskodePDL.GIFT,
                            registrert = separeringstidspunkt.minusMonths(75),
                            gyldigFom = separeringstidspunkt.minusMonths(75).toLocalDate(),
                            historisk = true,
                            master = "FREG",
                            bekreftelsesdato = null,
                        ),
                    )
                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
                    grunnlagsdata = grunnlagsdataSivilstand,
                )

                val periodisertSivilstand =
                    SivilstandApi.beregnV2(
                        minOf(behandling.virkningstidspunktEllerSøktFomDato),
                        grunnlagsdataSivilstand.tilSivilstandRequest(),
                    )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                    grunnlagsdata = periodisertSivilstand,
                )

                behandling.sivilstand.add(
                    Sivilstand(
                        behandling,
                        datoFom = behandling.virkningstidspunkt?.plusMonths(2),
                        datoTom = null,
                        Sivilstandskode.GIFT_SAMBOER,
                        Kilde.MANUELL,
                    ),
                )

                entityManager.flush()

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 3
                    s.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                }

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaSivilstand(behandling, false)

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.sivilstand) { s ->
                    s.size shouldBe 2
                    s.first { Sivilstandskode.BOR_ALENE_MED_BARN == it.sivilstand } shouldNotBe null
                    s.first { behandling.virkningstidspunktEllerSøktFomDato == it.datoFom } shouldNotBe null
                    s.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                }

                entityManager.flush()

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 2
                    g.filter { it.aktiv != null } shouldHaveSize 2
                }
            }
        }

        @Nested
        open inner class OppdatereManuelt {
            @Test
            @Transactional
            open fun `skal legge til sivilstand manuelt`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 2
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                }

                val fomdato = LocalDate.now().minusMonths(7)

                // hvis
                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(
                        nyEllerEndretSivilstandsperiode =
                        Sivilstandsperiode(
                            fomdato,
                            null,
                            Sivilstandskode.GIFT_SAMBOER,
                        ),
                    ),
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 3
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                }

                assertSoftly(behandling.sivilstand.maxBy { it.datoFom!! }){
                    it.datoFom shouldBe fomdato
                    it.datoTom shouldBe null
                    it.kilde shouldBe Kilde.MANUELL
                }
            }

            @Test
            @Transactional
            @Disabled
            open fun `skal slette sivilstandsperiode`() {
            }
        }

    }

    private fun stubbeHentingAvPersoninfoForTestpersoner() {
        Mockito.`when`(bidragPersonConsumer.hentPerson(testdataBM.ident)).thenReturn(testdataBM.tilPersonDto())
        Mockito.`when`(bidragPersonConsumer.hentPerson(testdataBarn1.ident))
            .thenReturn(testdataBarn1.tilPersonDto())
        Mockito.`when`(bidragPersonConsumer.hentPerson(testdataBarn2.ident))
            .thenReturn(testdataBarn2.tilPersonDto())
    }
}
