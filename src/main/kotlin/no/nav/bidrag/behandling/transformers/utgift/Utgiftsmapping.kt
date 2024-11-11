package no.nav.bidrag.behandling.transformers.utgift

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragKategoriDto
import no.nav.bidrag.behandling.dto.v2.behandling.TotalBeregningUtgifterDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.utgift.MaksGodkjentBeløpDto
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.validering.MaksGodkjentBeløpValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.UtgiftValideringsfeilDto
import no.nav.bidrag.behandling.transformers.erDatoForUtgiftForeldet
import no.nav.bidrag.behandling.transformers.erUtgiftForeldet
import no.nav.bidrag.behandling.transformers.sorterBeregnetUtgifter
import no.nav.bidrag.behandling.transformers.validerUtgiftspost
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal

val kategorierSomKreverType = listOf(Særbidragskategori.ANNET, Særbidragskategori.KONFIRMASJON)
val Utgift.totalGodkjentBeløpBp
    get() = utgiftsposter.filter { it.betaltAvBp }.sumOf { it.godkjentBeløp }
val Utgift.totalGodkjentBeløp get() = utgiftsposter.sumOf { it.godkjentBeløp }
val Utgift.totalKravbeløp get() = utgiftsposter.sumOf { it.kravbeløp }
val Utgift.totalBeløpBetaltAvBp
    get() = totalGodkjentBeløpBp + beløpDirekteBetaltAvBp

fun Behandling.tilSærbidragKategoriDto() =
    SærbidragKategoriDto(
        kategori = særbidragKategori,
        beskrivelse = kategoriBeskrivelse,
    )

fun Utgift?.hentValideringsfeil() =
    UtgiftValideringsfeilDto(
        ugyldigUtgiftspost =
            this?.utgiftsposter?.any {
                OppdatereUtgift(
                    dato = it.dato,
                    type =
                        when {
                            kategorierSomKreverType.contains(behandling.særbidragKategori) -> it.type
                            else -> null
                        },
                    kravbeløp = it.kravbeløp,
                    godkjentBeløp = it.godkjentBeløp,
                    kommentar = it.kommentar,
                    betaltAvBp = it.betaltAvBp,
                    id = it.id,
                ).validerUtgiftspost(behandling).isNotEmpty()
            } ?: false,
        manglerUtgifter = this == null || utgiftsposter.isEmpty(),
        maksGodkjentBeløp = this?.validerMaksGodkjentBeløp(),
    ).takeIf { it.harFeil }

fun Utgift.validerMaksGodkjentBeløp() =
    if (maksGodkjentBeløpTaMed) {
        MaksGodkjentBeløpValideringsfeil(
            manglerBeløp = maksGodkjentBeløp == null || maksGodkjentBeløp == BigDecimal.ZERO,
            manglerBegrunnelse = maksGodkjentBeløpBegrunnelse.isNullOrEmpty(),
        ).takeIf { it.harFeil }
    } else {
        null
    }

fun Utgift.tilTotalBeregningDto() =
    utgiftsposter
        .groupBy {
            Pair(it.type, it.betaltAvBp)
        }.map { (gruppe, utgifter) ->
            TotalBeregningUtgifterDto(
                betaltAvBp = gruppe.second,
                utgiftstype = gruppe.first,
                utgifter.sumOf {
                    it.kravbeløp
                },
                utgifter.sumOf { it.godkjentBeløp },
            )
        }.sorterBeregnetUtgifter()

fun Utgift.tilMaksGodkjentBeløpDto() =
    MaksGodkjentBeløpDto(
        taMed = maksGodkjentBeløpTaMed,
        beløp = maksGodkjentBeløp,
        begrunnelse = maksGodkjentBeløpBegrunnelse,
    )

fun Utgift.tilBeregningDto() =
    UtgiftBeregningDto(
        beløpDirekteBetaltAvBp = beløpDirekteBetaltAvBp,
        totalBeløpBetaltAvBp = totalBeløpBetaltAvBp,
        totalGodkjentBeløp = totalGodkjentBeløp,
        totalKravbeløp = totalKravbeløp,
        totalGodkjentBeløpBp = totalGodkjentBeløpBp,
    )

fun Utgiftspost.tilDto() =
    UtgiftspostDto(
        id = id!!,
        kommentar = if (erUtgiftForeldet()) "Utgiften er foreldet" else kommentar ?: "",
        type = type,
        godkjentBeløp = godkjentBeløp,
        kravbeløp = kravbeløp,
        betaltAvBp = betaltAvBp,
        dato = dato,
    )

fun OppdatereUtgift.tilUtgiftspost(utgift: Utgift) =
    Utgiftspost(
        utgift = utgift,
        kommentar = kommentar,
        type =
            when {
                kategorierSomKreverType.contains(utgift.behandling.særbidragKategori) -> type!!
                utgift.behandling.særbidragKategori == Særbidragskategori.OPTIKK -> Utgiftstype.OPTIKK.name
                utgift.behandling.særbidragKategori == Særbidragskategori.TANNREGULERING -> Utgiftstype.TANNREGULERING.name
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
