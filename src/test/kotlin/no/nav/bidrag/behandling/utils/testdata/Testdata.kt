package no.nav.bidrag.behandling.utils.testdata

import com.fasterxml.jackson.databind.node.POJONode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.controller.v2.tilTransformerInntekterRequest
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
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereManuellInntekt
import no.nav.bidrag.commons.service.sjablon.Sjablontall
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
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.person.PersonDto
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val fødselsnummerBm = "33" + LocalDate.now().minusMonths(450).format(DateTimeFormatter.ofPattern("MMyy")) + "40405"
val fødselsnummerBp = "34" + LocalDate.now().minusMonths(476).format(DateTimeFormatter.ofPattern("MMyy")) + "72320"
val fødselsnummerBarn1 = "81" + LocalDate.now().minusMonths(88).format(DateTimeFormatter.ofPattern("MMyy")) + "28921"
val fødselsnummerBarn2 = "82" + LocalDate.now().minusMonths(45).format(DateTimeFormatter.ofPattern("MMyy")) + "36333"

data class TestDataPerson(
    val ident: String,
    val navn: String,
    val foedselsdato: LocalDate,
    val rolletype: Rolletype,
) {
    fun tilRolle(behandling: Behandling = oppretteBehandling()) =
        Rolle(
            id = behandling.roller.find { it.ident == ident }?.id ?: 1,
            ident = ident,
            navn = navn,
            foedselsdato = foedselsdato,
            rolletype = rolletype,
            opprettet = LocalDateTime.now(),
            behandling = behandling,
        )

    fun tilPersonDto() =
        PersonDto(
            ident = Personident(ident),
            navn = navn,
            foedselsdato = foedselsdato,
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
                        fødselsdato = foedselsdato,
                    ),
                ),
        )
}

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L

val ROLLE_BM = ForsendelseRolleDto(Personident(fødselsnummerBm), type = Rolletype.BIDRAGSMOTTAKER)
val ROLLE_BP = ForsendelseRolleDto(Personident(fødselsnummerBp), type = Rolletype.BIDRAGSPLIKTIG)
val ROLLE_BA_1 = ForsendelseRolleDto(Personident(fødselsnummerBarn1), type = Rolletype.BARN)
val ROLLE_BA_2 = ForsendelseRolleDto(Personident(fødselsnummerBarn2), type = Rolletype.BARN)
val testdataBP =
    TestDataPerson(
        navn = "Kor Mappe",
        ident = "213244124",
        rolletype = Rolletype.BIDRAGSPLIKTIG,
        foedselsdato = LocalDate.parse("2000-03-01"),
    )
val testdataBM =
    TestDataPerson(
        navn = "Oran Mappe",
        ident = "313213213",
        rolletype = Rolletype.BIDRAGSMOTTAKER,
        foedselsdato = LocalDate.parse("1978-08-25"),
    )

val testdataBarn1 =
    TestDataPerson(
        navn = "Kran Mappe",
        ident = "1344124",
        rolletype = Rolletype.BARN,
        foedselsdato = LocalDate.parse("2020-03-01"),
    )
val testdataBarn2 =
    TestDataPerson(
        navn = "Gran Mappe",
        ident = "54545454545",
        rolletype = Rolletype.BARN,
        foedselsdato = LocalDate.parse("2018-05-09"),
    )
val testdataHusstandsmedlem1 =
    TestDataPerson(
        navn = "Huststand Gapp",
        ident = "231323123123123123",
        rolletype = Rolletype.BARN,
        foedselsdato = LocalDate.parse("2001-05-09"),
    )

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

