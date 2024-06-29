package no.nav.bidrag.behandling.transformers.utgift

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftResponse
import no.nav.bidrag.behandling.transformers.erDatoForUtgiftForeldet
import no.nav.bidrag.behandling.transformers.sorter
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal

val Behandling.kanInneholdeUtgiftBetaltAvBp get() = særbidragKategori == Særbidragskategori.KONFIRMASJON
val Utgift.totalGodkjentBeløpBp
    get() =
        behandling.kanInneholdeUtgiftBetaltAvBp.ifTrue {
            utgiftsposter.filter { it.betaltAvBp }.sumOf { it.godkjentBeløp }
        }
val Utgift.totalGodkjentBeløp get() = utgiftsposter.sumOf { it.godkjentBeløp }
val Utgift.totalBeløpBetaltAvBp
    get() = utgiftsposter.filter { it.betaltAvBp }.sumOf { it.godkjentBeløp } + beløpDirekteBetaltAvBp

fun Utgift.tilUtgiftResponse(utgiftspostId: Long? = null) =
    if (behandling.avslag != null) {
        OppdatereUtgiftResponse(
            avslag = behandling.avslag,
            notat = BehandlingNotatDto(behandling.utgiftsbegrunnelseKunINotat ?: ""),
        )
    } else {
        OppdatereUtgiftResponse(
            oppdatertUtgiftspost = utgiftsposter.find { it.id == utgiftspostId }?.tilDto(),
            utgiftposter = utgiftsposter.sorter().map { it.tilDto() },
            notat = BehandlingNotatDto(behandling.utgiftsbegrunnelseKunINotat ?: ""),
            beregning = tilBeregningDto(),
        )
    }

fun Utgift.tilBeregningDto() =
    UtgiftBeregningDto(
        beløpDirekteBetaltAvBp = beløpDirekteBetaltAvBp,
        totalBeløpBetaltAvBp = totalBeløpBetaltAvBp,
        totalGodkjentBeløp = totalGodkjentBeløp,
        totalGodkjentBeløpBp = totalGodkjentBeløpBp,
    )

fun Utgiftspost.tilDto() =
    UtgiftspostDto(
        id = id!!,
        begrunnelse = begrunnelse ?: "",
        type = type,
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
        type =
            when (utgift.behandling.særbidragKategori) {
                Særbidragskategori.KONFIRMASJON -> type!!
                Særbidragskategori.OPTIKK -> Utgiftstype.OPTIKK
                Særbidragskategori.TANNREGULERING -> Utgiftstype.TANNREGULERING
                Særbidragskategori.ANNET -> Utgiftstype.ANNET
                else -> throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Kunne ikke bestemme type for utgiftspost")
            },
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
