package no.nav.bidrag.behandling.transformers

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

val cuttoffBidrag18ÅrAlder = 24
val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val formatterDDMMYYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun LocalDate.toDDMMYYYY(): String = this.format(formatterDDMMYYYY)

fun LocalDate.opphørSisteTilDato() = this.withDayOfMonth(1).minusDays(1)

fun erOverAntallÅrGammel(
    birthDate: LocalDate?,
    antallÅr: Int,
): Boolean = birthDate?.withDayOfMonth(1)?.let { Period.between(it, LocalDate.now().withDayOfMonth(1)).years >= antallÅr } ?: false
