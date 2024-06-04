package no.nav.bidrag.behandling.transformers.utgift

import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftResponse
import no.nav.bidrag.behandling.transformers.erDatoForUtgiftForeldet
import no.nav.bidrag.behandling.transformers.sorter
import java.math.BigDecimal

val Utgift.beregnetBeløp get() = utgiftsposter.sumOf { it.godkjentBeløp }
val Utgift.totalBeløpBetaltAvBp
    get() = utgiftsposter.filter { it.betaltAvBp }.sumOf { it.godkjentBeløp } + beløpDirekteBetaltAvBp

fun Utgift.tilUtgiftResponse(utgiftspostId: Long? = null) =
    OppdatereUtgiftResponse(
        oppdatertUtgiftspost = utgiftsposter.find { it.id == utgiftspostId }?.tilDto(),
        utgiftposter = utgiftsposter.sorter().map { it.tilDto() },
        notat = BehandlingNotatDto(behandling.utgiftsbegrunnelseKunINotat ?: ""),
        beløpDirekteBetaltAvBp = beløpDirekteBetaltAvBp,
        totalBeløpBetaltAvBp = totalBeløpBetaltAvBp,
        beregnetBeløp = beregnetBeløp,
    )

fun Utgiftspost.tilDto() =
    UtgiftspostDto(
        id = id!!,
        begrunnelse = begrunnelse ?: "",
        beskrivelse = beskrivelse,
        godkjentBeløp = godkjentBeløp,
        kravbeløp = kravbeløp,
        betaltAvBp = betaltAvBp,
        dato = dato,
    )

fun OppdatereUtgift.tilUtgiftspost(utgift: Utgift) =
    Utgiftspost(
        utgift = utgift,
        begrunnelse =
            if (utgift.behandling.erDatoForUtgiftForeldet(dato)) {
                "Utgiften er foreldet"
            } else {
                begrunnelse
            },
        beskrivelse = beskrivelse,
        godkjentBeløp =
            if (utgift.behandling.erDatoForUtgiftForeldet(dato)) {
                BigDecimal.ZERO
            } else {
                godkjentBeløp
            },
        kravbeløp = kravbeløp,
        betaltAvBp = betaltAvBp,
        dato = dato,
    )
