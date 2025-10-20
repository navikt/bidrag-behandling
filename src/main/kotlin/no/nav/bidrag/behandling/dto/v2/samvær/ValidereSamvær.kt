package no.nav.bidrag.behandling.dto.v2.samvær

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.dto.v2.felles.OverlappendePeriode
import no.nav.bidrag.behandling.transformers.finnHullIPerioder
import no.nav.bidrag.behandling.transformers.finnOverlappendePerioder
import no.nav.bidrag.behandling.transformers.opphørSisteTilDato
import no.nav.bidrag.behandling.transformers.ugyldigSluttperiode
import no.nav.bidrag.beregn.core.util.sluttenAvForrigeMåned
import no.nav.bidrag.domene.enums.samværskalkulator.SamværskalkulatorFerietype
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate

data class SamværValideringsfeilDto(
    val samværId: Long,
    @JsonIgnore
    val gjelderRolle: Rolle,
    val manglerBegrunnelse: Boolean,
    val ingenLøpendeSamvær: Boolean,
    val manglerSamvær: Boolean,
    val ugyldigSluttperiode: Boolean,
    val overlappendePerioder: Set<OverlappendePeriode>,
    @Schema(description = "Liste med perioder hvor det mangler inntekter. Vil alltid være tom liste for ytelser")
    val hullIPerioder: List<Datoperiode> = emptyList(),
) {
    val harPeriodiseringsfeil get() =
        ingenLøpendeSamvær ||
            manglerSamvær ||
            overlappendePerioder.isNotEmpty() ||
            hullIPerioder.isNotEmpty() ||
            ugyldigSluttperiode
    val gjelderBarn get() = gjelderRolle.ident
    val gjelderBarnNavn get() = gjelderRolle.navn

    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse || harPeriodiseringsfeil
}

fun Set<Samvær>.mapValideringsfeil(): Set<SamværValideringsfeilDto> =
    map { samvær -> samvær.mapValideringsfeil() }
        .filter { it.harFeil }
        .toSet()

fun Samvær.mapValideringsfeil(): SamværValideringsfeilDto {
    val notatSæmvær = behandling.notater.find { it.type == NotatGrunnlag.NotatType.SAMVÆR && it.rolle.id == rolle.id }
    val perioder = perioder
    val opphørsdato = rolle.opphørsdato
    return SamværValideringsfeilDto(
        samværId = id!!,
        gjelderRolle = rolle,
        manglerBegrunnelse = if (behandling.erKlageEllerOmgjøring) false else notatSæmvær?.innhold.isNullOrBlank(),
        ingenLøpendeSamvær =
            (opphørsdato == null || opphørsdato.opphørSisteTilDato().isAfter(LocalDate.now().sluttenAvForrigeMåned)) &&
                (perioder.isEmpty() || perioder.maxByOrNull { it.fom }!!.tom != null),
        overlappendePerioder =
            perioder
                .map { Pair(it.id ?: -1, it.tilDatoperiode()) }
                .finnOverlappendePerioder(),
        manglerSamvær = perioder.isEmpty(),
        ugyldigSluttperiode =
            perioder
                .map { it.tilDatoperiode() }
                .ugyldigSluttperiode(rolle.opphørsdato),
        hullIPerioder =
            perioder
                .map { it.tilDatoperiode() }
                .finnHullIPerioder(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    rolle.opphørsdato,
                ).filter {
                    it.til !=
                        null
                },
    )
}

fun Samværsperiode.tilDatoperiode() = Datoperiode(fom, if (tom?.isBefore(fom) == true) null else tom)

fun OppdaterSamværDto.valider(opphørsdato: LocalDate?) {
    val feilliste = mutableListOf<String>()

    periode?.valider(opphørsdato)?.also { feilliste.addAll(it) }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av samvær: ${feilliste.joinToString(", ")}",
        )
    }
}

fun OppdaterSamværsperiodeDto.valider(opphørsdato: LocalDate?): MutableList<String> {
    val feilliste = mutableListOf<String>()

    if (samværsklasse != null && beregning != null) {
        feilliste.add("Kan ikke sette beregning og manuell samværsklasse samtidig")
    }
    if (samværsklasse == null && beregning == null) {
        feilliste.add("Samværsklasse eller beregning må settes")
    }
    if (opphørsdato != null && periode.fom >= opphørsdato) {
        feilliste.add("Fom-dato kan ikke være etter opphørsdato")
    }
    if (periode.tom != null && opphørsdato != null && periode.tom!! > opphørsdato) {
        feilliste.add("Tom-dato kan ikke være etter opphørsdato")
    }
    if (periode.tom != null && periode.tom!! > LocalDate.now().plusMonths(1).withDayOfMonth(1)) {
        feilliste.add("Periode tom-dato kan ikke være i frem i tid")
    }
    if (beregning != null) {
        if (beregning.regelmessigSamværNetter > BigDecimal(15)) {
            feilliste.add("Regelmessig samvær kan ikke være over 15 netter")
        }
        if (!beregning.ferier.harMaksEnInnslagMedSammeType()) {
            feilliste.add("SamværskalkulatorFerie kan ikke ha flere innslag av samme type")
        }
    }
    return feilliste
}

private fun List<SamværskalkulatorDetaljer.SamværskalkulatorFerie>.harMaksEnInnslagMedSammeType(): Boolean {
    val typeCountMap = mutableMapOf<SamværskalkulatorFerietype, Int>()
    val ferier = this

    for (ferie in ferier) {
        val type = ferie.type
        typeCountMap[type] = typeCountMap.getOrDefault(type, 0) + 1
    }

    return typeCountMap.any { it.value <= 1 }
}
