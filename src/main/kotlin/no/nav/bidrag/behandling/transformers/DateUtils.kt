package no.nav.bidrag.behandling.transformers

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun Date.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(this.toInstant(), ZoneId.systemDefault())
}

fun LocalDate.toDate(): Date {
    return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
}

fun LocalDate.toNoString(): String = this.format(formatter)
