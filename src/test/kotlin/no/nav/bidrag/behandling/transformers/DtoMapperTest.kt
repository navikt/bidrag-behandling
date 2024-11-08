package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteArbeidsforhold
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.person.PersonDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime

class DtoMapperTest : TestContainerRunner() {
    @Autowired
    lateinit var testdataManager: TestdataManager

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var validering: ValiderBeregning

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockK
    lateinit var personService: PersonService

    @InjectMockKs
    lateinit var dtomapper: Dtomapper

    @BeforeEach
    fun initMocks() {
        stubUtils.stubTilgangskontrollPersonISak()
    }

    @Nested
    @DisplayName("Teste differensiering av nytt mot gammelt grunnlag")
    open inner class Diffing {
        @Test
        fun `skal returnere diff for boforhold`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")
            val nyFomdato = LocalDate.now().minusYears(10)

            // gjeldende boforhold
            behandling.grunnlag.add(
                Grunnlag(
                    aktiv = LocalDateTime.now().minusDays(5),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    gjelder = testdataBarn1.ident,
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                BoforholdResponseV2(
                                    kilde = Kilde.OFFENTLIG,
                                    periodeFom = LocalDate.now().minusYears(13),
                                    periodeTom = null,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    fødselsdato = LocalDate.now().minusYears(13),
                                    gjelderPersonId = testdataBarn1.ident,
                                ),
                            ),
                        ),
                ),
            )

            // nytt boforhold
            behandling.grunnlag.add(
                Grunnlag(
                    aktiv = null,
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    gjelder = testdataBarn1.ident,
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                BoforholdResponseV2(
                                    kilde = Kilde.OFFENTLIG,
                                    periodeFom = nyFomdato,
                                    periodeTom = null,
                                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                    fødselsdato = LocalDate.now().minusYears(13),
                                    gjelderPersonId = testdataBarn1.ident,
                                ),
                            ),
                        ),
                ),
            )

            // hvis
            val ikkeAktivereGrunnlagsdata =
                dtomapper.tilAktivereGrunnlagResponseV2(behandling).ikkeAktiverteEndringerIGrunnlagsdata

            // så
            assertSoftly(ikkeAktivereGrunnlagsdata) { resultat ->
                resultat.husstandsmedlem shouldNotBe null
                resultat.husstandsmedlem shouldHaveSize 1
                resultat.husstandsmedlem.find { testdataBarn1.ident == it.ident } shouldNotBe null
                resultat.husstandsmedlem
                    .find { testdataBarn1.ident == it.ident }
                    ?.perioder
                    ?.shouldHaveSize(1)
                resultat.husstandsmedlem
                    .find {
                        testdataBarn1.ident == it.ident
                    }?.perioder
                    ?.maxBy { it.datoFom!! }!!
                    .datoFom shouldBe behandling.virkningstidspunktEllerSøktFomDato
                resultat.husstandsmedlem
                    .find {
                        testdataBarn1.ident == it.ident
                    }?.perioder
                    ?.maxBy { it.datoFom!! }!!
                    .bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
            }
        }

        @Test
        fun `skal returnere diff for arbeidsforhold`() {
            // gitt
            val b = oppretteTestbehandling(inkludereBp = true, inkludereArbeidsforhold = true)
            val nyttArbeidsforhold =
                oppretteArbeidsforhold(b.bidragspliktig!!.ident!!).copy(
                    startdato = LocalDate.now(),
                    arbeidsgiverNavn = "Skruer og mutrer AS",
                )
            b.grunnlag.add(
                Grunnlag(
                    b,
                    Grunnlagsdatatype.ARBEIDSFORHOLD,
                    false,
                    commonObjectmapper.writeValueAsString(setOf(nyttArbeidsforhold)),
                    LocalDateTime.now(),
                    null,
                    b.bidragspliktig!!,
                ),
            )

            testdataManager.lagreBehandlingNewTransaction(b)

            // hvis
            val ikkeAktivereGrunnlagsdata =
                dtomapper.tilAktivereGrunnlagResponseV2(b).ikkeAktiverteEndringerIGrunnlagsdata

            // så
            assertSoftly(ikkeAktivereGrunnlagsdata) { resultat ->
                resultat.arbeidsforhold shouldNotBe null
                resultat.arbeidsforhold shouldHaveSize 1
            }
        }

        @Test
        open fun `skal returnere diff for sivilstand`() {
            // gitt
            val behandling = testdataManager.oppretteBehandling(false)
            behandling.virkningstidspunkt = LocalDate.parse("2023-01-01")

            // gjeldende sivilstand
            behandling.grunnlag.add(
                Grunnlag(
                    aktiv = LocalDateTime.now().minusDays(5),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                Sivilstand(
                                    kilde = Kilde.OFFENTLIG,
                                    periodeFom = LocalDate.now().minusYears(13),
                                    periodeTom = null,
                                    sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                                ),
                            ),
                        ),
                ),
            )

            // ny sivilstand
            behandling.grunnlag.add(
                Grunnlag(
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                Sivilstand(
                                    kilde = Kilde.OFFENTLIG,
                                    periodeFom = LocalDate.now().minusYears(15),
                                    periodeTom = null,
                                    sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                                ),
                            ),
                        ),
                ),
            )

            // hvis
            val ikkeAktivereGrunnlagsdata =
                dtomapper.tilAktivereGrunnlagResponseV2(behandling).ikkeAktiverteEndringerIGrunnlagsdata

            // så
            assertSoftly(ikkeAktivereGrunnlagsdata) { resultat ->
                resultat.sivilstand shouldNotBe null
            }
        }
    }

    @Nested
    open inner class Underholdskostnad {
        @Test
        fun `skal mappe ident og navn til barnet underholdskostnaden gjelder`() {
            // gitt
            stubUtils.stubPerson(status = HttpStatus.OK, personident = testdataBarn1.ident)

            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            every { personService.hentPerson(testdataBarn1.ident) } returns
                PersonDto(
                    ident = Personident(testdataBarn1.ident),
                    navn = testdataBarn1.navn,
                    fødselsdato = testdataBarn1.fødselsdato,
                )

            // hvis
            val dto = dtomapper.tilUnderholdDto(behandling.underholdskostnader.first())

            // så
            dto.gjelderBarn.navn shouldBe testdataBarn1.navn
            dto.gjelderBarn.ident?.verdi shouldBe testdataBarn1.ident
        }
    }
}
