package no.nav.bidrag.behandling.utils.testdata

import com.fasterxml.jackson.databind.node.POJONode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.ainntektListe
import no.nav.bidrag.behandling.transformers.grunnlag.skattegrunnlagListe
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.service.sjablon.Sjablontall
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.sak.Bidragssakstatus
import no.nav.bidrag.domene.enums.sak.Sakskategori
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.ident.ReellMottager
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.TransformerInntekterResponse
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.person.PersonDto
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.random.Random

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L
val SAKSBEHANDLER_IDENT = "Z999999"

val testdataBP =
    TestDataPerson(
        navn = "Kor Mappe",
        ident = "213244124",
        rolletype = Rolletype.BIDRAGSPLIKTIG,
        fødselsdato = LocalDate.parse("2000-03-01"),
    )
val testdataBM =
    TestDataPerson(
        navn = "Oran Mappe",
        ident = "313213213",
        rolletype = Rolletype.BIDRAGSMOTTAKER,
        fødselsdato = LocalDate.parse("1978-08-25"),
    )

val testdataBarn1 =
    TestDataPerson(
        navn = "Kran Mappe",
        ident = "1344124",
        rolletype = Rolletype.BARN,
        fødselsdato = LocalDate.parse("2020-03-01"),
    )
val testdataBarn2 =
    TestDataPerson(
        navn = "Gran Mappe",
        ident = "54545454545",
        rolletype = Rolletype.BARN,
        fødselsdato = LocalDate.parse("2018-05-09"),
    )
val testdataHusstandsmedlem1 =
    TestDataPerson(
        navn = "Huststand Gapp",
        ident = "31231231231",
        rolletype = Rolletype.BARN,
        fødselsdato = LocalDate.parse("2001-05-09"),
    )

data class TestDataPerson(
    val ident: String,
    val navn: String,
    val fødselsdato: LocalDate,
    val rolletype: Rolletype,
) {
    fun tilRolle(behandling: Behandling = oppretteBehandling()) =
        Rolle(
            id = behandling.roller.find { it.ident == ident }?.id ?: 1,
            ident = ident,
            navn = navn,
            foedselsdato = fødselsdato,
            rolletype = rolletype,
            opprettet = LocalDateTime.now(),
            behandling = behandling,
        )

    fun tilPersonDto() =
        PersonDto(
            ident = Personident(ident),
            navn = navn,
            fødselsdato = fødselsdato,
        )

    fun tilGrunnlagDto() =
        GrunnlagDto(
            referanse = "${ident}_${rolletype.name}_$navn",
            type = rolletype.tilGrunnlagstype(),
            innhold =
                POJONode(
                    Person(
                        ident = Personident(ident),
                        navn = navn,
                        fødselsdato = fødselsdato,
                    ),
                ),
        )

    fun tilForsendelseRolleDto() = ForsendelseRolleDto(Personident(ident), type = rolletype)
}

fun opprettForsendelseResponsUnderOpprettelse(forsendelseId: Long = 1) =
    ForsendelseResponsTo(
        forsendelseId = forsendelseId,
        saksnummer = SAKSNUMMER,
        behandlingInfo =
            BehandlingInfoResponseDto(
                soknadId = SOKNAD_ID.toString(),
                erFattet = false,
            ),
        forsendelseType = ForsendelseTypeTo.UTGÅENDE,
        status = ForsendelseStatusTo.UNDER_OPPRETTELSE,
    )

