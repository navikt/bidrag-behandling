package no.nav.bidrag.behandling.transformers.samvær

import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværDto
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag

fun Samvær.tilOppdaterSamværResponseDto() =
    OppdaterSamværResponsDto(
        oppdatertSamvær = tilDto(),
        valideringsfeil = null,
    )

fun Samvær.tilDto() =
    SamværDto(
        gjelderBarn = rolle.ident!!,
        begrunnelse =
            behandling.notater.find { it.rolle.id == rolle.id && it.type == NotatGrunnlag.NotatType.SAMVÆR }?.let {
                BegrunnelseDto(it.innhold, it.rolle.tilDto())
            },
        perioder =
            perioder.map {
                SamværDto.SamværsperiodeDto(
                    periode = Datoperiode(it.datoFom, it.datoTom),
                    samværsklasse = it.samværsklasse,
                    beregning = it.beregning,
                )
            },
    )
