package no.nav.bidrag.behandling.utils.testdata

import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val fødselsnummerBm = "33" + LocalDate.now().minusMonths(450).format(DateTimeFormatter.ofPattern("MMyy")) + "40405"
val fødselsnummerBp = "34" + LocalDate.now().minusMonths(476).format(DateTimeFormatter.ofPattern("MMyy")) + "72320"
val fødselsnummerBarn1 = "81" + LocalDate.now().minusMonths(88).format(DateTimeFormatter.ofPattern("MMyy")) + "28921"
val fødselsnummerBarn2 = "82" + LocalDate.now().minusMonths(45).format(DateTimeFormatter.ofPattern("MMyy")) + "36333"

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L

val ROLLE_BM = ForsendelseRolleDto(Personident(fødselsnummerBm), type = Rolletype.BIDRAGSMOTTAKER)
val ROLLE_BP = ForsendelseRolleDto(Personident(fødselsnummerBp), type = Rolletype.BIDRAGSPLIKTIG)
val ROLLE_BA_1 = ForsendelseRolleDto(Personident(fødselsnummerBarn1), type = Rolletype.BARN)
val ROLLE_BA_2 = ForsendelseRolleDto(Personident(fødselsnummerBarn2), type = Rolletype.BARN)

val testdataBM =
    mapOf(
        Rolle::navn.name to "Oran Mappe",
        Rolle::ident.name to fødselsnummerBm,
        Rolle::rolletype.name to Rolletype.BIDRAGSMOTTAKER,
        Rolle::foedselsdato.name to LocalDate.parse("2020-03-01"),
    )

val testdataBarn1 =
    mapOf<String, Any>(
        Rolle::navn.name to "Kran Mappe",
        Rolle::ident.name to fødselsnummerBarn1,
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
        virkningsdato = LocalDate.parse("2023-02-01"),
        id = id,
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
    Kilde.OFFENTLIG,
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
        Kilde.OFFENTLIG,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.LIGNINGSINNTEKT,
        BigDecimal.valueOf(33000),
        LocalDate.parse("2023-01-01"),
        LocalDate.parse("2023-12-31"),
        data[Rolle::ident.name] as String,
        Kilde.OFFENTLIG,
        true,
        behandling = behandling,
    ),
    Inntekt(
        Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
        BigDecimal.valueOf(55000),
        LocalDate.parse("2022-01-01"),
        LocalDate.parse("2022-12-31"),
        data[Rolle::ident.name] as String,
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
    data: Map<String, Any>,
): Rolle {
    return Rolle(
        navn = data[Rolle::navn.name] as String,
        ident = data[Rolle::ident.name] as String,
        rolletype = data[Rolle::rolletype.name] as Rolletype,
        behandling = behandling,
        foedselsdato = data[Rolle::foedselsdato.name] as LocalDate,
        opprettet = LocalDateTime.now(),
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

fun oppretteBehandlingRoller(
    behandling: Behandling,
    generateId: Boolean = false,
    medBp: Boolean = false,
): MutableSet<Rolle> {
    val roller =
        mutableSetOf(
            Rolle(
                ident = ROLLE_BM.fødselsnummer?.verdi!!,
                rolletype = Rolletype.BIDRAGSMOTTAKER,
                behandling = behandling,
                foedselsdato = LocalDate.now().minusMonths(29 * 13),
                id = if (generateId) (1).toLong() else null,
            ),
            Rolle(
                ident = ROLLE_BA_1.fødselsnummer?.verdi!!,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                foedselsdato = LocalDate.now().minusMonths(3 * 14),
                id = if (generateId) (2).toLong() else null,
            ),
            Rolle(
                ident = ROLLE_BA_2.fødselsnummer?.verdi!!,
                rolletype = Rolletype.BARN,
                behandling = behandling,
                foedselsdato = LocalDate.now().minusMonths(3 * 14),
                id = if (generateId) (3).toLong() else null,
            ),
        )

    if (medBp) {
        roller.add(
            Rolle(
                ident = ROLLE_BP.fødselsnummer?.verdi!!,
                rolletype = Rolletype.BIDRAGSPLIKTIG,
                behandling = behandling,
                foedselsdato = LocalDate.now().minusMonths(29 * 14),
                id = if (generateId) (4).toLong() else null,
            ),
        )
    }
    return roller
}

fun opprettGyldigBehandlingForBeregning(generateId: Boolean = false): Behandling {
    // given
    val behandling = oppretteBehandling(if (generateId) 1 else null)
    behandling.roller = oppretteBehandlingRoller(behandling, generateId)
    val husstandsbarn =
        behandling.getSøknadsbarn().mapIndexed { i, it ->
            val husstandsbarn =
                Husstandsbarn(
                    behandling = behandling,
                    medISaken = true,
                    ident = it.ident,
                    navn = it.navn ?: "Lavransdottir",
                    foedselsdato = it.foedselsdato,
                    id = if (generateId) (i + 1).toLong() else null,
                )
            husstandsbarn.perioder =
                mutableSetOf(
                    Husstandsbarnperiode(
                        husstandsbarn = husstandsbarn,
                        datoFom = LocalDate.now().minusMonths(5),
                        datoTom = LocalDate.now().plusMonths(3),
                        bostatus = Bostatuskode.MED_FORELDER,
                        kilde = Kilde.OFFENTLIG,
                        id = if (generateId) (i + 1).toLong() else null,
                    ),
                )
            husstandsbarn
        }.toMutableSet()
    val sivilstand =
        Sivilstand(
            sivilstand = Sivilstandskode.BOR_ALENE_MED_BARN,
            behandling = behandling,
            datoFom = LocalDate.now().minusMonths(12),
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
                ident = behandling.getBidragsmottaker()!!.ident!!,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                inntektsrapportering = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                id = if (generateId) (1).toLong() else null,
            ),
            Inntekt(
                belop = BigDecimal(60000),
                datoTom = null,
                datoFom = LocalDate.parse("2022-07-01"),
                ident = behandling.getBidragsmottaker()!!.ident!!,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                inntektsrapportering = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
                id = if (generateId) (2).toLong() else null,
            ),
            Inntekt(
                belop = BigDecimal(60000),
                datoTom = LocalDate.parse("2022-12-31"),
                datoFom = LocalDate.parse("2022-01-01"),
                ident = behandling.getBidragsmottaker()!!.ident!!,
                taMed = false,
                kilde = Kilde.OFFENTLIG,
                behandling = behandling,
                inntektsrapportering = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                id = if (generateId) (3).toLong() else null,
            ),
        )
    behandling.husstandsbarn = husstandsbarn
    behandling.inntekter = inntekter
    behandling.sivilstand = mutableSetOf(sivilstand)
    return behandling
}
