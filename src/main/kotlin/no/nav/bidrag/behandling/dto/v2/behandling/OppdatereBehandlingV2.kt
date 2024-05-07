package no.nav.bidrag.behandling.dto.v2.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.domene.ident.Personident

data class OppdaterBehandlingRequestV2(
    val virkningstidspunkt: OppdatereVirkningstidspunkt? = null,
    val boforhold: OppdaterBoforholdRequest? = null,
    val inntekter: OppdatereInntekterRequestV2? = null,
    val aktivereGrunnlagForPerson: AktivereGrunnlagRequest? = null,
)

@Deprecated("Bruk AktivereGrunnlagRequestV2 via eget endepunkt /behandling/{behandlingsid}/aktivere.")
data class AktivereGrunnlagRequest(
    @Schema(description = "Personident tilhørende rolle i behandling grunnlag skal aktiveres for")
    val personident: Personident,
    @Schema(description = "Grunnlagstyper som skal aktiveres")
    val grunnlagsdatatyper: Set<Grunnlagsdatatype> = emptySet(),
)

fun AktivereGrunnlagRequest.toV2() =
    AktivereGrunnlagRequestV2(
        personident,
        grunnlagstype = grunnlagsdatatyper.first(),
    )

data class AktivereGrunnlagResponseV2(
    val inntekter: InntekterDtoV2,
    val boforhold: BoforholdDtoV2,
    val aktiveGrunnlagsdata: AktiveGrunnlagsdata,
    val ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
)

data class AktivereGrunnlagRequestV2(
    @Schema(description = "Personident tilhørende rolle i behandling grunnlag skal aktiveres for")
    val personident: Personident,
    @Schema(description = "Grunnlagstype som skal aktiveres")
    val grunnlagstype: Grunnlagsdatatype,
    @Schema(description = "Angi om manuelle opplysninger skal overskrives")
    val overskriveManuelleOpplysninger: Boolean = true,
)
