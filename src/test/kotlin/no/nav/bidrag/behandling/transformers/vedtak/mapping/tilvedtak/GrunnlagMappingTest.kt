package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID
import no.nav.bidrag.behandling.utils.testdata.TestDataPerson
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettEvnevurderingResultat
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettRolle
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SærbidragskategoriGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftDirekteBetaltGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftspostGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjektListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetHusstandsmedlemGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetSivilstandGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.søknadsbarn
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import stubHentPersonNyIdent
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class GrunnlagMappingTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initPersonMock() {
            stubPersonConsumer()
        }
    }

    private val fødslesdato = LocalDate.parse("2024-04-03")

    val grunnlagBm =
        Rolle(
            behandling = oppretteTestbehandling(),
            ident = testdataBM.ident,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            fødselsdato = testdataBM.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val grunnlagBp =
        Rolle(
            behandling = oppretteTestbehandling(),
            ident = testdataBP.ident,
            rolletype = Rolletype.BIDRAGSPLIKTIG,
            fødselsdato = testdataBP.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag1 =
        Rolle(
            behandling = oppretteTestbehandling(),
            ident = testdataBarn1.ident,
            rolletype = Rolletype.BARN,
            fødselsdato = testdataBarn1.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag2 =
        Rolle(
            behandling = oppretteTestbehandling(),
            ident = testdataBarn2.ident,
            rolletype = Rolletype.BARN,
            fødselsdato = testdataBarn2.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()

    val personobjekter = setOf(grunnlagBm, grunnlagBp, søknadsbarnGrunnlag1, søknadsbarnGrunnlag2)

    lateinit var personStub: BidragPersonConsumer
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting
    lateinit var mapper: VedtakGrunnlagMapper
    lateinit var behandlingTilGrunnlagMapping: BehandlingTilGrunnlagMappingV2
    val evnevurderingService: BeregningEvnevurderingService = mockkClass(BeregningEvnevurderingService::class)

    @BeforeEach
    fun initMocks() {
        clearAllMocks()
        stubKodeverkProvider()
        personStub = stubPersonConsumer()
        barnebidragGrunnlagInnhenting = mockkClass(BarnebidragGrunnlagInnhenting::class)
        val personService = PersonService(personStub)
        behandlingTilGrunnlagMapping = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        val validering = ValiderBeregning()

        every { evnevurderingService.hentLøpendeBidragForBehandling(any()) } returns
            opprettEvnevurderingResultat(
                listOf(
                    testdataBarn1 to Stønadstype.BIDRAG,
                    testdataHusstandsmedlem1 to Stønadstype.BIDRAG,
                ),
            )
        mapper = VedtakGrunnlagMapper(behandlingTilGrunnlagMapping, validering, evnevurderingService, barnebidragGrunnlagInnhenting, personService, BeregnGebyrApi(stubSjablonService()))
    }

    @Nested
    inner class GrunnlagByggerTest {
        @Test
        fun `skal bygge grunnlag for stønad for forskudd`(): Unit =
            mapper.run {
                val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
                behandling.leggTilNotat(
                    "Notat inntekt BM",
                    NotatGrunnlag.NotatType.INNTEKT,
                    behandling.bidragsmottaker!!,
                )
                behandling.leggTilNotat(
                    "Utgift notat",
                    NotatGrunnlag.NotatType.UTGIFTER,
                )
                behandling.leggTilNotat(
                    "Boforhold",
                    NotatGrunnlag.NotatType.BOFORHOLD,
                )
                behandling.stonadstype = Stønadstype.FORSKUDD
                behandling.engangsbeloptype = null
                val grunnlag = behandling.byggGrunnlagGenerelt()

                assertSoftly(grunnlag.toList()) {
                    it shouldHaveSize 5
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.NOTAT) shouldHaveSize 3
                }
            }

        @Test
        fun `skal bygge grunnlag for engangsbeløp for særbidrag`(): Unit =
            mapper.run {
                val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
                behandling.leggTilNotat(
                    "Notat inntekt BM",
                    NotatGrunnlag.NotatType.INNTEKT,
                    behandling.bidragsmottaker!!,
                )
                behandling.leggTilNotat(
                    "Utgift notat",
                    NotatGrunnlag.NotatType.UTGIFTER,
                )
                behandling.leggTilNotat(
                    "Boforhold",
                    NotatGrunnlag.NotatType.BOFORHOLD,
                )
                behandling.stonadstype = null
                behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG

                val grunnlag = behandling.byggGrunnlagGenerelt()

                assertSoftly(grunnlag.toList()) {
                    it shouldHaveSize 6
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.NOTAT) shouldHaveSize 3
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.SÆRBIDRAG_KATEGORI) shouldHaveSize 1
                }
            }

        @Test
        fun `skal bygge grunnlag for vedtak`(): Unit =
            mapper.run {
                val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
                behandling.grunnlag =
                    opprettAlleAktiveGrunnlagFraFil(
                        behandling,
                        "grunnlagresponse.json",
                    )
                behandling.inntekter =
                    mutableSetOf(
                        Inntekt(
                            belop = BigDecimal(50000),
                            datoTom = LocalDate.parse("2022-06-30"),
                            datoFom = LocalDate.parse("2022-01-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                            id = 1,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            id = 2,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = testdataBarn1.ident,
                            type = Inntektsrapportering.KONTANTSTØTTE,
                            id = 22,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = testdataBarn2.ident,
                            type = Inntektsrapportering.KONTANTSTØTTE,
                            id = 33,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = testdataBarn1.ident,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            id = 2,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = testdataBarn2.ident,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            id = 2,
                        ),
                    )
                val grunnlagForBeregning =
                    behandling.byggGrunnlagForVedtak()

                assertSoftly(grunnlagForBeregning.toList()) {
                    it shouldHaveSize 39
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_HUSSTANDSMEDLEM) shouldHaveSize 3
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 2
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE) shouldHaveSize 6
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE) shouldHaveSize 6
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 5
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 2
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 1
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 4
                    it.filtrerBasertPåEgenReferanse(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3
                }
            }

        @Test
        fun `skal bygge grunnlag for beregning`(): Unit =
            mapper.run {
                val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
                behandling.inntekter =
                    mutableSetOf(
                        Inntekt(
                            belop = BigDecimal(50000),
                            datoTom = LocalDate.parse("2022-06-30"),
                            datoFom = LocalDate.parse("2022-01-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                            id = 1,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            id = 2,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = testdataBarn1.ident,
                            type = Inntektsrapportering.KONTANTSTØTTE,
                            id = 22,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = testdataBarn2.ident,
                            type = Inntektsrapportering.KONTANTSTØTTE,
                            id = 33,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = testdataBarn1.ident,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            id = 2,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = testdataBarn2.ident,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            id = 2,
                        ),
                    )
                val søknadsbarn1 = behandling.søknadsbarn.find { it.ident == testdataBarn1.ident }
                val grunnlagForBeregning =
                    byggGrunnlagForBeregning(behandling, søknadsbarn1!!)

                assertSoftly(grunnlagForBeregning) {
                    it.grunnlagListe shouldHaveSize 15
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_HUSSTANDSMEDLEM) shouldHaveSize 1
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 2
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE) shouldHaveSize 6
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE) shouldHaveSize 4
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE) shouldHaveSize 1
                    it.søknadsbarnReferanse shouldBe søknadsbarn1.tilGrunnlagPerson().referanse

                    it.grunnlagListe
                        .filtrerBasertPåFremmedReferanse(
                            Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = søknadsbarnGrunnlag2.referanse,
                        ).shouldBeEmpty()
                    it.grunnlagListe
                        .filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
                        .filter { it.innholdTilObjekt<InntektsrapporteringPeriode>().gjelderBarn == søknadsbarnGrunnlag2.referanse }
                        .shouldBeEmpty()
                }
                val søknadsbarn2 = behandling.søknadsbarn.find { it.ident == testdataBarn2.ident }
                val grunnlagForBeregning2 =
                    byggGrunnlagForBeregning(behandling, søknadsbarn2!!)

                assertSoftly(grunnlagForBeregning2) {
                    it.grunnlagListe shouldHaveSize 15
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_BIDRAGSMOTTAKER) shouldHaveSize 1
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_HUSSTANDSMEDLEM) shouldHaveSize 1
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_SØKNADSBARN) shouldHaveSize 2
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE) shouldHaveSize 6
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE) shouldHaveSize 4
                    it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE) shouldHaveSize 1
                    it.søknadsbarnReferanse shouldBe søknadsbarn2.tilGrunnlagPerson().referanse

                    it.grunnlagListe
                        .filtrerBasertPåFremmedReferanse(
                            Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = søknadsbarnGrunnlag1.referanse,
                        ).shouldBeEmpty()
                    it.grunnlagListe
                        .filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
                        .filter { it.innholdTilObjekt<InntektsrapporteringPeriode>().gjelderBarn == søknadsbarnGrunnlag1.referanse }
                        .shouldBeEmpty()
                }
            }
    }

    @Nested
    inner class SærbidragGrunnlagByggerTest {
        @Test
        fun `skal bygge særbidragskategori grunnlag`(): Unit =
            mapper.run {
                val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
                behandling.stonadstype = null
                behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
                behandling.kategori = Særbidragskategori.ANNET.name
                behandling.kategoriBeskrivelse = "Kategori beskrivelse test"

                val grunnlag = behandling.byggGrunnlagSærbidragKategori()

                assertSoftly(grunnlag.toList()) {
                    it shouldHaveSize 1
                    val utgift = it.innholdTilObjekt<SærbidragskategoriGrunnlag>().first()
                    utgift.kategori shouldBe Særbidragskategori.ANNET
                    utgift.beskrivelse shouldBe "Kategori beskrivelse test"
                }
            }

        @Test
        fun `skal bygge utgift direkte betalt`(): Unit =
            mapper.run {
                val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
                behandling.stonadstype = null
                behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
                behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal(1234)

                val grunnlag = behandling.byggGrunnlagUtgiftDirekteBetalt()

                assertSoftly(grunnlag.toList()) {
                    it shouldHaveSize 1
                    val utgift = it.innholdTilObjekt<UtgiftDirekteBetaltGrunnlag>().first()
                    utgift.beløpDirekteBetalt shouldBe BigDecimal(1234)
                }
            }

        @Test
        fun `skal bygge utgiftsposter grunnlag`(): Unit =
            mapper.run {
                val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
                behandling.stonadstype = null
                behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
                behandling.utgift!!.utgiftsposter =
                    mutableSetOf(
                        Utgiftspost(
                            dato = LocalDate.now().minusMonths(3),
                            type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                            utgift = behandling.utgift!!,
                            godkjentBeløp = BigDecimal(15000),
                            kravbeløp = BigDecimal(5000),
                            kommentar = "Inneholder avgifter for alkohol og pynt",
                            betaltAvBp = true,
                        ),
                        Utgiftspost(
                            dato = LocalDate.now().minusMonths(8),
                            type = Utgiftstype.KLÆR.name,
                            utgift = behandling.utgift!!,
                            kravbeløp = BigDecimal(10000),
                            godkjentBeløp = BigDecimal(10000),
                        ),
                        Utgiftspost(
                            dato = LocalDate.now().minusMonths(5),
                            type = Utgiftstype.SELSKAP.name,
                            utgift = behandling.utgift!!,
                            kravbeløp = BigDecimal(10000),
                            godkjentBeløp = BigDecimal(5000),
                            kommentar = "Inneholder utgifter til mat og drikke",
                        ),
                    )

                val grunnlag = behandling.byggGrunnlagUtgiftsposter()

                assertSoftly(grunnlag.toList()) {
                    it shouldHaveSize 1
                    val utgiftsposter = it.first().innholdTilObjektListe<List<UtgiftspostGrunnlag>>()
                    utgiftsposter shouldHaveSize 3
                    val utgiftspostKonfirmasjon = utgiftsposter.find { it.type == Utgiftstype.KONFIRMASJONSAVGIFT.name }!!
                    utgiftspostKonfirmasjon.dato shouldBe LocalDate.now().minusMonths(3)
                    utgiftspostKonfirmasjon.type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
                    utgiftspostKonfirmasjon.betaltAvBp shouldBe true
                    utgiftspostKonfirmasjon.godkjentBeløp shouldBe BigDecimal(15000)
                    utgiftspostKonfirmasjon.kravbeløp shouldBe BigDecimal(5000)
                    utgiftspostKonfirmasjon.kommentar shouldBe "Inneholder avgifter for alkohol og pynt"
                }
            }
    }

    @Nested
    inner class PersonGrunnlagTest {
        @Test
        fun `skal mappe bare ene søknadsbarnet og parter til liste over persongrunnlag`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteBehandling(1)
                behandling.roller =
                    mutableSetOf(
                        opprettRolle(behandling, testdataBM, 1),
                        opprettRolle(behandling, testdataBarn1, 1),
                        opprettRolle(behandling, testdataBarn2, 1),
                    )
                assertSoftly(behandling.tilPersonobjekter(testdataBarn1.tilRolle())) {
                    shouldHaveSize(2)
                    it.søknadsbarn shouldHaveSize 1
                    it.søknadsbarn
                        .toList()
                        .firstOrNull()
                        ?.referanse shouldBe søknadsbarnGrunnlag1.referanse
                    it.bidragsmottaker shouldNotBe null
                }

                assertSoftly(behandling.tilPersonobjekter(testdataBarn2.tilRolle())) {
                    shouldHaveSize(2)
                    it.søknadsbarn shouldHaveSize 1
                    it.søknadsbarn
                        .toList()
                        .firstOrNull()
                        ?.referanse shouldBe søknadsbarnGrunnlag2.referanse
                    it.bidragsmottaker shouldNotBe null
                }
            }

        @Test
        fun `skal mappe alle roller til liste over persongrunnlag`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteBehandling(1)
                behandling.roller =
                    mutableSetOf(
                        opprettRolle(behandling, testdataBM, 1),
                        opprettRolle(behandling, testdataBarn1, 1),
                        opprettRolle(behandling, testdataBarn2, 1),
                    )
                assertSoftly(behandling.tilPersonobjekter()) {
                    shouldHaveSize(3)
                    it.søknadsbarn shouldHaveSize 2
                    it.bidragsmottaker shouldNotBe null
                    it.bidragspliktig shouldBe null
                }

                behandling.roller =
                    mutableSetOf(
                        opprettRolle(behandling, testdataBM, 1),
                        opprettRolle(behandling, testdataBP, 1),
                        opprettRolle(behandling, testdataBarn1, 1),
                        opprettRolle(behandling, testdataBarn2, 1),
                    )

                assertSoftly(behandling.tilPersonobjekter()) {
                    shouldHaveSize(4)
                    it.søknadsbarn shouldHaveSize 2
                    it.bidragsmottaker shouldNotBe null
                    it.bidragspliktig shouldNotBe null
                }
            }

        @Test
        fun `skal mappe rolle til grunnlag`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                assertSoftly(
                    Rolle(
                        behandling = behandling,
                        ident = "12345678901",
                        rolletype = Rolletype.BIDRAGSMOTTAKER,
                        fødselsdato = fødslesdato,
                        id = 1L,
                    ).tilGrunnlagPerson(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_BIDRAGSMOTTAKER
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe Personident("12345678901")
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
                assertSoftly(
                    Rolle(
                        behandling = behandling,
                        ident = "12345678901",
                        rolletype = Rolletype.BARN,
                        fødselsdato = fødslesdato,
                        id = 1L,
                    ).tilGrunnlagPerson(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_SØKNADSBARN
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe Personident("12345678901")
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
                assertSoftly(
                    Rolle(
                        behandling = behandling,
                        ident = "12345678901",
                        rolletype = Rolletype.BIDRAGSPLIKTIG,
                        fødselsdato = fødslesdato,
                        id = 1L,
                    ).tilGrunnlagPerson(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_BIDRAGSPLIKTIG
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe Personident("12345678901")
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
            }

        @Test
        fun `skal mappe husstandsmedlem til grunnlag`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                assertSoftly(
                    Husstandsmedlem(
                        behandling = behandling,
                        ident = "12345678901",
                        fødselsdato = fødslesdato,
                        perioder = mutableSetOf(),
                        kilde = Kilde.MANUELL,
                        id = 1L,
                    ).tilGrunnlagPerson(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe Personident("12345678901")
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
            }

        @Test
        fun `skal mappe relatertperson til grunnlag`(): Unit =
            behandlingTilGrunnlagMapping.run {
                assertSoftly(
                    RelatertPersonGrunnlagDto(
                        partPersonId = "123123123",
                        relatertPersonPersonId = "12345678901",
                        fødselsdato = fødslesdato,
                        navn = "Ola Nordmann",
                        borISammeHusstandDtoListe = emptyList(),
                        erBarnAvBmBp = true,
                    ).tilPersonGrunnlag(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe Personident("12345678901")
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
            }

        @Test
        fun `skal mappe husstandsmedlem uten ident til grunnlag`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                assertSoftly(
                    Husstandsmedlem(
                        behandling = behandling,
                        ident = "",
                        navn = "Navn navnesen",
                        fødselsdato = fødslesdato,
                        perioder = mutableSetOf(),
                        kilde = Kilde.MANUELL,
                        id = 1L,
                    ).tilGrunnlagPerson(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe null
                    personGrunnlag.navn shouldBe "Navn navnesen"
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
            }

        @Test
        fun `skal mappe relatertperson med nyeste ident`(): Unit =
            behandlingTilGrunnlagMapping.run {
                stubHentPersonNyIdent("12345678901", "1231232131")
                assertSoftly(
                    RelatertPersonGrunnlagDto(
                        partPersonId = "123123123",
                        relatertPersonPersonId = "12345678901",
                        fødselsdato = null,
                        navn = "Ola Nordmann",
                        borISammeHusstandDtoListe = emptyList(),
                        erBarnAvBmBp = true,
                    ).tilPersonGrunnlag(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe Personident("1231232131")
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe LocalDate.parse("2020-02-02")
                }
            }

        @Test
        fun `skal mappe husstandsmedlem med nyeste ident`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                stubHentPersonNyIdent("12345678901", "1231232131")
                assertSoftly(
                    Husstandsmedlem(
                        behandling = behandling,
                        ident = "12345678901",
                        navn = "Navn navnesen",
                        fødselsdato = fødslesdato,
                        perioder = mutableSetOf(),
                        kilde = Kilde.MANUELL,
                        id = 1L,
                    ).tilGrunnlagPerson(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident?.verdi shouldBe "12345678901"
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
            }

        @Test
        fun `skal mappe rolle med nyeste ident`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                stubHentPersonNyIdent("12345678901", "1231232131", personStub)
                assertSoftly(
                    Rolle(
                        behandling = behandling,
                        ident = "12345678901",
                        rolletype = Rolletype.BIDRAGSPLIKTIG,
                        fødselsdato = fødslesdato,
                        id = 1L,
                    ).tilGrunnlagPerson(),
                ) {
                    it.type shouldBe Grunnlagstype.PERSON_BIDRAGSPLIKTIG
                    it.referanse.shouldStartWith("person")
                    val personGrunnlag: Person = it.innholdTilObjekt()
                    personGrunnlag.ident shouldBe Personident("1231232131")
                    personGrunnlag.navn shouldBe null
                    personGrunnlag.fødselsdato shouldBe fødslesdato
                }
            }
    }

    @Nested
    inner class InntektGrunnlagTest {
        val ainntektGrunnlagsreferanseListe = listOf("ainntekt_1", "ainntekt_2", "ainntekt_3")
        val skattegrunnlagGrunnlagsreferanseListe = listOf("skattegrunnlag_2023")

        @Test
        fun `skal mappe inntekt til grunnlag`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                behandling.grunnlag =
                    opprettInntekterBearbeidetGrunnlag(
                        behandling,
                        testdataBM,
                    ).toMutableSet()

                behandling.inntekter = opprettInntekter(behandling, testdataBM, testdataBarn1)
                assertSoftly(
                    behandling.tilGrunnlagInntekt(personobjekter, søknadsbarnGrunnlag1).toList(),
                ) {
                    it.forEach { inntekt ->
                        inntekt.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
                        inntekt.referanse.shouldStartWith("inntekt")
                        inntekt.gjelderReferanse shouldBe grunnlagBm.referanse
                    }
                    assertSoftly(this[0]) { inntekt ->
                        val innhold: InntektsrapporteringPeriode = inntekt.innholdTilObjekt()
                        innhold.inntektsrapportering shouldBe Inntektsrapportering.AINNTEKT_BEREGNET_12MND
                        innhold.periode.fom shouldBe YearMonth.parse("2023-01")
                        innhold.periode.til shouldBe YearMonth.parse("2024-01")
                        innhold.opprinneligPeriode?.fom shouldBe YearMonth.parse("2023-01")
                        innhold.opprinneligPeriode?.til shouldBe YearMonth.parse("2023-07")
                        innhold.beløp shouldBe BigDecimal(45000)
                        innhold.valgt shouldBe false
                        innhold.manueltRegistrert shouldBe false
                        innhold.gjelderBarn.shouldBeNull()
                        innhold.inntektspostListe shouldHaveSize 2
                        inntekt.grunnlagsreferanseListe shouldHaveSize 3
                        with(innhold.inntektspostListe[0]) {
                            beløp shouldBe BigDecimal(5000)
                            kode shouldBe "fisking"
                        }
                        with(innhold.inntektspostListe[1]) {
                            beløp shouldBe BigDecimal(40000)
                            kode shouldBe "krypto"
                        }
                    }
                    assertSoftly(this[1]) { inntekt ->
                        val innhold: InntektsrapporteringPeriode = inntekt.innholdTilObjekt()
                        innhold.inntektsrapportering shouldBe Inntektsrapportering.LIGNINGSINNTEKT
                        innhold.periode.fom shouldBe YearMonth.parse("2023-01")
                        innhold.periode.til shouldBe YearMonth.parse("2024-01")
                        innhold.beløp shouldBe BigDecimal(33000)
                        innhold.valgt shouldBe true
                        innhold.manueltRegistrert shouldBe false
                        innhold.gjelderBarn.shouldBeNull()
                        innhold.inntektspostListe shouldHaveSize 2
                        inntekt.grunnlagsreferanseListe shouldHaveSize 1
                        with(innhold.inntektspostListe[0]) {
                            beløp shouldBe BigDecimal(5000)
                            kode shouldBe ""
                            inntektstype shouldBe Inntektstype.NÆRINGSINNTEKT
                        }
                        with(innhold.inntektspostListe[1]) {
                            beløp shouldBe BigDecimal(28000)
                            kode shouldBe ""
                            inntektstype shouldBe Inntektstype.LØNNSINNTEKT
                        }
                    }

                    assertSoftly(this[2]) { inntekt ->
                        val innhold: InntektsrapporteringPeriode = inntekt.innholdTilObjekt()
                        innhold.inntektsrapportering shouldBe Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT
                        innhold.periode.fom shouldBe YearMonth.parse("2022-01")
                        innhold.periode.til shouldBe YearMonth.parse("2023-01")
                        innhold.beløp shouldBe BigDecimal(55000)
                        innhold.valgt shouldBe true
                        innhold.manueltRegistrert shouldBe true
                        inntekt.grunnlagsreferanseListe shouldHaveSize 0
                        innhold.gjelderBarn.shouldBeNull()
                        innhold.inntektspostListe shouldHaveSize 0
                    }

                    assertSoftly(this[3]) { inntekt ->
                        val innhold: InntektsrapporteringPeriode = inntekt.innholdTilObjekt()
                        innhold.inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
                        innhold.periode.fom shouldBe YearMonth.parse("2022-01")
                        innhold.periode.til shouldBe YearMonth.parse("2023-01")
                        innhold.beløp shouldBe BigDecimal(5000)
                        innhold.valgt shouldBe true
                        innhold.gjelderBarn shouldBe søknadsbarnGrunnlag1.referanse
                        innhold.manueltRegistrert shouldBe false
                        innhold.inntektspostListe shouldHaveSize 1
                        inntekt.grunnlagsreferanseListe shouldHaveSize 3
                        with(innhold.inntektspostListe[0]) {
                            beløp shouldBe BigDecimal(5000)
                            kode shouldBe ""
                            inntektstype shouldBe Inntektstype.BARNETILLEGG_PENSJON
                        }
                    }

                    assertSoftly(this[4]) { inntekt ->
                        val innhold: InntektsrapporteringPeriode = inntekt.innholdTilObjekt()
                        innhold.inntektsrapportering shouldBe Inntektsrapportering.KONTANTSTØTTE
                        innhold.periode.fom shouldBe YearMonth.parse("2023-01")
                        innhold.periode.til shouldBe YearMonth.parse("2024-01")
                        innhold.beløp shouldBe BigDecimal(5000)
                        innhold.valgt shouldBe true
                        innhold.gjelderBarn shouldBe søknadsbarnGrunnlag1.referanse
                        innhold.manueltRegistrert shouldBe false
                        inntekt.grunnlagsreferanseListe shouldHaveSize 3
                        innhold.inntektspostListe shouldHaveSize 1
                        with(innhold.inntektspostListe[0]) {
                            beløp shouldBe BigDecimal(5000)
                            kode shouldBe ""
                            inntektstype shouldBe Inntektstype.KONTANTSTØTTE
                        }
                    }
                }
            }

        @Test
        fun `skal ikke mappe inntekt til grunnlag hvis inntekt ikke tilhører søknadsbarn`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                behandling.roller = oppretteBehandlingRoller(behandling)
                behandling.grunnlag =
                    opprettInntekterBearbeidetGrunnlag(
                        behandling,
                        testdataBM,
                    ).toMutableSet()

                behandling.inntekter =
                    mutableSetOf(
                        Inntekt(
                            belop = BigDecimal(50000),
                            datoTom = LocalDate.parse("2022-06-30"),
                            datoFom = LocalDate.parse("2022-01-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                            id = 1,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                            id = 2,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = testdataBarn1.ident,
                            type = Inntektsrapportering.KONTANTSTØTTE,
                            id = 22,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = testdataBarn2.ident,
                            type = Inntektsrapportering.BARNETILLEGG,
                            id = 22,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = "123123123123",
                            type = Inntektsrapportering.KONTANTSTØTTE,
                            id = 33,
                        ),
                        Inntekt(
                            belop = BigDecimal(60000),
                            datoTom = LocalDate.parse("2022-09-01"),
                            datoFom = LocalDate.parse("2022-07-01"),
                            ident = behandling.bidragsmottaker!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            gjelderBarn = "123123123123",
                            type = Inntektsrapportering.BARNETILLEGG,
                            id = 33,
                        ),
                    )
                assertSoftly(
                    behandling.tilGrunnlagInntekt(personobjekter).toList(),
                ) {
                    it shouldHaveSize 4

                    it.hentInntekt(Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER) shouldHaveSize 1
                    it.hentInntekt(Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT) shouldHaveSize 1
                    it.hentInntekt(Inntektsrapportering.KONTANTSTØTTE) shouldHaveSize 1
                    it.hentInntekt(Inntektsrapportering.BARNETILLEGG) shouldHaveSize 1

                    it
                        .hentInntekt(Inntektsrapportering.KONTANTSTØTTE)
                        .first()
                        .innholdTilObjekt<InntektsrapporteringPeriode>()
                        .gjelderBarn shouldBe søknadsbarnGrunnlag1.referanse
                    it
                        .hentInntekt(Inntektsrapportering.BARNETILLEGG)
                        .first()
                        .innholdTilObjekt<InntektsrapporteringPeriode>()
                        .gjelderBarn shouldBe søknadsbarnGrunnlag2.referanse
                }
            }

        @Test
        fun `skal feile mapping av inntektperiode hvis søknadsbarn mangler`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                behandling.grunnlag =
                    opprettInntekterBearbeidetGrunnlag(
                        behandling,
                        testdataBM,
                    ).toMutableSet()

                behandling.inntekter = opprettInntekter(behandling, testdataBM, testdataBarn1)
                behandling.inntekter.forEach {
                    if (it.type == Inntektsrapportering.BARNETILLEGG) {
                        it.gjelderBarn = null
                    }
                }
                val exception =
                    assertThrows<HttpClientErrorException> {
                        behandling.tilGrunnlagInntekt(
                            personobjekter,
                            søknadsbarnGrunnlag1,
                        )
                    }
                exception.message shouldContain "Kunne ikke bygge grunnlag: Mangler søknadsbarn for inntektsrapportering BARNETILLEGG"
            }

        @Test
        fun `skal mappe inntekt til grunnlag hvis inneholder inntektliste for Barn og BP`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()
                behandling.grunnlag =
                    (
                        opprettInntekterBearbeidetGrunnlag(behandling, testdataBM) +
                            opprettInntekterBearbeidetGrunnlag(
                                behandling,
                                testdataBP,
                            ) +
                            opprettInntekterBearbeidetGrunnlag(
                                behandling,
                                testdataBarn1,
                            )
                    ).toMutableSet()

                behandling.inntekter.addAll(
                    (
                        opprettInntekter(behandling, testdataBM, testdataBarn1) +
                            opprettInntekter(behandling, testdataBM, testdataBarn2) +
                            opprettInntekter(
                                behandling,
                                testdataBP,
                            ) +
                            opprettInntekter(behandling, testdataBarn1) +
                            opprettInntekter(behandling, testdataBarn2)

                    ).toSet(),
                )
                val personobjektersøknadsbarn = setOf(grunnlagBm, grunnlagBp, søknadsbarnGrunnlag1)
                assertSoftly(
                    behandling
                        .tilGrunnlagInntekt(personobjektersøknadsbarn, søknadsbarnGrunnlag1)
                        .toList(),
                ) {
                    it shouldHaveSize 11
                    val bpInntekter =
                        it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBp.referanse)
                    bpInntekter.shouldHaveSize(3)

                    val bmInntekter =
                        it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBm.referanse)
                    bmInntekter.shouldHaveSize(5)

                    val barnInntekter =
                        it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag1.referanse)
                    barnInntekter.shouldHaveSize(3)

                    val barn2Inntekter =
                        it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag2.referanse)
                    barn2Inntekter.shouldHaveSize(0)
                }
            }

        @Test
        fun `skal legge til grunnlagsliste for innhentet inntekter`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()

                behandling.grunnlag =
                    opprettInntekterBearbeidetGrunnlag(
                        behandling,
                        testdataBM,
                    ).toMutableSet()
                behandling.inntekter.addAll(
                    opprettInntekter(behandling, testdataBM, testdataBarn1),
                )

                assertSoftly(behandling.tilGrunnlagInntekt(personobjekter).toList()) {
                    it.forEach {
                        val innhold = it.innholdTilObjekt<InntektsrapporteringPeriode>()
                        if (innhold.inntektsrapportering == Inntektsrapportering.AINNTEKT_BEREGNET_12MND) {
                            it.grunnlagsreferanseListe shouldBe ainntektGrunnlagsreferanseListe
                            innhold.versjon shouldBe "VERSJON_INNTEKT"
                        } else if (!innhold.manueltRegistrert) {
                            it.grunnlagsreferanseListe.shouldNotBeEmpty()
                            innhold.versjon shouldBe "VERSJON_INNTEKT"
                        } else {
                            innhold.versjon shouldBe null
                            it.grunnlagsreferanseListe.shouldBeEmpty()
                        }
                        it.gjelderReferanse shouldBe grunnlagBm.referanse
                    }
                }
            }

        @Test
        fun `skal feile hvis offentlig inntekt mangler grunnlagsreferanseliste`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()

                behandling.inntekter.addAll(
                    opprettInntekter(behandling, testdataBM, testdataBarn1),
                )

                val result =
                    assertThrows<HttpClientErrorException> {
                        behandling.tilGrunnlagInntekt(personobjekter)
                    }
                result.message shouldContain "Mangler grunnlagsreferanse for offentlig inntekt AINNTEKT_BEREGNET_12MND"
            }

        private fun opprettInntekterBearbeidetGrunnlag(
            behandling: Behandling,
            gjelder: TestDataPerson,
        ): List<Grunnlag> =
            listOf(
                Grunnlag(
                    behandling = behandling,
                    type = Grunnlagsdatatype.KONTANTSTØTTE,
                    rolle = gjelder.tilRolle(behandling),
                    innhentet = LocalDateTime.now(),
                    aktiv = LocalDateTime.now(),
                    erBearbeidet = true,
                    data =
                        commonObjectmapper.writeValueAsString(
                            SummerteInntekter(
                                versjon = "VERSJON_INNTEKT",
                                inntekter =
                                    listOf(
                                        SummertÅrsinntekt(
                                            Inntektsrapportering.KONTANTSTØTTE,
                                            periode =
                                                ÅrMånedsperiode(
                                                    YearMonth.parse("2023-01"),
                                                    YearMonth.parse("2024-01"),
                                                ),
                                            sumInntekt = BigDecimal.ONE,
                                            gjelderBarnPersonId = testdataBarn1.ident,
                                            inntektPostListe = emptyList(),
                                            grunnlagsreferanseListe = ainntektGrunnlagsreferanseListe,
                                        ),
                                    ),
                            ),
                        ),
                ),
                Grunnlag(
                    behandling = behandling,
                    type = Grunnlagsdatatype.BARNETILLEGG,
                    rolle = gjelder.tilRolle(behandling),
                    innhentet = LocalDateTime.now(),
                    aktiv = LocalDateTime.now(),
                    erBearbeidet = true,
                    data =
                        commonObjectmapper.writeValueAsString(
                            SummerteInntekter(
                                versjon = "VERSJON_INNTEKT",
                                inntekter =
                                    listOf(
                                        SummertÅrsinntekt(
                                            Inntektsrapportering.BARNETILLEGG,
                                            periode =
                                                ÅrMånedsperiode(
                                                    YearMonth.parse("2023-01"),
                                                    YearMonth.parse("2024-01"),
                                                ),
                                            sumInntekt = BigDecimal.ONE,
                                            gjelderBarnPersonId = testdataBarn1.ident,
                                            inntektPostListe = emptyList(),
                                            grunnlagsreferanseListe = ainntektGrunnlagsreferanseListe,
                                        ),
                                    ),
                            ),
                        ),
                ),
                Grunnlag(
                    behandling = behandling,
                    type = Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    rolle = gjelder.tilRolle(behandling),
                    innhentet = LocalDateTime.now(),
                    aktiv = LocalDateTime.now(),
                    erBearbeidet = true,
                    data =
                        commonObjectmapper.writeValueAsString(
                            SummerteInntekter(
                                versjon = "VERSJON_INNTEKT",
                                inntekter =
                                    listOf(
                                        SummertÅrsinntekt(
                                            Inntektsrapportering.LIGNINGSINNTEKT,
                                            periode =
                                                ÅrMånedsperiode(
                                                    YearMonth.parse("2023-01"),
                                                    YearMonth.parse("2024-01"),
                                                ),
                                            sumInntekt = BigDecimal.ONE,
                                            inntektPostListe = emptyList(),
                                            grunnlagsreferanseListe = skattegrunnlagGrunnlagsreferanseListe,
                                        ),
                                        SummertÅrsinntekt(
                                            Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                                            periode =
                                                ÅrMånedsperiode(
                                                    YearMonth.parse("2023-01"),
                                                    YearMonth.parse("2023-07"),
                                                ),
                                            sumInntekt = BigDecimal.ONE,
                                            inntektPostListe = emptyList(),
                                            grunnlagsreferanseListe = ainntektGrunnlagsreferanseListe,
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        private fun opprettInntekter(
            behandling: Behandling,
            gjelder: TestDataPerson,
            barn: TestDataPerson? = null,
        ) = mutableSetOf(
            Inntekt(
                Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                BigDecimal.valueOf(45000),
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2023-12-31"),
                opprinneligFom = LocalDate.parse("2023-01-01"),
                opprinneligTom = LocalDate.parse("2023-07-01"),
                ident = gjelder.ident,
                kilde = Kilde.OFFENTLIG,
                taMed = false,
                behandling = behandling,
                inntektsposter =
                    mutableSetOf(
                        Inntektspost(
                            beløp = BigDecimal.valueOf(5000),
                            kode = "fisking",
                            inntektstype = null,
                        ),
                        Inntektspost(
                            beløp = BigDecimal.valueOf(40000),
                            kode = "krypto",
                            inntektstype = null,
                        ),
                    ),
            ),
            Inntekt(
                Inntektsrapportering.LIGNINGSINNTEKT,
                BigDecimal.valueOf(33000),
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2023-12-31"),
                opprinneligFom = LocalDate.parse("2023-01-01"),
                opprinneligTom = LocalDate.parse("2024-01-01"),
                ident = gjelder.ident,
                kilde = Kilde.OFFENTLIG,
                taMed = true,
                behandling = behandling,
                inntektsposter =
                    mutableSetOf(
                        Inntektspost(
                            beløp = BigDecimal.valueOf(5000),
                            kode = "",
                            inntektstype = Inntektstype.NÆRINGSINNTEKT,
                        ),
                        Inntektspost(
                            beløp = BigDecimal.valueOf(28000),
                            kode = "",
                            inntektstype = Inntektstype.LØNNSINNTEKT,
                        ),
                    ),
            ),
            Inntekt(
                Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                BigDecimal.valueOf(55000),
                LocalDate.parse("2022-01-01"),
                LocalDate.parse("2022-12-31"),
                gjelder.ident,
                Kilde.MANUELL,
                true,
                behandling = behandling,
            ),
            barn?.let {
                Inntekt(
                    Inntektsrapportering.BARNETILLEGG,
                    BigDecimal.valueOf(5000),
                    LocalDate.parse("2022-01-01"),
                    LocalDate.parse("2022-12-31"),
                    gjelder.ident,
                    Kilde.OFFENTLIG,
                    true,
                    opprinneligFom = LocalDate.parse("2023-01-01"),
                    opprinneligTom = LocalDate.parse("2024-01-01"),
                    gjelderBarn = barn.ident,
                    behandling = behandling,
                    inntektsposter =
                        mutableSetOf(
                            Inntektspost(
                                beløp = BigDecimal.valueOf(5000),
                                kode = "",
                                inntektstype = Inntektstype.BARNETILLEGG_PENSJON,
                            ),
                        ),
                )
            },
            barn?.let {
                Inntekt(
                    Inntektsrapportering.KONTANTSTØTTE,
                    BigDecimal.valueOf(5000),
                    LocalDate.parse("2023-01-01"),
                    LocalDate.parse("2023-12-31"),
                    gjelder.ident,
                    Kilde.OFFENTLIG,
                    true,
                    opprinneligFom = LocalDate.parse("2023-01-01"),
                    opprinneligTom = LocalDate.parse("2024-01-01"),
                    gjelderBarn = testdataBarn1.ident,
                    behandling = behandling,
                    inntektsposter =
                        mutableSetOf(
                            Inntektspost(
                                beløp = BigDecimal.valueOf(5000),
                                kode = "",
                                inntektstype = Inntektstype.KONTANTSTØTTE,
                            ),
                        ),
                )
            },
        ).filterNotNull().toMutableSet()

        fun List<GrunnlagDto>.hentInntekt(inntektsrapportering: Inntektsrapportering) =
            filter {
                it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
            }.filter { it.innholdTilObjekt<InntektsrapporteringPeriode>().inntektsrapportering == inntektsrapportering }
    }

    @Nested
    inner class BosstatusGrunnlagTest {
        fun oppretteHusstandsmedlem(
            behandling: Behandling,
            ident: String?,
            fødselsdato: LocalDate = LocalDate.parse("2024-04-03"),
            navn: String? = null,
        ): Husstandsmedlem {
            val husstandsmedlem =
                Husstandsmedlem(
                    behandling = behandling,
                    ident = ident,
                    navn = navn,
                    fødselsdato = fødselsdato,
                    perioder = mutableSetOf(),
                    kilde = Kilde.MANUELL,
                    id = 1L,
                )

            husstandsmedlem.perioder = opprettPerioder(husstandsmedlem)
            return husstandsmedlem
        }

        fun opprettPerioder(husstandsmedlem: Husstandsmedlem) =
            mutableSetOf(
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlem,
                    datoFom = LocalDate.parse("2023-01-01"),
                    datoTom = LocalDate.parse("2023-06-30"),
                    bostatus = Bostatuskode.MED_FORELDER,
                    kilde = Kilde.OFFENTLIG,
                    id = 1,
                ),
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlem,
                    datoFom = LocalDate.parse("2023-07-01"),
                    datoTom = LocalDate.parse("2023-08-31"),
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    kilde = Kilde.OFFENTLIG,
                    id = 2,
                ),
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlem,
                    datoFom = LocalDate.parse("2023-09-01"),
                    datoTom = LocalDate.parse("2023-12-31"),
                    bostatus = Bostatuskode.MED_FORELDER,
                    kilde = Kilde.MANUELL,
                    id = 3,
                ),
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlem,
                    datoFom = LocalDate.parse("2024-01-01"),
                    datoTom = null,
                    bostatus = Bostatuskode.REGNES_IKKE_SOM_BARN,
                    kilde = Kilde.MANUELL,
                    id = 4,
                ),
            )

        @Test
        fun `skal opprette grunnlag for husstandsmedlem`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteBehandling(1)

                behandling.roller =
                    mutableSetOf(
                        opprettRolle(behandling, testdataBM),
                        opprettRolle(behandling, testdataBarn1),
                        opprettRolle(behandling, testdataBarn2),
                    )
                val husstandsmedlem =
                    mutableSetOf(
                        oppretteHusstandsmedlem(
                            behandling,
                            testdataBarn1.ident,
                            testdataBarn1.fødselsdato,
                        ),
                        oppretteHusstandsmedlem(
                            behandling,
                            testdataBarn2.ident,
                            testdataBarn2.fødselsdato,
                        ),
                        oppretteHusstandsmedlem(behandling, "123213123123"),
                        oppretteHusstandsmedlem(
                            behandling,
                            "4124214124",
                            fødselsdato = LocalDate.parse("2023-03-03"),
                        ),
                    )

                behandling.husstandsmedlem = husstandsmedlem

                assertSoftly(
                    behandling
                        .tilGrunnlagBostatus(personobjekter)
                        .toList(),
                ) {
                    it shouldHaveSize 18
                    val husstandsmedlemmer =
                        filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)
                    husstandsmedlemmer shouldHaveSize 2
                    assertSoftly(husstandsmedlemmer[0]) { person ->
                        person.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                        person.innholdTilObjekt<Person>().ident shouldBe Personident("123213123123")
                    }
                    assertSoftly(husstandsmedlemmer[1]) { person ->
                        person.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                        person.innholdTilObjekt<Person>().ident shouldBe Personident("4124214124")
                    }
                }
            }

        @Test
        fun `skal opprette grunnlag for husstandsmedlem uten ident`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteBehandling(1)

                behandling.roller =
                    mutableSetOf(
                        opprettRolle(behandling, testdataBM),
                        opprettRolle(behandling, testdataBarn1),
                        opprettRolle(behandling, testdataBarn2),
                    )
                val husstandsmedlem =
                    mutableSetOf(
                        oppretteHusstandsmedlem(
                            behandling,
                            null,
                            fødselsdato = LocalDate.parse("2023-03-03"),
                            "navn navnesen",
                        ),
                    )

                behandling.husstandsmedlem = husstandsmedlem

                val husstandsmedlemGrunnlag =
                    behandling
                        .tilGrunnlagBostatus(personobjekter)
                        .toList()
                assertSoftly(husstandsmedlemGrunnlag) {
                    it shouldHaveSize 5
                    val husstandsmedlemmer =
                        filtrerBasertPåEgenReferanse(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)
                    husstandsmedlemmer shouldHaveSize 1
                    assertSoftly(husstandsmedlemmer[0]) { person ->
                        person.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                        person.innholdTilObjekt<Person>().ident shouldBe null
                        person.innholdTilObjekt<Person>().navn shouldBe "navn navnesen"
                    }

                    it.filter { it.type != Grunnlagstype.PERSON_HUSSTANDSMEDLEM }.forEach { grunnlag ->
                        grunnlag.gjelderReferanse shouldBe husstandsmedlemmer[0].referanse
                        grunnlag.innholdTilObjekt<BostatusPeriode>().relatertTilPart shouldBe grunnlagBm.referanse
                    }
                }
            }

        @Test
        fun `skal mappe husstandsmedlem til bosstatus`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()

                behandling.roller =
                    mutableSetOf(
                        opprettRolle(behandling, testdataBM),
                        opprettRolle(behandling, testdataBarn1),
                        opprettRolle(behandling, testdataBarn2),
                    )
                val husstandsmedlem =
                    mutableSetOf(
                        oppretteHusstandsmedlem(
                            behandling,
                            testdataBarn1.ident,
                            testdataBarn1.fødselsdato,
                        ),
                        oppretteHusstandsmedlem(
                            behandling,
                            testdataBarn2.ident,
                            testdataBarn2.fødselsdato,
                        ),
                        oppretteHusstandsmedlem(behandling, "123213123123"),
                        oppretteHusstandsmedlem(
                            behandling,
                            "4124214124",
                            fødselsdato = LocalDate.parse("2023-03-03"),
                        ),
                    )

                behandling.husstandsmedlem = husstandsmedlem

                assertSoftly(behandling.tilGrunnlagBostatus(personobjekter).toList()) {
                    it shouldHaveSize 18
                    it.filter { it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM } shouldHaveSize 2
                    assertSoftly(it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag1.referanse)) {
                        this shouldHaveSize 4
                        assertSoftly(this[0]) {
                            type shouldBe Grunnlagstype.BOSTATUS_PERIODE
                            gjelderReferanse shouldBe søknadsbarnGrunnlag1.referanse
                            grunnlagsreferanseListe shouldHaveSize 1
                            grunnlagsreferanseListe shouldContainAll
                                listOf(
                                    opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
                                        grunnlagBm.referanse,
                                        søknadsbarnGrunnlag1.referanse,
                                    ),
                                )
                            val innhold = innholdTilObjekt<BostatusPeriode>()
                            innhold.bostatus shouldBe Bostatuskode.MED_FORELDER
                            innhold.manueltRegistrert shouldBe false
                            innhold.relatertTilPart shouldBe grunnlagBm.referanse
                            innhold.periode.fom shouldBe YearMonth.parse("2023-01")
                            innhold.periode.til shouldBe YearMonth.parse("2023-07")
                        }
                        assertSoftly(this[3]) {
                            type shouldBe Grunnlagstype.BOSTATUS_PERIODE
                            gjelderReferanse shouldBe søknadsbarnGrunnlag1.referanse
                            grunnlagsreferanseListe shouldHaveSize 0
                            val innhold = innholdTilObjekt<BostatusPeriode>()
                            innhold.bostatus shouldBe Bostatuskode.REGNES_IKKE_SOM_BARN
                            innhold.manueltRegistrert shouldBe true
                            innhold.relatertTilPart shouldBe grunnlagBm.referanse
                            innhold.periode.fom shouldBe YearMonth.parse("2024-01")
                            innhold.periode.til shouldBe null
                        }
                    }

                    it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag2.referanse) shouldHaveSize 4
                    it.filter {
                        it.gjelderReferanse?.startsWith("person_${Grunnlagstype.PERSON_HUSSTANDSMEDLEM}") == true
                    } shouldHaveSize 8

                    it.filter { it.type != Grunnlagstype.PERSON_HUSSTANDSMEDLEM }.forEach {
                        val innhold = it.innholdTilObjekt<BostatusPeriode>()
                        innhold.relatertTilPart shouldBe grunnlagBm.referanse
                        if (innhold.manueltRegistrert) {
                            it.grunnlagsreferanseListe shouldHaveSize 0
                        } else {
                            it.grunnlagsreferanseListe shouldHaveSize 1
                            it.grunnlagsreferanseListe.shouldContainAll(
                                listOf(
                                    opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
                                        grunnlagBm.referanse,
                                        it.gjelderReferanse!!,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
    }

    @Nested
    inner class SivilstandGrunnlagTest {
        fun opprettSivilstand(behandling: Behandling): MutableSet<Sivilstand> =
            mutableSetOf(
                Sivilstand(
                    behandling = behandling,
                    datoFom = LocalDate.parse("2022-01-01"),
                    datoTom = LocalDate.parse("2022-06-30"),
                    sivilstand = Sivilstandskode.BOR_ALENE_MED_BARN,
                    kilde = Kilde.OFFENTLIG,
                ),
                Sivilstand(
                    behandling = behandling,
                    datoFom = LocalDate.parse("2022-07-01"),
                    datoTom = LocalDate.parse("2022-12-31"),
                    sivilstand = Sivilstandskode.GIFT_SAMBOER,
                    kilde = Kilde.OFFENTLIG,
                ),
                Sivilstand(
                    behandling = behandling,
                    datoFom = LocalDate.parse("2023-01-01"),
                    datoTom = LocalDate.parse("2023-12-31"),
                    sivilstand = Sivilstandskode.BOR_ALENE_MED_BARN,
                    kilde = Kilde.MANUELL,
                ),
                Sivilstand(
                    behandling = behandling,
                    datoFom = LocalDate.parse("2024-01-01"),
                    datoTom = null,
                    sivilstand = Sivilstandskode.GIFT_SAMBOER,
                    kilde = Kilde.MANUELL,
                ),
            )

        @Test
        fun `skal opprette grunnlag for sivilstand`(): Unit =
            behandlingTilGrunnlagMapping.run {
                val behandling = oppretteTestbehandling()

                behandling.roller =
                    mutableSetOf(
                        opprettRolle(behandling, testdataBM),
                        opprettRolle(behandling, testdataBarn1),
                        opprettRolle(behandling, testdataBarn2),
                    )

                behandling.sivilstand = opprettSivilstand(behandling)

                assertSoftly(
                    behandling.tilGrunnlagSivilstand(grunnlagBm).toList(),
                ) {
                    it shouldHaveSize 4
                    it.forEach {
                        val innhold = it.innholdTilObjekt<SivilstandPeriode>()
                        if (innhold.manueltRegistrert) {
                            it.grunnlagsreferanseListe shouldHaveSize 0
                        } else {
                            it.grunnlagsreferanseListe shouldHaveSize 1
                            it.grunnlagsreferanseListe.shouldContainAll(
                                listOf(
                                    opprettInnhentetSivilstandGrunnlagsreferanse(
                                        grunnlagBm.referanse,
                                    ),
                                ),
                            )
                        }
                    }
                    assertSoftly(this[0]) { sivilstand ->
                        sivilstand.type shouldBe Grunnlagstype.SIVILSTAND_PERIODE
                        sivilstand.referanse.shouldStartWith("sivilstand_person_${Grunnlagstype.PERSON_BIDRAGSMOTTAKER}")
                        sivilstand.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        sivilstand.grunnlagsreferanseListe.shouldHaveSize(1)
                        assertSoftly(sivilstand.innholdTilObjekt<SivilstandPeriode>()) {
                            it.sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                            it.manueltRegistrert shouldBe false
                            it.periode.fom shouldBe YearMonth.parse("2022-01")
                            it.periode.til shouldBe YearMonth.parse("2022-07")
                        }
                    }
                    assertSoftly(this[1]) { sivilstand ->
                        sivilstand.type shouldBe Grunnlagstype.SIVILSTAND_PERIODE
                        sivilstand.referanse.shouldStartWith("sivilstand_person_${Grunnlagstype.PERSON_BIDRAGSMOTTAKER}")
                        sivilstand.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        sivilstand.grunnlagsreferanseListe.shouldHaveSize(1)
                        assertSoftly(sivilstand.innholdTilObjekt<SivilstandPeriode>()) {
                            it.sivilstand shouldBe Sivilstandskode.GIFT_SAMBOER
                            it.manueltRegistrert shouldBe false
                            it.periode.fom shouldBe YearMonth.parse("2022-07")
                            it.periode.til shouldBe YearMonth.parse("2023-01")
                        }
                    }
                    assertSoftly(this[2]) { sivilstand ->
                        sivilstand.type shouldBe Grunnlagstype.SIVILSTAND_PERIODE
                        sivilstand.referanse.shouldStartWith("sivilstand_person_${Grunnlagstype.PERSON_BIDRAGSMOTTAKER}")
                        sivilstand.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        sivilstand.grunnlagsreferanseListe.shouldBeEmpty()
                        assertSoftly(sivilstand.innholdTilObjekt<SivilstandPeriode>()) {
                            it.sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                            it.manueltRegistrert shouldBe true
                            it.periode.fom shouldBe YearMonth.parse("2023-01")
                            it.periode.til shouldBe YearMonth.parse("2024-01")
                        }
                    }
                    assertSoftly(this[3]) { sivilstand ->
                        sivilstand.type shouldBe Grunnlagstype.SIVILSTAND_PERIODE
                        sivilstand.referanse.shouldStartWith("sivilstand_person_${Grunnlagstype.PERSON_BIDRAGSMOTTAKER}")
                        sivilstand.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        sivilstand.grunnlagsreferanseListe.shouldBeEmpty()
                        assertSoftly(sivilstand.innholdTilObjekt<SivilstandPeriode>()) {
                            it.sivilstand shouldBe Sivilstandskode.GIFT_SAMBOER
                            it.manueltRegistrert shouldBe true
                            it.periode.fom shouldBe YearMonth.parse("2024-01")
                            it.periode.til shouldBe null
                        }
                    }
                }
            }
    }

    @Nested
    inner class BehandlingGrunnlagTest {
        fun opprettBehandling(
            id: Long? = null,
            søknadRefId: Long? = null,
        ): Behandling =
            Behandling(
                Vedtakstype.FASTSETTELSE,
                null,
                søktFomDato = YearMonth.parse("2022-02").atEndOfMonth(),
                datoTom = YearMonth.now().plusYears(100).atEndOfMonth(),
                mottattdato = LocalDate.parse("2023-03-15"),
                klageMottattdato = null,
                SAKSNUMMER,
                SOKNAD_ID,
                søknadRefId,
                "4806",
                "Z9999",
                "Navn Navnesen",
                "bisys",
                SøktAvType.BIDRAGSMOTTAKER,
                Stønadstype.FORSKUDD,
                null,
                årsak = VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT,
                virkningstidspunkt = LocalDate.parse("2023-02-01"),
                id = id,
            )

        @Test
        fun `skal opprette grunnlag for søknad`(): Unit =
            mapper.run {
                val behandling = opprettBehandling()

                assertSoftly(behandling.byggGrunnlagSøknad().toList()) {
                    shouldHaveSize(1)
                    assertSoftly(it[0]) {
                        type shouldBe Grunnlagstype.SØKNAD
                        val søknad = innholdTilObjekt<SøknadGrunnlag>()
                        søknad.mottattDato shouldBe LocalDate.parse("2023-03-15")
                        søknad.søktFraDato shouldBe LocalDate.parse("2022-02-28")
                        søknad.søktAv shouldBe SøktAvType.BIDRAGSMOTTAKER
                    }
                }
            }

        @Test
        fun `skal opprette grunnlag for virkningstidspunkt`(): Unit =
            mapper.run {
                val behandling = opprettBehandling()

                assertSoftly(behandling.byggGrunnlagVirkningsttidspunkt().toList()) {
                    shouldHaveSize(1)
                    assertSoftly(it[0]) {
                        type shouldBe Grunnlagstype.VIRKNINGSTIDSPUNKT
                        val virkningstidspunkt = innholdTilObjekt<VirkningstidspunktGrunnlag>()
                        virkningstidspunkt.virkningstidspunkt shouldBe LocalDate.parse("2023-02-01")
                        virkningstidspunkt.årsak shouldBe VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
                    }
                }
            }

        @Test
        fun `skal opprette grunnlag for behandlingsreferanser`(): Unit =
            mapper.run {
                val behandling = opprettBehandling(10)

                val grunnlagsliste = behandling.tilBehandlingreferanseListe()

                assertSoftly(grunnlagsliste.toList()) {
                    shouldHaveSize(2)
                    it.firstOrNull { it.kilde == BehandlingsrefKilde.BEHANDLING_ID }?.referanse shouldBe "10"
                    it.firstOrNull { it.kilde == BehandlingsrefKilde.BISYS_SØKNAD }?.referanse shouldBe SOKNAD_ID.toString()
                    it.firstOrNull { it.kilde == BehandlingsrefKilde.BISYS_KLAGE_REF_SØKNAD }?.referanse shouldBe null
                }
            }

        @Test
        fun `skal opprette grunnlag for behandlingsreferanser med klageid`(): Unit =
            mapper.run {
                val behandling = opprettBehandling(10, 111)

                val grunnlagsliste = behandling.tilBehandlingreferanseListe()

                assertSoftly(grunnlagsliste.toList()) {
                    shouldHaveSize(3)
                    it.firstOrNull { it.kilde == BehandlingsrefKilde.BEHANDLING_ID }?.referanse shouldBe "10"
                    it.firstOrNull { it.kilde == BehandlingsrefKilde.BISYS_SØKNAD }?.referanse shouldBe SOKNAD_ID.toString()
                    it.firstOrNull { it.kilde == BehandlingsrefKilde.BISYS_KLAGE_REF_SØKNAD }?.referanse shouldBe "111"
                }
            }

        @Test
        fun `skal opprette grunnlag for notat og ikke ta med notat hvis tomt eller null`(): Unit =
            mapper.run {
                val behandling = oppretteTestbehandling(true, setteDatabaseider = true)
                behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
                behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
                behandling.boforholdsbegrunnelseKunINotat = "Boforhold"

                assertSoftly(behandling.byggGrunnlagNotater().toList()) {
                    shouldHaveSize(3)
                    assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
                        innhold shouldBe behandling.virkningstidspunktbegrunnelseKunINotat
                        erMedIVedtaksdokumentet shouldBe false
                        type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
                    }
                    assertSoftly(it[1].innholdTilObjekt<NotatGrunnlag>()) {
                        innhold shouldBe behandling.boforholdsbegrunnelseKunINotat
                        erMedIVedtaksdokumentet shouldBe false
                        type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.BOFORHOLD
                    }
                    assertSoftly(it[2].innholdTilObjekt<NotatGrunnlag>()) {
                        erMedIVedtaksdokumentet shouldBe false
                        type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.INNTEKT
                    }
                }
            }

        @Test
        fun `skal opprette grunnlag for notat`(): Unit =
            mapper.run {
                val behandling = oppretteTestbehandling(true, setteDatabaseider = true)
                behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
                behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
                behandling.boforholdsbegrunnelseKunINotat = "Boforhold"

                assertSoftly(behandling.byggGrunnlagNotater().toList()) {
                    shouldHaveSize(3)
                    assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
                        innhold shouldBe behandling.virkningstidspunktbegrunnelseKunINotat
                        erMedIVedtaksdokumentet shouldBe false
                        type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
                    }
                    assertSoftly(it[1].innholdTilObjekt<NotatGrunnlag>()) {
                        erMedIVedtaksdokumentet shouldBe false
                        type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.BOFORHOLD
                    }
                    assertSoftly(it[2].innholdTilObjekt<NotatGrunnlag>()) {
                        erMedIVedtaksdokumentet shouldBe false
                        type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.INNTEKT
                    }
                }
            }
    }
}
