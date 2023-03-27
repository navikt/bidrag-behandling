package no.nav.bidrag.behandling.ext

import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

fun Date.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(this.toInstant(), ZoneId.systemDefault())
}

fun LocalDate.toDate(): Date {
    return Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
}
