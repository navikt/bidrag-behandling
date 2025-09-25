package no.nav.bidrag.behandling.transformers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.service.BarnebidragGrunnlagInnhenting
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteArbeidsforhold
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.person.PersonDto
import org.assertj.core.error.ShouldNotBeNull.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stubPersonConsumer
import java.time.LocalDate
import java.time.LocalDateTime

class DtoMapperTest : TestContainerRunner() {
    @Autowired
    lateinit var testdataManager: TestdataManager

    lateinit var personService: PersonService

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var validering: ValiderBeregning

    @MockK
    lateinit var behandlingService: BehandlingService

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockkBean
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockkBean(relaxed = true)
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    lateinit var grunnlagsmapper: BehandlingTilGrunnlagMappingV2

    lateinit var vedtakGrunnlagsmapper: VedtakGrunnlagMapper

    lateinit var dtomapper: Dtomapper

    @BeforeEach
    fun initMocks() {
        stubSjablonProvider()
        personService = PersonService(stubPersonConsumer())
        grunnlagsmapper = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        vedtakGrunnlagsmapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService())),
                ValiderBeregning(),
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
        dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validering,
                validerBehandlingService,
                vedtakGrunnlagsmapper,
                BeregnBarnebidragApi(),
            )
        stubUtils.stubTilgangskontrollPersonISak()
        every { tilgangskontrollService.harBeskyttelse(any()) } returns false
        every { tilgangskontrollService.harTilgang(any(), any()) } returns true
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
        fun `skal returnere diff for Barnetilsyn`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    false,
                    false,
                    false,
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val barnetilsynInnhentesForRolle = Grunnlagsdatatype.BARNETILSYN.innhentesForRolle(behandling)!!
            barnetilsynInnhentesForRolle shouldBe behandling.bidragsmottaker!!
            val innhentet = LocalDateTime.now()

            // gjeldende barnetilsyn
            behandling.grunnlag.add(
                Grunnlag(
                    aktiv = LocalDateTime.now().minusDays(5),
                    behandling = behandling,
                    innhentet = LocalDateTime.now().minusDays(5),
                    gjelder = testdataBarn1.ident,
                    erBearbeidet = true,
                    rolle = barnetilsynInnhentesForRolle,
                    type = Grunnlagsdatatype.BARNETILSYN,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                BarnetilsynGrunnlagDto(
                                    beløp = 4000,
                                    periodeFra = LocalDate.now().minusYears(13),
                                    periodeTil = null,
                                    skolealder = null,
                                    tilsynstype = null,
                                    barnPersonId = testdataBarn1.ident,
                                    partPersonId = barnetilsynInnhentesForRolle.ident!!,
                                ),
                            ),
                        ),
                ),
            )

            // nytt barnetilsyn
            behandling.grunnlag.add(
                Grunnlag(
                    aktiv = null,
                    behandling = behandling,
                    innhentet = innhentet,
                    gjelder = testdataBarn1.ident,
                    erBearbeidet = true,
                    rolle = barnetilsynInnhentesForRolle,
                    type = Grunnlagsdatatype.BARNETILSYN,
                    data =
                        commonObjectmapper.writeValueAsString(
                            setOf(
                                BarnetilsynGrunnlagDto(
                                    beløp = 4500,
                                    periodeFra = LocalDate.now().minusYears(1),
                                    periodeTil = LocalDate.now().minusMonths(6),
                                    skolealder = Skolealder.IKKE_ANGITT,
                                    tilsynstype = Tilsynstype.IKKE_ANGITT,
                                    barnPersonId = testdataBarn1.ident,
                                    partPersonId = barnetilsynInnhentesForRolle.ident!!,
                                ),
                                BarnetilsynGrunnlagDto(
                                    beløp = 4600,
                                    periodeFra = LocalDate.now().minusMonths(6),
                                    periodeTil = LocalDate.now().minusMonths(4),
                                    skolealder = Skolealder.OVER,
                                    tilsynstype = Tilsynstype.HELTID,
                                    barnPersonId = testdataBarn1.ident,
                                    partPersonId = barnetilsynInnhentesForRolle.ident!!,
                                ),
                                BarnetilsynGrunnlagDto(
                                    beløp = 4700,
                                    periodeFra = LocalDate.now().minusMonths(4),
                                    periodeTil = null,
                                    skolealder = null,
                                    tilsynstype = null,
                                    barnPersonId = testdataBarn1.ident,
                                    partPersonId = barnetilsynInnhentesForRolle.ident!!,
                                ),
                            ),
                        ),
                ),
            )

            // hvis
            val ikkeAktivereGrunnlagsdata =
                dtomapper.tilAktivereGrunnlagResponseV2(behandling).ikkeAktiverteEndringerIGrunnlagsdata

            // så
            ikkeAktivereGrunnlagsdata.stønadTilBarnetilsyn shouldNotBe null

            assertSoftly(ikkeAktivereGrunnlagsdata.stønadTilBarnetilsyn!!) {
                stønadTilBarnetilsyn shouldNotBe null
                grunnlag shouldNotBe null
                innhentetTidspunkt shouldBe innhentet
            }

            ikkeAktivereGrunnlagsdata.stønadTilBarnetilsyn.stønadTilBarnetilsyn.shouldHaveSize(1)

            val nyttBarnetilsyn =
                ikkeAktivereGrunnlagsdata.stønadTilBarnetilsyn.stønadTilBarnetilsyn[Personident(testdataBarn1.ident)]
            nyttBarnetilsyn?.shouldHaveSize(3)

            assertSoftly(nyttBarnetilsyn!!.elementAt(0)) {
                periode shouldBe
                    DatoperiodeDto(
                        LocalDate.now().minusYears(1),
                        LocalDate.now().minusMonths(6).minusDays(1),
                    )
                tilsynstype shouldBe null
                skolealder shouldBe Skolealder.UNDER
                kilde shouldBe Kilde.OFFENTLIG
            }

            assertSoftly(nyttBarnetilsyn.elementAt(1)) {
                skolealder shouldBe Skolealder.UNDER
                tilsynstype shouldBe Tilsynstype.HELTID
                periode shouldBe
                    DatoperiodeDto(
                        LocalDate.now().minusMonths(6),
                        LocalDate.now().minusMonths(4).minusDays(1),
                    )
                kilde shouldBe Kilde.OFFENTLIG
            }

            assertSoftly(nyttBarnetilsyn.elementAt(2)) {
                skolealder shouldBe Skolealder.UNDER
                tilsynstype shouldBe null
                periode shouldBe DatoperiodeDto(LocalDate.now().minusMonths(4), null)
                kilde shouldBe Kilde.OFFENTLIG
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
        fun `skal mappe ident, navn, og begrunnelse til søknadsbarn`() {
            // gitt
            stubSjablonProvider()
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val rolleSøknadsbarn = behandling.roller.first { Rolletype.BARN == it.rolletype }
            behandling.notater.add(
                Notat(
                    1,
                    behandling,
                    rolleSøknadsbarn,
                    NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
                    innhold = "Underholdskostnad for søknadsbarn",
                ),
            )

            // hvis
            val dto = dtomapper.tilUnderholdDto(behandling.underholdskostnader.first())

            // så
            dto.gjelderBarn.navn shouldBe testdataBarn1.navn
            dto.gjelderBarn.ident?.verdi shouldBe testdataBarn1.ident
            dto.begrunnelse shouldBe "Underholdskostnad for søknadsbarn"
        }

        @Test
        fun `skal mappe ident, navn, og begrunnelse til annet barn`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            behandling.underholdskostnader.add(
                no.nav.bidrag.behandling.database.datamodell.Underholdskostnad(
                    3,
                    behandling,
                    Person(10, navn = "Annet Barn Bm", fødselsdato = LocalDate.now()),
                ),
            )

            behandling.notater.add(
                Notat(
                    10,
                    behandling,
                    behandling.bidragsmottaker!!,
                    NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
                    innhold = "Underholdskostnad for Bms andre barn",
                ),
            )

            every { personService.hentPerson(testdataBarn1.ident) } returns
                PersonDto(
                    ident = Personident(testdataBarn1.ident),
                    navn = testdataBarn1.navn,
                    fødselsdato = testdataBarn1.fødselsdato,
                )

            // hvis
            val dto = dtomapper.tilUnderholdDto(behandling.underholdskostnader.find { it.person?.id == 10L }!!)

            // så
            dto.gjelderBarn.navn shouldBe "Annet Barn Bm"
            dto.gjelderBarn.ident.shouldBeNull()
            dto.begrunnelse shouldBe "Underholdskostnad for Bms andre barn"
        }

        @Test
        fun `skal legge til informasjon om tilleggsstønad`() {
            // gitt
            val behandling =
                oppretteTestbehandling(
                    setteDatabaseider = true,
                    inkludereBp = true,
                    behandlingstype = TypeBehandling.BIDRAG,
                )

            val innhentetForRolle = behandling.bidragsmottaker!!
            val tilleggsstønadsgrunnlag = TilleggsstønadGrunnlagDto(innhentetForRolle.personident!!.verdi, true)
            val innhentetGrunnlag =
                Grunnlag(
                    behandling,
                    Grunnlagsdatatype.TILLEGGSSTØNAD,
                    false,
                    false,
                    commonObjectmapper.writeValueAsString(setOf(tilleggsstønadsgrunnlag)),
                    LocalDateTime.now(),
                    rolle = innhentetForRolle,
                )
            behandling.grunnlag.add(innhentetGrunnlag)

            every { validerBehandlingService.kanBehandlesINyLøsning(any()) } returns null

            // hvis
            val dto = dtomapper.tilDto(behandling)

            // så
            assertSoftly(dto.roller.find { Rolletype.BIDRAGSMOTTAKER == it.rolletype }) {
                shouldNotBeNull()
                it!!.harInnvilgetTilleggsstønad shouldNotBe null
                it.harInnvilgetTilleggsstønad shouldBe true
            }
        }
    }
}
