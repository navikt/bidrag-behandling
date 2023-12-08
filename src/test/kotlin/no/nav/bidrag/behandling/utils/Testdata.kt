package no.nav.bidrag.behandling.utils

import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.InntektPostDomain
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.toDate
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L
val ROLLE_BM = ForsendelseRolleDto(Personident("313213213"), type = Rolletype.BIDRAGSMOTTAKER)
val ROLLE_BA_1 = ForsendelseRolleDto(Personident("1344124"), type = Rolletype.BARN)
val ROLLE_BP = ForsendelseRolleDto(Personident("213244124"), type = Rolletype.BIDRAGSPLIKTIG)

val testdataBM =
    mapOf(
        Rolle::navn.name to "Oran Mappe",
        Rolle::ident.name to "1232134544",
        Rolle::rolleType.name to Rolletype.BIDRAGSMOTTAKER,
        Rolle::fodtDato.name to LocalDate.parse("2020-03-01").toDate(),
    )

val testdataBarn1 =
    mapOf<String, Any>(
        Rolle::navn.name to "Kran Mappe",
        Rolle::ident.name to "6216464366",
        Rolle::rolleType.name to Rolletype.BARN,
        Rolle::fodtDato.name to LocalDate.parse("2020-03-01").toDate(),
    )

val testdataBarn2 =
    mapOf<String, Any>(
        Rolle::navn.name to "Gran Mappe",
        Rolle::ident.name to "123312312",
        Rolle::rolleType.name to Rolletype.BARN,
        Rolle::fodtDato.name to LocalDate.parse("2018-05-09").toDate(),
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

fun oppretteBehandling(): Behandling {
    return Behandling(
        Behandlingstype.FORSKUDD,
        SoknadType.FASTSETTELSE,
        datoFom = YearMonth.parse("2022-08").atEndOfMonth().toDate(),
        datoTom = YearMonth.now().plusMonths(10).atEndOfMonth().toDate(),
        mottatDato = LocalDate.parse("2023-03-15").toDate(),
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
        virkningsDato = LocalDate.parse("2023-02-01").toDate(),
    )
}

fun opprettInntekt(
    behandling: Behandling,
    data: Map<String, Any>,
) = Inntekt(
    Inntektsrapportering.AINNTEKT_BEREGNET_12MND.name,
    BigDecimal.valueOf(45000),
    LocalDate.now().minusYears(1).withDayOfYear(1).toDate(),
    LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31).toDate(),
    data[Rolle::ident.name] as String,
    true,
    true,
    behandling = behandling,
)

fun opprettInntekter(
    behandling: Behandling,
    data: Map<String, Any>,
) = mutableSetOf(
    Inntekt(
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND.name,
        BigDecimal.valueOf(45000),
        LocalDate.parse("2023-01-01").toDate(),
        LocalDate.parse("2023-12-31").toDate(),
        data[Rolle::ident.name] as String,
        true,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.LIGNINGSINNTEKT.name,
        BigDecimal.valueOf(33000),
        LocalDate.parse("2023-01-01").toDate(),
        LocalDate.parse("2023-12-31").toDate(),
        data[Rolle::ident.name] as String,
        true,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT.name,
        BigDecimal.valueOf(55000),
        LocalDate.parse("2022-01-01").toDate(),
        LocalDate.parse("2022-12-31").toDate(),
        data[Rolle::ident.name] as String,
        false,
        true,
        behandling = behandling,
    ),
)

fun opprettInntektsposter(inntekt: Inntekt): MutableSet<InntektPostDomain> =
    setOf(
        InntektPostDomain(
            BigDecimal.valueOf(400000),
            "lønnFraFluefiske",
            "Lønn fra fluefiske",
            inntekt = inntekt,
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
        datoFom = datoFom.toDate(),
        datoTom = datoTom?.toDate(),
        sivilstand = sivilstand,
        kilde = Kilde.OFFENTLIG,
    )
}

fun opprettRolle(
    behandling: Behandling,
    data: Map<String, Any>,
): Rolle {
    return Rolle(
        navn = data[Rolle::navn.name] as String,
        ident = data[Rolle::ident.name] as String,
        rolleType = data[Rolle::rolleType.name] as Rolletype,
        behandling = behandling,
        fodtDato = data[Rolle::fodtDato.name] as Date,
        opprettetDato = LocalDate.now().toDate(),
    )
}

fun opprettHusstandsbarn(
    behandling: Behandling,
    data: Map<String, Any>,
): HusstandsBarn {
    val husstandsbarn =
        HusstandsBarn(
            navn = data[Rolle::navn.name] as String,
            ident = data[Rolle::ident.name] as String,
            medISaken = true,
            behandling = behandling,
            foedselsDato = data[Rolle::fodtDato.name] as Date,
        )
    husstandsbarn.perioder =
        mutableSetOf(
            HusstandsBarnPeriode(
                datoFom = LocalDate.parse("2023-01-01").toDate(),
                datoTom = LocalDate.parse("2023-05-31").toDate(),
                bostatus = Bostatuskode.MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                husstandsBarn = husstandsbarn,
            ),
            HusstandsBarnPeriode(
                datoFom = LocalDate.parse("2023-05-31").toDate(),
                datoTom = null,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                kilde = Kilde.OFFENTLIG,
                husstandsBarn = husstandsbarn,
            ),
        )
    return husstandsbarn
}

fun oppretteBehandlingRoller(behandling: Behandling) =
    mutableSetOf(
        Rolle(
            ident = ROLLE_BM.fødselsnummer?.verdi!!,
            rolleType = Rolletype.BIDRAGSMOTTAKER,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BP.fødselsnummer?.verdi!!,
            rolleType = Rolletype.BIDRAGSPLIKTIG,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BA_1.fødselsnummer?.verdi!!,
            rolleType = Rolletype.BARN,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null,
        ),
    )
