package no.nav.bidrag.behandling.transformers.samvær

import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.mapValideringsfeil
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import java.math.BigDecimal

fun Samvær.tilOppdaterSamværResponseDto() =
    OppdaterSamværResponsDto(
        oppdatertSamvær = tilDto(),
    )

fun Samvær.tilBegrunnelse() =
    behandling.notater.find { it.rolle.id == rolle.id && it.type == NotatGrunnlag.NotatType.SAMVÆR }?.let {
        BegrunnelseDto(it.innhold, it.rolle.tilDto())
    } ?: BegrunnelseDto("", rolle.tilDto())

fun Samvær.tilDto() =
    SamværDto(
        id = id!!,
        gjelderBarn = rolle.ident!!,
        begrunnelse = tilBegrunnelse(),
        valideringsfeil = mapValideringsfeil().takeIf { it.harFeil },
        perioder =
            perioder
                .sortedBy { it.fom }
                .map {
                    SamværDto.SamværsperiodeDto(
                        id = it.id,
                        periode = DatoperiodeDto(it.fom, it.tom),
                        samværsklasse = it.samværsklasse,
                        gjennomsnittligSamværPerMåned =
                            it.beregning?.let { BeregnSamværsklasseApi.beregnSumGjennomsnittligSamværPerMåned(it) }
                                ?: BigDecimal.ZERO,
                        beregning = it.beregning,
                    )
                },
    )
