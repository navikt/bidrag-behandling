package no.nav.bidrag.behandling.utils

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektPerBarnDto
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonSjablontallPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe

fun List<InntektPerBarnDto>.hentInntektForBarn(barnIdent: String) = find { it.inntektGjelderBarnIdent == Personident(barnIdent) }

fun List<BaseGrunnlag>.harReferanseTilGrunnlag(
    grunnlag: Grunnlagstype,
    baseGrunnlag: BaseGrunnlag,
) = finnGrunnlagSomErReferertAv(grunnlag, baseGrunnlag).isNotEmpty()

fun List<BaseGrunnlag>.validerHarReferanseTilGrunnlagIReferanser(
    grunnlag: Grunnlagstype,
    referanse: List<Grunnlagsreferanse>,
    antall: Int? = null,
) = if (antall != null) {
    finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(grunnlag, referanse).shouldHaveSize(antall)
} else {
    finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(grunnlag, referanse).shouldNotBeEmpty()
}

fun List<BaseGrunnlag>.validerHarGrunnlag(
    grunnlag: Grunnlagstype,
    antall: Int? = null,
) = if (antall != null) {
    filtrerBasertPåEgenReferanse(grunnlag).shouldHaveSize(antall)
} else {
    filtrerBasertPåEgenReferanse(grunnlag).shouldNotBeEmpty()
}

fun List<BaseGrunnlag>.validerHarReferanseTilSjablon(
    sjablon: SjablonTallNavn,
) {
    val grunnlag = filtrerOgKonverterBasertPåEgenReferanse<SjablonSjablontallPeriode>(Grunnlagstype.SJABLON_SJABLONTALL)
    grunnlag.shouldNotBeEmpty()
    grunnlag.any { it.innhold.sjablon == sjablon } shouldBe true
}

fun List<BaseGrunnlag>.validerHarReferanseTilSjablonIReferanser(
    sjablon: SjablonTallNavn,
    referanse: List<Grunnlagsreferanse>,
) {
    val grunnlag = finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<SjablonSjablontallPeriode>(Grunnlagstype.SJABLON_SJABLONTALL, referanse)
    grunnlag.shouldNotBeEmpty()
    grunnlag.first().innhold.sjablon shouldBe sjablon
}
