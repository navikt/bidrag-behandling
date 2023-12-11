package no.nav.bidrag.behandling.utils

import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
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

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L
val ROLLE_BM = ForsendelseRolleDto(Personident("313213213"), type = Rolletype.BIDRAGSMOTTAKER)
val ROLLE_BA_1 = ForsendelseRolleDto(Personident("1344124"), type = Rolletype.BARN)
val ROLLE_BP = ForsendelseRolleDto(Personident("213244124"), type = Rolletype.BIDRAGSPLIKTIG)

val testdataBM =
    mapOf(
        Rolle::navn.name to "Oran Mappe",
        Rolle::ident.name to "1232134544",
        Rolle::rolletype.name to Rolletype.BIDRAGSMOTTAKER,
        Rolle::foedselsdato.name to LocalDate.parse("2020-03-01"),
    )

val testdataBarn1 =
    mapOf<String, Any>(
        Rolle::navn.name to "Kran Mappe",
        Rolle::ident.name to "6216464366",
        Rolle::rolletype.name to Rolletype.BARN,
        Rolle::foedselsdato.name to LocalDate.parse("2020-03-01"),
    )

val testdataBarn2 =
    mapOf<String, Any>(
        Rolle::navn.name to "Gran Mappe",
        Rolle::ident.name to "123312312",
        Rolle::rolletype.name to Rolletype.BARN,
        Rolle::foedselsdato.name to LocalDate.parse("2018-05-09"),
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
        Soknadstype.FASTSETTELSE,
        datoFom = YearMonth.parse("2022-08").atEndOfMonth(),
        datoTom = YearMonth.now().plusMonths(10).atEndOfMonth(),
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
        virkningsdato = LocalDate.parse("2023-02-01"),
    )
}

fun opprettInntekt(
    behandling: Behandling,
    data: Map<String, Any>,
) = Inntekt(
    Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
    BigDecimal.valueOf(45000),
    LocalDate.now().minusYears(1).withDayOfYear(1),
    LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31),
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
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
        BigDecimal.valueOf(45000),
        LocalDate.parse("2023-01-01"),
        LocalDate.parse("2023-12-31"),
        data[Rolle::ident.name] as String,
        true,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.LIGNINGSINNTEKT,
        BigDecimal.valueOf(33000),
        LocalDate.parse("2023-01-01"),
        LocalDate.parse("2023-12-31"),
        data[Rolle::ident.name] as String,
        true,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
        BigDecimal.valueOf(55000),
        LocalDate.parse("2022-01-01"),
        LocalDate.parse("2022-12-31"),
        data[Rolle::ident.name] as String,
        false,
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
    data: Map<String, Any>,
): Rolle {
    return Rolle(
        navn = data[Rolle::navn.name] as String,
        ident = data[Rolle::ident.name] as String,
        rolletype = data[Rolle::rolletype.name] as Rolletype,
        behandling = behandling,
        foedselsdato = data[Rolle::foedselsdato.name] as LocalDate,
        opprettetDato = LocalDate.now(),
    )
}

fun opprettHusstandsbarn(
    behandling: Behandling,
    data: Map<String, Any>,
): Husstandsbarn {
    val husstandsbarn =
        Husstandsbarn(
            navn = data[Rolle::navn.name] as String,
            ident = data[Rolle::ident.name] as String,
            medISaken = true,
            behandling = behandling,
            foedselsdato = data[Rolle::foedselsdato.name] as LocalDate,
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

fun oppretteBehandlingRoller(behandling: Behandling) =
    mutableSetOf(
        Rolle(
            ident = ROLLE_BM.fødselsnummer?.verdi!!,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            behandling = behandling,
            foedselsdato = LocalDate.now().minusMonths(29 * 13),
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BP.fødselsnummer?.verdi!!,
            rolletype = Rolletype.BIDRAGSPLIKTIG,
            behandling = behandling,
            foedselsdato = LocalDate.now().minusMonths(33 * 11),
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BA_1.fødselsnummer?.verdi!!,
            rolletype = Rolletype.BARN,
            behandling = behandling,
            foedselsdato = LocalDate.now().minusMonths(3 * 14),
            opprettetDato = null,
        ),
    )
