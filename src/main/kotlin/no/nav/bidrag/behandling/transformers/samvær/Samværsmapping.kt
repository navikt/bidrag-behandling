package no.nav.bidrag.behandling.transformers.samvær

import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.mapValideringsfeil
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag

fun Samvær.tilOppdaterSamværResponseDto() =
    OppdaterSamværResponsDto(
        oppdatertSamvær = tilDto(),
        valideringsfeil = mapValideringsfeil(),
    )

fun Samvær.tilDto() =
    SamværDto(
        id = id!!,
        gjelderBarn = rolle.ident!!,
        begrunnelse =
            behandling.notater.find { it.rolle.id == rolle.id && it.type == NotatGrunnlag.NotatType.SAMVÆR }?.let {
                BegrunnelseDto(it.innhold, it.rolle.tilDto())
            },
        valideringsfeil = mapValideringsfeil(),
        perioder =
            perioder.map {
                SamværDto.SamværsperiodeDto(
                    id = it.id,
                    periode = DatoperiodeDto(it.fom, it.tom),
                    samværsklasse = it.samværsklasse,
                    beregning = it.beregning,
                )
            },
    )
