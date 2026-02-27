package no.nav.bidrag.behandling.transformers

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
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