fun oppretteBehandling(id: Long? = null): Behandling {
    return Behandling(
        Vedtakstype.FASTSETTELSE,
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
            BigDecimal.valueOf(400000),
            "lønnFraFluefiske",
            "Lønn fra fluefiske",
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
        foedselsdato = data.foedselsdato,
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

fun opprettHusstandsbarn(
    behandling: Behandling,
    data: TestDataPerson,
): Husstandsbarn {
    val husstandsbarn =
        Husstandsbarn(
            navn = data.navn,
            ident = data.ident,
            medISaken = true,
            behandling = behandling,
            foedselsdato = data.foedselsdato,
        )
    husstandsbarn.perioder =
        mutableSetOf(
            Husstandsbarnperiode(
                datoFom = LocalDate.parse("2023-01-01"),
                datoTom = LocalDate.parse("2023-05-31"),
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                husstandsbarn = husstandsbarn,
            ),
            Husstandsbarnperiode(
                datoFom = LocalDate.parse("2023-05-31"),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                husstandsbarn = husstandsbarn,
            ),
        )
    return husstandsbarn
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
                foedselsdato = testdataBM.foedselsdato,
                id = if (generateId) (1).toLong() else null,
            ),
            Rolle(
                ident = testdataBarn1.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                foedselsdato = testdataBarn1.foedselsdato,
                id = if (generateId) (2).toLong() else null,
            ),
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                foedselsdato = testdataBarn2.foedselsdato,
                id = if (generateId) (3).toLong() else null,
            ),
        )

    if (medBp) {
        roller.add(
            Rolle(
                ident = testdataBP.ident,
                rolletype = Rolletype.BIDRAGSPLIKTIG,
                behandling = behandling,
                foedselsdato = testdataBP.foedselsdato,
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

fun opprettGyldigBehandlingForBeregningOgVedtak(generateId: Boolean = false): Behandling {
    // given
    val behandling = oppretteBehandling(if (generateId) 1 else null)
    behandling.roller = oppretteBehandlingRoller(behandling, generateId)
    val husstandsbarn =
        mutableSetOf(
            behandling.opprettHusstandsbarn(
                if (generateId) 1 else null,
                testdataBarn1.ident,
                testdataBarn1.navn,
                testdataBarn1.foedselsdato,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(20),
            ),
            behandling.opprettHusstandsbarn(
                if (generateId) 2 else null,
                testdataBarn2.ident,
                testdataBarn2.navn,
                testdataBarn2.foedselsdato,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(18),
            ),
            behandling.opprettHusstandsbarn(
                if (generateId) 3 else null,
                testdataHusstandsmedlem1.ident,
                testdataHusstandsmedlem1.navn,
                null,
                behandling.virkningstidspunkt!!.plusMonths(8),
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
                datoTom = LocalDate.parse("2022-06-30"),
                datoFom = LocalDate.parse("2022-01-01"),
                ident = behandling.bidragsmottaker!!.ident!!,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                id = if (generateId) (1).toLong() else null,
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
                id = if (generateId) (2).toLong() else null,
            ),
        )

    val aInntekt =
        Inntekt(
            belop = BigDecimal(60000),
            datoFom = LocalDate.parse("2022-01-01"),
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
            inntektsrapportering = Inntektsrapportering.BARNETILLEGG,
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
            inntektsrapportering = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
            id = if (generateId) (5).toLong() else null,
        )
    inntekter.add(aInntekt)
    inntekter.add(barnetillegg)
    inntekter.add(barnInntekt)
    behandling.husstandsbarn = husstandsbarn
    behandling.inntekter = inntekter
    behandling.sivilstand = mutableSetOf(sivilstand)
    return behandling
}

fun Behandling.opprettHusstandsbarn(
    index: Int?,
    ident: String,
    navn: String?,
    fødselsdato: LocalDate?,
    førstePeriodeFra: LocalDate? = null,
    førstePeridoeTil: LocalDate? = null,
): Husstandsbarn {
    val husstandsbarn =
        Husstandsbarn(
            behandling = this,
            medISaken = true,
            ident = ident,
            navn = navn,
            foedselsdato = fødselsdato ?: LocalDate.parse("2020-01-01"),
            id = if (index != null) (index + 1).toLong() else null,
        )
    husstandsbarn.perioder =
        mutableSetOf(
            Husstandsbarnperiode(
                husstandsbarn = husstandsbarn,
                datoFom = førstePeriodeFra ?: søktFomDato,
                datoTom = førstePeridoeTil ?: søktFomDato.plusMonths(3),
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                id = if (index != null) (index + 1).toLong() else null,
            ),
            Husstandsbarnperiode(
                husstandsbarn = husstandsbarn,
                datoFom = førstePeridoeTil ?: søktFomDato.plusMonths(3),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                id = if (index != null) (index + 1).toLong() else null,
            ),
        )
    return husstandsbarn
}

fun opprettAlleAktiveGrunnlagFraFil(
    behandling: Behandling,
    filnavn: String,
): List<BehandlingGrunnlag> {
    return listOf(
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.HUSSTANDSMEDLEMMER),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.SIVILSTAND),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.ARBEIDSFORHOLD),
        opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.INNTEKT),
        opprettBeregnetInntektFraGrunnlag(behandling, filnavn),
    )
}

