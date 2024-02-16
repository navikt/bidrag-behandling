package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.opplysninger.InntektBearbeidet
import no.nav.bidrag.behandling.database.opplysninger.InntektsopplysningerBearbeidet
import no.nav.bidrag.behandling.transformers.grunnlag.opprettGrunnlagForHusstandsbarn
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagBostatus
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagSivilstand
import no.nav.bidrag.behandling.transformers.grunnlag.tilPersonGrunnlag
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagNotater
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagSøknad
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagVirkningsttidspunkt
import no.nav.bidrag.behandling.utils.TestDataPerson
import no.nav.bidrag.behandling.utils.opprettRolle
import no.nav.bidrag.behandling.utils.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdataBM
import no.nav.bidrag.behandling.utils.testdataBP
import no.nav.bidrag.behandling.utils.testdataBarn1
import no.nav.bidrag.behandling.utils.testdataBarn2
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class GrunnlagMappingTest {
    private val fødslesdato = LocalDate.parse("2024-04-03")

    val grunnlagBm =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBM.ident,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            foedselsdato = testdataBM.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val grunnlagBp =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBP.ident,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            foedselsdato = testdataBP.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag1 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn1.ident,
            rolletype = Rolletype.BARN,
            foedselsdato = testdataBarn1.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag2 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn2.ident,
            rolletype = Rolletype.BARN,
            foedselsdato = testdataBarn2.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()

    @BeforeEach
    fun initMocks() {
        stubKodeverkProvider()
    }

    @Nested
    inner class PersonGrunnlagTest {
        @Test
        fun `skal mappe rolle til grunnlag`() {
            val behandling = oppretteBehandling()
            assertSoftly(
                Rolle(
                    behandling = behandling,
                    ident = "12345678901",
                    rolletype = Rolletype.BIDRAGSMOTTAKER,
                    foedselsdato = fødslesdato,
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
                    foedselsdato = fødslesdato,
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
                    foedselsdato = fødslesdato,
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
        fun `skal mappe husstandsmedlem til grunnlag`() {
            val behandling = oppretteBehandling()
            assertSoftly(
                Husstandsbarn(
                    behandling = behandling,
                    ident = "12345678901",
                    foedselsdato = fødslesdato,
                    perioder = mutableSetOf(),
                    medISaken = false,
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
        fun `skal mappe relatertperson til grunnlag`() {
            assertSoftly(
                RelatertPersonGrunnlagDto(
                    partPersonId = "123123123",
                    relatertPersonPersonId = "12345678901",
                    fødselsdato = fødslesdato,
                    navn = "Ola Nordmann",
                    borISammeHusstandDtoListe = emptyList(),
                    erBarnAvBmBp = true,
                ).tilPersonGrunnlag(1),
            ) {
                it.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                it.referanse.shouldStartWith("person")
                val personGrunnlag: Person = it.innholdTilObjekt()
                personGrunnlag.ident shouldBe Personident("12345678901")
                personGrunnlag.navn shouldBe null
                personGrunnlag.fødselsdato shouldBe fødslesdato
            }
        }
    }

    @Nested
    inner class InntektGrunnlagTest {
        @Test
        fun `skal mappe inntekt til grunnlag`() {
            val behandling = oppretteBehandling()
            behandling.inntekter = opprettInntekter(behandling, testdataBM, testdataBarn1)
            assertSoftly(behandling.tilGrunnlagInntekt(grunnlagBm, søknadsbarnGrunnlag1).toList()) {
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
                    innhold.beløp shouldBe BigDecimal(45000)
                    innhold.valgt shouldBe false
                    innhold.manueltRegistrert shouldBe false
                    innhold.gjelderBarn.shouldBeNull()
                    innhold.inntekstpostListe shouldHaveSize 2
                    with(innhold.inntekstpostListe[0]) {
                        beløp shouldBe BigDecimal(5000)
                        kode shouldBe "fisking"
                    }
                    with(innhold.inntekstpostListe[1]) {
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
                    innhold.inntekstpostListe shouldHaveSize 2
                    with(innhold.inntekstpostListe[0]) {
                        beløp shouldBe BigDecimal(5000)
                        kode shouldBe ""
                        inntekstype shouldBe Inntektstype.NÆRINGSINNTEKT
                    }
                    with(innhold.inntekstpostListe[1]) {
                        beløp shouldBe BigDecimal(28000)
                        kode shouldBe ""
                        inntekstype shouldBe Inntektstype.LØNNSINNTEKT
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
                    innhold.gjelderBarn.shouldBeNull()
                    innhold.inntekstpostListe shouldHaveSize 0
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
                    innhold.inntekstpostListe shouldHaveSize 1
                    with(innhold.inntekstpostListe[0]) {
                        beløp shouldBe BigDecimal(5000)
                        kode shouldBe ""
                        inntekstype shouldBe Inntektstype.BARNETILLEGG_PENSJON
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
                    innhold.inntekstpostListe shouldHaveSize 1
                    with(innhold.inntekstpostListe[0]) {
                        beløp shouldBe BigDecimal(5000)
                        kode shouldBe ""
                        inntekstype shouldBe Inntektstype.KONTANTSTØTTE
                    }
                }
            }
        }

        @Test
        fun `skal mappe inntekt til grunnlag hvis inneholder inntektliste for Barn og BP`() {
            val behandling = oppretteBehandling()
            behandling.inntekter.addAll(
                opprettInntekter(behandling, testdataBM, testdataBarn1) +
                    opprettInntekter(
                        behandling,
                        testdataBP,
                    ) +
                    opprettInntekter(behandling, testdataBarn1),
            )
            assertSoftly(behandling.tilGrunnlagInntekt(grunnlagBm, søknadsbarnGrunnlag1).toList()) {
                it.forEach { inntekt ->
                    inntekt.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
                    inntekt.referanse.shouldStartWith("inntekt")
                    inntekt.gjelderReferanse shouldBe grunnlagBm.referanse
                }
                it shouldHaveSize 5
            }
            assertSoftly(behandling.tilGrunnlagInntekt(grunnlagBp).toList()) {
                it.forEach { inntekt ->
                    inntekt.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
                    inntekt.referanse.shouldStartWith("inntekt")
                    inntekt.gjelderReferanse shouldBe grunnlagBp.referanse
                }
                it shouldHaveSize 3
            }
            assertSoftly(behandling.tilGrunnlagInntekt(søknadsbarnGrunnlag1).toList()) {
                it.forEach { inntekt ->
                    inntekt.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
                    inntekt.referanse.shouldStartWith("inntekt")
                    inntekt.gjelderReferanse shouldBe søknadsbarnGrunnlag1.referanse
                }
                it shouldHaveSize 3
            }
        }

        @Test
        fun `skal legge til grunnlagsliste for innhentet inntekter`() {
            val behandling = oppretteBehandling()
            val ainntektGrunnlagsreferanseListe = listOf("ainntekt_1", "ainntekt_2", "ainntekt_3")
            val innteksopplynsingerBearbeidet =
                InntektsopplysningerBearbeidet(
                    inntekt =
                        listOf(
                            InntektBearbeidet(
                                ident = testdataBM.ident,
                                versjon = "1",
                                summertMånedsinntektListe = emptyList(),
                                summertAarsinntektListe =
                                    listOf(
                                        SummertÅrsinntekt(
                                            Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                                            periode =
                                                ÅrMånedsperiode(
                                                    YearMonth.parse("2023-01"),
                                                    YearMonth.parse("2024-01"),
                                                ),
                                            sumInntekt = BigDecimal.ONE,
                                            inntektPostListe = emptyList(),
                                            grunnlagsreferanseListe = ainntektGrunnlagsreferanseListe,
                                        ),
                                    ),
                            ),
                        ),
                )

            behandling.grunnlagListe =
                listOf(
                    BehandlingGrunnlag(
                        behandling = behandling,
                        type = Grunnlagsdatatype.INNTEKT_BEARBEIDET,
                        data =
                            commonObjectmapper.writeValueAsString(
                                POJONode(
                                    innteksopplynsingerBearbeidet,
                                ),
                            ),
                        innhentet = LocalDateTime.now(),
                    ),
                )
            behandling.inntekter.addAll(
                opprettInntekter(behandling, testdataBM, testdataBarn1),
            )

            assertSoftly(behandling.tilGrunnlagInntekt(grunnlagBm).toList()) {
                it.forEach {
                    val innhold = it.innholdTilObjekt<InntektsrapporteringPeriode>()
                    if (innhold.inntektsrapportering == Inntektsrapportering.AINNTEKT_BEREGNET_12MND) {
                        it.grunnlagsreferanseListe shouldBe ainntektGrunnlagsreferanseListe
                    } else {
                        it.grunnlagsreferanseListe.shouldBeEmpty()
                    }
                    it.gjelderReferanse shouldBe grunnlagBm.referanse
                }
            }
        }

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
                opprinneligTom = LocalDate.parse("2024-01-01"),
                ident = gjelder.ident,
                kilde = Kilde.OFFENTLIG,
                taMed = false,
                behandling = behandling,
                inntektsposter =
                    mutableSetOf(
                        Inntektspost(
                            beløp = BigDecimal.valueOf(5000),
                            kode = "fisking",
                            visningsnavn = "",
                            inntektstype = null,
                        ),
                        Inntektspost(
                            beløp = BigDecimal.valueOf(40000),
                            kode = "krypto",
                            visningsnavn = "",
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
                            visningsnavn = "",
                            inntektstype = Inntektstype.NÆRINGSINNTEKT,
                        ),
                        Inntektspost(
                            beløp = BigDecimal.valueOf(28000),
                            kode = "",
                            visningsnavn = "",
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
                    gjelderBarn = barn.ident,
                    behandling = behandling,
                    inntektsposter =
                        mutableSetOf(
                            Inntektspost(
                                beløp = BigDecimal.valueOf(5000),
                                kode = "",
                                visningsnavn = "",
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
                    gjelderBarn = testdataBarn1.ident,
                    behandling = behandling,
                    inntektsposter =
                        mutableSetOf(
                            Inntektspost(
                                beløp = BigDecimal.valueOf(5000),
                                kode = "",
                                visningsnavn = "",
                                inntektstype = Inntektstype.KONTANTSTØTTE,
                            ),
                        ),
                )
            },
        ).filterNotNull().toMutableSet()
    }

    @Nested
    inner class BosstatusGrunnlagTest {
        fun opprettHusstandsbarn(
            behandling: Behandling,
            ident: String,
            fødselsdato: LocalDate = LocalDate.parse("2024-04-03"),
        ): Husstandsbarn {
            val husstandsbarn =
                Husstandsbarn(
                    behandling = behandling,
                    ident = ident,
                    foedselsdato = fødselsdato,
                    perioder = mutableSetOf(),
                    medISaken = false,
                    id = 1L,
                )

            husstandsbarn.perioder = opprettPerioder(husstandsbarn)
            return husstandsbarn
        }

        fun opprettPerioder(husstandsbarn: Husstandsbarn) =
            mutableSetOf(
                Husstandsbarnperiode(
                    husstandsbarn = husstandsbarn,
                    datoFom = LocalDate.parse("2023-01-01"),
                    datoTom = LocalDate.parse("2023-06-30"),
                    bostatus = Bostatuskode.MED_FORELDER,
                    kilde = Kilde.OFFENTLIG,
                    id = 1,
                ),
                Husstandsbarnperiode(
                    husstandsbarn = husstandsbarn,
                    datoFom = LocalDate.parse("2023-07-01"),
                    datoTom = LocalDate.parse("2023-08-31"),
                    bostatus = Bostatuskode.IKKE_MED_FORELDER,
                    kilde = Kilde.OFFENTLIG,
                    id = 2,
                ),
                Husstandsbarnperiode(
                    husstandsbarn = husstandsbarn,
                    datoFom = LocalDate.parse("2023-09-01"),
                    datoTom = LocalDate.parse("2023-12-31"),
                    bostatus = Bostatuskode.MED_FORELDER,
                    kilde = Kilde.MANUELL,
                    id = 3,
                ),
                Husstandsbarnperiode(
                    husstandsbarn = husstandsbarn,
                    datoFom = LocalDate.parse("2024-01-01"),
                    datoTom = null,
                    bostatus = Bostatuskode.REGNES_IKKE_SOM_BARN,
                    kilde = Kilde.MANUELL,
                    id = 4,
                ),
            )

        @Test
        fun `skal opprette grunnlag for husstandsbarn`() {
            val behandling = oppretteBehandling()

            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val husstandsbarn =
                mutableSetOf(
                    opprettHusstandsbarn(
                        behandling,
                        testdataBarn1.ident,
                        testdataBarn1.foedselsdato,
                    ),
                    opprettHusstandsbarn(
                        behandling,
                        testdataBarn2.ident,
                        testdataBarn2.foedselsdato,
                    ),
                    opprettHusstandsbarn(behandling, "123213123123"),
                    opprettHusstandsbarn(
                        behandling,
                        "4124214124",
                        fødselsdato = LocalDate.parse("2023-03-03"),
                    ),
                )

            behandling.husstandsbarn = husstandsbarn

            assertSoftly(
                behandling.opprettGrunnlagForHusstandsbarn(testdataBarn1.tilGrunnlagDto())
                    .toList(),
            ) {
                it shouldHaveSize 3
                assertSoftly(this[0]) { person ->
                    person.type shouldBe Grunnlagstype.PERSON_SØKNADSBARN
                    person.innholdTilObjekt<Person>().ident shouldBe Personident(testdataBarn2.ident)
                }
                assertSoftly(this[1]) { person ->
                    person.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                    person.innholdTilObjekt<Person>().ident shouldBe Personident("123213123123")
                }
                assertSoftly(this[2]) { person ->
                    person.type shouldBe Grunnlagstype.PERSON_HUSSTANDSMEDLEM
                    person.innholdTilObjekt<Person>().ident shouldBe Personident("4124214124")
                }
            }
        }

        @Test
        fun `skal mappe husstandsbarn til bosstatus`() {
            val behandling = oppretteBehandling()

            behandling.roller =
                mutableSetOf(
                    opprettRolle(behandling, testdataBM),
                    opprettRolle(behandling, testdataBarn1),
                    opprettRolle(behandling, testdataBarn2),
                )
            val husstandsbarn =
                mutableSetOf(
                    opprettHusstandsbarn(
                        behandling,
                        testdataBarn1.ident,
                        testdataBarn1.foedselsdato,
                    ),
                    opprettHusstandsbarn(
                        behandling,
                        testdataBarn2.ident,
                        testdataBarn2.foedselsdato,
                    ),
                    opprettHusstandsbarn(behandling, "123213123123"),
                    opprettHusstandsbarn(
                        behandling,
                        "4124214124",
                        fødselsdato = LocalDate.parse("2023-03-03"),
                    ),
                )

            behandling.husstandsbarn = husstandsbarn

            val barnGrunnlag =
                behandling.opprettGrunnlagForHusstandsbarn(testdataBarn1.tilGrunnlagDto())
                    .toList() + søknadsbarnGrunnlag1
            assertSoftly(behandling.tilGrunnlagBostatus(barnGrunnlag.toSet()).toList()) {
                it shouldHaveSize 16
                assertSoftly(it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag1.referanse)) {
                    this shouldHaveSize 4
                    assertSoftly(this[0]) {
                        type shouldBe Grunnlagstype.BOSTATUS_PERIODE
                        gjelderReferanse shouldBe søknadsbarnGrunnlag1.referanse
                        val innhold = innholdTilObjekt<BostatusPeriode>()
                        innhold.bostatus shouldBe Bostatuskode.MED_FORELDER
                        innhold.manueltRegistrert shouldBe false
                        innhold.periode.fom shouldBe YearMonth.parse("2023-01")
                        innhold.periode.til shouldBe YearMonth.parse("2023-07")
                    }
                    assertSoftly(this[3]) {
                        type shouldBe Grunnlagstype.BOSTATUS_PERIODE
                        gjelderReferanse shouldBe søknadsbarnGrunnlag1.referanse
                        val innhold = innholdTilObjekt<BostatusPeriode>()
                        innhold.bostatus shouldBe Bostatuskode.REGNES_IKKE_SOM_BARN
                        innhold.manueltRegistrert shouldBe true
                        innhold.periode.fom shouldBe YearMonth.parse("2024-01")
                        innhold.periode.til shouldBe null
                    }
                }

                it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarnGrunnlag2.referanse) shouldHaveSize 4
                it.filter {
                    it.gjelderReferanse?.startsWith("person_${Grunnlagstype.PERSON_HUSSTANDSMEDLEM}") == true
                } shouldHaveSize 8
            }
        }
    }

    @Nested
    inner class SivilstandGrunnlagTest {
        fun opprettSivilstand(behandling: Behandling): MutableSet<Sivilstand> {
            return mutableSetOf(
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
        }

        @Test
        fun `skal opprette grunnlag for husstandsbarn`() {
            val behandling = oppretteBehandling()

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
                assertSoftly(this[0]) { sivilstand ->
                    sivilstand.type shouldBe Grunnlagstype.SIVILSTAND_PERIODE
                    sivilstand.referanse.shouldStartWith("sivilstand_person_${Grunnlagstype.PERSON_BIDRAGSMOTTAKER}")
                    sivilstand.gjelderReferanse.shouldBe(grunnlagBm.referanse)
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
        @Test
        fun `skal opprette grunnlag for søknad`() {
            val behandling = oppretteBehandling()

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
        fun `skal opprette grunnlag for virkningstidspunkt`() {
            val behandling = oppretteBehandling()

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
        fun `skal opprette grunnlag for notat og ikke ta med notat hvis tom eller null`() {
            val behandling = oppretteBehandling()
            behandling.inntektsbegrunnelseIVedtakOgNotat = null
            behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
            behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "   "
            behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
            behandling.boforholdsbegrunnelseKunINotat = "Boforhold"
            behandling.boforholdsbegrunnelseIVedtakOgNotat = "Boforhold kun i notat"

            assertSoftly(behandling.byggGrunnlagNotater().toList()) {
                shouldHaveSize(4)
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
                    innhold shouldBe behandling.boforholdsbegrunnelseIVedtakOgNotat
                    erMedIVedtaksdokumentet shouldBe true
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.BOFORHOLD
                }
                assertSoftly(it[3].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.inntektsbegrunnelseKunINotat
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.INNTEKT
                }
            }
        }

        @Test
        fun `skal opprette grunnlag for notat`() {
            val behandling = oppretteBehandling()
            behandling.inntektsbegrunnelseIVedtakOgNotat = "Inntektsbegrunnelse"
            behandling.inntektsbegrunnelseKunINotat = "Inntektsbegrunnelse kun i notat"
            behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "Virkningstidspunkt"
            behandling.virkningstidspunktbegrunnelseKunINotat = "Virkningstidspunkt kun i notat"
            behandling.boforholdsbegrunnelseKunINotat = "Boforhold"
            behandling.boforholdsbegrunnelseIVedtakOgNotat = "Boforhold kun i notat"

            assertSoftly(behandling.byggGrunnlagNotater().toList()) {
                shouldHaveSize(6)
                assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.virkningstidspunktbegrunnelseKunINotat
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
                }
                assertSoftly(it[1].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat
                    erMedIVedtaksdokumentet shouldBe true
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
                }
                assertSoftly(it[2].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.boforholdsbegrunnelseKunINotat
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.BOFORHOLD
                }
                assertSoftly(it[3].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.boforholdsbegrunnelseIVedtakOgNotat
                    erMedIVedtaksdokumentet shouldBe true
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.BOFORHOLD
                }
                assertSoftly(it[4].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.inntektsbegrunnelseKunINotat
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.INNTEKT
                }
                assertSoftly(it[5].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe behandling.inntektsbegrunnelseIVedtakOgNotat
                    erMedIVedtaksdokumentet shouldBe true
                    type shouldBe no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType.INNTEKT
                }
            }
        }
    }
}
