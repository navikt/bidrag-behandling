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
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.transformers.behandling.henteRolleForNotat
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdVoksneRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatusperiode
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsmedlem
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.behandling.transformers.grunnlag.ainntektListe
import no.nav.bidrag.behandling.transformers.grunnlag.skattegrunnlagListe
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.commons.service.sjablon.Sjablontall
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.sak.Bidragssakstatus
import no.nav.bidrag.domene.enums.sak.Sakskategori
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.ident.ReellMottager
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.dto.SivilstandRequest
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektspostDto
import no.nav.bidrag.transport.behandling.grunnlag.response.Ansettelsesdetaljer
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
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

val voksenPersonIBpsHusstand =
    Testperson(navn = "Gillinger Owa", personident = "01010012345", fødselsdato = LocalDate.of(2000, 1, 1))

data class Testperson(
    val personident: String,
    val navn: String,
    val fødselsdato: LocalDate,
    val rolletype: Rolletype? = null,
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
            fødselsdato = fødselsdato,
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
    virkningstidspunkt: LocalDate = LocalDate.parse("2023-02-01"),
): Behandling =
    Behandling(
        vedtakstype,
        null,
        søktFomDato = YearMonth.parse("2022-02").atEndOfMonth(),
        datoTom = YearMonth.now().plusYears(100).atEndOfMonth(),
        mottattdato = LocalDate.parse("2023-03-15"),
        klageMottattdato = null,
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
        virkningstidspunkt = virkningstidspunkt,
        id = id,
    )

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
): Sivilstand =
    Sivilstand(
        behandling = behandling,
        datoFom = datoFom,
        datoTom = datoTom,
        sivilstand = sivilstand,
        kilde = Kilde.OFFENTLIG,
    )

fun opprettRolle(
    behandling: Behandling,
    data: TestDataPerson,
    id: Long? = null,
): Rolle =
    Rolle(
        id = id,
        navn = data.navn,
        ident = data.ident,
        rolletype = data.rolletype,
        behandling = behandling,
        fødselsdato = data.fødselsdato,
        opprettet = LocalDateTime.now(),
    )

fun oppretteRequestForOppdateringAvManuellInntekt(idInntekt: Long? = null) =
    OppdatereManuellInntekt(
        id = idInntekt,
        type = Inntektsrapportering.KONTANTSTØTTE,
        beløp = BigDecimal(305203),
        datoFom = LocalDate.now().minusYears(1).withDayOfYear(1),
        datoTom =
        LocalDate
            .now()
            .minusYears(1)
            .withMonth(12)
            .withDayOfMonth(31),
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
    typeBehandling: TypeBehandling = TypeBehandling.FORSKUDD,
): MutableSet<Rolle> {
    val roller =
        mutableSetOf(
            Rolle(
                ident = testdataBM.ident,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = behandling,
                fødselsdato = testdataBM.fødselsdato,
                id = if (generateId) (1).toLong() else null,
            ),
            Rolle(
                ident = testdataBarn1.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn1.fødselsdato,
                id = if (generateId) (2).toLong() else null,
            ),
        )

    if (typeBehandling != TypeBehandling.SÆRBIDRAG) {
        roller.add(
            Rolle(
                ident = testdataBarn2.ident,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                fødselsdato = testdataBarn2.fødselsdato,
                id = if (generateId) (3).toLong() else null,
            ),
        )
    }
    if (medBp) {
        roller.add(
            Rolle(
                ident = testdataBP.ident,
                rolletype = Rolletype.BIDRAGSPLIKTIG,
                behandling = behandling,
                fødselsdato = testdataBP.fødselsdato,
                id = if (generateId) (4).toLong() else null,
            ),
        )
    }
    return roller
}

