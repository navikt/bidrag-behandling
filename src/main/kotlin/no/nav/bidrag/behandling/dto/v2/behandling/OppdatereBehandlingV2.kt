package no.nav.bidrag.behandling.dto.v2.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.domene.ident.Personident

data class AktivereGrunnlagResponseV2(
    val inntekter: InntekterDtoV2,
    val boforhold: BoforholdDtoV2,
    val aktiveGrunnlagsdata: AktiveGrunnlagsdata,
    val ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
)

data class AktivereGrunnlagRequestV2(
    @Schema(description = "Personident tilhørende rolle i behandling grunnlag skal aktiveres for")
    val personident: Personident? = null,
    @Schema(description = "Grunnlagstype som skal aktiveres")
    val grunnlagstype: Grunnlagsdatatype,
    @Schema(description = "Angi om manuelle opplysninger skal overskrives")
    val overskriveManuelleOpplysninger: Boolean = true,
    @Schema(
        description =
            "Ident på person grunnlag gjelder." +
                " Er relevant for blant annet Barnetillegg, Kontantstøtte og Boforhold",
    )
    val gjelderIdent: Personident? = null,
)

data class OppdatereBegrunnelse(
    @Schema(description = "Saksbehandlers begrunnelse", defaultValue = "", type = "String")
    var nyBegrunnelse: String = "",
    @Schema(description = "Id til rollen begrunnelsen gjelder for")
    val rolleid: Long? = null,
)
