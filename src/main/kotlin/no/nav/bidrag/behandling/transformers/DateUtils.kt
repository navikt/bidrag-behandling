package no.nav.bidrag.behandling.transformers

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val formatterCommpact = DateTimeFormatter.ofPattern("yyyyMMdd")

fun Date.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(this.toInstant(), ZoneId.systemDefault())
}

fun LocalDate.toCompactString(): String = this.format(formatterCommpact)
