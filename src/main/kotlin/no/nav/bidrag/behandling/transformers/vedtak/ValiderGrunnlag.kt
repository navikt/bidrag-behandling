package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.felles.toCompactString

fun OpprettVedtakRequestDto.validerGrunnlagsreferanser() {
    val feilListe = mutableListOf<String>()

//    val grunnlagreferanserGruppert = grunnlagListe.groupingBy { it.referanse }
//    val harDuplikater = grunnlagreferanserGruppert.eachCount().any { it.value > 1 }
//    if (harDuplikater) {
//        val referanserSomErDuplikat =
//            grunnlagreferanserGruppert.eachCount().filter { it.value > 1 }.keys
//        feilListe.add(
//            "Grunnlagslisten har duplikat grunnlagsreferanser for følgende referanse: ${
//                referanserSomErDuplikat.joinToString(
//                    ",",
//                )
//            }",
//        )
//    }
    grunnlagListe.forEach {
        feilListe.addAll(
            grunnlagListe.validerInneholderListe(
                it.grunnlagsreferanseListe,
                it.referanse,
            ),
        )
        it.gjelderReferanse?.let { gjelderReferanse ->
            feilListe.addAll(
                grunnlagListe.validerInneholderListe(
                    listOf(gjelderReferanse),
                    it.referanse,
                ),
            )
        }
    }
    stønadsendringListe.forEach {
        feilListe.addAll(
            grunnlagListe.validerInneholderListe(
                it.grunnlagReferanseListe,
                "Stønadsendring(${it.type}, ${it.kravhaver})",
            ),
        )
        it.periodeListe.forEach { periode ->
            feilListe.addAll(
                grunnlagListe.validerInneholderListe(
                    periode.grunnlagReferanseListe,
                    "Stønadsendring(${it.type}, ${it.kravhaver}, ${periode.periode.fom.toCompactString()})",
                ),
            )
        }
    }

    engangsbeløpListe.forEach {
        feilListe.addAll(
            grunnlagListe.validerInneholderListe(
                it.grunnlagReferanseListe,
                "Engangsbeløp(${it.type}, ${it.mottaker})",
            ),
        )
    }

    if (feilListe.isNotEmpty()) {
        throw RuntimeException(
            "Feil i grunnlagsreferanser: ${
                feilListe.toSet().joinToString("\n")
            }",
        )
    }
}

fun List<BaseGrunnlag>.validerInneholderListe(
    referanseliste: List<Grunnlagsreferanse>,
    referertAv: Grunnlagsreferanse?,
): List<String> {
    val feilListe = mutableListOf<String>()
    referanseliste.forEach {
        feilListe.addAll(validerGrunnlagsreferanse(it, referertAv))
    }

    return feilListe
}

private fun List<BaseGrunnlag>.validerGrunnlagsreferanse(
    referanse: Grunnlagsreferanse,
    referertAv: Grunnlagsreferanse? = null,
): List<String> {
    val feilListe = mutableListOf<String>()
    val grunnlag = filtrerBasertPåEgenReferanse(referanse = referanse)
    if (grunnlag.isEmpty()) {
        feilListe.add("Grunnlaget med referanse \"$referanse\" referert av \"$referertAv\" finnes ikke i grunnlagslisten")
    }
    grunnlag.forEach {
        val grunnlagsreferanser =
            it.grunnlagsreferanseListe + listOf(it.gjelderReferanse).filterNotNull()

        if (grunnlagsreferanser.contains(referertAv)) {
            feilListe.add(
                "Grunnlaget med referanse \"$referanse\" referert av \"$referertAv\" inneholder sirkulær avhengighet. " +
                    "Referanseliste $grunnlagsreferanser",
            )
        } else {
            grunnlagsreferanser.forEach {
                feilListe.addAll(validerGrunnlagsreferanse(it, referanse))
            }
        }
    }
    return feilListe
}
