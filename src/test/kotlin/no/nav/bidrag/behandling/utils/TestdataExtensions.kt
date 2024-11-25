package no.nav.bidrag.behandling.utils

import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.InntektPerBarn
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv

fun List<InntektPerBarn>.hentInntektForBarn(barnIdent: String) = find { it.inntektGjelderBarnIdent == Personident(barnIdent) }

fun List<BaseGrunnlag>.harReferanseTilGrunnlag(
    grunnlag: Grunnlagstype,
    baseGrunnlag: BaseGrunnlag,
) = finnGrunnlagSomErReferertAv(grunnlag, baseGrunnlag).isNotEmpty()