fun opprettInntektBearbeidet(
    testDataPerson: TestDataPerson,
    grunnlag: HentGrunnlagDto,
    behandling: Behandling,
): InntektBearbeidet {
    val inntekt =
        InntektApi("").transformerInntekter(
            grunnlag.tilTransformerInntekterRequest(
                testDataPerson.tilRolle(behandling),
                behandling.roller,
            ),
        )

    return InntektBearbeidet(
        ident = testDataPerson.ident,
        versjon = "1",
        summertAarsinntektListe = inntekt.summertÅrsinntektListe,
        summertMånedsinntektListe = inntekt.summertMånedsinntektListe,
    )
}

fun opprettBeregnetInntektFraGrunnlag(
    behandling: Behandling,
    filnavn: String,
): BehandlingGrunnlag {
    val fil = hentFil("/__files/$filnavn")
    val grunnlag: HentGrunnlagDto = commonObjectmapper.readValue(fil)
    val innteksopplynsingerBearbeidet =
        InntektsopplysningerBearbeidet(
            inntekt =
                listOf(
                    opprettInntektBearbeidet(testdataBM, grunnlag, behandling),
                    opprettInntektBearbeidet(testdataBarn1, grunnlag, behandling),
                    opprettInntektBearbeidet(testdataBarn2, grunnlag, behandling),
                ),
            arbeidsforhold = grunnlag.arbeidsforholdListe,
            barnetillegg = grunnlag.barnetilleggListe,
        )
    return BehandlingGrunnlag(
        behandling = behandling,
        type = Grunnlagsdatatype.INNTEKT_BEARBEIDET,
        data = commonObjectmapper.writeValueAsString(POJONode(innteksopplynsingerBearbeidet)),
        innhentet = LocalDateTime.now(),
    )
}

fun opprettBeregnetInntektFraFil(
    behandling: Behandling,
    filnavn: String,
): BehandlingGrunnlag {
    val fil = hentFil("/__files/$filnavn")
    return BehandlingGrunnlag(
        behandling = behandling,
        type = Grunnlagsdatatype.INNTEKT_BEARBEIDET,
        data = fil.readText(),
        innhentet = LocalDateTime.now(),
    )
}

fun opprettGrunnlagFraFil(
    behandling: Behandling,
    filnavn: String,
    type: Grunnlagsdatatype,
): BehandlingGrunnlag {
    val fil = hentFil("/__files/$filnavn")
    val grunnlag: HentGrunnlagDto = commonObjectmapper.readValue(fil)

    val data =
        when (type) {
            Grunnlagsdatatype.HUSSTANDSMEDLEMMER -> commonObjectmapper.writeValueAsString(grunnlag.husstandsmedlemmerOgEgneBarnListe)
            Grunnlagsdatatype.SIVILSTAND -> commonObjectmapper.writeValueAsString(grunnlag.sivilstandListe)
            Grunnlagsdatatype.ARBEIDSFORHOLD -> commonObjectmapper.writeValueAsString(grunnlag.arbeidsforholdListe)
            // Inntekter er en subset av grunnlag så lagrer bare alt
            Grunnlagsdatatype.INNTEKT -> commonObjectmapper.writeValueAsString(grunnlag)
            else -> "{}"
        }
    return BehandlingGrunnlag(
        behandling = behandling,
        type = type,
        data = data,
        innhentet = LocalDateTime.now(),
    )
}

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
