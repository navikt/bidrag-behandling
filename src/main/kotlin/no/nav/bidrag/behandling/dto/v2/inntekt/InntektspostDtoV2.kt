package no.nav.bidrag.behandling.dto.v2.inntekt

import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektBeløpType
import java.math.BigDecimal

data class InntektspostDtoV2(
    val kode: String,
    val visningsnavn: String,
    val inntektstype: Inntektstype?,
    val beløp: BigDecimal?,
    val beløpstype: InntektBeløpType?,
)