fun oppretteBehandling(
    id: Long? = null,
    vedtakstype: Vedtakstype = Vedtakstype.FASTSETTELSE,
): Behandling {
    return Behandling(
        vedtakstype,
        søktFomDato = YearMonth.parse("2022-02").atEndOfMonth(),
        datoTom = YearMonth.now().plusYears(100).atEndOfMonth(),
        mottattdato = LocalDate.parse("2023-03-15"),
        SAKSNUMMER,
        SOKNAD_ID,
        null,
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
}

fun opprettInntekter(
    behandling: Behandling,
    data: TestDataPerson,
) = mutableSetOf(
    Inntekt(
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
        BigDecimal.valueOf(45000),
        LocalDate.parse("2023-01-01"),
        LocalDate.parse("2023-12-31"),
        data.ident,
        Kilde.OFFENTLIG,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.LIGNINGSINNTEKT,
        BigDecimal.valueOf(33000),
        LocalDate.parse("2023-01-01"),
        LocalDate.parse("2023-12-31"),
        data.ident,
        Kilde.OFFENTLIG,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
        BigDecimal.valueOf(55000),
        LocalDate.parse("2022-01-01"),
        LocalDate.parse("2022-12-31"),
        data.ident,
        Kilde.MANUELL,
        true,
        behandling = behandling,
    ),
)

fun opprettInntektsposter(inntekt: Inntekt): MutableSet<Inntektspost> =
    setOf(
        Inntektspost(
            beløp = BigDecimal.valueOf(400000),
            kode = "lønnFraFluefiske",
            inntekt = inntekt,
            inntektstype = Inntektstype.NÆRINGSINNTEKT,
        ),
    ).toMutableSet()

fun opprettSivilstand(
    behandling: Behandling,
    datoFom: LocalDate = LocalDate.parse("2023-01-01"),
    datoTom: LocalDate? = null,
    sivilstand: Sivilstandskode = Sivilstandskode.BOR_ALENE_MED_BARN,
): Sivilstand {
    return Sivilstand(
        behandling = behandling,
        datoFom = datoFom,
        datoTom = datoTom,
        sivilstand = sivilstand,
        kilde = Kilde.OFFENTLIG,
    )
}

fun opprettRolle(
    behandling: Behandling,
    data: TestDataPerson,
    id: Long? = null,
): Rolle {
    return Rolle(
        id = id,
        navn = data.navn,
        ident = data.ident,
        rolletype = data.rolletype,
        behandling = behandling,
        foedselsdato = data.fødselsdato,
        opprettet = LocalDateTime.now(),
    )
}

fun oppretteRequestForOppdateringAvManuellInntekt(idInntekt: Long? = null) =
    OppdatereManuellInntekt(
        id = idInntekt,
        type = Inntektsrapportering.KONTANTSTØTTE,
        beløp = BigDecimal(305203),
        datoFom = LocalDate.now().minusYears(1).withDayOfYear(1),
        datoTom = LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31),
        ident = Personident("12345678910"),
        gjelderBarn = Personident("01234567891"),
    )

fun oppretteHusstandsmedlem(
    behandling: Behandling,
    data: TestDataPerson,
): Husstandsmedlem {
    val husstandsmedlem =
        Husstandsmedlem(
            navn = data.navn,
            ident = data.ident,
            kilde = Kilde.OFFENTLIG,
            behandling = behandling,
            fødselsdato = data.fødselsdato,
        )
    husstandsmedlem.perioder =
        mutableSetOf(
            Bostatusperiode(
                datoFom = LocalDate.parse("2022-01-01"),
                datoTom = LocalDate.parse("2022-12-31"),
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.MANUELL,
                husstandsmedlem = husstandsmedlem,
            ),
            Bostatusperiode(
                datoFom = LocalDate.parse("2023-01-01"),
                datoTom = LocalDate.parse("2023-05-31"),
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                husstandsmedlem = husstandsmedlem,
            ),
            Bostatusperiode(
                datoFom = LocalDate.parse("2023-06-01"),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                husstandsmedlem = husstandsmedlem,
            ),
        )
    return husstandsmedlem
}

fun oppretteBehandlingRoller(
    behandling: Behandling,
    generateId: Boolean = false,
    medBp: Boolean = false,
): MutableSet<Rolle> {
    val roller =
        mutableSetOf(
            Rolle(
                ident = testdataBM.ident,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = behandling,
                foedselsdato = testdataBM.fødselsdato,
                id = if (generateId) (1).toLong() else null,
            ),
            Rolle(
                ident = testdataBarn1.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                foedselsdato = testdataBarn1.fødselsdato,
                id = if (generateId) (2).toLong() else null,
            ),
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                foedselsdato = testdataBarn2.fødselsdato,
                id = if (generateId) (3).toLong() else null,
            ),
        )

    if (medBp) {
        roller.add(
            Rolle(
                ident = testdataBP.ident,
                rolletype = Rolletype.BIDRAGSPLIKTIG,
                behandling = behandling,
                foedselsdato = testdataBP.fødselsdato,
                id = if (generateId) (4).toLong() else null,
            ),
        )
    }
    return roller
}

