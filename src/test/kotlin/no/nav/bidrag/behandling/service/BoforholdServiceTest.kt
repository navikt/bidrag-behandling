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
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.finnBostatusperiode
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdaterHusstandsmedlemPeriode
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.dto.v2.boforhold.OpprettHusstandsstandsmedlem
import no.nav.bidrag.behandling.dto.v2.boforhold.Sivilstandsperiode
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandskodePDL
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlemMedOffentligePerioder
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.diverse.TypeEndring
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.dto.SivilstandRequest
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
    open inner class Husstandsmedlemstester {
        @Nested
        open inner class Førstegangsinnhenting {
            @Test
            @Transactional
            open fun `skal lagre periodisert boforhold basert på førstegangsinnhenting av grunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                // Fjerner eksisterende barn for å simulere førstegangsinnhenting av grunnlag
                behandling.husstandsmedlem.removeAll(behandling.husstandsmedlem)
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
                    BoforholdApi.beregnBoforholdBarnV2(
                        testdataBarn2.fødselsdato,
                        grunnlagBoforhold.tilBoforholdBarnRequest(behandling),
                    )

                // hvis
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(
                    behandling,
                    periodisertBoforhold,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsmedlem) {
                    it.size shouldBe 2
                    it.first { barn -> testdataBarn2.ident == barn.ident }.perioder.size shouldBe 3
                }
            }

            @Test
            @Transactional
            open fun `skal erstatte lagrede offentlige husstandsmedlem med nye fra grunnlag ved førstegangsinnhenting `() {
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
                    BoforholdApi.beregnBoforholdBarnV2(
                        LocalDate.now(),
                        grunnlagBoforhold.tilBoforholdBarnRequest(behandling),
                    )

                behandling.husstandsmedlem.size shouldBe 2

                // hvis
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(
                    behandling,
                    periodisertBoforhold,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly {
                    behandling.husstandsmedlem.size shouldBe 1
                }
            }
        }

        @Nested
        open inner class OppdatereAutomatisk {
            @Test
            @Transactional
            open fun `skal oppdatere automatisk innhenta husstandsmedlem og overskrive manuell informasjon`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, true)

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

                val hb1 = behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }
                hb1!!.perioder.add(
                    Bostatusperiode(
                        datoFom = LocalDate.now().minusMonths(3),
                        bostatus = Bostatuskode.MED_FORELDER,
                        husstandsmedlem = hb1,
                        datoTom = null,
                        kilde = Kilde.MANUELL,
                    ),
                )

                assertSoftly(behandling.husstandsmedlem) { hb ->
                    hb.size shouldBe 2
                    hb.find { testdataBarn1.ident == it.ident } shouldNotBe null
                    hb.find { testdataBarn1.ident == it.ident }?.perioder shouldNotBe emptySet<Bostatusperiode>()
                }
                assertSoftly(behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }?.perioder) { p ->
                    p?.size shouldBe 3
                    p?.filter { Kilde.MANUELL == it.kilde }?.size shouldBe 1
                }

                val periodeFom = minOf(testdataBarn1.fødselsdato, testdataBarn2.fødselsdato)
                val periodisertBoforhold =
                    BoforholdApi.beregnBoforholdBarnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdBarnRequest(behandling),
                    )

                // hvis
                boforholdService.oppdatereAutomatiskInnhentetBoforhold(
                    behandling,
                    periodisertBoforhold.filter { it.relatertPersonPersonId == testdataBarn1.ident },
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                    testdataBarn1.tilPersonDto().ident,
                )
                boforholdService.oppdatereAutomatiskInnhentetBoforhold(
                    behandling,
                    periodisertBoforhold.filter { it.relatertPersonPersonId == testdataBarn2.ident },
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                    testdataBarn2.tilPersonDto().ident,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsmedlem) { husstandsmedlem ->
                    husstandsmedlem.size shouldBe 2
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == testdataBarn1.ident }) { barn1 ->
                    barn1 shouldNotBe null
                    barn1!!.perioder.size shouldBe 1
                    barn1.perioder.filter { Kilde.MANUELL == it.kilde } shouldBe emptyList()
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == testdataBarn2.ident }) { barn2 ->
                    barn2 shouldNotBe null
                    barn2!!.perioder.size shouldBe 3
                    barn2.perioder.filter { Kilde.MANUELL == it.kilde } shouldBe emptyList()
                }
            }

            @Test
            @Transactional
            open fun `skal oppdatere automatisk innhenta husstandsmedlem og slette manuelle perioder`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()
                val virkningstidspunkt = testdataBarn1.fødselsdato
                behandling.virkningstidspunkt = virkningstidspunkt
                behandling.husstandsmedlem.forEach {
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
                    BoforholdApi.beregnBoforholdBarnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdBarnRequest(behandling),
                    )

                periodisertBoforhold.groupBy { it.relatertPersonPersonId }.forEach { (personId, boforhold) ->
                    testdataManager.oppretteOgLagreGrunnlag(
                        behandling = behandling,
                        grunnlagstype = Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
                        gjelderIdent = personId,
                        grunnlagsdata = boforhold,
                    )
                }
                testdataManager.lagreBehandling(behandling)

                // hvis
                boforholdService.oppdatereAutomatiskInnhentetBoforhold(
                    behandling,
                    periodisertBoforhold.filter { it.relatertPersonPersonId == testdataBarn1.ident }!!,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                    testdataBarn1.tilPersonDto().ident,
                )
                boforholdService.oppdatereAutomatiskInnhentetBoforhold(
                    behandling,
                    periodisertBoforhold.filter { it.relatertPersonPersonId == testdataBarn2.ident },
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                    testdataBarn2.tilPersonDto().ident,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsmedlem) { husstandsmedlem ->
                    husstandsmedlem.size shouldBe 2
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == testdataBarn1.ident }) { barn1 ->
                    barn1 shouldNotBe null
                    barn1!!.perioder.size shouldBe 1
                    barn1.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 0
                    barn1.perioder.last().datoFom shouldBe virkningstidspunkt
                    barn1.perioder.last().datoTom shouldBe null
                    barn1.perioder.last().bostatus shouldBe Bostatuskode.MED_FORELDER
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == testdataBarn2.ident }) { barn2 ->
                    barn2 shouldNotBe null
                    barn2!!.perioder.size shouldBe 3
                    barn2.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 0
                    barn2.perioder.last().kilde shouldBe Kilde.OFFENTLIG
                    barn2.perioder.last().datoFom shouldBe LocalDate.of(2022, 1, 1)
                    barn2.perioder.last().datoTom shouldBe null
                    barn2.perioder.last().bostatus shouldBe Bostatuskode.MED_FORELDER
                }
            }

            @Test
            @Transactional
            open fun `skal endre kilde til manuell for offentlig husstandsmedlem som ikke finnes i nyeste grunnlag`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, false, true)
                behandling.virkningstidspunkt = testdataBarn1.fødselsdato
                behandling.husstandsmedlem.forEach {
                    it.perioder.forEach {
                        it.kilde = Kilde.MANUELL
                    }
                }

                assertSoftly(behandling.husstandsmedlem) { hb ->
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
                    BoforholdApi.beregnBoforholdBarnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdBarnRequest(behandling),
                    )

                // hvis
                boforholdService.oppdatereAutomatiskInnhentetBoforhold(
                    behandling,
                    periodisertBoforhold,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    false,
                    testdataBarn2.tilPersonDto().ident,
                )

                boforholdService.oppdatereAutomatiskInnhentetBoforhold(
                    behandling,
                    periodisertBoforhold,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                    testdataBarn1.tilPersonDto().ident,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsmedlem) { husstandsmedlem ->
                    husstandsmedlem.size shouldBe 2
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == testdataBarn1.ident }) { barn1 ->
                    barn1 shouldNotBe null
                    barn1!!.kilde shouldBe Kilde.OFFENTLIG
                    barn1.perioder.size shouldBe 1
                    barn1.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 0
                    barn1.perioder.last().kilde shouldBe Kilde.OFFENTLIG
                    barn1.perioder.last().datoFom shouldBe testdataBarn1.fødselsdato
                    barn1.perioder.last().datoTom shouldBe null
                    barn1.perioder.last().bostatus shouldBe Bostatuskode.MED_FORELDER
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == testdataBarn2.ident }) { barn2 ->
                    barn2 shouldNotBe null
                    barn2!!.kilde shouldBe Kilde.MANUELL
                    barn2.perioder.size shouldBe 2
                    barn2.perioder.filter { Kilde.MANUELL == it.kilde }.size shouldBe 2
                    barn2.perioder.last().kilde shouldBe Kilde.MANUELL
                    barn2.perioder.last().datoFom shouldBe LocalDate.of(2023, 6, 1)
                    barn2.perioder.last().datoTom shouldBe null
                    barn2.perioder.last().bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                }
            }

            @Test
            @Transactional
            open fun `skal slette offentlige husstandsmedlem som ikke finnes i nyeste grunnlag hvis overskriving er valgt`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()
                behandling.virkningstidspunkt = testdataBarn1.fødselsdato
                behandling.husstandsmedlem.forEach {
                    it.perioder.forEach {
                        it.kilde = Kilde.MANUELL
                    }
                }

                assertSoftly(behandling.husstandsmedlem) { hb ->
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
                    BoforholdApi.beregnBoforholdBarnV2(
                        periodeFom,
                        grunnlagBoforhold.tilBoforholdBarnRequest(behandling),
                    )

                // hvis
                boforholdService.oppdatereAutomatiskInnhentetBoforhold(
                    behandling,
                    periodisertBoforhold,
                    grunnlagBoforhold.groupBy { it.relatertPersonPersonId }.map { Personident(it.key!!) }.toSet(),
                    true,
                    testdataBarn2.tilPersonDto().ident,
                )

                // så
                entityManager.refresh(behandling)

                assertSoftly(behandling.husstandsmedlem) { husstandsmedlem ->
                    husstandsmedlem.size shouldBe 1
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == testdataBarn1.ident }) { barn1 ->
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
            open fun `skal bruke offentlig bostedsinformasjon for manuelt barn som bor på samme adresse som BM`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()
                val personidentBarnMedDNummer = "213123"
                val fødselsdatoBarnMedDNummer = LocalDate.now().minusYears(7)
                val navnBarnMedDNummer = "Bark E. Bille"

                val dataFraGrunnlag =
                    behandling.grunnlag.filter { Grunnlagsdatatype.BOFORHOLD == it.type && it.erBearbeidet }.groupBy {
                        it.gjelder
                    }.map {
                        val t = it.value.filter { it.aktiv != null }.maxBy { it.aktiv!! }
                        val data = t.konvertereData<Set<BoforholdResponse>>()
                        RelatertPersonGrunnlagDto(
                            relatertPersonPersonId = it.key,
                            borISammeHusstandDtoListe =
                                data?.map { BorISammeHusstandDto(it.periodeFom, it.periodeTom) }
                                    ?: emptyList(),
                            erBarnAvBmBp = true,
                            fødselsdato = data?.first()?.fødselsdato,
                            navn = null,
                            partPersonId = behandling.bidragsmottaker!!.ident,
                        )
                    } +
                        listOf(
                            RelatertPersonGrunnlagDto(
                                relatertPersonPersonId = personidentBarnMedDNummer,
                                borISammeHusstandDtoListe =
                                    listOf(
                                        BorISammeHusstandDto(
                                            LocalDate.now().minusMonths(7),
                                            null,
                                        ),
                                    ),
                                erBarnAvBmBp = false,
                                fødselsdato = fødselsdatoBarnMedDNummer,
                                navn = navnBarnMedDNummer,
                                partPersonId = behandling.bidragsmottaker!!.ident,
                            ),
                        )

                behandling.grunnlag.add(
                    Grunnlag(
                        aktiv = LocalDateTime.now(),
                        behandling = behandling,
                        data = objectmapper.writeValueAsString(dataFraGrunnlag),
                        innhentet = LocalDateTime.now().minusDays(1),
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        erBearbeidet = false,
                    ),
                )

                behandling.husstandsmedlem.shouldHaveSize(1)

                // hvis
                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        opprettHusstandsmedlem =
                            OpprettHusstandsstandsmedlem(
                                personident = Personident(personidentBarnMedDNummer),
                                fødselsdato = fødselsdatoBarnMedDNummer,
                                navn = navnBarnMedDNummer,
                            ),
                    ),
                )

                // så
                assertSoftly(behandling.grunnlag.filter { Grunnlagsdatatype.BOFORHOLD == it.type }) { g ->
                    g shouldHaveSize 4
                    g.filter { it.erBearbeidet } shouldHaveSize 3
                    g.filter { it.erBearbeidet && personidentBarnMedDNummer == it.gjelder } shouldHaveSize 1
                }

                assertSoftly(behandling.husstandsmedlem.find { it.ident == personidentBarnMedDNummer }!!) {
                    it.perioder.shouldHaveSize(2)
                    it.navn shouldBe navnBarnMedDNummer
                    it.fødselsdato shouldBe fødselsdatoBarnMedDNummer
                    val periode = it.perioder.sortedBy { it.datoFom }.first()
                    periode.kilde shouldBe Kilde.OFFENTLIG
                    periode.datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                    periode.datoTom shouldNotBe null
                    periode.bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
                }
            }

            @Test
            @Transactional
            open fun `skal få slette både manuelle og offentlige bostatusperioder`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()
                stubbeHentingAvPersoninfoForTestpersoner()

                val bostatusperiodeSomSkalSlettes = behandling.husstandsmedlem.first().perioder.first()
                assertSoftly {
                    bostatusperiodeSomSkalSlettes shouldNotBe null
                    behandling.finnBostatusperiode(bostatusperiodeSomSkalSlettes.id).shouldNotBeNull()
                }

                // hvis
                val oppdatereBoforholdResponse =
                    boforholdService.oppdatereHusstandsmedlemManuelt(
                        behandling.id!!,
                        OppdatereHusstandsmedlem(slettPeriode = bostatusperiodeSomSkalSlettes.id!!),
                    )

                // så
                assertSoftly(oppdatereBoforholdResponse) {
                    it.oppdatertHusstandsmedlem shouldNotBe null
                    it.oppdatertHusstandsmedlem!!.ident shouldBe bostatusperiodeSomSkalSlettes.husstandsmedlem.ident
                }
                assertSoftly(behandling.husstandsmedlem.find { it == bostatusperiodeSomSkalSlettes.husstandsmedlem }) {
                    it.shouldNotBeNull()
                    it.perioder.find { it == bostatusperiodeSomSkalSlettes } shouldBe null
                    behandling.finnBostatusperiode(bostatusperiodeSomSkalSlettes.id).shouldBeNull()
                }
            }

            @Test
            @Transactional
            open fun `skal ikke få slette husstandsmedlem med kilde offentlig`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling()

                // hvis, så
                assertFailsWith<HttpClientErrorException> {
                    boforholdService.oppdatereHusstandsmedlemManuelt(
                        behandling.id!!,
                        OppdatereHusstandsmedlem(
                            slettHusstandsmedlem = behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }!!.id,
                        ),
                    )
                }
            }

            @Test
            @Transactional
            open fun `skal kunne slette manuelt husstandsmedlem`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                val husstandsmedlem = behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }
                husstandsmedlem?.kilde = Kilde.MANUELL

                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        slettHusstandsmedlem = behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }!!.id,
                    ),
                )

                assertSoftly {
                    behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident } shouldBe null
                }
            }

            @Test
            @Transactional
            open fun `skal tilbakestille til offentlig opplysninger`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                val husstandsmedlem = behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }!!
                husstandsmedlem.perioder.shouldHaveSize(3)
                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(tilbakestillPerioderForHusstandsmedlem = husstandsmedlem.id),
                )

                assertSoftly {
                    husstandsmedlem.perioder.shouldHaveSize(2)
                    husstandsmedlem.perioder.filter { it.kilde == Kilde.MANUELL }.shouldBeEmpty()
                    husstandsmedlem.forrigePerioder.shouldNotBeEmpty()
                }
            }

            @Test
            @Transactional
            open fun `skal slette periode`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                val husstandsmedlem = behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }!!
                husstandsmedlem.perioder.shouldHaveSize(3)

                // hvis
                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(slettPeriode = husstandsmedlem.perioder.find { it.kilde == Kilde.MANUELL }!!.id),
                )

                // så
                husstandsmedlem.perioder.shouldHaveSize(2)
            }

            @Test
            open fun `skal ikke slette periode hvis det er bare en igjen`() {
                // gitt
                var behandling = opprettBehandlingForBoforholdTest()

                val fødselsdato = LocalDate.now().minusYears(5)
                val ident = "213123"
                val husstandsmedlem =
                    Husstandsmedlem(
                        behandling = behandling,
                        kilde = Kilde.MANUELL,
                        perioder = mutableSetOf(),
                        ident = ident,
                        fødselsdato = fødselsdato,
                    )
                husstandsmedlem.perioder.add(
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = LocalDate.parse("2020-01-01"),
                        datoTom = null,
                        kilde = Kilde.MANUELL,
                        bostatus = Bostatuskode.MED_FORELDER,
                    ),
                )
                behandling.husstandsmedlem.add(husstandsmedlem)
                behandling = testdataManager.lagreBehandling(behandling)
                val husstandsmedlemPersisted = behandling.husstandsmedlem.find { it.ident == ident }

                val exception =
                    assertThrows<HttpClientErrorException> {
                        boforholdService.oppdatereHusstandsmedlemManuelt(
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

                val husstandsmedlem = behandling.husstandsmedlem.find { testdataBarn1.ident == it.ident }!!
                val periodeSomSkalOppdateres = husstandsmedlem.perioder.maxByOrNull { it.datoFom!! }!!
                husstandsmedlem.perioder.shouldHaveSize(3)

                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        oppdaterPeriode =
                            OppdaterHusstandsmedlemPeriode(
                                idHusstandsmedlem = husstandsmedlem.id!!,
                                idPeriode = periodeSomSkalOppdateres.id,
                                datoFom = LocalDate.parse("2024-02-01"),
                                datoTom = null,
                                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                            ),
                    ),
                )

                assertSoftly("Resultat etter første oppdatering") {
                    husstandsmedlem.perioder.shouldHaveSize(4)
                    husstandsmedlem.forrigePerioder.shouldNotBeEmpty()
                    val førstePeriode = husstandsmedlem.perioder.minBy { it.datoFom!! }
                    val andrePeriode = husstandsmedlem.perioder.maxBy { it.datoFom!! }
                    førstePeriode!!.datoFom shouldBe LocalDate.parse("2023-01-01")
                    andrePeriode!!.datoFom shouldBe LocalDate.parse("2024-02-01")
                }

                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        angreSisteStegForHusstandsmedlem = husstandsmedlem.id,
                    ),
                )

                assertSoftly("Resultat etter angre forrige steg") {
                    husstandsmedlem.perioder.shouldHaveSize(3)
                    husstandsmedlem.forrigePerioder.shouldNotBeEmpty()
                    val førstePeriode = husstandsmedlem.perioder.minBy { it.datoFom!! }
                    val sistePeriode = husstandsmedlem.perioder.maxBy { it.datoFom!! }
                    førstePeriode.datoFom shouldBe LocalDate.parse("2023-01-01")
                    sistePeriode.datoFom shouldBe LocalDate.parse("2024-01-01")
                    sistePeriode.kilde shouldBe Kilde.MANUELL
                }

                // hvis
                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        angreSisteStegForHusstandsmedlem = husstandsmedlem.id,
                    ),
                )

                // så
                husstandsmedlem.perioder.shouldHaveSize(4)
            }

            @Test
            @Transactional
            open fun `skal opprette husstandsmedlem`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest(inkludereBoforhold = false, inkludereSivilstand = true)

                behandling.husstandsmedlem.shouldHaveSize(1)

                // hvis
                boforholdService.oppdatereHusstandsmedlemManuelt(
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

                // så
                assertSoftly(behandling.husstandsmedlem.find { it.ident == "213123" }!!) {
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
            open fun `skal opprette husstandsmedlem uten personident`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                behandling.husstandsmedlem.shouldHaveSize(1)

                // hvis
                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        opprettHusstandsmedlem =
                            OpprettHusstandsstandsmedlem(
                                fødselsdato = LocalDate.parse("2020-02-01"),
                                navn = "Navn Navnesen",
                            ),
                    ),
                )

                // så
                assertSoftly(behandling.husstandsmedlem.find { it.ident == null }!!) {
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

                behandling.husstandsmedlem.shouldHaveSize(1)
                boforholdService.oppdatereHusstandsmedlemManuelt(
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
                behandling.husstandsmedlem.shouldHaveSize(2)
                boforholdService.oppdatereHusstandsmedlemManuelt(
                    behandling.id!!,
                    OppdatereHusstandsmedlem(
                        slettHusstandsmedlem = behandling.husstandsmedlem.find { it.ident == "213123" }!!.id,
                    ),
                )
                behandling.husstandsmedlem.shouldHaveSize(1)
            }

            @Test
            @Transactional
            @Disabled("Magnus skal fikse Boforhold api slik at oppføreselen blir riktig")
            open fun `skal opprette husstandsmedlem over 18`() {
                // gitt
                val behandling = opprettBehandlingForBoforholdTest()

                behandling.husstandsmedlem.shouldHaveSize(1)
                val fødselsdato = LocalDate.now().minusYears(17).minusMonths(7)
                boforholdService.oppdatereHusstandsmedlemManuelt(
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

                assertSoftly(behandling.husstandsmedlem.find { it.ident == "213123" }!!) {
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
                behandling.husstandsmedlem.add(
                    Husstandsmedlem(
                        behandling = behandling,
                        kilde = Kilde.MANUELL,
                        perioder = mutableSetOf(),
                        ident = ident,
                        fødselsdato = fødselsdato,
                    ),
                )
                val exception =
                    assertThrows<HttpClientErrorException> {
                        boforholdService.oppdatereHusstandsmedlemManuelt(
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

        private fun opprettBehandlingForBoforholdTest(
            inkludereInntekter: Boolean = false,
            inkludereSivilstand: Boolean = false,
            inkludereBoforhold: Boolean = false,
        ): Behandling {
            val behandling =
                testdataManager.oppretteBehandling(inkludereInntekter, inkludereSivilstand, inkludereBoforhold)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            behandling.husstandsmedlem.clear()
            behandling.husstandsmedlem.addAll(
                setOf(
                    oppretteHusstandsmedlem(behandling, testdataBarn1).let {
                        it.perioder =
                            mutableSetOf(
                                Bostatusperiode(
                                    datoFom = LocalDate.parse("2023-01-01"),
                                    datoTom = LocalDate.parse("2023-05-31"),
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsmedlem = it,
                                ),
                                Bostatusperiode(
                                    datoFom = LocalDate.parse("2023-06-01"),
                                    datoTom = null,
                                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                    kilde = Kilde.OFFENTLIG,
                                    husstandsmedlem = it,
                                ),
                                Bostatusperiode(
                                    datoFom = LocalDate.parse("2024-01-01"),
                                    datoTom = null,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    kilde = Kilde.MANUELL,
                                    husstandsmedlem = it,
                                ),
                            )
                        it
                    },
                ),
            )
            behandling.grunnlag.addAll(
                oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(
                    oppretteHusstandsmedlemMedOffentligePerioder(behandling),
                ),
            )
            return testdataManager.lagreBehandling(behandling)
        }

        @Test
        @Transactional
        open fun `skal returnere riktig kilde på bostatusperiode`() {
            // gitt
            val behandling = opprettBehandlingForBoforholdTest()
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

            val boforholdsrequest = grunnlagBoforhold.tilBoforholdBarnRequest(behandling)
            val manuellPeriode =
                Bostatus(
                    periodeFom = LocalDate.now().minusMonths(5),
                    periodeTom = null,
                    bostatusKode = Bostatuskode.IKKE_MED_FORELDER,
                    kilde = Kilde.MANUELL,
                )
            val offentligeBostatuser =
                boforholdsrequest.find { it.relatertPersonPersonId == testdataBarn2.ident }!!.innhentedeOffentligeOpplysninger
            val request =
                boforholdsrequest.find { it.relatertPersonPersonId == testdataBarn2.ident }!!
                    .copy(behandledeBostatusopplysninger = offentligeBostatuser + manuellPeriode)

            val periodiseringsrequest =
                request.copy(
                    endreBostatus =
                        EndreBostatus(
                            typeEndring = TypeEndring.NY,
                            nyBostatus = manuellPeriode,
                            originalBostatus = null,
                        ),
                )

            // hvis
            val periodisertBoforhold =
                BoforholdApi.beregnBoforholdBarnV2(testdataBarn2.fødselsdato, listOf(periodiseringsrequest))

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
                val behandling = testdataManager.oppretteBehandling(false, false, false)

                stubbeHentingAvPersoninfoForTestpersoner()

                val separeringstidspunkt = LocalDateTime.now().minusMonths(125)

                val grunnlagSivilstand =
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

                val periodisertSivilstand =
                    SivilstandApi.beregnV2(
                        behandling.virkningstidspunktEllerSøktFomDato,
                        grunnlagSivilstand.tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.foedselsdato),
                    )

                // hvis
                boforholdService.lagreFørstegangsinnhentingAvPeriodisertSivilstand(
                    behandling,
                    periodisertSivilstand.toSet(),
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
                val behandling = testdataManager.oppretteBehandling(false, false, false)
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
                        grunnlagsdataSivilstand.tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.foedselsdato),
                    )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                    grunnlagsdata = periodisertSivilstand,
                )

                behandling.sivilstand.add(
                    Sivilstand(
                        behandling,
                        datoFom = behandling.virkningstidspunktEllerSøktFomDato,
                        datoTom = null,
                        Sivilstandskode.BOR_ALENE_MED_BARN,
                        Kilde.MANUELL,
                    ),
                )

                entityManager.flush()

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 1
                    s.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                }

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaSivilstand(behandling, true)

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
                val behandling = testdataManager.oppretteBehandling(false, false, false)

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
                        grunnlagsdataSivilstand.tilSivilstandRequest(fødselsdatoBm = behandling.bidragsmottaker!!.foedselsdato),
                    )

                testdataManager.oppretteOgLagreGrunnlag(
                    behandling = behandling,
                    grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, true),
                    grunnlagsdata = periodisertSivilstand,
                )

                behandling.sivilstand.add(
                    Sivilstand(
                        behandling,
                        datoFom = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(2),
                        datoTom = behandling.virkningstidspunkt?.plusMonths(4),
                        Sivilstandskode.GIFT_SAMBOER,
                        Kilde.MANUELL,
                    ),
                )

                entityManager.flush()

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 1
                    s.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 1
                }

                // hvis
                boforholdService.oppdatereAutomatiskInnhentaSivilstand(behandling, false)

                assertSoftly(behandling.sivilstand) { s ->
                    s.size shouldBe 3
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
                val behandling = testdataManager.oppretteBehandling(false, true, false)

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 2
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                }

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 2
                }

                val fomdato = behandling.sivilstand.maxBy { it.datoFom }.datoFom.plusMonths(7)

                // hvis
                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(
                        nyEllerEndretSivilstandsperiode =
                            Sivilstandsperiode(fomdato, null, Sivilstandskode.GIFT_SAMBOER),
                    ),
                )

                // så
                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 3
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                    s.filter { it.id != null } shouldHaveSize 3
                }
            }

            @Test
            @Transactional
            open fun `skal slette sivilstandsperiode`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, true, false)

                behandling.sivilstand shouldHaveSize 2

                val fomdato = behandling.sivilstand.maxBy { it.datoFom }.datoFom.plusMonths(7)
                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(
                        nyEllerEndretSivilstandsperiode =
                            Sivilstandsperiode(fomdato, null, Sivilstandskode.GIFT_SAMBOER),
                    ),
                )

                behandling.sivilstand shouldHaveSize 3

                // hvis
                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(sletteSivilstandsperiode = behandling.sivilstand.maxBy { it.datoFom }.id),
                )

                // så
                assertSoftly(behandling.sivilstand) {
                    it shouldHaveSize 2
                    it.filter { Kilde.MANUELL == it.kilde } shouldHaveSize 0
                    it.filter { it.id != null } shouldHaveSize 2
                }
            }

            @Test
            @Transactional
            open fun `skal tilbakestille til offentlig sivilstandshistorikk`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, true, false)

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 2
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                }

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 2
                }

                val fomdato = behandling.sivilstand.maxBy { it.datoFom }.datoFom.plusMonths(7)

                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(
                        nyEllerEndretSivilstandsperiode =
                            Sivilstandsperiode(fomdato, null, Sivilstandskode.GIFT_SAMBOER),
                    ),
                )

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 3
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                    s.filter { it.id != null } shouldHaveSize 3
                }

                // hvis
                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(tilbakestilleHistorikk = true),
                )

                // så
                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 2
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                    s.filter { it.id != null } shouldHaveSize 2
                }
            }

            @Test
            @Transactional
            open fun `skal angre siste endring`() {
                // gitt
                val behandling = testdataManager.oppretteBehandling(false, true, false)

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 2
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                }

                assertSoftly(behandling.grunnlag) { g ->
                    g shouldHaveSize 2
                }

                val fomdato1 = behandling.sivilstand.maxBy { it.datoFom }.datoFom.plusMonths(7)

                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(
                        nyEllerEndretSivilstandsperiode =
                            Sivilstandsperiode(fomdato1, null, Sivilstandskode.GIFT_SAMBOER),
                    ),
                )

                val fomdato2 = behandling.sivilstand.maxBy { it.datoFom }.datoFom.plusMonths(1)

                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(
                        nyEllerEndretSivilstandsperiode =
                            Sivilstandsperiode(fomdato2, null, Sivilstandskode.ENSLIG),
                    ),
                )

                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 4
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                    s.filter { it.id != null } shouldHaveSize 4
                }

                // hvis
                boforholdService.oppdatereSivilstandManuelt(
                    behandling.id!!,
                    OppdatereSivilstand(angreSisteEndring = true),
                )

                // så
                assertSoftly(behandling.sivilstand) { s ->
                    s shouldHaveSize 3
                    s.filter { Kilde.OFFENTLIG == it.kilde } shouldHaveSize 2
                    s.filter { it.id != null } shouldHaveSize 3
                }
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

fun Set<Sivilstand>.tilSivilstandGrunnlagDto(): List<SivilstandGrunnlagDto> {
    val nyesteGyldigFom = this.maxBy { it.datoFom }.datoFom
    return this.map {
        SivilstandGrunnlagDto(
            gyldigFom = it.datoFom,
            type = it.sivilstand.tilSivilstandskodePDL(),
            bekreftelsesdato = it.datoFom,
            personId = it.behandling.bidragsmottaker!!.ident,
            master = "Freg",
            historisk = it.datoFom.isBefore(nyesteGyldigFom),
            registrert = it.datoFom.atStartOfDay(),
        )
    }
}

fun leggeTilGrunnlagForSivilstand(behandling: Behandling) {
    behandling.grunnlag.add(
        Grunnlag(
            aktiv = LocalDateTime.now(),
            type = Grunnlagsdatatype.SIVILSTAND,
            erBearbeidet = false,
            behandling = behandling,
            innhentet = LocalDateTime.now(),
            rolle = behandling.bidragsmottaker!!,
            data = tilJson(behandling.sivilstand.tilSivilstandGrunnlagDto()),
        ),
    )

    val førstegangsrequest =
        SivilstandRequest(
            behandledeSivilstandsopplysninger = emptyList(),
            innhentedeOffentligeOpplysninger = behandling.sivilstand.tilSivilstandGrunnlagDto(),
            endreSivilstand = null,
            fødselsdatoBM = behandling.bidragsmottaker!!.foedselsdato,
        )

    val førstegangsperiodisering =
        SivilstandApi.beregnV2(
            virkningstidspunkt = behandling.virkningstidspunktEllerSøktFomDato,
            førstegangsrequest,
        )

    behandling.grunnlag.add(
        Grunnlag(
            aktiv = LocalDateTime.now(),
            type = Grunnlagsdatatype.SIVILSTAND,
            erBearbeidet = true,
            behandling = behandling,
            innhentet = LocalDateTime.now(),
            rolle = behandling.bidragsmottaker!!,
            data = tilJson(førstegangsperiodisering),
        ),
    )
}
