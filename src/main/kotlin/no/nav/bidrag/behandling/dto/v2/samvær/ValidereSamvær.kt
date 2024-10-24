package no.nav.bidrag.behandling.dto.v2.samvær

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.transformers.finnHullIPerioder
import no.nav.bidrag.behandling.transformers.minOfNullable
import no.nav.bidrag.behandling.transformers.tilDatoperiode
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

data class SamværValideringsfeilDto(
    val samværId: Long,
    val gjelderBarn: String,
    val manglerBegrunnelse: Boolean,
    val ingenLøpendeSamvær: Boolean,
    val manglerSamvær: Boolean,
    val overlappendePerioder: Set<OverlappendeSamværPeriode>,
    @Schema(description = "Liste med perioder hvor det mangler inntekter. Vil alltid være tom liste for ytelser")
    val hullIPerioder: List<Datoperiode> = emptyList(),
) {
    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse || ingenLøpendeSamvær || manglerSamvær || overlappendePerioder.isNotEmpty() || hullIPerioder.isNotEmpty()
}

data class OverlappendeSamværPeriode(
    val periode: Datoperiode,
    @Schema(description = "Teknisk id på inntekter som overlapper")
    val idListe: MutableSet<Long>,
)

fun Set<Samvær>.mapValideringsfeil(virkningstidspunkt: LocalDate): Set<SamværValideringsfeilDto> =
    map { samvær -> samvær.mapValideringsfeil() }
        .filter { it.harFeil }
        .toSet()

fun Samvær.mapValideringsfeil(): SamværValideringsfeilDto {
    val notatSæmvær = behandling.notater.find { it.type == NotatGrunnlag.NotatType.SAMVÆR && it.rolle.id == rolle.id }
    val perioder = perioder
    return SamværValideringsfeilDto(
        samværId = id!!,
        gjelderBarn = rolle.ident!!,
        manglerBegrunnelse = notatSæmvær?.innhold.isNullOrBlank(),
        ingenLøpendeSamvær = perioder.isEmpty() || perioder.maxByOrNull { it.fom }!!.tom != null,
        overlappendePerioder = perioder.finnOverlappendePerioder(),
        manglerSamvær = perioder.isEmpty(),
        hullIPerioder = perioder.map { it.tilDatoperiode() }.finnHullIPerioder(behandling.virkningstidspunktEllerSøktFomDato),
    )
}

fun Samværsperiode.tilDatoperiode() = Datoperiode(fom, tom)

fun Set<Samværsperiode>.finnOverlappendePerioder(): Set<OverlappendeSamværPeriode> =
    sortedBy { it.fom }
        .flatMapIndexed { index, periode ->
            sortedBy { it.fom }
                .drop(index + 1)
                .filter { nestePeriode ->
                    nestePeriode.tilDatoperiode().overlapper(periode.tilDatoperiode())
                }.map { nesteBostatusperiode ->
                    OverlappendeSamværPeriode(
                        Datoperiode(
                            maxOf(periode.fom, nesteBostatusperiode.fom),
                            minOfNullable(periode.tom, nesteBostatusperiode.tom),
                        ),
                        mutableSetOf(periode.id!!, nesteBostatusperiode.id!!),
                    )
                }
        }.toSet()

fun OppdaterSamværDto.valider() {
    val feilliste = mutableListOf<String>()

    periode?.valider()?.also { feilliste.addAll(it) }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av samvær: ${feilliste.joinToString(", ")}",
        )
    }
}

fun OppdaterSamværsperiodeDto.valider(): MutableList<String> {
    val feilliste = mutableListOf<String>()

    if (samværsklasse != null && beregning != null) {
        feilliste.add("Kan ikke sette beregning og manuell samværsklasse samtidig")
    }
    if (samværsklasse == null && beregning == null) {
        feilliste.add("Samværsklasse eller beregning må settes")
    }
    return feilliste
}