fun opprettSakForBehandling(behandling: Behandling): BidragssakDto =
    BidragssakDto(
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

fun opprettSakForBehandlingMedReelMottaker(behandling: Behandling): BidragssakDto =
    BidragssakDto(
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

fun opprettGyldigBehandlingForBeregningOgVedtak(
    generateId: Boolean = false,
    vedtakstype: Vedtakstype = Vedtakstype.FASTSETTELSE,
    typeBehandling: TypeBehandling = TypeBehandling.FORSKUDD,
): Behandling {
    // given
    val behandling =
        oppretteBehandling(
            if (generateId) 1 else null,
            vedtakstype = vedtakstype,
            virkningstidspunkt =
            when (typeBehandling) {
                TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG -> LocalDate.parse("2023-02-01")
                TypeBehandling.SÆRBIDRAG -> LocalDate.now().withDayOfMonth(1)
            },
        )
    behandling.innkrevingstype = Innkrevingstype.MED_INNKREVING
    behandling.roller =
        oppretteBehandlingRoller(behandling, generateId, typeBehandling != TypeBehandling.FORSKUDD, typeBehandling)
    val husstandsmedlem =
        mutableSetOf(
            behandling.oppretteHusstandsmedlem(
                if (generateId) 1 else null,
                testdataBarn1.ident,
                testdataBarn1.navn,
                testdataBarn1.fødselsdato,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(5),
                typeBehandling = typeBehandling,
            ),
            behandling.oppretteHusstandsmedlem(
                if (generateId) 3 else null,
                testdataHusstandsmedlem1.ident,
                testdataHusstandsmedlem1.navn,
                null,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(10),
                typeBehandling = typeBehandling,
            ),
        )

    when (typeBehandling) {
        TypeBehandling.FORSKUDD -> {
            husstandsmedlem.add(
                behandling.oppretteHusstandsmedlem(
                    if (generateId) 2 else null,
                    testdataBarn2.ident,
                    testdataBarn2.navn,
                    testdataBarn2.fødselsdato,
                    behandling.virkningstidspunkt,
                    behandling.virkningstidspunkt!!.plusMonths(8),
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
            behandling.sivilstand = mutableSetOf(sivilstand)
            val inntekterBm =
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
            inntekterBm.add(aInntekt)
            inntekterBm.add(barnetillegg)
            inntekterBm.add(barnInntekt)
            behandling.inntekter.addAll(inntekterBm)
        }

        TypeBehandling.SÆRBIDRAG -> {
            behandling.stonadstype = null
            behandling.årsak = null
            behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
            behandling.kategori = Særbidragskategori.KONFIRMASJON.name
            behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name, generateId)
            husstandsmedlem.add(
                behandling.oppretteHusstandsmedlem(
                    if (generateId) 2 else null,
                    testdataBarn2.ident,
                    testdataBarn2.navn,
                    testdataBarn2.fødselsdato,
                    behandling.virkningstidspunkt,
                    behandling.virkningstidspunkt!!.plusMonths(8),
                    behandling.bidragspliktig,
                    true,
                    typeBehandling = typeBehandling,
                ),
            )
            val inntekterBp =
                mutableSetOf(
                    Inntekt(
                        belop = BigDecimal(500000),
                        datoFom = behandling.virkningstidspunkt,
                        datoTom = null,
                        ident = behandling.bidragspliktig!!.ident!!,
                        taMed = true,
                        kilde = Kilde.MANUELL,
                        behandling = behandling,
                        type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                        id = if (generateId) (1).toLong() else null,
                    ),
                )
            val inntekterBm =
                mutableSetOf(
                    Inntekt(
                        belop = BigDecimal(50000),
                        datoFom = behandling.virkningstidspunkt,
                        datoTom = null,
                        ident = behandling.bidragsmottaker!!.ident!!,
                        taMed = true,
                        kilde = Kilde.MANUELL,
                        behandling = behandling,
                        type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                        id = if (generateId) (1).toLong() else null,
                    ),
                )

            behandling.inntekter.addAll(inntekterBp)
            behandling.inntekter.addAll(inntekterBm)
        }

        else -> {}
    }

    behandling.husstandsmedlem = husstandsmedlem

    return behandling
}

fun Behandling.oppretteHusstandsmedlem(
    index: Int?,
    ident: String,
    navn: String?,
    fødselsdato: LocalDate?,
    førstePeriodeFra: LocalDate? = null,
    førstePeridoeTil: LocalDate? = null,
    rolle: Rolle? = null,
    andreVoksneIHusstanden: Boolean = false,
    typeBehandling: TypeBehandling = TypeBehandling.FORSKUDD,
): Husstandsmedlem {
    val husstandsmedlem =
        Husstandsmedlem(
            behandling = this,
            kilde = Kilde.OFFENTLIG,
            ident = ident,
            navn = navn,
            fødselsdato = fødselsdato ?: LocalDate.parse("2020-01-01"),
            id = if (index != null) (index + 1).toLong() else null,
            rolle = rolle,
        )
    val førsteDatoTom =
        if (førstePeridoeTil != null) {
            YearMonth
                .from(
                    førstePeridoeTil,
                ).atEndOfMonth()
        } else {
            YearMonth.from(søktFomDato.plusMonths(3)).atEndOfMonth()
        }

    when (typeBehandling) {
        TypeBehandling.SÆRBIDRAG -> {
            husstandsmedlem.perioder =
                mutableSetOf(
                    Bostatusperiode(
                        husstandsmedlem = husstandsmedlem,
                        datoFom = førstePeriodeFra ?: søktFomDato,
                        datoTom = null,
                        bostatus = if (andreVoksneIHusstanden) Bostatuskode.BOR_MED_ANDRE_VOKSNE else Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = if (index != null) (index + 1).toLong() else null,
                    ),
                )
        }

        else -> {
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
        }
    }

    return husstandsmedlem
}

fun opprettAlleAktiveGrunnlagFraFil(
    behandling: Behandling,
    filnavn: String,
): MutableSet<Grunnlag> {
    val grunnlagListe =
        listOf(
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.BOFORHOLD),
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.ARBEIDSFORHOLD),
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.BARNETILSYN),
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.BARNETILLEGG),
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.KONTANTSTØTTE),
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.SMÅBARNSTILLEGG),
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.UTVIDET_BARNETRYGD),
            opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER),
            opprettBeregnetInntektFraGrunnlag(behandling, filnavn, testdataBM),
            opprettBeregnetInntektFraGrunnlag(behandling, filnavn, testdataBarn1),
        ).flatten().toMutableSet()
    when (behandling.tilType()) {
        TypeBehandling.FORSKUDD -> {
            grunnlagListe.addAll(
                listOf(
                    opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.SIVILSTAND),
                    opprettBeregnetInntektFraGrunnlag(behandling, filnavn, testdataBarn2),
                ).flatten(),
            )
        }

        else -> {
            grunnlagListe.addAll(
                listOf(
                    opprettBeregnetInntektFraGrunnlag(behandling, filnavn, testdataBP),
                    opprettGrunnlagFraFil(behandling, filnavn, Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN),
                ).flatten(),
            )
        }
    }
    return grunnlagListe
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
                        inntekter =
                        inntekterBearbeidet.summertÅrsinntektListe.ainntektListe +
                                inntekterBearbeidet.summertÅrsinntektListe.skattegrunnlagListe,
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

