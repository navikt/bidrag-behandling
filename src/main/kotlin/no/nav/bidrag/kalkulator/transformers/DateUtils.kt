package no.nav.bidrag.kalkulator.transformers

import java.time.LocalDate
import java.time.format.DateTimeFormatter

val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val formatterDDMMYYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun LocalDate.toDDMMYYYY(): String = this.format(formatterDDMMYYYY)