fun opprettSakForBehandling(behandling: Behandling): BidragssakDto {
    return BidragssakDto(
        eierfogd = Enhetsnummer(behandling.behandlerEnhet),
        saksnummer = Saksnummer(behandling.saksnummer),
        saksstatus = Bidragssakstatus.IN,
        kategori = Sakskategori.N,
        opprettetDato = LocalDate.now(),
        levdeAdskilt = false,
        ukjentPart = false,
        roller =
            behandling.roller.map {
                RolleDto(
                    fødselsnummer = Personident(it.ident!!),
                    type = it.rolletype,
                )
            },
    )
}

fun opprettSakForBehandlingMedReelMottaker(behandling: Behandling): BidragssakDto {
    return BidragssakDto(
        eierfogd = Enhetsnummer(behandling.behandlerEnhet),
        saksnummer = Saksnummer(behandling.saksnummer),
        saksstatus = Bidragssakstatus.IN,
        kategori = Sakskategori.N,
        opprettetDato = LocalDate.now(),
        levdeAdskilt = false,
        ukjentPart = false,
        roller =
            behandling.roller.map {
                RolleDto(
                    fødselsnummer = Personident(it.ident!!),
                    reellMottager = if (it.ident == testdataBarn1.ident) ReellMottager("REEL_MOTTAKER") else null,
                    type = it.rolletype,
                )
            },
    )
}

fun opprettGyldigBehandlingForBeregningOgVedtak(
    generateId: Boolean = false,
    vedtakstype: Vedtakstype = Vedtakstype.FASTSETTELSE,
): Behandling {
    // given
    val behandling = oppretteBehandling(if (generateId) 1 else null, vedtakstype = vedtakstype)
    behandling.roller = oppretteBehandlingRoller(behandling, generateId)
    val husstandsmedlem =
        mutableSetOf(
            behandling.oppretteHusstandsmedlem(
                if (generateId) 1 else null,
                testdataBarn1.ident,
                testdataBarn1.navn,
                testdataBarn1.fødselsdato,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(5),
            ),
            behandling.oppretteHusstandsmedlem(
                if (generateId) 2 else null,
                testdataBarn2.ident,
                testdataBarn2.navn,
                testdataBarn2.fødselsdato,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(8),
            ),
            behandling.oppretteHusstandsmedlem(
                if (generateId) 3 else null,
                testdataHusstandsmedlem1.ident,
                testdataHusstandsmedlem1.navn,
                null,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(10),
            ),
        )
    val sivilstand =
        Sivilstand(
            sivilstand = Sivilstandskode.BOR_ALENE_MED_BARN,
            behandling = behandling,
            datoFom = behandling.søktFomDato,
            datoTom = null,
            kilde = Kilde.OFFENTLIG,
            id = if (generateId) (1).toLong() else null,
        )
    val inntekter =
        mutableSetOf(
            Inntekt(
                belop = BigDecimal(50000),
                datoFom = LocalDate.parse("2022-01-01"),
                datoTom = LocalDate.parse("2022-06-30"),
                ident = behandling.bidragsmottaker!!.ident!!,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                id = if (generateId) (1).toLong() else null,
            ),
            Inntekt(
                belop = BigDecimal(60000),
                datoFom = LocalDate.parse("2022-07-01"),
                datoTom = LocalDate.parse("2022-09-30"),
                ident = behandling.bidragsmottaker!!.ident!!,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                id = if (generateId) (2).toLong() else null,
            ),
        )

    val aInntekt =
        Inntekt(
            belop = BigDecimal(60000),
            datoFom = LocalDate.parse("2022-10-01"),
            datoTom = null,
            opprinneligFom = LocalDate.parse("2023-02-01"),
            opprinneligTom = LocalDate.parse("2024-01-01"),
            ident = behandling.bidragsmottaker!!.ident!!,
            taMed = true,
            kilde = Kilde.OFFENTLIG,
            behandling = behandling,
            type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
            id = if (generateId) (3).toLong() else null,
        )
    aInntekt.inntektsposter.addAll(opprettInntektsposter(aInntekt))
    val barnetillegg =
        Inntekt(
            belop = BigDecimal(60000),
            datoFom = LocalDate.parse("2022-01-01"),
            datoTom = null,
            opprinneligFom = LocalDate.parse("2023-01-01"),
            opprinneligTom = LocalDate.parse("2023-12-31"),
            ident = behandling.bidragsmottaker!!.ident!!,
            taMed = true,
            gjelderBarn = testdataBarn1.ident,
            kilde = Kilde.OFFENTLIG,
            behandling = behandling,
            type = Inntektsrapportering.BARNETILLEGG,
            id = if (generateId) (4).toLong() else null,
        )
    val barnInntekt =
        Inntekt(
            belop = BigDecimal(60000),
            datoFom = LocalDate.parse("2022-01-01"),
            datoTom = null,
            opprinneligFom = LocalDate.parse("2023-02-01"),
            opprinneligTom = LocalDate.parse("2024-01-01"),
            ident = testdataBarn1.ident,
            taMed = true,
            kilde = Kilde.OFFENTLIG,
            behandling = behandling,
            type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
            id = if (generateId) (5).toLong() else null,
        )
    inntekter.add(aInntekt)
    inntekter.add(barnetillegg)
    inntekter.add(barnInntekt)
    behandling.husstandsmedlem = husstandsmedlem
    behandling.inntekter = inntekter
    behandling.sivilstand = mutableSetOf(sivilstand)
    return behandling
}

