package no.nav.bidrag.behandling.transformers.behandling

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class AktivBoforholdSivilstandGrunnlagMappingTest : AktivGrunnlagTestFelles() {
    @Nested
    inner class BoforholdGrunnlagendringTest {
        @Test
        fun `skal finne differanser i boforhold ved endring`() {
            val behandling = byggBehandling()
            val aktivBoforholdGrunnlagListe =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                )
            val aktivtBoforholdGrunnlagListe2 =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn2.ident,
                    ),
                )
            val aktivtGrunnlagBoforhold =
                listOf(
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                        behandling = behandling,
                        gjelder = testdataBarn1.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(aktivtBoforholdGrunnlagListe2),
                        behandling = behandling,
                        gjelder = testdataBarn2.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                )

            val boforholdGrunnlagListe =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                )
            val boforholdGrunnlagListe2 =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn2.ident,
                    ),
                )
            val nyttGrunnlagBoforhold =
                listOf(
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(boforholdGrunnlagListe),
                        behandling = behandling,
                        gjelder = testdataBarn1.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(boforholdGrunnlagListe2),
                        behandling = behandling,
                        gjelder = testdataBarn2.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                )

            val resultat = nyttGrunnlagBoforhold.henteEndringerIBoforhold(aktivtGrunnlagBoforhold, behandling)

            resultat shouldHaveSize 2
            val resultatBarn1 = resultat.find { it.ident == testdataBarn1.ident }
            resultatBarn1!!.perioder shouldHaveSize 1
            resultatBarn1.perioder.toList()[0].datoFom shouldBe behandling.virkningstidspunkt
            resultatBarn1.perioder.toList()[0].datoTom shouldBe null
            resultatBarn1.perioder.toList()[0].bostatus shouldBe Bostatuskode.MED_FORELDER

            val resultatBarn2 = resultat.find { it.ident == testdataBarn2.ident }
            resultatBarn2!!.perioder shouldHaveSize 1
            resultatBarn2.perioder.toList()[0].datoFom shouldBe LocalDate.parse("2023-12-01")
            resultatBarn2.perioder.toList()[0].datoTom shouldBe null
            resultatBarn2.perioder.toList()[0].bostatus shouldBe Bostatuskode.MED_FORELDER
        }

        @Test
        fun `skal finne differanser i boforhold ved endring hvis ny periode kommer`() {
            val behandling = byggBehandling()
            val aktivBoforholdGrunnlagListe =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                )
            val aktivGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val boforholdGrunnlagListe =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = YearMonth.of(2023, 12).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                )
            val nyGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(boforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val resultat =
                listOf(
                    nyGrunnlagBoforhold,
                ).henteEndringerIBoforhold(listOf(aktivGrunnlagBoforhold), behandling)

            resultat shouldHaveSize 1
            val resultatBarn1 = resultat.find { it.ident == testdataBarn1.ident }
            resultatBarn1!!.perioder shouldHaveSize 3
            resultatBarn1.perioder.toList()[0].datoFom shouldBe behandling.virkningstidspunkt
            resultatBarn1.perioder.toList()[0].datoTom shouldBe LocalDate.parse("2023-11-30")
            resultatBarn1.perioder.toList()[0].bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
        }

        @Test
        fun `skal ikke finne differanser i boforhold ved endring hvis endring ikke etter virkningstidspunkt`() {
            val behandling = byggBehandling()
            val aktivBoforholdGrunnlagListe =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 11).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2022, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                )
            val aktivGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val boforholdGrunnlagListe =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2021, 11).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2021, 12).atDay(1),
                        periodeTom = YearMonth.of(2022, 5).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2022, 6).atDay(1),
                        periodeTom = YearMonth.of(2022, 11).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2022, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                )
            val nyttGrunnlagBoforhold =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    data = commonObjectmapper.writeValueAsString(boforholdGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val resultat =
                listOf(nyttGrunnlagBoforhold).henteEndringerIBoforhold(listOf(aktivGrunnlagBoforhold), behandling)

            resultat shouldHaveSize 0
        }

        @Test
        fun `skal ikke finne differanser i boforhold hvis ingen endring`() {
            val behandling = byggBehandling()
            val aktivBoforholdGrunnlagListe =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2005, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 11).atEndOfMonth(),
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn1.ident,
                    ),
                )
            val aktivBoforholdGrunnlagListe2 =
                listOf(
                    BoforholdResponseV2(
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        fødselsdato = LocalDate.parse("2005-01-01"),
                        periodeFom = YearMonth.of(2023, 12).atDay(1),
                        periodeTom = null,
                        gjelderPersonId = testdataBarn2.ident,
                    ),
                )
            val aktivGrunnlagBoforhold =
                listOf(
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                        behandling = behandling,
                        gjelder = testdataBarn1.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe2),
                        behandling = behandling,
                        gjelder = testdataBarn2.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                )

            val nyGrunnlagBoforhold =
                listOf(
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe),
                        behandling = behandling,
                        gjelder = testdataBarn1.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                    Grunnlag(
                        erBearbeidet = true,
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        data = commonObjectmapper.writeValueAsString(aktivBoforholdGrunnlagListe2),
                        behandling = behandling,
                        gjelder = testdataBarn2.ident,
                        innhentet = LocalDateTime.now(),
                    ),
                )

            val resultat = nyGrunnlagBoforhold.henteEndringerIBoforhold(aktivGrunnlagBoforhold, behandling)

            resultat shouldHaveSize 0
        }
    }

    @Nested
    inner class SivilstandGrunnlagendringTest {
        @Test
        fun `skal ikke finne differanser i sivilstand ved endring`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag), LocalDate.parse("2020-01-01"))

            resultat shouldBe null
        }

        @Test
        fun `skal finne differanser i sivilstand ved endring`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val sivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 8).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 9).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )
            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(sivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag), LocalDate.parse("2020-01-01"))

            resultat shouldNotBe null
            resultat!!.grunnlag shouldHaveSize 2
            resultat.sivilstand shouldHaveSize 2
            assertSoftly(resultat.sivilstand.toList()[0]) {
                it.datoFom shouldBe LocalDate.parse("2022-01-01")
                it.datoTom shouldBe LocalDate.parse("2022-08-31")
                it.sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                it.kilde shouldBe Kilde.OFFENTLIG
            }
            assertSoftly(resultat.sivilstand.toList()[1]) {
                it.datoFom shouldBe LocalDate.parse("2022-09-01")
                it.datoTom shouldBe null
                it.sivilstand shouldBe Sivilstandskode.GIFT_SAMBOER
                it.kilde shouldBe Kilde.OFFENTLIG
            }
        }

        @Test
        fun `skal finne differanser i sivilstand ved endring scenarie 2`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val sivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = YearMonth.of(2023, 2).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )
            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(sivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag), LocalDate.parse("2020-01-01"))

            resultat shouldNotBe null
            resultat!!.grunnlag shouldHaveSize 2
            resultat.sivilstand shouldHaveSize 2
            assertSoftly(resultat.sivilstand.toList()[0]) {
                it.datoFom shouldBe LocalDate.parse("2022-01-01")
                it.datoTom shouldBe LocalDate.parse("2022-12-31")
                it.sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                it.kilde shouldBe Kilde.OFFENTLIG
            }
            assertSoftly(resultat.sivilstand.toList()[1]) {
                it.datoFom shouldBe LocalDate.parse("2023-01-01")
                it.datoTom shouldBe LocalDate.parse("2023-02-28")
                it.sivilstand shouldBe Sivilstandskode.GIFT_SAMBOER
                it.kilde shouldBe Kilde.OFFENTLIG
            }
        }

        @Test
        fun `skal finne differanser i sivilstand ved endring hvis status er UKJENT`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val nySivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.UKJENT,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )

            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(nySivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag), LocalDate.parse("2020-01-01"))

            resultat shouldNotBe null
            resultat!!.sivilstand shouldHaveSize 1
            resultat.sivilstand[0].sivilstand shouldBe Sivilstandskode.UKJENT
            resultat.grunnlag shouldHaveSize 2
        }

        @Test
        fun `skal finne differanser i sivilstand endringer i lengde`() {
            val behandling = byggBehandling()
            val aktivSivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2021, 1).atDay(1),
                        periodeTom = YearMonth.of(2021, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2022, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )

            val aktivSivilstandGrunnlag =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(aktivSivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )

            val nySivilstandGrunnlagListe =
                listOf(
                    Sivilstand(
                        periodeFom = YearMonth.of(2021, 1).atDay(1),
                        periodeTom = YearMonth.of(2022, 12).atEndOfMonth(),
                        sivilstandskode = Sivilstandskode.GIFT_SAMBOER,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    Sivilstand(
                        periodeFom = YearMonth.of(2023, 1).atDay(1),
                        periodeTom = null,
                        sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
                        kilde = Kilde.OFFENTLIG,
                    ),
                )

            val nyGrunnlagSivilstandBeregnet =
                Grunnlag(
                    erBearbeidet = true,
                    rolle = behandling.bidragsmottaker!!,
                    type = Grunnlagsdatatype.SIVILSTAND,
                    data = commonObjectmapper.writeValueAsString(nySivilstandGrunnlagListe),
                    behandling = behandling,
                    innhentet = LocalDateTime.now(),
                )
            val resultat =
                listOf(
                    nyGrunnlagSivilstandBeregnet,
                    opprettSivilstandGrunnlag(behandling),
                ).hentEndringerSivilstand(listOf(aktivSivilstandGrunnlag), LocalDate.parse("2020-01-01"))

            resultat shouldNotBe null
            resultat!!.sivilstand shouldHaveSize 2
            resultat.grunnlag shouldHaveSize 2
        }

        fun opprettSivilstandGrunnlag(behandling: Behandling) =
            Grunnlag(
                erBearbeidet = false,
                rolle = behandling.bidragsmottaker!!,
                type = Grunnlagsdatatype.SIVILSTAND,
                data =
                    commonObjectmapper.writeValueAsString(
                        setOf(
                            SivilstandGrunnlagDto(
                                personId = "213",
                                type = SivilstandskodePDL.SKILT,
                                gyldigFom = LocalDate.of(2005, 1, 1),
                                historisk = true,
                                bekreftelsesdato = LocalDate.now(),
                                master = "PDL",
                                registrert = LocalDateTime.now(),
                            ),
                            SivilstandGrunnlagDto(
                                personId = "213",
                                type = SivilstandskodePDL.GIFT,
                                gyldigFom = LocalDate.of(2022, 1, 1),
                                historisk = false,
                                bekreftelsesdato = LocalDate.now(),
                                master = "PDL",
                                registrert = LocalDateTime.now(),
                            ),
                        ),
                    ),
                behandling = behandling,
                innhentet = LocalDateTime.now(),
            )
    }
}
