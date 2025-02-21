package no.nav.bidrag.behandling.utils

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto

fun OpprettVedtakRequestDto.hentGrunnlagstyperForReferanser(
    grunnlagstype: Grunnlagstype,
    referanseListe: List<Grunnlagsreferanse>,
) = grunnlagListe.filter { it.type == grunnlagstype && referanseListe.contains(it.referanse) }

fun List<BaseGrunnlag>.grunnlagstyperForReferanser(
    grunnlagstype: Grunnlagstype,
    referanseListe: List<Grunnlagsreferanse>,
) = filter { it.type == grunnlagstype && referanseListe.contains(it.referanse) }

fun OpprettVedtakRequestDto.hentGrunnlagstyper(grunnlagstype: Grunnlagstype) = grunnlagListe.filter { it.type == grunnlagstype }

fun OpprettVedtakRequestDto.hentGrunnlagstype(
    grunnlagstype: Grunnlagstype,
    gjelderReferanse: String? = null,
) = grunnlagListe.find {
    it.type == grunnlagstype && (gjelderReferanse == null || it.gjelderReferanse == gjelderReferanse)
}

fun OpprettVedtakRequestDto.hentNotat(
    notatType: NotatGrunnlag.NotatType,
    gjelderReferanse: String? = null,
) = grunnlagListe.find {
    it.type == Grunnlagstype.NOTAT && (gjelderReferanse == null || it.gjelderReferanse == gjelderReferanse) && it.innholdTilObjekt<NotatGrunnlag>().type == notatType
}

fun List<OpprettGrunnlagRequestDto>.hentPerson(ident: String) =
    hentGrunnlagstyper("PERSON_")
        .find { grunnlag -> grunnlag.innholdTilObjekt<Person>().ident?.verdi == ident }

fun List<OpprettGrunnlagRequestDto>.shouldContainPerson(
    ident: String,
    navn: String? = null,
) {
    withClue("Should have person with ident $ident and name $navn") {
        val person =
            filter { it.type.name.startsWith("PERSON_") }
                .map { it.innholdTilObjekt<Person>() }
                .find { it.ident?.verdi == ident && (navn == null || it.navn == navn) }
        person shouldNotBe null
    }
}

fun List<OpprettGrunnlagRequestDto>.hentGrunnlagstyper(grunnlagstype: Grunnlagstype) = filter { it.type == grunnlagstype }

fun List<OpprettGrunnlagRequestDto>.hentGrunnlagstyper(prefix: String) = filter { it.type.name.startsWith(prefix) }

val List<OpprettGrunnlagRequestDto>.søknad get() = find { it.type == Grunnlagstype.SØKNAD }
val List<OpprettGrunnlagRequestDto>.virkningsdato get() = find { it.type == Grunnlagstype.VIRKNINGSTIDSPUNKT }
