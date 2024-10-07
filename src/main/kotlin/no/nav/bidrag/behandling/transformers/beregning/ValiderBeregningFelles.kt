package no.nav.bidrag.behandling.transformers.beregning

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.transformers.erDatoForUtgiftForeldet
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilGrunnlagUtgift
import no.nav.bidrag.behandling.transformers.vedtak.særbidragDirekteAvslagskoderSomInneholderUtgifter
import no.nav.bidrag.beregn.særbidrag.ValiderSærbidragForBeregningService
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.nio.charset.Charset

fun BeregnetSærbidragResultat.validerForSærbidrag() {
    val feilListe = mutableListOf<String>()
    val sluttberegninger =
        grunnlagListe
            .toList()
            .filtrerBasertPåEgenReferanse(
                no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG,
            )

    if (sluttberegninger.size != 1) {
        feilListe.add("Det er flere enn 1 eller ingen sluttberegninger i beregningsgrunnlaget.")
    }
    if (feilListe.isNotEmpty()) {
        secureLogger.warn {
            "Feil ved validering beregning av særbidrag" +
                commonObjectmapper.writeValueAsString(feilListe)
        }
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Feil ved validering av beregning av særbidrag",
            commonObjectmapper.writeValueAsBytes(feilListe),
            Charset.defaultCharset(),
        )
    }
}

fun Resultatkode?.erAvslagSomInneholderUtgifter(): Boolean {
    if (this == null) return false
    return særbidragDirekteAvslagskoderSomInneholderUtgifter.contains(this)
}

@Deprecated("Bruk heller metoden som ligger i validerBeregning bønnen")
fun Behandling.tilSærbidragAvslagskode(): Resultatkode? {
    if (avslag != null || utgift == null || utgift?.utgiftsposter?.isEmpty() == true) return avslag
    val service = ValiderSærbidragForBeregningService()
    val delberegningUtgift = tilGrunnlagUtgift().innholdTilObjekt<DelberegningUtgift>()
    return when {
        utgift?.utgiftsposter?.all { erDatoForUtgiftForeldet(it.dato) } == true -> Resultatkode.ALLE_UTGIFTER_ER_FORELDET
        else -> service.validerForBeregning(vedtakstype, delberegningUtgift)
    }
}
