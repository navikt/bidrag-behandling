package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavnIntern
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import java.math.BigDecimal

data class ResultatBeregningBarnDto(
    val barn: ResultatRolle,
    val perioder: List<ResultatPeriodeDto>,
) {
    data class ResultatPeriodeDto(
        val periode: ÅrMånedsperiode,
        val beløp: BigDecimal,
        val resultatKode: Resultatkode,
        val vedtakstype: Vedtakstype?,
        val regel: String,
        val sivilstand: Sivilstandskode?,
        val inntekt: BigDecimal,
        val antallBarnIHusstanden: Int,
    ) {
        @Suppress("unused")
        val resultatkodeVisningsnavn get() = resultatKode.visningsnavnIntern(vedtakstype)
    }
}

data class ResultatForskuddsberegningBarn(
    val barn: ResultatRolle,
    val resultat: BeregnetForskuddResultat,
)
