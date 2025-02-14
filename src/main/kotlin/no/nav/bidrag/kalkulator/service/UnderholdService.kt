package no.nav.bidrag.kalkulator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate

private val log = KotlinLogging.logger {}

private fun periodeFomJuli(year: Int) =
    LocalDate
        .of(year, 7, 1)