fun oppretteBoforholdBearbeidetGrunnlagForhusstandsmedlem(husstandsmedlemSet: Set<Husstandsmedlem>): List<Grunnlag> =
    husstandsmedlemSet.groupBy { it.ident }.map { (ident, husstandsmedlem) ->
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
                        BoforholdResponseV2(
                            gjelderPersonId = hb.ident,
                            periodeFom = it.datoFom!!,
                            periodeTom = it.datoTom,
                            kilde = it.kilde,
                            bostatus = it.bostatus,
                            fødselsdato = hb.fødselsdato!!,
                        )
                    }
                },
            ),
        )
    }

fun oppretteHusstandsmedlemMedOffentligePerioder(behandling: Behandling): Set<Husstandsmedlem> =
    setOf(
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
    medId: Boolean = true,
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
            id = if (medId) Random.nextLong(1000) else null,
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

fun oppretteUtgift(
    behandling: Behandling,
    utgiftstype: String,
    medId: Boolean = false,
): Utgift {
    val utgift =
        Utgift(
            id = if (medId) 1 else null,
            behandling = behandling,
            beløpDirekteBetaltAvBp = BigDecimal(0),
        )
    utgift.utgiftsposter =
        mutableSetOf(
            Utgiftspost(
                id = if (medId) 1 else null,
                dato = LocalDate.now().minusDays(3),
                type = utgiftstype,
                kravbeløp = BigDecimal(3000),
                godkjentBeløp = BigDecimal(2500),
                kommentar = "Trekker fra alkohol",
                utgift = utgift,
            ),
        )
    return utgift
}

fun oppretteBehandling(
    inkludereInntekter: Boolean = false,
    inkludereSivilstand: Boolean = true,
    inkludereBoforhold: Boolean = true,
    inkludereBp: Boolean = false,
    behandlingstype: TypeBehandling = TypeBehandling.FORSKUDD,
    inkludereVoksneIBpsHusstand: Boolean = false,
    setteDatabaseider: Boolean = false,
    inkludereArbeidsforhold: Boolean = false,
): Behandling {
    val behandlingsid = if (setteDatabaseider) 1L else null
    val behandling = oppretteBehandling(behandlingsid)

    when (behandlingstype) {
        TypeBehandling.FORSKUDD -> {
            behandling.stonadstype = Stønadstype.FORSKUDD
        }

        TypeBehandling.SÆRBIDRAG -> {
            behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
            behandling.kategori = Særbidragskategori.KONFIRMASJON.name
            behandling.stonadstype = null
            behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        }

        else -> throw IllegalStateException("Behandlingstype $behandlingstype er foreløpig ikke støttet")
    }

    behandling.virkningstidspunktbegrunnelseKunINotat = "notat virkning"

    val parterIBehandlingen = hashMapOf(1 to testdataBM, 2 to testdataBarn1, 3 to testdataBarn2)

    parterIBehandlingen.forEach {
        val dbid = if (setteDatabaseider) it.key.toLong() else null
        behandling.roller.add(opprettRolle(behandling, it.value, dbid))
    }

    if (inkludereBp) {
        val dbid = if (setteDatabaseider) 4.toLong() else null
        val rolleBp = opprettRolle(behandling, testdataBP, dbid)
        behandling.roller.add(rolleBp)
    }

    if (inkludereBoforhold) {
        oppretteBoforhold(behandling, inkludereVoksneIBpsHusstand)
    }

    if (inkludereSivilstand) {
        oppretteSivilstand(behandling)
    }

    if (inkludereInntekter) {
        behandling.inntekter = opprettInntekter(behandling, testdataBM)
        behandling.inntekter.forEach {
            it.inntektsposter = opprettInntektsposter(it)
        }
    }

    if (inkludereArbeidsforhold) {
        oppretteArbeidsforhold(behandling, behandling.rolleGrunnlagSkalHentesFor!!.ident!!)
    }

    return behandling
}

fun oppretteArbeidsforhold(personident: String): ArbeidsforholdGrunnlagDto =
    ArbeidsforholdGrunnlagDto(
        arbeidsgiverNavn = "Liv og røre",
        arbeidsgiverOrgnummer = "001122445577",
        startdato = LocalDate.now().minusMonths(144),
        sluttdato = null,
        partPersonId = personident,
        permitteringListe = emptyList(),
        permisjonListe = emptyList(),
        ansettelsesdetaljerListe =
        listOf(
            Ansettelsesdetaljer(
                periodeFra = YearMonth.now().minusMonths(144),
                periodeTil = null,
                ansettelsesformBeskrivelse = "Fast ansatt",
                antallTimerPrUke = 40.0,
                arbeidsforholdType = "Ordinaer",
                avtaltStillingsprosent = 100.0,
                arbeidstidsordningBeskrivelse = "Ikke skift",
                sisteLønnsendringDato = LocalDate.now().minusMonths(10).withMonth(1),
                sisteStillingsprosentendringDato = LocalDate.now().minusMonths(144),
                yrkeBeskrivelse = "Snekker",
            ),
        ),
    )

fun oppretteArbeidsforhold(
    behandling: Behandling,
    personident: String,
) {
    val arbeidsforhold = oppretteArbeidsforhold(personident)

    behandling.grunnlag.add(
        Grunnlag(
            aktiv = LocalDateTime.now(),
            behandling = behandling,
            innhentet = LocalDateTime.now().minusDays(3),
            data = commonObjectmapper.writeValueAsString(setOf(arbeidsforhold)),
            rolle = behandling.rolleGrunnlagSkalHentesFor!!,
            type = Grunnlagsdatatype.ARBEIDSFORHOLD,
            erBearbeidet = false,
        ),
    )
}

private fun oppretteBoforhold(
    behandling: Behandling,
    inkludereVoksneIBpsHusstand: Boolean,
) {
    val husstandsmedlem1 = oppretteHusstandsmedlem(behandling, testdataBarn1)
    husstandsmedlem1.perioder.clear()

    val husstandsmedlem2 = oppretteHusstandsmedlem(behandling, testdataBarn2)
    husstandsmedlem2.perioder.clear()

    val grunnlagHusstandsmedlemmer =
        mutableSetOf(
            RelatertPersonGrunnlagDto(
                relatertPersonPersonId = testdataBarn1.ident,
                fødselsdato = testdataBarn1.fødselsdato,
                erBarnAvBmBp = true,
                relasjon = Familierelasjon.BARN,
                navn = "Lyrisk Sopp",
                partPersonId = behandling.rolleGrunnlagSkalHentesFor!!.ident,
                borISammeHusstandDtoListe =
                listOf(
                    BorISammeHusstandDto(
                        periodeFra = LocalDate.parse("2023-01-01"),
                        periodeTil = LocalDate.parse("2023-05-31"),
                    ),
                ),
            ),
            RelatertPersonGrunnlagDto(
                relatertPersonPersonId = testdataBarn2.ident,
                fødselsdato = testdataBarn2.fødselsdato,
                erBarnAvBmBp = true,
                relasjon = Familierelasjon.BARN,
                navn = "Lyrisk Sopp",
                partPersonId = behandling.rolleGrunnlagSkalHentesFor!!.ident,
                borISammeHusstandDtoListe =
                listOf(
                    BorISammeHusstandDto(
                        periodeFra = LocalDate.parse("2023-01-01"),
                        periodeTil = LocalDate.parse("2023-05-31"),
                    ),
                ),
            ),
        )

    if (TypeBehandling.SÆRBIDRAG == behandling.tilType() && inkludereVoksneIBpsHusstand) {
        grunnlagHusstandsmedlemmer.add(
            RelatertPersonGrunnlagDto(
                relatertPersonPersonId = voksenPersonIBpsHusstand.personident,
                fødselsdato = voksenPersonIBpsHusstand.fødselsdato,
                erBarnAvBmBp = false,
                relasjon = Familierelasjon.INGEN,
                navn = voksenPersonIBpsHusstand.navn,
                partPersonId = behandling.rolleGrunnlagSkalHentesFor!!.ident,
                borISammeHusstandDtoListe =
                listOf(
                    BorISammeHusstandDto(
                        periodeFra = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(2).withDayOfMonth(1),
                        periodeTil =
                        behandling.virkningstidspunktEllerSøktFomDato
                            .plusMonths(6)
                            .withDayOfMonth(1)
                            .minusDays(1),
                    ),
                ),
            ),
        )

        behandling.grunnlag.add(
            Grunnlag(
                aktiv = LocalDateTime.now(),
                behandling = behandling,
                innhentet = LocalDateTime.now().minusDays(3),
                data = commonObjectmapper.writeValueAsString(grunnlagHusstandsmedlemmer),
                rolle = behandling.rolleGrunnlagSkalHentesFor!!,
                type = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                erBearbeidet = false,
            ),
        )

        val periodisertVoksneIBpsHusstand =
            BoforholdApi.beregnBoforholdAndreVoksne(
                behandling.virkningstidspunktEllerSøktFomDato,
                grunnlagHusstandsmedlemmer.tilBoforholdVoksneRequest(),
            )

        behandling.grunnlag.add(
            Grunnlag(
                aktiv = LocalDateTime.now(),
                behandling = behandling,
                innhentet = LocalDateTime.now().minusDays(3),
                data = commonObjectmapper.writeValueAsString(periodisertVoksneIBpsHusstand),
                rolle = behandling.bidragspliktig!!,
                type = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                gjelder = behandling.bidragspliktig!!.ident,
                erBearbeidet = true,
            ),
        )
    }

    behandling.grunnlag.add(
        Grunnlag(
            aktiv = LocalDateTime.now(),
            behandling = behandling,
            innhentet = LocalDateTime.now().minusDays(3),
            data = commonObjectmapper.writeValueAsString(grunnlagHusstandsmedlemmer),
            rolle = behandling.rolleGrunnlagSkalHentesFor!!,
            type = Grunnlagsdatatype.BOFORHOLD,
            erBearbeidet = false,
        ),
    )

    val boforholdPeriodisert =
        BoforholdApi.beregnBoforholdBarnV3(
            behandling.virkningstidspunktEllerSøktFomDato,
            behandling.tilType(),
            grunnlagHusstandsmedlemmer.tilBoforholdBarnRequest(behandling),
        )

    boforholdPeriodisert
        .filter { it.gjelderPersonId != null }
        .groupBy { it.gjelderPersonId }
        .forEach {
            behandling.grunnlag.add(
                Grunnlag(
                    aktiv = LocalDateTime.now(),
                    behandling = behandling,
                    innhentet = LocalDateTime.now().minusDays(3),
                    data = commonObjectmapper.writeValueAsString(it.value),
                    rolle = behandling.rolleGrunnlagSkalHentesFor!!,
                    type = Grunnlagsdatatype.BOFORHOLD,
                    gjelder = it.key,
                    erBearbeidet = true,
                ),
            )
        }

    behandling.husstandsmedlem.addAll(boforholdPeriodisert.tilHusstandsmedlem(behandling))

    if (TypeBehandling.SÆRBIDRAG == behandling.tilType() && inkludereVoksneIBpsHusstand) {
        leggeTilAndreVoksneIBpsHusstand(behandling, grunnlagHusstandsmedlemmer)
    }
}

private fun leggeTilAndreVoksneIBpsHusstand(
    behandling: Behandling,
    grunnlag: Set<RelatertPersonGrunnlagDto>,
) {
    val husstandsmedlemBp =
        Husstandsmedlem(behandling = behandling, kilde = Kilde.OFFENTLIG, rolle = behandling.bidragspliktig)

    val andreVoksneIBpsHusstand =
        BoforholdApi.beregnBoforholdAndreVoksne(
            behandling.virkningstidspunktEllerSøktFomDato,
            grunnlag.tilBoforholdVoksneRequest(),
        )

    husstandsmedlemBp.perioder.addAll(andreVoksneIBpsHusstand.toSet().tilBostatusperiode(husstandsmedlemBp))
    behandling.husstandsmedlem.add(husstandsmedlemBp)
}

private fun oppretteSivilstand(behandling: Behandling) {
    val sivilstandshistorikk =
        listOf(
            SivilstandGrunnlagDto(
                bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8),
                gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8),
                historisk = true,
                master = "Freg",
                personId = behandling.bidragsmottaker!!.ident!!,
                registrert = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8).atStartOfDay(),
                type = SivilstandskodePDL.UGIFT,
            ),
            SivilstandGrunnlagDto(
                bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.minusYears(5),
                gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.minusYears(5),
                historisk = true,
                master = "Freg",
                personId = behandling.bidragsmottaker!!.ident!!,
                registrert = behandling.virkningstidspunktEllerSøktFomDato.minusYears(5).atStartOfDay(),
                type = SivilstandskodePDL.GIFT,
            ),
            SivilstandGrunnlagDto(
                bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(3),
                gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(3),
                historisk = false,
                master = "Freg",
                personId = behandling.bidragsmottaker!!.ident!!,
                registrert = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(9).atStartOfDay(),
                type = SivilstandskodePDL.SKILT,
            ),
        )

    val periodiseringsrequest =
        SivilstandRequest(
            behandledeSivilstandsopplysninger = emptyList(),
            endreSivilstand = null,
            innhentedeOffentligeOpplysninger = sivilstandshistorikk,
            fødselsdatoBM = behandling.bidragsmottaker!!.fødselsdato,
        )

    val periodisertHistorikk =
        SivilstandApi.beregnV2(behandling.virkningstidspunktEllerSøktFomDato, periodiseringsrequest)

    behandling.grunnlag.add(
        Grunnlag(
            aktiv = LocalDateTime.now().minusDays(5),
            behandling = behandling,
            data = commonObjectmapper.writeValueAsString(sivilstandshistorikk),
            erBearbeidet = false,
            innhentet = LocalDateTime.now().minusDays(5),
            rolle = behandling.bidragsmottaker!!,
            type = Grunnlagsdatatype.SIVILSTAND,
        ),
    )

    behandling.grunnlag.add(
        Grunnlag(
            aktiv = LocalDateTime.now().minusDays(5),
            behandling = behandling,
            data = commonObjectmapper.writeValueAsString(periodisertHistorikk),
            erBearbeidet = true,
            innhentet = LocalDateTime.now().minusDays(5),
            rolle = behandling.bidragsmottaker!!,
            type = Grunnlagsdatatype.SIVILSTAND,
        ),
    )

    behandling.sivilstand.addAll(periodisertHistorikk.toSet().tilSivilstand(behandling))
}

