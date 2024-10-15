package no.nav.bidrag.behandling.transformers.samvær

import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværDto
import no.nav.bidrag.domene.tid.Datoperiode

fun Samvær.tilOppdaterSamværResponseDto() =
    OppdaterSamværResponsDto(
        oppdatertSamvær = tilDto(),
        valideringsfeil = null,
    )

fun Samvær.tilDto() =
    SamværDto(
        gjelderBarn = rolle.ident!!,
        perioder =
            perioder.map {
                SamværDto.SamværsperiodeDto(
                    periode = Datoperiode(it.datoFom, it.datoTom),
                    samværsklasse = it.samværsklasse,
                    beregning = it.beregning,
                )
            },
    )
