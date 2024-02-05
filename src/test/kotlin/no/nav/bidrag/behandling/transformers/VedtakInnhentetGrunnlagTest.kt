package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.opplysninger.InntektGrunnlag
import no.nav.bidrag.behandling.transformers.vedtak.tilInnhentetArbeidsforhold
import no.nav.bidrag.behandling.transformers.vedtak.tilInnhentetGrunnlagInntekt
import no.nav.bidrag.behandling.transformers.vedtak.tilInnhentetHusstandsmedlemmer
import no.nav.bidrag.behandling.transformers.vedtak.tilInnhentetSivilstand
import no.nav.bidrag.behandling.utils.opprettAinntektGrunnlagListe
import no.nav.bidrag.behandling.utils.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.opprettArbeidsforholdGrunnlagListe
import no.nav.bidrag.behandling.utils.opprettBarnetilleggListe
import no.nav.bidrag.behandling.utils.opprettBarnetilsynListe
import no.nav.bidrag.behandling.utils.opprettKontantstøtteListe
import no.nav.bidrag.behandling.utils.opprettRolle
import no.nav.bidrag.behandling.utils.opprettSkattegrunnlagGrunnlagListe
import no.nav.bidrag.behandling.utils.opprettSmåbarnstillegListe
import no.nav.bidrag.behandling.utils.opprettUtvidetBarnetrygdGrunnlagListe
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdataBM
import no.nav.bidrag.behandling.utils.testdataBP
import no.nav.bidrag.behandling.utils.testdataBarn1
import no.nav.bidrag.behandling.utils.testdataBarn2
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class VedtakInnhentetGrunnlagTest {
    val grunnlagBm =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBM.ident,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            foedselsdato = testdataBM.foedselsdato,
            id = 1L,
        ).tilPersonGrunnlag()
    val grunnlagBp =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBP.ident,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            foedselsdato = testdataBP.foedselsdato,
            id = 1L,
        ).tilPersonGrunnlag()
    val søknadsbarnGrunnlag1 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn1.ident,
            rolletype = Rolletype.BARN,
            foedselsdato = testdataBarn1.foedselsdato,
            id = 1L,
        ).tilPersonGrunnlag()
    val søknadsbarnGrunnlag2 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn2.ident,
            rolletype = Rolletype.BARN,
            foedselsdato = testdataBarn2.foedselsdato,
            id = 1L,
        ).tilPersonGrunnlag()

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

            assertSoftly(grunnlagListe.tilInnhentetSivilstand(grunnlagBm).toList()) {
                this shouldHaveSize 3
                assertSoftly(this[0]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_SIVILSTAND_PERIODE
                    val grunnlag = it.innholdTilObjekt<InnhentetSivilstand>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("1978-08-25")
                    grunnlag.periode.til shouldBe null
                    grunnlag.hentetTidspunkt shouldHaveSameDayAs LocalDateTime.now()
                    grunnlag.grunnlag.sivilstand shouldBe SivilstandskodePDL.UGIFT
                    grunnlag.grunnlag.master shouldBe "FREG"
                    grunnlag.grunnlag.historisk shouldBe true
                }
                assertSoftly(this[1]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_SIVILSTAND_PERIODE
                    val grunnlag = it.innholdTilObjekt<InnhentetSivilstand>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2022-11-01")
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.sivilstand shouldBe SivilstandskodePDL.SKILT
                    grunnlag.grunnlag.master shouldBe "FREG"
                    grunnlag.grunnlag.historisk shouldBe false
                }
                assertSoftly(this[2]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_SIVILSTAND_PERIODE
                    val grunnlag = it.innholdTilObjekt<InnhentetSivilstand>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2022-12-01")
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.sivilstand shouldBe SivilstandskodePDL.SKILT
                    grunnlag.grunnlag.master shouldBe "PDL"
                    grunnlag.grunnlag.historisk shouldBe false
                }
            }
        }

        @Test
        fun `skal mappe innhentet grunnlag for sivilstand med null gyldigFom`() {
            val behandling = oppretteBehandling()

            val grunnlagListe =
                listOf(
                    BehandlingGrunnlag(
                        type = Grunnlagsdatatype.SIVILSTAND,
                        behandling = behandling,
                        innhentet = LocalDateTime.now(),
                        data =
                            commonObjectmapper.writeValueAsString(
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
                                ),
                            ),
                    ),
                )

            assertSoftly(grunnlagListe.tilInnhentetSivilstand(grunnlagBm).toList()) {
                this shouldHaveSize 1
                assertSoftly(this[0]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_SIVILSTAND_PERIODE
                    val grunnlag = it.innholdTilObjekt<InnhentetSivilstand>()
                    grunnlag.periode.fom shouldBe LocalDate.MIN
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.sivilstand shouldBe SivilstandskodePDL.SKILT
                    grunnlag.grunnlag.master shouldBe "PDL"
                    grunnlag.grunnlag.historisk shouldBe false
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
            val barnGrunnlag = listOf(søknadsbarnGrunnlag1, søknadsbarnGrunnlag2)
            assertSoftly(
                grunnlagListe.tilInnhentetHusstandsmedlemmer(grunnlagBm, barnGrunnlag).toList(),
            ) {
                it.hentAllePersoner().shouldHaveSize(5)
                val personGrunnlagHusstandsmedlemListe = it.hentGrunnlagPersonHusstandsmedlem()
                val husstandGrunnlag = it.hentGrunnlagHusstand()
                husstandGrunnlag shouldHaveSize 6
                assertSoftly(husstandGrunnlag[0]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE
                    this.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2017-03-30")
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag1.referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                }
                assertSoftly(husstandGrunnlag[1]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE
                    this.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2023-07-31")
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.relatertPerson shouldBe personGrunnlagHusstandsmedlemListe[0].referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe false
                }
                assertSoftly(husstandGrunnlag[2]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE
                    this.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2017-03-30")
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag2.referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                }
                assertSoftly(husstandGrunnlag[3]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE
                    this.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2010-10-06")
                    grunnlag.periode.til shouldBe LocalDate.parse("2023-01-02")
                    grunnlag.grunnlag.relatertPerson shouldBe personGrunnlagHusstandsmedlemListe[1].referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                }
                assertSoftly(husstandGrunnlag[4]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE
                    this.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2015-07-24")
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.relatertPerson shouldBe søknadsbarnGrunnlag1.referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe true
                }
                assertSoftly(husstandGrunnlag[5]) {
                    this.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE
                    this.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                    val grunnlag = it.innholdTilObjekt<InnhentetHusstandsmedlem>()
                    grunnlag.periode.fom shouldBe LocalDate.parse("2017-03-30")
                    grunnlag.periode.til shouldBe null
                    grunnlag.grunnlag.relatertPerson shouldBe personGrunnlagHusstandsmedlemListe[2].referanse
                    grunnlag.grunnlag.erBarnAvBmBp shouldBe false
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
                    BehandlingGrunnlag(
                        type = Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
                        behandling = behandling,
                        innhentet = LocalDateTime.now(),
                        data =
                            commonObjectmapper.writeValueAsString(
                                listOf(
                                    RelatertPersonGrunnlagDto(
                                        partPersonId = testdataBM.ident,
                                        relatertPersonPersonId = testdataBarn1.ident,
                                        navn = testdataBarn1.navn,
                                        fødselsdato = testdataBarn1.foedselsdato,
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
                                        fødselsdato = testdataBarn2.foedselsdato,
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
                                        fødselsdato = testdataBarn1.foedselsdato,
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
                                ),
                            ),
                    ),
                )
            val barnGrunnlag = listOf(søknadsbarnGrunnlag1, søknadsbarnGrunnlag2)
            val grunnlagHusstandsmedlemmer =
                grunnlagListe.tilInnhentetHusstandsmedlemmer(grunnlagBm, barnGrunnlag).toList()
            assertSoftly(grunnlagHusstandsmedlemmer) {
                it.hentAllePersoner().shouldHaveSize(3)
                val personGrunnlagHusstandsmedlemListe = it.hentGrunnlagPersonHusstandsmedlem()
                val husstandGrunnlag = it.hentGrunnlagHusstand()
                husstandGrunnlag shouldHaveSize 6
                husstandGrunnlag.hentHusstandsbarnMedReferanse(søknadsbarnGrunnlag2.referanse)
                    .shouldHaveSize(1)
                husstandGrunnlag.hentHusstandsbarnMedReferanse(personGrunnlagHusstandsmedlemListe[0].referanse)
                    .shouldHaveSize(1)
                assertSoftly(husstandGrunnlag.hentHusstandsbarnMedReferanse(søknadsbarnGrunnlag1.referanse)) {
                    this.shouldHaveSize(4)
                    assertSoftly(this[0].innholdTilObjekt<InnhentetHusstandsmedlem>()) {
                        it.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        it.periode.til shouldBe LocalDate.parse("2022-06-08")
                    }
                    assertSoftly(this[1].innholdTilObjekt<InnhentetHusstandsmedlem>()) {
                        it.periode.fom shouldBe LocalDate.parse("2023-01-02")
                        it.periode.til shouldBe LocalDate.parse("2023-06-28")
                    }
                    assertSoftly(this[2].innholdTilObjekt<InnhentetHusstandsmedlem>()) {
                        it.periode.fom shouldBe LocalDate.parse("2023-07-01")
                        it.periode.til shouldBe null
                    }
                    assertSoftly(this[3].innholdTilObjekt<InnhentetHusstandsmedlem>()) {
                        it.periode.fom shouldBe LocalDate.MIN
                        it.periode.til shouldBe null
                    }
                }
            }
        }

        fun List<GrunnlagDto>.hentHusstandsbarnMedReferanse(referanse: String) =
            this.filter { it.innholdTilObjekt<InnhentetHusstandsmedlem>().grunnlag.relatertPerson == referanse }

        fun List<GrunnlagDto>.hentGrunnlagHusstand() = filter { it.type == Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM_PERIODE }

        fun List<GrunnlagDto>.hentGrunnlagPersonHusstandsmedlem() =
            hentAllePersoner().filter { it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM }
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
                grunnlagListe.tilInnhentetGrunnlagInntekt(grunnlagBm, søknadsbarnGrunnlag1)
                    .toList(),
            ) {
                this shouldHaveSize 16
                hentGrunnlagAinntekt() shouldHaveSize 13
                hentSkattegrunnlag() shouldHaveSize 3
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
                listOf(
                    BehandlingGrunnlag(
                        type = Grunnlagsdatatype.INNTEKT,
                        behandling = behandling,
                        innhentet = LocalDateTime.now(),
                        data =
                            commonObjectmapper.writeValueAsString(
                                InntektGrunnlag(
                                    skattegrunnlagListe = opprettSkattegrunnlagGrunnlagListe(),
                                    ainntektListe = opprettAinntektGrunnlagListe(),
                                ),
                            ),
                    ),
                )
            assertSoftly(
                grunnlagListe.tilInnhentetGrunnlagInntekt(grunnlagBm, søknadsbarnGrunnlag1)
                    .toList(),
            ) {
                this shouldHaveSize 3
                assertSoftly(hentGrunnlagAinntekt()) {
                    it shouldHaveSize 1
                    assertSoftly(it[0]) {
                        it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                        val ainntekt = it.innholdTilObjekt<InnhentetAinntekt>()
                        ainntekt.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        ainntekt.periode.til shouldBe LocalDate.parse("2023-01-01")
                        ainntekt.grunnlag.ainntektspostListe shouldHaveSize 2
                        assertSoftly(ainntekt.grunnlag.ainntektspostListe[0]) {
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
                listOf(
                    BehandlingGrunnlag(
                        type = Grunnlagsdatatype.INNTEKT,
                        behandling = behandling,
                        innhentet = LocalDateTime.now(),
                        data =
                            commonObjectmapper.writeValueAsString(
                                InntektGrunnlag(
                                    skattegrunnlagListe = opprettSkattegrunnlagGrunnlagListe(),
                                    ainntektListe = opprettAinntektGrunnlagListe(),
                                ),
                            ),
                    ),
                )
            assertSoftly(
                grunnlagListe.tilInnhentetGrunnlagInntekt(grunnlagBm, søknadsbarnGrunnlag1)
                    .toList(),
            ) {
                this shouldHaveSize 3
                assertSoftly(hentSkattegrunnlag()) {
                    it shouldHaveSize 2
                    assertSoftly(it[0]) {
                        it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                        val skattegrunnlag = it.innholdTilObjekt<InnhentetSkattegrunnlag>()
                        skattegrunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        skattegrunnlag.periode.til shouldBe LocalDate.parse("2023-01-01")
                        skattegrunnlag.grunnlag.skattegrunnlagListe shouldHaveSize 2
                        assertSoftly(skattegrunnlag.grunnlag.skattegrunnlagListe[0]) {
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
                    BehandlingGrunnlag(
                        type = Grunnlagsdatatype.INNTEKT,
                        behandling = behandling,
                        innhentet = LocalDateTime.now(),
                        data =
                            commonObjectmapper.writeValueAsString(
                                InntektGrunnlag(
                                    skattegrunnlagListe = opprettSkattegrunnlagGrunnlagListe(),
                                    ainntektListe = opprettAinntektGrunnlagListe(),
                                    barnetilleggListe = opprettBarnetilleggListe(),
                                    utvidetBarnetrygdListe = opprettUtvidetBarnetrygdGrunnlagListe(),
                                    kontantstotteListe = opprettKontantstøtteListe(),
                                    småbarnstilleggListe = opprettSmåbarnstillegListe(),
                                    barnetilsynListe = opprettBarnetilsynListe(),
                                ),
                            ),
                    ),
                )
            assertSoftly(
                grunnlagListe.tilInnhentetGrunnlagInntekt(grunnlagBm, søknadsbarnGrunnlag1)
                    .toList(),
            ) {
                this shouldHaveSize 17
                assertSoftly(hentBarnetillegg()) {
                    it shouldHaveSize 3
                    assertSoftly(it[0]) {
                        it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                        it.grunnlagsreferanseListe.shouldContain(søknadsbarnGrunnlag1.referanse)
                        val grunnlag = it.innholdTilObjekt<InnhentetBarnetillegg>()
                        grunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        grunnlag.periode.til shouldBe LocalDate.parse("2022-04-30")
                        grunnlag.grunnlag.gjelderBarn shouldBe søknadsbarnGrunnlag1.referanse // TODO er dette vits å ta med?
                        grunnlag.grunnlag.barnetilleggType shouldBe "PENSJON"
                        grunnlag.grunnlag.beløpBrutto shouldBe BigDecimal(1000)
                        grunnlag.grunnlag.barnType shouldBe "FELLES"
                    }
                }
                assertSoftly(hentUtvidetBarnetrygd()) {
                    it shouldHaveSize 3
                    assertSoftly(it[0]) {
                        it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                        val grunnlag = it.innholdTilObjekt<InnhentetUtvidetBarnetrygd>()
                        grunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        grunnlag.periode.til shouldBe LocalDate.parse("2022-03-30")
                        grunnlag.grunnlag.beløp shouldBe BigDecimal(5000)
                        grunnlag.grunnlag.manueltBeregnet shouldBe false
                    }
                }
                assertSoftly(hentSmåbarnstillegg()) {
                    it shouldHaveSize 3
                    assertSoftly(it[0]) {
                        it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                        val grunnlag = it.innholdTilObjekt<InnhentetSmåbarnstillegg>()
                        grunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        grunnlag.periode.til shouldBe LocalDate.parse("2022-03-30")
                        grunnlag.grunnlag.beløp shouldBe BigDecimal(5000)
                        grunnlag.grunnlag.manueltBeregnet shouldBe false
                    }
                }
                assertSoftly(hentKontantstøtte()) {
                    it shouldHaveSize 3
                    assertSoftly(it[0]) {
                        it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                        val grunnlag = it.innholdTilObjekt<InnhentetKontantstøtte>()
                        grunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        grunnlag.periode.til shouldBe LocalDate.parse("2022-07-31")
                        grunnlag.grunnlag.beløp shouldBe 1000
                        grunnlag.grunnlag.gjelderBarn shouldBe søknadsbarnGrunnlag1.referanse
                    }
                }
                assertSoftly(hentBarnetilsyn()) {
                    it shouldHaveSize 2
                    assertSoftly(it[0]) {
                        it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                        val grunnlag = it.innholdTilObjekt<InnhentetBarnetilsyn>()
                        grunnlag.periode.fom shouldBe LocalDate.parse("2022-01-01")
                        grunnlag.periode.til shouldBe LocalDate.parse("2022-07-31")
                        grunnlag.grunnlag.tilsynstype shouldBe Tilsynstype.HELTID
                        grunnlag.grunnlag.skolealder shouldBe Skolealder.IKKE_ANGITT
                    }
                }
            }
        }

        fun List<GrunnlagDto>.hentGrunnlagAinntekt() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE }

        fun List<GrunnlagDto>.hentSkattegrunnlag() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE }

        fun List<GrunnlagDto>.hentUtvidetBarnetrygd() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD_PERIODE }

        fun List<GrunnlagDto>.hentSmåbarnstillegg() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG_PERIODE }

        fun List<GrunnlagDto>.hentBarnetillegg() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG_PERIODE }

        fun List<GrunnlagDto>.hentKontantstøtte() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE_PERIODE }

        fun List<GrunnlagDto>.hentBarnetilsyn() = this.filter { it.type == Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN_PERIODE }
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
            val grunnlagListe =
                listOf(
                    BehandlingGrunnlag(
                        type = Grunnlagsdatatype.ARBEIDSFORHOLD,
                        behandling = behandling,
                        innhentet = LocalDateTime.now(),
                        data =
                            commonObjectmapper.writeValueAsString(
                                opprettArbeidsforholdGrunnlagListe(),
                            ),
                    ),
                )
            assertSoftly(
                grunnlagListe.tilInnhentetArbeidsforhold(grunnlagBm)
                    .toList(),
            ) {
                this shouldHaveSize 2
                assertSoftly(this[0]) {
                    it.grunnlagsreferanseListe.shouldContain(grunnlagBm.referanse)
                    val arbeidsforhold = it.innholdTilObjekt<InnhentetArbeidsforhold>()
                    arbeidsforhold.periode.fom shouldBe LocalDate.parse("2008-01-01")
                    arbeidsforhold.periode.til shouldBe LocalDate.parse("2021-12-31")
                    assertSoftly(arbeidsforhold.grunnlag) {
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
