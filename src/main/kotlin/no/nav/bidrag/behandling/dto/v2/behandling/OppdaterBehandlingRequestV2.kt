package no.nav.bidrag.behandling.dto.v2.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.domene.ident.Personident

data class OppdaterBehandlingRequestV2(
    val virkningstidspunkt: OppdaterVirkningstidspunkt? = null,
    val boforhold: OppdaterBoforholdRequest? = null,
    val inntekter: OppdatereInntekterRequestV2? = null,
    val aktivereGrunnlagForPerson: AktivereGrunnlagRequest? = null,
)

data class AktivereGrunnlagRequest(
    @Schema(description = "Personident tilhørende rolle i behandling grunnlag skal aktiveres for")
    val personident: Personident,
    @Schema(description = "Grunnlagstyper som skal aktiveres")
    val grunnlagsdatatyper: Set<Grunnlagsdatatype> = emptySet(),
)
