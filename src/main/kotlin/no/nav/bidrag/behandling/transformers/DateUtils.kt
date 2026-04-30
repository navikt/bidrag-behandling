package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

val cuttoffBidrag18ÅrAlder = 24
val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val formatterDDMMYYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun LocalDate.tilDato18årsBidrag() = plusYears(18).plusMonths(1).withDayOfMonth(1)

fun LocalDate.toDDMMYYYY(): String = this.format(formatterDDMMYYYY)

fun LocalDate.opphørSisteTilDato() = this.withDayOfMonth(1).minusDays(1)

fun erOverAntallÅrGammel(
    birthDate: LocalDate?,
    antallÅr: Int,
): Boolean = birthDate?.withDayOfMonth(1)?.let { Period.between(it, LocalDate.now().withDayOfMonth(1)).years >= antallÅr } ?: false

fun String?.toLocalDateTime(): LocalDateTime {
    if (this == null) return LocalDateTime.now()
    val formatted = this.replace(' ', 'T').replace(Regex("[+-]\\d{2}(:\\d{2})?$"), "")
    return try {
        LocalDateTime.parse(formatted)
    } catch (e: DateTimeParseException) {
        OffsetDateTime.parse(formatted).toLocalDateTime()
    }
}

fun List<ÅrMånedsperiode>.filtrerMatchendePeriode(periode: ÅrMånedsperiode) = filter { it.overlapperMed(periode) }

private fun ÅrMånedsperiode.overlapperMed(annenPeriode: ÅrMånedsperiode): Boolean {
    val starterForEllerNarAndreSlutter = annenPeriode.til?.let { fom < it } ?: true
    val slutterEtterEllerNarAndreStarter = til?.let { it > annenPeriode.fom } ?: true
    return starterForEllerNarAndreSlutter && slutterEtterEllerNarAndreStarter
}

fun List<ÅrMånedsperiode>.filtrerOgJusterFraVirkningstidspunkt(virkningstidspunkt: YearMonth): List<ÅrMånedsperiode> =
    mapNotNull { periode ->
        if (periode.til != null && periode.til!! <= virkningstidspunkt) {
            null
        } else {
            ÅrMånedsperiode(
                maxOf(periode.fom, virkningstidspunkt),
                periode.til,
            )
        }
    }
