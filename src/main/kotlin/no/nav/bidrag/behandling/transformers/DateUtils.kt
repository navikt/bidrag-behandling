package no.nav.bidrag.behandling.transformers

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val formatterCommpact = DateTimeFormatter.ofPattern("yyyyMMdd")
val INFINITY = LocalDate.ofYearDay(999999,1)

fun Date.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(this.toInstant(), ZoneId.systemDefault())
}

fun LocalDate.toDate(): Date {
    return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
}

fun LocalDate.toNoString(): String = this.format(formatter)
fun LocalDate.toCompactString(): String = this.format(formatterCommpact)
