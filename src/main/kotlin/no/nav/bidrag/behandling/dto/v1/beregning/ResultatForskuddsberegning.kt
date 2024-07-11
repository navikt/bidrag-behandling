package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import java.math.BigDecimal
import java.time.LocalDate

data class ResultatBeregningBarnDto(
    val barn: ResultatRolle,
    val perioder: List<ResultatPeriodeDto>,
) {
    data class ResultatPeriodeDto(
        val periode: ÅrMånedsperiode,
        val beløp: BigDecimal,
        val resultatKode: Resultatkode,
        val regel: String,
        val sivilstand: Sivilstandskode?,
        val inntekt: BigDecimal,
        val antallBarnIHusstanden: Int,
    )
}

data class ResultatForskuddsberegningBarn(
    val barn: ResultatRolle,
    val resultat: BeregnetForskuddResultat,
)

data class ResultatRolle(
    val ident: Personident?,
    val navn: String,
    val fødselsdato: LocalDate,
)

data class ResultatSærbidragsberegning(
    val inntektBM: BigDecimal,
    val inntektBP: BigDecimal,
    val inntektBarn: BigDecimal,
    val andelBp: Double,
    val beregning: UtgiftBeregningDto,
    val resultat: BigDecimal,
    val resultatKode: Resultatkode,
    val antallBarnIHusstanden: Double,
    val voksenIHusstanden: Boolean,
)
