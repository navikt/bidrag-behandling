package no.nav.bidrag.behandling.transformers.samvær

import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværBarnDto
import no.nav.bidrag.behandling.dto.v2.samvær.mapValideringsfeil
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import java.math.BigDecimal

fun Samvær.tilOppdaterSamværResponseDto() =
    OppdaterSamværResponsDto(
        oppdatertSamvær = tilDto(),
    )

fun Samvær.tilBegrunnelse() = BegrunnelseDto(henteNotatinnhold(behandling, NotatType.SAMVÆR, rolle), rolle.tilDto())

fun Samvær.tilBegrunnelseFraOpprinneligVedtak() =
    henteNotatinnhold(behandling, NotatType.SAMVÆR, rolle, false)
        .takeIfNotNullOrEmpty { BegrunnelseDto(it, rolle.tilDto()) }

fun Samvær.tilDto() =
    SamværBarnDto(
        id = id!!,
        gjelderBarn = rolle.ident!!,
        barn = rolle.tilDto(),
        begrunnelse = tilBegrunnelse(),
        begrunnelseFraOpprinneligVedtak =
            if (behandling.erKlageEllerOmgjøring) {
                tilBegrunnelseFraOpprinneligVedtak()
            } else {
                null
            },
        valideringsfeil = mapValideringsfeil().takeIf { it.harFeil },
        perioder =
            perioder
                .sortedBy { it.fom }
                .map {
                    SamværBarnDto.SamværsperiodeDto(
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