fun lagGrunnlagsdata(
    filnavn: String,
    virkningstidspunkt: YearMonth,
    gjelderIdent: String,
    barnIdent: String = testdataBarn1.ident,
    barnIdent2: String = testdataBarn2.ident,
    hustandsmedlem1: String = testdataHusstandsmedlem1.ident,
): HentGrunnlagDto {
    val fil = hentFil("/__files/$filnavn")
    var stringValue = fil.readText().replace("{personId}", gjelderIdent)
    stringValue = stringValue.replace("{barnId}", barnIdent)
    stringValue = stringValue.replace("{barnId2}", barnIdent2)
    stringValue = stringValue.replace("{barnId2}", barnIdent2)
    stringValue = stringValue.replace("{hustandsmedlem1}", hustandsmedlem1)
    stringValue = stringValue.replace("{dagens_dato}", LocalDateTime.now().toString())
    (0..24).forEach {
        stringValue =
            stringValue.replace(
                "{virkningstidspunkt-minus-${it}m}",
                virkningstidspunkt.atDay(1).minusMonths(it.toLong()).toString(),
            )
        stringValue =
            stringValue.replace(
                "{virkningstidspunkt-minus-${it}m-ym}",
                virkningstidspunkt.minusMonths(it.toLong()).toString(),
            )
        stringValue =
            stringValue.replace(
                "{virkningstidspunkt-minus-${it}y-januar}",
                virkningstidspunkt
                    .atDay(1)
                    .minusYears(it.toLong())
                    .withMonth(1)
                    .toString(),
            )
        stringValue =
            stringValue.replace(
                "{virkningstidspunkt-minus-${it}y-januar-ym}",
                virkningstidspunkt.minusYears(it.toLong()).withMonth(1).toString(),
            )

        stringValue =
            stringValue.replace(
                "{virkningstidspunkt-plus-${it}m-ym}",
                virkningstidspunkt.plusMonths(it.toLong()).toString(),
            )
        stringValue =
            stringValue.replace(
                "{virkningstidspunkt-plus-${it}m}",
                virkningstidspunkt.atDay(1).plusMonths(it.toLong()).toString(),
            )
    }
    val grunnlag: HentGrunnlagDto = commonObjectmapper.readValue(stringValue)
    return grunnlag
}

fun Behandling.leggTilNotat(
    innhold: String,
    type: NotatGrunnlag.NotatType,
    rolleForInntekt: Rolle? = null,
) {
    notater.add(
        Notat(
            behandling = this,
            rolle = henteRolleForNotat(type, rolleForInntekt),
            innhold = innhold,
            type = type,
        ),
    )
}