fun Behandling.oppretteHusstandsmedlem(
    index: Int?,
    ident: String,
    navn: String?,
    fødselsdato: LocalDate?,
    førstePeriodeFra: LocalDate? = null,
    førstePeridoeTil: LocalDate? = null,
): Husstandsmedlem {
    val husstandsmedlem =
        Husstandsmedlem(
            behandling = this,
            kilde = Kilde.OFFENTLIG,
            ident = ident,
            navn = navn,
            fødselsdato = fødselsdato ?: LocalDate.parse("2020-01-01"),
            id = if (index != null) (index + 1).toLong() else null,
        )
    val førsteDatoTom =
        if (førstePeridoeTil != null) {
            YearMonth.from(
                førstePeridoeTil,
            ).atEndOfMonth()
        } else {
            YearMonth.from(søktFomDato.plusMonths(3)).atEndOfMonth()
        }
    husstandsmedlem.perioder =
        mutableSetOf(
            Bostatusperiode(
                husstandsmedlem = husstandsmedlem,
                datoFom = førstePeriodeFra ?: søktFomDato,
                datoTom = førsteDatoTom,
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                id = if (index != null) (index + 1).toLong() else null,
            ),
            Bostatusperiode(
                husstandsmedlem = husstandsmedlem,
                datoFom = YearMonth.from(førsteDatoTom).plusMonths(1).atDay(1),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                id = if (index != null) (index + 1).toLong() else null,
            ),
        )
    return husstandsmedlem
}

fun opprettAlleAktiveGrunnlagFraFil(
    behandling: Behandling,
    filnavn: String,
): MutableSet<Grunnlag> {
    return listOf(
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.BOFORHOLD),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.SIVILSTAND),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.ARBEIDSFORHOLD),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.BARNETILSYN),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.BARNETILLEGG),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.KONTANTSTØTTE),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.SMÅBARNSTILLEGG),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.UTVIDET_BARNETRYGD),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER),
        opprettBeregnetInntektFraGrunnlag(behandling, filnavn, testdataBM),
        opprettBeregnetInntektFraGrunnlag(behandling, filnavn, testdataBarn1),
        opprettBeregnetInntektFraGrunnlag(behandling, filnavn, testdataBarn2),
    ).flatten().toMutableSet()
}

