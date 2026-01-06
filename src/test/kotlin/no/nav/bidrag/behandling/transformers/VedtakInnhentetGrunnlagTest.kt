package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetArbeidsforhold
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetGrunnlagInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetGrunnlagUnderholdskostnad
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetHusstandsmedlemmer
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetSivilstand
import no.nav.bidrag.behandling.utils.opprettAinntektGrunnlagListe
import no.nav.bidrag.behandling.utils.opprettArbeidsforholdGrunnlagListe
import no.nav.bidrag.behandling.utils.opprettBarnetilleggListe
import no.nav.bidrag.behandling.utils.opprettBarnetilsynListe
import no.nav.bidrag.behandling.utils.opprettKontantstøtteListe
import no.nav.bidrag.behandling.utils.opprettSkattegrunnlagGrunnlagListe
import no.nav.bidrag.behandling.utils.opprettSmåbarnstillegListe
import no.nav.bidrag.behandling.utils.opprettUtvidetBarnetrygdGrunnlagListe
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGrunnlagEntityForInntekt
import no.nav.bidrag.behandling.utils.testdata.opprettRolle
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataGrunnlagInnhentetTidspunkt
import no.nav.bidrag.behandling.utils.testdata.tilGrunnlagEntity
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagDatakilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetAinntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetArbeidsforhold
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetBarnetillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetBarnetilsyn
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetHusstandsmedlem
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetKontantstøtte
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSivilstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSkattegrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSmåbarnstillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetUtvidetBarnetrygd
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class VedtakInnhentetGrunnlagTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initPersonMock() {
            stubPersonConsumer()
        }
    }

    val grunnlagBm =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBM.ident,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            fødselsdato = testdataBM.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val grunnlagBp =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBP.ident,
            rolletype = Rolletype.BIDRAGSPLIKTIG,
            fødselsdato = testdataBP.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag1 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn1.ident,
            rolletype = Rolletype.BARN,
            fødselsdato = testdataBarn1.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag2 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn2.ident,
            rolletype = Rolletype.BARN,
            fødselsdato = testdataBarn2.fødselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val personobjekter = setOf(grunnlagBm, grunnlagBp, søknadsbarnGrunnlag1, søknadsbarnGrunnlag2)

    @BeforeEach
    fun initMocks() {
        stubKodeverkProvider()
    }

    @Nested
    inner class InnhentetSivilstandTest {
        @Test
        fun `skal mappe innhentet grunnlag for sivilstand`() {
            val behandling = oppretteBehandling()

            val grunnlagListe =
                opprettAlleAktiveGrunnlagFraFil(
                    behandling,
                    "grunnlagresponse.json",
                )

            assertSoftly(grunnlagListe.toList().tilInnhentetSivilstand(personobjekter).toList()) {
                this shouldHaveSize 1
                assertSoftly(this[0]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_SIVILSTAND
                    gjelderReferanse shouldBe grunnlagBm.referanse
                    val grunnlag = it.innholdTilObjekt<InnhentetSivilstand>()
                    grunnlag.hentetTidspunkt shouldBeEqual testdataGrunnlagInnhentetTidspunkt
                    assertSoftly(grunnlag.grunnlag[0]) {
                        gyldigFom shouldBe LocalDate.parse("1978-08-25")
                        sivilstand shouldBe SivilstandskodePDL.UGIFT
                        master shouldBe "FREG"
                        historisk shouldBe true
                        registrert shouldBe LocalDateTime.parse("2024-01-05T07:45:19")
                    }
                    assertSoftly(grunnlag.grunnlag[1]) {
                        gyldigFom shouldBe LocalDate.parse("2022-11-01")
                        sivilstand shouldBe SivilstandskodePDL.SKILT
                        master shouldBe "FREG"
                        historisk shouldBe false
                        registrert shouldBe LocalDateTime.parse("2024-01-05T07:45:19")
                    }
                    assertSoftly(grunnlag.grunnlag[2]) {
                        gyldigFom shouldBe LocalDate.parse("2022-12-01")
                        sivilstand shouldBe SivilstandskodePDL.SKILT
                        master shouldBe "PDL"
                        historisk shouldBe false
                        registrert shouldBe LocalDateTime.parse("2024-01-05T07:45:19")
                    }
                }
            }
        }

        @Test
        fun `skal mappe innhentet grunnlag for sivilstand med null gyldigFom`() {
            val behandling = oppretteBehandling()

            val grunnlagListe =
                listOf(
                    SivilstandGrunnlagDto(
                        type = SivilstandskodePDL.SKILT,
                        bekreftelsesdato = null,
                        gyldigFom = null,
                        master = "PDL",
                        historisk = false,
                        registrert = LocalDate.parse("2023-01-01").atStartOfDay(),
                        personId = testdataBM.ident,
                    ),
                    SivilstandGrunnlagDto(
                        type = SivilstandskodePDL.SKILT,
                        bekreftelsesdato = null,
                        gyldigFom = null,
                        master = "PDL",
                        historisk = false,
                        registrert = LocalDate.parse("2023-01-01").atStartOfDay(),
                        personId = testdataBP.ident,
                    ),
                ).tilGrunnlagEntity(behandling)

            assertSoftly(grunnlagListe.tilInnhentetSivilstand(personobjekter).toList()) {
                this shouldHaveSize 2

                assertSoftly(this[0]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_SIVILSTAND
                    val grunnlag = it.innholdTilObjekt<InnhentetSivilstand>()
                    gjelderReferanse shouldBe grunnlagBm.referanse
                    assertSoftly(grunnlag.grunnlag[0]) {
                        gyldigFom shouldBe null
                        sivilstand shouldBe SivilstandskodePDL.SKILT
                        master shouldBe "PDL"
                        historisk shouldBe false
                        registrert shouldBe LocalDate.parse("2023-01-01").atStartOfDay()
                    }
                }
                assertSoftly(this[1]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_SIVILSTAND
                    val grunnlag = it.innholdTilObjekt<InnhentetSivilstand>()
                    gjelderReferanse shouldBe grunnlagBp.referanse
                    assertSoftly(grunnlag.grunnlag[0]) {
                        gyldigFom shouldBe null
                        sivilstand shouldBe SivilstandskodePDL.SKILT
                        master shouldBe "PDL"
                        historisk shouldBe false
                        registrert shouldBe LocalDate.parse("2023-01-01").atStartOfDay()
                    }
                }
            }
        }
    }

    @Nested
    inner class InnhentetHusstandsmedlemmerTest {
        @Test
        fun `skal mappe innhentet grunnlag for husstandsmedlemmer`() {
            val behandling = oppretteBehandling()

            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                opprettAlleAktiveGrunnlagFraFil(
                    behandling,
                    "grunnlagresponse.json",
                )
            assertSoftly(
                grunnlagListe.toList().tilInnhentetHusstandsmedlemmer(personobjekter, behandling).toList(),
            ) {
                it.hentAllePersoner().shouldHaveSize(3)
                val personGrunnlagHusstandsmedlemListe = it.hentGrunnlagPersonHusstandsmedlem()
                val husstandGrunnlag = it.hentGrunnlagHusstand()
                husstandGrunnlag shouldHaveSize 5
                assertSoftly(husstandGrunnlag[0]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag1.referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                    grunnlag.grunnlag.perioder shouldHaveSize 3
                    grunnlag.grunnlag.perioder[0].fom shouldBe LocalDate.parse("2016-11-30")
                    grunnlag.grunnlag.perioder[0].til shouldBe LocalDate.parse("2016-12-31")

                    grunnlag.grunnlag.perioder[1].fom shouldBe LocalDate.parse("2017-02-01")
                    grunnlag.grunnlag.perioder[1].til shouldBe LocalDate.parse("2017-03-15")

                    grunnlag.grunnlag.perioder[2].fom shouldBe LocalDate.parse("2017-03-30")
                    grunnlag.grunnlag.perioder[2].til shouldBe null
                }
                assertSoftly(husstandGrunnlag[1]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe personGrunnlagHusstandsmedlemListe[0].referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe false
                    grunnlag.grunnlag.perioder shouldHaveSize 2
                }
                assertSoftly(husstandGrunnlag[2]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag2.referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                    grunnlag.grunnlag.perioder shouldHaveSize 2

                    grunnlag.grunnlag.perioder[0].fom shouldBe LocalDate.parse("2005-05-21")
                    grunnlag.grunnlag.perioder[0].til shouldBe LocalDate.parse("2012-02-01")

                    grunnlag.grunnlag.perioder[1].fom shouldBe LocalDate.parse("2017-03-30")
                    grunnlag.grunnlag.perioder[1].til shouldBe null
                }
                assertSoftly(husstandGrunnlag[3]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe personGrunnlagHusstandsmedlemListe[1].referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                    grunnlag.grunnlag.perioder shouldHaveSize 2
                }
                assertSoftly(husstandGrunnlag[4]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe "person_PERSON_HUSSTANDSMEDLEM_20010509_innhentet_-653873409"
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe false
                    grunnlag.grunnlag.perioder shouldHaveSize 1
                }
            }
        }

        @Test
        fun `skal mappe innhentet grunnlag for husstandsmedlemmer hvis innhentet grunnlag for søknadsbarn mangler`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                listOf(
                    RelatertPersonGrunnlagDto(
                        partPersonId = testdataBM.ident,
                        relatertPersonPersonId = testdataBarn2.ident,
                        navn = testdataBarn2.navn,
                        fødselsdato = testdataBarn2.fødselsdato,
                        erBarnAvBmBp = true,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    LocalDate.parse("2023-07-01"),
                                    null,
                                ),
                            ),
                    ),
                    RelatertPersonGrunnlagDto(
                        partPersonId = testdataBM.ident,
                        relatertPersonPersonId = "12312312",
                        navn = "Voksen i husstand",
                        fødselsdato = LocalDate.parse("1999-01-01"),
                        erBarnAvBmBp = false,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    LocalDate.parse("2020-07-01"),
                                    null,
                                ),
                            ),
                    ),
                ).tilGrunnlagEntity(behandling)
            val grunnlagHusstandsmedlemmer =
                grunnlagListe.tilInnhentetHusstandsmedlemmer(personobjekter, behandling).toList()
            assertSoftly(grunnlagHusstandsmedlemmer) {
                it.hentAllePersoner().shouldHaveSize(1)
                val husstandGrunnlag = it.hentGrunnlagHusstand()
                husstandGrunnlag shouldHaveSize 3
                husstandGrunnlag
                    .hentHusstandsmedlemMedReferanse(søknadsbarnGrunnlag2.referanse)
                    .shouldHaveSize(1)
                husstandGrunnlag
                    .hentHusstandsmedlemMedReferanse(søknadsbarnGrunnlag1.referanse)
                    .shouldHaveSize(1)

                assertSoftly(
                    husstandGrunnlag
                        .hentHusstandsmedlemMedReferanse(søknadsbarnGrunnlag1.referanse)
                        .hentGrunnlagHusstand()
                        .first(),
                ) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag1.referanse
                    grunnlag.grunnlag.navn shouldBe testdataBarn1.navn
                    grunnlag.grunnlag.fødselsdato shouldBe testdataBarn1.fødselsdato
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                    grunnlag.grunnlag.perioder shouldHaveSize 0
                }
            }
        }

        @Test
        fun `skal mappe innhentet grunnlag for husstandsmedlemmer med null periode`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                listOf(
                    RelatertPersonGrunnlagDto(
                        partPersonId = testdataBM.ident,
                        relatertPersonPersonId = testdataBarn1.ident,
                        navn = testdataBarn1.navn,
                        fødselsdato = testdataBarn1.fødselsdato,
                        erBarnAvBmBp = true,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    LocalDate.parse("2022-01-01"),
                                    LocalDate.parse("2022-06-08"),
                                ),
                                BorISammeHusstandDto(
                                    LocalDate.parse("2023-01-02"),
                                    LocalDate.parse("2023-06-28"),
                                ),
                                BorISammeHusstandDto(
                                    LocalDate.parse("2023-07-01"),
                                    null,
                                ),
                                BorISammeHusstandDto(
                                    null,
                                    null,
                                ),
                            ),
                    ),
                    RelatertPersonGrunnlagDto(
                        partPersonId = testdataBM.ident,
                        relatertPersonPersonId = testdataBarn2.ident,
                        navn = testdataBarn2.navn,
                        fødselsdato = testdataBarn2.fødselsdato,
                        erBarnAvBmBp = true,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    LocalDate.parse("2023-07-01"),
                                    null,
                                ),
                            ),
                    ),
                    RelatertPersonGrunnlagDto(
                        partPersonId = testdataBM.ident,
                        relatertPersonPersonId = "12312312",
                        navn = "Voksen i husstand",
                        fødselsdato = LocalDate.parse("1999-01-01"),
                        erBarnAvBmBp = false,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    LocalDate.parse("2020-07-01"),
                                    null,
                                ),
                            ),
                    ),
                    RelatertPersonGrunnlagDto(
                        partPersonId = testdataBP.ident,
                        relatertPersonPersonId = testdataBarn1.ident,
                        navn = testdataBarn1.navn,
                        fødselsdato = testdataBarn1.fødselsdato,
                        erBarnAvBmBp = true,
                        borISammeHusstandDtoListe =
                            listOf(
                                BorISammeHusstandDto(
                                    LocalDate.parse("2022-01-01"),
                                    LocalDate.parse("2022-06-08"),
                                ),
                                BorISammeHusstandDto(
                                    LocalDate.parse("2023-01-01"),
                                    LocalDate.parse("2023-06-30"),
                                ),
                                BorISammeHusstandDto(
                                    LocalDate.parse("2023-07-01"),
                                    null,
                                ),
                            ),
                    ),
                ).tilGrunnlagEntity(behandling)
            val grunnlagHusstandsmedlemmer =
                grunnlagListe.tilInnhentetHusstandsmedlemmer(personobjekter, behandling).toList()
            assertSoftly(grunnlagHusstandsmedlemmer) {
                it.hentAllePersoner().shouldHaveSize(1)
                val personGrunnlagHusstandsmedlemListe = it.hentGrunnlagPersonHusstandsmedlem()
                val husstandGrunnlag = it.hentGrunnlagHusstand()
                husstandGrunnlag shouldHaveSize 4
                husstandGrunnlag
                    .hentHusstandsmedlemMedReferanse(søknadsbarnGrunnlag2.referanse)
                    .shouldHaveSize(1)
                husstandGrunnlag
                    .hentHusstandsmedlemMedReferanse(personGrunnlagHusstandsmedlemListe[0].referanse)
                    .shouldHaveSize(1)

                assertSoftly(husstandGrunnlag[0]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag1.referanse
                    grunnlag.grunnlag.navn shouldBe testdataBarn1.navn
                    grunnlag.grunnlag.fødselsdato shouldBe testdataBarn1.fødselsdato
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                    grunnlag.grunnlag.perioder shouldHaveSize 4
                    grunnlag.grunnlag.perioder[0].fom shouldBe LocalDate.parse("2022-01-01")
                    grunnlag.grunnlag.perioder[0].til shouldBe LocalDate.parse("2022-06-08")

                    grunnlag.grunnlag.perioder[1].fom shouldBe LocalDate.parse("2023-01-02")
                    grunnlag.grunnlag.perioder[1].til shouldBe LocalDate.parse("2023-06-28")

                    grunnlag.grunnlag.perioder[2].fom shouldBe LocalDate.parse("2023-07-01")
                    grunnlag.grunnlag.perioder[2].til shouldBe null

                    grunnlag.grunnlag.perioder[3].fom shouldBe LocalDate.MIN
                    grunnlag.grunnlag.perioder[3].til shouldBe null
                }
                assertSoftly(husstandGrunnlag[1]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag2.referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                    grunnlag.grunnlag.perioder shouldHaveSize 1
                    grunnlag.grunnlag.perioder[0].fom shouldBe LocalDate.parse("2023-07-01")
                    grunnlag.grunnlag.perioder[0].til shouldBe null
                }
                assertSoftly(husstandGrunnlag[2]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe personGrunnlagHusstandsmedlemListe[0].referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe false
                    grunnlag.grunnlag.perioder shouldHaveSize 1
                    grunnlag.grunnlag.perioder[0].fom shouldBe LocalDate.parse("2020-07-01")
                    grunnlag.grunnlag.perioder[0].til shouldBe null
                }

                assertSoftly(husstandGrunnlag[3]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
                    it.gjelderReferanse.shouldBe(grunnlagBp.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag1.referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                    grunnlag.grunnlag.perioder shouldHaveSize 3
                    grunnlag.grunnlag.perioder[0].fom shouldBe LocalDate.parse("2022-01-01")
                    grunnlag.grunnlag.perioder[0].til shouldBe LocalDate.parse("2022-06-08")

                    grunnlag.grunnlag.perioder[1].fom shouldBe LocalDate.parse("2023-01-01")
                    grunnlag.grunnlag.perioder[1].til shouldBe LocalDate.parse("2023-06-30")

                    grunnlag.grunnlag.perioder[2].fom shouldBe LocalDate.parse("2023-07-01")
                    grunnlag.grunnlag.perioder[2].til shouldBe null
                }
            }
        }

        fun List<GrunnlagDto>.hentHusstandsmedlemMedReferanse(referanse: String) = this.filter { it.innholdTilObjekt<InnhentetHusstandsmedlem>().grunnlag.gjelderPerson == referanse }

        fun List<GrunnlagDto>.hentGrunnlagHusstand() = filter { it.type == Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM }

        fun List<GrunnlagDto>.hentGrunnlagPersonHusstandsmedlem() = hentAllePersoner().filter { it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM }
    }

    @Nested
    inner class InnhentetInntekterTest {
        @Test
        fun `skal mappe innhentet grunnlag for inntekter fra json fil`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                opprettAlleAktiveGrunnlagFraFil(
                    behandling,
                    "grunnlagresponse.json",
                )
            assertSoftly(
                grunnlagListe
                    .toList()
                    .tilInnhentetGrunnlagInntekt(personobjekter)
                    .toList(),
            ) {
                this shouldHaveSize 10
                hentGrunnlagAinntekt() shouldHaveSize 2
                hentSkattegrunnlag() shouldHaveSize 4
            }
        }

        @Test
        fun `skal mappe innhentet grunnlag for ainntekter`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                behandling.opprettGrunnlagEntityForInntekt(
                    opprettAinntektGrunnlagListe(),
                    opprettSkattegrunnlagGrunnlagListe(),
                )
            assertSoftly(
                grunnlagListe
                    .tilInnhentetGrunnlagInntekt(personobjekter)
                    .toList(),
            ) {
                it shouldHaveSize 5
                assertSoftly(hentGrunnlagAinntekt()) {
                    it shouldHaveSize 2
                    it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag1.referanse) shouldHaveSize 1
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBm.referanse) shouldHaveSize 1
                    assertSoftly(it[0]) {
                        it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        val ainntekt = it.innholdTilObjekt<InnhentetAinntekt>()
                        ainntekt.grunnlag shouldHaveSize 2
                        ainntekt.datakilde shouldBe GrunnlagDatakilde.INNTEKSKOMPONENTEN
                        assertSoftly(ainntekt.grunnlag[0]) {
                            periode.fom shouldBe LocalDate.parse("2022-01-01")
                            periode.til shouldBe LocalDate.parse("2023-01-01")
                            assertSoftly(ainntektspostListe[0]) {
                                beløp shouldBe BigDecimal(60000)
                                kategori shouldBe "LOENNSINNTEKT"
                                fordelType shouldBe "kontantytelse"
                                utbetalingsperiode shouldBe "2023-01"
                                opptjeningsperiodeFra shouldBe LocalDate.parse("2022-01-31")
                                opptjeningsperiodeTil shouldBe LocalDate.parse("2023-01-01")
                            }
                        }
                    }
                }
            }
        }

        @Test
        fun `skal mappe innhentet grunnlag for skattegrunnlag`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                behandling.opprettGrunnlagEntityForInntekt(
                    opprettAinntektGrunnlagListe(),
                    opprettSkattegrunnlagGrunnlagListe(),
                )
            assertSoftly(
                grunnlagListe
                    .tilInnhentetGrunnlagInntekt(personobjekter)
                    .toList(),
            ) {
                this shouldHaveSize 5
                assertSoftly(hentSkattegrunnlag()) {
                    it shouldHaveSize 3
                    it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag1.referanse) shouldHaveSize 1
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBm.referanse) shouldHaveSize 2
                    assertSoftly(it[2]) {
                        it.gjelderReferanse.shouldBe(søknadsbarnGrunnlag1.referanse)
                        val skattegrunnlag = it.innholdTilObjekt<InnhentetSkattegrunnlag>()
                        skattegrunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        skattegrunnlag.periode.til shouldBe LocalDate.parse("2023-01-01")
                        skattegrunnlag.grunnlag shouldHaveSize 1
                        skattegrunnlag.datakilde shouldBe GrunnlagDatakilde.SIGRUN
                        assertSoftly(skattegrunnlag.grunnlag[0]) {
                            beløp shouldBe BigDecimal.valueOf(5000.0)
                            skattegrunnlagType shouldBe "ORDINÆR"
                            kode shouldBe "annenArbeidsinntekt"
                        }
                    }
                    assertSoftly(it[0]) {
                        it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        val skattegrunnlag = it.innholdTilObjekt<InnhentetSkattegrunnlag>()
                        skattegrunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        skattegrunnlag.periode.til shouldBe LocalDate.parse("2023-01-01")
                        skattegrunnlag.grunnlag shouldHaveSize 2
                        assertSoftly(skattegrunnlag.grunnlag[0]) {
                            beløp shouldBe BigDecimal.valueOf(5000.0)
                            skattegrunnlagType shouldBe "ORDINÆR"
                            kode shouldBe "annenArbeidsinntekt"
                        }
                    }
                }
            }
        }

        @Test
        fun `skal mappe innhentet grunnlag for alle inntekter`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                listOf(
                    opprettSmåbarnstillegListe().tilGrunnlagEntity(behandling),
                    opprettBarnetilsynListe().tilGrunnlagEntity(behandling),
                    opprettKontantstøtteListe().tilGrunnlagEntity(behandling),
                    opprettUtvidetBarnetrygdGrunnlagListe().tilGrunnlagEntity(behandling),
                    opprettBarnetilleggListe().tilGrunnlagEntity(behandling),
                    behandling.opprettGrunnlagEntityForInntekt(
                        opprettAinntektGrunnlagListe(),
                        opprettSkattegrunnlagGrunnlagListe(),
                    ),
                ).flatten()
            assertSoftly(
                grunnlagListe
                    .tilInnhentetGrunnlagInntekt(personobjekter)
                    .toList(),
            ) {
                this shouldHaveSize 12
                assertSoftly(hentBarnetillegg()) {
                    it shouldHaveSize 2
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBp.referanse) shouldHaveSize 1
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBm.referanse) shouldHaveSize 1
                    assertSoftly(it[0]) {
                        val barnetillegg = innholdTilObjekt<InnhentetBarnetillegg>()
                        barnetillegg.grunnlag shouldHaveSize 1
                        barnetillegg.hentBarnetilleggForBarn(søknadsbarnGrunnlag1.referanse) shouldHaveSize 1
                    }
                    assertSoftly(it[1]) {
                        val barnetillegg = innholdTilObjekt<InnhentetBarnetillegg>()
                        barnetillegg.grunnlag shouldHaveSize 5
                        barnetillegg.datakilde shouldBe GrunnlagDatakilde.PENSJON
                        barnetillegg.hentBarnetilleggForBarn(søknadsbarnGrunnlag1.referanse) shouldHaveSize 3
                        barnetillegg.hentBarnetilleggForBarn(søknadsbarnGrunnlag2.referanse) shouldHaveSize 2
                        it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        val grunnlag = it.innholdTilObjekt<InnhentetBarnetillegg>()
                        assertSoftly(grunnlag.grunnlag[0]) {
                            periode.fom shouldBe LocalDate.parse("2022-01-01")
                            periode.til shouldBe LocalDate.parse("2022-04-30")
                            gjelderBarn shouldBe søknadsbarnGrunnlag1.referanse
                            barnetilleggType shouldBe "PENSJON"
                            beløpBrutto shouldBe BigDecimal(1000)
                            barnType shouldBe "FELLES"
                        }
                    }
                }
                assertSoftly(hentUtvidetBarnetrygd()) {
                    it shouldHaveSize 1
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBm.referanse) shouldHaveSize 1
                    assertSoftly(it[0]) {
                        it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        val utvidetBarnetrygd = it.innholdTilObjekt<InnhentetUtvidetBarnetrygd>()
                        utvidetBarnetrygd.grunnlag shouldHaveSize 3
                        utvidetBarnetrygd.datakilde shouldBe GrunnlagDatakilde.FAMILIE_BA_SAK
                        assertSoftly(utvidetBarnetrygd.grunnlag[0]) {
                            periode.fom shouldBe LocalDate.parse("2022-01-01")
                            periode.til shouldBe LocalDate.parse("2022-03-30")
                            beløp shouldBe BigDecimal(5000)
                            manueltBeregnet shouldBe false
                        }
                    }
                }
                assertSoftly(hentSmåbarnstillegg()) {
                    it shouldHaveSize 2
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBp.referanse) shouldHaveSize 1
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBm.referanse) shouldHaveSize 1
                    assertSoftly(it[0]) {
                        it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        val småbarnstillegg = it.innholdTilObjekt<InnhentetSmåbarnstillegg>()
                        småbarnstillegg.grunnlag shouldHaveSize 3
                        småbarnstillegg.datakilde shouldBe GrunnlagDatakilde.FAMILIE_BA_SAK
                        assertSoftly(småbarnstillegg.grunnlag[0]) {
                            periode.fom shouldBe LocalDate.parse("2022-01-01")
                            periode.til shouldBe LocalDate.parse("2022-03-30")
                            beløp shouldBe BigDecimal(5000)
                            manueltBeregnet shouldBe false
                        }
                    }
                }
                assertSoftly(hentKontantstøtte()) {
                    it shouldHaveSize 2
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBp.referanse) shouldHaveSize 1
                    it.filtrerBasertPåFremmedReferanse(referanse = grunnlagBm.referanse) shouldHaveSize 1
                    assertSoftly(it[1]) {
                        it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        val innhentetKontantstøtte = it.innholdTilObjekt<InnhentetKontantstøtte>()
                        innhentetKontantstøtte.datakilde shouldBe GrunnlagDatakilde.FAMILIE_KONTANTSTØTTE_SAK
                        innhentetKontantstøtte.grunnlag shouldHaveSize 4
                        innhentetKontantstøtte.hentKontantstøtteForBarn(søknadsbarnGrunnlag1.referanse) shouldHaveSize 3
                        innhentetKontantstøtte.hentKontantstøtteForBarn(søknadsbarnGrunnlag2.referanse) shouldHaveSize 1
                        assertSoftly(innhentetKontantstøtte.grunnlag[0]) {
                            periode.fom shouldBe LocalDate.parse("2022-01-01")
                            periode.til shouldBe LocalDate.parse("2022-07-31")
                            beløp shouldBe 1000
                            gjelderBarn shouldBe søknadsbarnGrunnlag1.referanse
                        }
                    }
                }
            }
        }

        fun List<GrunnlagDto>.hentGrunnlagAinntekt() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT }

        fun List<GrunnlagDto>.hentSkattegrunnlag() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE }

        fun List<GrunnlagDto>.hentUtvidetBarnetrygd() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD }

        fun List<GrunnlagDto>.hentSmåbarnstillegg() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG }

        fun List<GrunnlagDto>.hentBarnetillegg() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG }

        fun List<GrunnlagDto>.hentKontantstøtte() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE }
    }

    @Nested
    inner class InnhentetArbeidsforholdTest {
        @Test
        fun `skal mappe innhentet grunnlag for ainntekter`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe = opprettArbeidsforholdGrunnlagListe().tilGrunnlagEntity(behandling)
            assertSoftly(
                grunnlagListe
                    .tilInnhentetArbeidsforhold(personobjekter)
                    .toList(),
            ) {
                it shouldHaveSize 2
                it[1].gjelderReferanse shouldBe søknadsbarnGrunnlag1.referanse
                assertSoftly(it[0]) {
                    it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                    val arbeidsforhold = it.innholdTilObjekt<InnhentetArbeidsforhold>()
                    assertSoftly(arbeidsforhold) {
                        it.hentetTidspunkt shouldBe testdataGrunnlagInnhentetTidspunkt
                        it.grunnlag.shouldHaveSize(2)
                        it.datakilde shouldBe GrunnlagDatakilde.AAREG
                        assertSoftly(this.grunnlag[0]) {
                            arbeidsgiverNavn shouldBe "Snekker Hansen"
                            arbeidsgiverOrgnummer shouldBe "88123123"
                            ansettelsesdetaljerListe.shouldHaveSize(2)
                            permisjonListe.shouldHaveSize(1)
                            permitteringListe.shouldHaveSize(1)
                            assertSoftly(ansettelsesdetaljerListe[1]) {
                                periodeFra shouldBe YearMonth.parse("2008-01")
                                periodeTil shouldBe YearMonth.parse("2022-01")
                                arbeidstidsordningBeskrivelse shouldBe "Dagtid"
                                arbeidsforholdType shouldBe "Ordinaer"
                                yrkeBeskrivelse shouldBe "KONTORLEDER"
                                antallTimerPrUke shouldBe 37.5
                                avtaltStillingsprosent shouldBe 100.0
                                sisteStillingsprosentendringDato shouldBe LocalDate.parse("2009-01-01")
                                sisteLønnsendringDato shouldBe LocalDate.parse("2009-01-01")
                            }
                            assertSoftly(permitteringListe[0]) {
                                startdato shouldBe LocalDate.parse("2009-01-01")
                                sluttdato shouldBe LocalDate.parse("2010-01-01")
                                beskrivelse shouldBe "Finanskrise"
                                prosent shouldBe 50.0
                            }
                            assertSoftly(permisjonListe[0]) {
                                startdato shouldBe LocalDate.parse("2015-01-01")
                                sluttdato shouldBe LocalDate.parse("2015-06-01")
                                beskrivelse shouldBe "Foreldrepermisjon"
                                prosent shouldBe 50.0
                            }
                        }
                    }
                }
            }
        }
    }

    @Nested
    inner class InnhentetUnderholdskostnadTest {
        fun List<GrunnlagDto>.hentBarnetilsyn() = this.filter { it.type == Grunnlagstype.INNHENTET_BARNETILSYN }

        @Test
        fun `skal mappe innhentet barnetilsyn`() {
            val behandling = oppretteBehandling()
            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val grunnlagListe =
                listOf(
                    opprettBarnetilsynListe().tilGrunnlagEntity(behandling),
                ).flatten()
            assertSoftly(
                grunnlagListe
                    .tilInnhentetGrunnlagUnderholdskostnad(personobjekter)
                    .toList(),
            ) {
                this shouldHaveSize 1
                assertSoftly(hentBarnetilsyn()) {
                    it shouldHaveSize 1
                    assertSoftly(it[0]) {
                        it.gjelderReferanse.shouldBe(grunnlagBm.referanse)
                        val barnetilsyn = it.innholdTilObjekt<InnhentetBarnetilsyn>()
                        barnetilsyn.grunnlag shouldHaveSize 2
                        barnetilsyn.datakilde shouldBe GrunnlagDatakilde.FAMILIE_EF_SAK
                        barnetilsyn.hentBarnetilsynForBarn(søknadsbarnGrunnlag1.referanse) shouldHaveSize 2
                        barnetilsyn.hentBarnetilsynForBarn(søknadsbarnGrunnlag2.referanse) shouldHaveSize 0
                        assertSoftly(barnetilsyn.grunnlag[0]) {
                            periode.fom shouldBe LocalDate.parse("2022-01-01")
                            periode.til shouldBe LocalDate.parse("2022-07-31")
                            tilsynstype shouldBe Tilsynstype.HELTID
                            skolealder shouldBe Skolealder.OVER
                        }
                    }
                }
            }
        }
    }
}
