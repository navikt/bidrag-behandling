package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.transformers.boforhold.tilRelatertPerson
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@RunWith(Enclosed::class)
class BoforholdServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var boforholdService: BoforholdService

    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var entityManager: EntityManager

    @Nested
    open inner class Boforhold {
        @Test
        @Transactional
        open fun `skal oppdatere husstandsbarn`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            val periode1Til = testdataBarn2.foedselsdato.plusMonths(19)
            val periode2Fra = testdataBarn2.foedselsdato.plusMonths(44)

            val grunnlagBoforhold =
                listOf(
                    RelatertPersonGrunnlagDto(
                        partPersonId = behandling.bidragsmottaker!!.ident!!,
                        relatertPersonPersonId = "50505012345",
                        fødselsdato = testdataBarn1.foedselsdato,
                        erBarnAvBmBp = true,
                        navn = testdataBarn1.navn,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn1.foedselsdato,
                                    periodeTil = null,
                                ),
                            ),
                    ),
                    RelatertPersonGrunnlagDto(
                        partPersonId = behandling.bidragsmottaker!!.ident!!,
                        relatertPersonPersonId = testdataBarn2.ident,
                        fødselsdato = testdataBarn2.foedselsdato,
                        erBarnAvBmBp = true,
                        navn = testdataBarn2.navn,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn2.foedselsdato,
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
                BoforholdApi.beregnV1(
                    minOf(testdataBarn1.foedselsdato, testdataBarn2.foedselsdato),
                    grunnlagBoforhold.tilRelatertPerson(),
                )

            // hvis
            boforholdService.oppdatereAutomatiskInnhentaBoforhold(behandling, periodisertBoforhold)

            // så
            entityManager.refresh(behandling)

            assertSoftly(behandling.husstandsbarn) { husstandsbarn ->
                husstandsbarn.size shouldBe 2
            }

            assertSoftly(behandling.husstandsbarn.find { it.ident == "50505012345" }) { barn1 ->
                barn1 shouldNotBe null
                barn1!!.perioder.size shouldBe 2
            }

            assertSoftly(behandling.husstandsbarn.find { it.ident == testdataBarn2.ident }) { barn2 ->
                barn2 shouldNotBe null
                barn2!!.perioder.size shouldBe 3
            }
        }

        @Test
        @Transactional
        open fun `skal erstatte lagrede offentlige husstandsbarn med nye fra grunnlag ved førstegangsinnhenting `() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

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

            val periodisertBoforhold = BoforholdApi.beregnV1(LocalDate.now(), grunnlagBoforhold.tilRelatertPerson())

            behandling.husstandsbarn.size shouldBe 2

            // hvis
            boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(
                behandling,
                Personident(behandling.bidragsmottaker!!.ident!!),
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

            stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

            val periode1Til = testdataBarn2.foedselsdato.plusMonths(19)
            val periode2Fra = testdataBarn2.foedselsdato.plusMonths(44)

            val grunnlagBoforhold =
                listOf(
                    RelatertPersonGrunnlagDto(
                        partPersonId = behandling.bidragsmottaker!!.ident!!,
                        relatertPersonPersonId = testdataBarn1.ident,
                        fødselsdato = testdataBarn1.foedselsdato,
                        erBarnAvBmBp = true,
                        navn = testdataBarn1.navn,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn1.foedselsdato,
                                    periodeTil = null,
                                ),
                            ),
                    ),
                    RelatertPersonGrunnlagDto(
                        partPersonId = behandling.bidragsmottaker!!.ident!!,
                        relatertPersonPersonId = testdataBarn2.ident,
                        fødselsdato = testdataBarn2.foedselsdato,
                        erBarnAvBmBp = true,
                        navn = testdataBarn2.navn,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    periodeFra = testdataBarn2.foedselsdato,
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
                BoforholdApi.beregnV1(testdataBarn2.foedselsdato, grunnlagBoforhold.tilRelatertPerson())

            // hvis
            boforholdService.lagreFørstegangsinnhentingAvPeriodisertBoforhold(
                behandling,
                Personident(behandling.bidragsmottaker!!.ident!!),
                periodisertBoforhold,
            )

            // så
            entityManager.refresh(behandling)

            assertSoftly(behandling.husstandsbarn) {
                it.size shouldBe 2
                it.first { barn -> testdataBarn2.ident == barn.ident }.perioder.size shouldBe 3
            }
        }
    }

    @Nested
    open inner class Sivilstand
}