fun opprettBeregnetInntektFraGrunnlag(
    behandling: Behandling,
    filnavn: String,
    testDataPerson: TestDataPerson,
): List<Grunnlag> {
    val fil = hentFil("/__files/$filnavn")
    val grunnlag: HentGrunnlagDto = commonObjectmapper.readValue(fil)
    val inntekterBearbeidet =
        InntektApi("").transformerInntekter(
            grunnlag.tilTransformerInntekterRequest(
                testDataPerson.tilRolle(behandling),
                LocalDate.parse("2024-02-10"),
            ),
        )
    return listOf(
        Grunnlag(
            behandling = behandling,
            type = Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
            erBearbeidet = true,
            aktiv = LocalDateTime.now(),
            data =
                commonObjectmapper.writeValueAsString(
                    POJONode(
                        SummerteInntekter(
                            versjon = inntekterBearbeidet.versjon,
                            inntekter = inntekterBearbeidet.summertMånedsinntektListe,
                        ),
                    ),
                ),
            innhentet = LocalDateTime.now(),
            rolle = testDataPerson.tilRolle(behandling),
        ),
        Grunnlag(
            behandling = behandling,
            type = Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
            erBearbeidet = true,
            aktiv = LocalDateTime.now(),
            data =
                commonObjectmapper.writeValueAsString(
                    POJONode(
                        SummerteInntekter(
                            versjon = inntekterBearbeidet.versjon,
                            inntekter = inntekterBearbeidet.summertÅrsinntektListe.ainntektListe + inntekterBearbeidet.summertÅrsinntektListe.skattegrunnlagListe,
                        ),
                    ),
                ),
            innhentet = LocalDateTime.now(),
            rolle = testDataPerson.tilRolle(behandling),
        ),
        inntekterBearbeidet.tilGrunnlag(
            Inntektsrapportering.KONTANTSTØTTE,
            testDataPerson,
            behandling,
        ),
        inntekterBearbeidet.tilGrunnlag(
            Inntektsrapportering.BARNETILLEGG,
            testDataPerson,
            behandling,
        ),
        inntekterBearbeidet.tilGrunnlag(
            Inntektsrapportering.BARNETILSYN,
            testDataPerson,
            behandling,
        ),
        inntekterBearbeidet.tilGrunnlag(
            Inntektsrapportering.SMÅBARNSTILLEGG,
            testDataPerson,
            behandling,
        ),
        inntekterBearbeidet.tilGrunnlag(
            Inntektsrapportering.UTVIDET_BARNETRYGD,
            testDataPerson,
            behandling,
        ),
    )
}

fun TransformerInntekterResponse.tilGrunnlag(
    type: Inntektsrapportering,
    person: TestDataPerson,
    behandling: Behandling,
) = Grunnlag(
    behandling = behandling,
    type = Grunnlagsdatatype.valueOf(type.name),
    erBearbeidet = true,
    aktiv = LocalDateTime.now(),
    data =
        commonObjectmapper.writeValueAsString(
            POJONode(
                SummerteInntekter(
                    versjon = versjon,
                    summertÅrsinntektListe.filter { it.inntektRapportering == type },
                ),
            ),
        ),
    innhentet = LocalDateTime.now(),
    rolle = person.tilRolle(behandling),
)

fun sjablonResponse(): List<Sjablontall> {
    val fil = hentFil("/__files/sjablon.json")
    return commonObjectmapper.readValue(fil)
}

fun hentFil(filsti: String) =
    TestdataManager::class.java.getResource(
        filsti,
    ) ?: throw RuntimeException("Fant ingen fil på sti $filsti")

