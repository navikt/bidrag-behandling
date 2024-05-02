package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.repository.HusstandsbarnperiodeRepository
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsbarn
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.dto.v2.boforhold.Sivilstandsperiode
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdRequest
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.BoforholdApi
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
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

@RunWith(Enclosed::class)
class BoforholdServiceTest : TestContainerRunner() {
    @MockBean
    lateinit var bidragPersonConsumer: BidragPersonConsumer

    @Autowired
    lateinit var husstandsbarnperiodeRepository: HusstandsbarnperiodeRepository

    @Autowired
    lateinit var boforholdService: BoforholdService

    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var entityManager: EntityManager

    @Nested
    open inner class Husstandsbarnstester {
        @Test
        @Transactional
        open fun `skal oppdatere automatisk innhenta husstandsbarn og overskrive manuell informasjon`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

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

            val periodisertBoforhold =
                BoforholdApi.beregnV2(
                    minOf(testdataBarn1.fødselsdato, testdataBarn2.fødselsdato),
                    grunnlagBoforhold.tilBoforholdRequest(),
                )

            // hvis
            boforholdService.oppdatereAutomatiskInnhentaBoforhold(behandling, periodisertBoforhold, true)

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
            val behandling = testdataManager.opprettBehandling()
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

            val periodisertBoforhold =
                BoforholdApi.beregnV2(
                    minOf(testdataBarn1.fødselsdato, testdataBarn2.fødselsdato),
                    grunnlagBoforhold.tilBoforholdRequest(),
                )

            // hvis
            boforholdService.oppdatereAutomatiskInnhentaBoforhold(behandling, periodisertBoforhold, false)

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
        open fun `skal erstatte lagrede offentlige husstandsbarn med nye fra grunnlag ved førstegangsinnhenting `() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

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
                BoforholdApi.beregnV2(LocalDate.now(), grunnlagBoforhold.tilBoforholdRequest())

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

        @Test
        @Transactional
        open fun `skal lagre periodisert boforhold basert på førstegangsinnhenting av grunnlag`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

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
                BoforholdApi.beregnV2(testdataBarn2.fødselsdato, grunnlagBoforhold.tilBoforholdRequest())

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
        open fun `skal ikke få slette husstandsbarn med kilde offentlig`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            // hvis, så
            assertFailsWith<HttpClientErrorException> {
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsbarn(
                        sletteHusstandsbarn = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }!!.id,
                    ),
                )
            }
        }

        @Test
        @Transactional
        open fun `skal få slette både manuelle og offentlige husstandsbarnperioder`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()
            stubbeHentingAvPersoninfoForTestpersoner()

            val husstandsbarnperiodeSomSkalSlettes = behandling.husstandsbarn.first().perioder.first()

            assertSoftly {
                husstandsbarnperiodeSomSkalSlettes shouldNotBe null
                husstandsbarnperiodeRepository.findById(husstandsbarnperiodeSomSkalSlettes.id!!).isPresent
            }

            // hvis
            val oppdatereBoforholdResponse =
                boforholdService.oppdatereHusstandsbarnManuelt(
                    behandling.id!!,
                    OppdatereHusstandsbarn(sletteHusstandsbarnperiode = husstandsbarnperiodeSomSkalSlettes.id!!),
                )

            // så
            entityManager.refresh(behandling)

            assertSoftly(oppdatereBoforholdResponse) {
                it.oppdatertHusstandsbarn shouldNotBe null
                it.oppdatertHusstandsbarn!!.ident shouldBe husstandsbarnperiodeSomSkalSlettes.husstandsbarn.ident
            }
        }

        @Test
        @Transactional
        open fun `skal kunne slette manuelt husstandsbarn`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            val husstandsbarn = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }
            husstandsbarn?.kilde = Kilde.MANUELL
            entityManager.persist(husstandsbarn)

            // hvis
            boforholdService.oppdatereHusstandsbarnManuelt(
                behandling.id!!,
                OppdatereHusstandsbarn(sletteHusstandsbarn = behandling.husstandsbarn.find { testdataBarn1.ident == it.ident }!!.id),
            )

            // så
            assertSoftly {
                behandling.husstandsbarn.find { testdataBarn1.ident == it.ident } shouldBe null
            }
        }
    }

    @Nested
    open inner class Sivilstandstester {
        @Test
        @Transactional
        open fun `skal oppdatere sivilstand`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

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
                SivilstandApi.beregnV1(
                    minOf(behandling.virkningstidspunktEllerSøktFomDato),
                    grunnlagSivilstand,
                )

            // hvis
            boforholdService.oppdatereAutomatiskInnhentaSivilstand(behandling, periodisertSivilstand)

            // så
            entityManager.refresh(behandling)

            assertSoftly(behandling.sivilstand) { s ->
                s.size shouldBe 1
                s.first { Sivilstandskode.BOR_ALENE_MED_BARN == it.sivilstand } shouldNotBe null
                s.first { behandling.virkningstidspunktEllerSøktFomDato == it.datoFom } shouldNotBe null
            }
        }

        @Test
        @Transactional
        open fun `skal lagre periodisert sivilstand basert på førstegangsinnhenting av grunnlag`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

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

        @Test
        @Transactional
        @Disabled
        open fun `skal legge til sivilstand manuelt`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            // hvis
            boforholdService.oppdatereSivilstandManuelt(
                behandling.id!!,
                OppdatereSivilstand(
                    leggeTilSivilstandsperiode =
                        Sivilstandsperiode(
                            LocalDate.now().minusMonths(7),
                            null,
                            Sivilstandskode.GIFT_SAMBOER,
                        ),
                ),
            )

            // så
            entityManager.refresh(behandling)
            assert(true)
        }

        @Test
        @Transactional
        @Disabled
        open fun `skal slette sivilstandsperiode`() {
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