fun tilAinntektspostDto(
    beløp: BigDecimal = BigDecimal(40000),
    fomDato: LocalDate,
    tilDato: LocalDate,
) = AinntektspostDto(
    belop = beløp,
    beskrivelse = "Ainntektspost",
    utbetalingsperiode = tilDato.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM")),
    opptjeningsperiodeFra = fomDato.withDayOfMonth(1),
    opptjeningsperiodeTil = tilDato.withDayOfMonth(1),
    etterbetalingsperiodeFra = null,
    etterbetalingsperiodeTil = null,
    fordelType = null,
    inntektType = "YTELSE_FRA_OFFENTLIGE",
    opplysningspliktigId = "995277670",
    virksomhetId = "995277670",
)

fun oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(husstandsmedlemSet: Set<Husstandsmedlem>): List<Grunnlag> {
    return husstandsmedlemSet.groupBy { it.ident }.map { (ident, husstandsmedlem) ->
        val behandling = husstandsmedlem.first().behandling!!
        Grunnlag(
            behandling = behandling,
            type = Grunnlagsdatatype.BOFORHOLD,
            erBearbeidet = true,
            gjelder = ident,
            aktiv = LocalDateTime.now(),
            rolle = behandling.bidragsmottaker!!,
            innhentet = LocalDateTime.now(),
            data =
                commonObjectmapper.writeValueAsString(
                    husstandsmedlem.flatMap { hb ->
                        hb.perioder.map {
                            BoforholdResponse(
                                relatertPersonPersonId = hb.ident,
                                periodeFom = it.datoFom!!,
                                periodeTom = it.datoTom,
                                kilde = it.kilde,
                                bostatus = it.bostatus,
                                fødselsdato = hb.fødselsdato,
                            )
                        }
                    },
                ),
        )
    }
}

fun oppretteHusstandsmedlemMedOffentligePerioder(behandling: Behandling): Set<Husstandsmedlem> {
    return setOf(
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
                )
            it
        },
        oppretteHusstandsmedlem(behandling, testdataBarn2).let {
            it.perioder =
                mutableSetOf(
                    Bostatusperiode(
                        datoFom = LocalDate.parse("2023-01-01"),
                        datoTom = LocalDate.parse("2023-10-31"),
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        husstandsmedlem = it,
                    ),
                    Bostatusperiode(
                        datoFom = LocalDate.parse("2023-11-01"),
                        datoTom = null,
                        bostatus = Bostatuskode.IKKE_MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        husstandsmedlem = it,
                    ),
                )
            it
        },
    )
}

fun opprettInntekt(
    datoFom: YearMonth? = null,
    datoTom: YearMonth? = null,
    opprinneligFom: YearMonth? = null,
    opprinneligTom: YearMonth? = null,
    type: Inntektsrapportering = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
    inntektstyper: List<Pair<Inntektstype, BigDecimal>> = emptyList(),
    inntektstyperKode: List<Pair<String, BigDecimal>> = emptyList(),
    ident: String = "",
    gjelderBarn: String? = null,
    taMed: Boolean = true,
    kilde: Kilde = Kilde.OFFENTLIG,
    beløp: BigDecimal = BigDecimal.ONE,
    behandling: Behandling = oppretteBehandling(),
): Inntekt {
    val inntekt =
        Inntekt(
            behandling = behandling,
            datoFom = datoFom?.atDay(1),
            datoTom = datoTom?.atEndOfMonth(),
            opprinneligFom = opprinneligFom?.atDay(1),
            opprinneligTom = opprinneligTom?.atEndOfMonth(),
            belop = beløp,
            ident = ident,
            gjelderBarn = gjelderBarn,
            id = Random.nextLong(1000),
            kilde = kilde,
            taMed = taMed,
            type = type,
        )

    inntekt.inntektsposter =
        (
            inntektstyper.map {
                Inntektspost(
                    inntekt = inntekt,
                    beløp = it.second,
                    inntektstype = it.first,
                    kode = "",
                )
            } +
                inntektstyperKode.map {
                    Inntektspost(
                        inntekt = inntekt,
                        beløp = it.second,
                        inntektstype = null,
                        kode = it.first,
                    )
                }
        ).toMutableSet()
    return inntekt
}
