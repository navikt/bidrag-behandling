package no.nav.bidrag.behandling.beregning

import arrow.core.Either
import arrow.core.NonEmptyList
import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.beregning.model.BarnetilleggModel
import no.nav.bidrag.behandling.beregning.model.BehandlingBeregningModel
import no.nav.bidrag.behandling.beregning.model.HusstandsBarnPeriodeModel
import no.nav.bidrag.behandling.beregning.model.InntektModel
import no.nav.bidrag.behandling.beregning.model.SivilstandModel
import no.nav.bidrag.behandling.beregning.model.UtvidetbarnetrygdModel
import no.nav.bidrag.behandling.consumer.BeregnForskuddPayload
import no.nav.bidrag.behandling.consumer.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import no.nav.bidrag.behandling.transformers.INFINITY
import no.nav.bidrag.behandling.transformers.toCompactString
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.transformers.toNoString
import no.nav.bidrag.beregn.felles.enums.BostatusKode
import no.nav.bidrag.beregn.felles.enums.SivilstandKode
import no.nav.bidrag.domain.enums.GrunnlagType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
@Service
class ForskuddBeregning {

    private fun prepareSoknadsBarn(soknadBarn: Rolle): List<Grunnlag> =
        listOf(
            Grunnlag(
                referanse = "Mottatt_SoknadsbarnInfo_SB" + soknadBarn.id,
                type = GrunnlagType.SOKNADSBARN_INFO,
                innhold = POJONode(
                    SoknadsBarnNode(
                        soknadsbarnId = soknadBarn.id!!.toInt(),
                        fodselsdato = soknadBarn.fodtDato?.toLocalDate()?.toNoString(),
                    ),
                ),
            ),
        )

    // TODO BostatusKode
    private fun boStatusTypeToBoStatusKode(boStatusType: BoStatusType): BostatusKode =
        if (boStatusType == BoStatusType.REGISTRERT_PA_ADRESSE
        ) {
            BostatusKode.BOR_MED_FORELDRE
        } else {
            BostatusKode.BOR_IKKE_MED_FORELDRE
        }

    private fun prepareBostatus(husstandsBarnPerioder: List<HusstandsBarnPeriodeModel>, soknadsBarnIdent: String, soknadBarn: Rolle): List<Grunnlag> =
        husstandsBarnPerioder
            .filter { soknadsBarnIdent == it.ident }
            .map {
                Grunnlag(
                    referanse = "Mottatt_Bostatus_" + it.datoFom.toCompactString(),
                    type = GrunnlagType.BOSTATUS,
                    innhold = POJONode(
                        BostatusNode(
                            datoFom = it.datoFom.toNoString(),
                            datoTil = it.datoTom?.toNoString(),
                            rolle = "SOKNADSBARN",
                            bostatusKode = it.boStatus?.let { it1 -> boStatusTypeToBoStatusKode(it1).name },
                            soknadsbarnId = soknadBarn.soknadsLinje,
                        ),
                    ),
                )
            }

    private fun prepareBarnIHusstand(behandlingBeregningModel: BehandlingBeregningModel): List<Grunnlag> =
        splitPeriods(behandlingBeregningModel.husstandsBarnPerioder)
            .map {
                Grunnlag(
                    referanse = "Mottatt_BarnIHusstand_" + it.datoFom.replace("-", ""),
                    type = GrunnlagType.BARN_I_HUSSTAND,
                    innhold = POJONode(it),
                )
            }

    private fun prepareInntekterForBeregning(
        inntekter: List<InntektModel>,
        barnetillegg: List<BarnetilleggModel>,
        utvidetbarnetrygd: List<UtvidetbarnetrygdModel>,
    ): List<Grunnlag> =
        inntekter
            .map {
                Grunnlag(
                    referanse = "Mottatt_Inntekt_${it.inntektType}_${it.rolle}_${it.datoFom.toCompactString()}",
                    type = GrunnlagType.INNTEKT,
                    innhold = POJONode(
                        InntektNode(
                            datoFom = it.datoFom.toNoString(),
                            datoTil = it.datoTom?.toNoString(),
                            rolle = it.rolle,
                            inntektType = it.inntektType,
                            belop = it.belop,
                        ),
                    ),
                )
            } + barnetillegg
            .map {
                Grunnlag(
                    referanse = "Mottatt_Inntekt_TG" + it.datoFom.toCompactString(),
                    type = GrunnlagType.INNTEKT,
                    innhold = POJONode(
                        InntektNode(
                            datoFom = it.datoFom.toNoString(),
                            datoTil = it.datoTom?.toNoString(),
                            rolle = "BIDRAGSMOTTAKER",
                            inntektType = "EKSTRA_SMAABARNSTILLEGG",
                            belop = it.belop,
                        ),
                    ),
                )
            } + utvidetbarnetrygd
            .map {
                Grunnlag(
                    referanse = "Mottatt_Inntekt_UB" + it.datoFom.toCompactString(),
                    type = GrunnlagType.INNTEKT,
                    innhold = POJONode(
                        InntektNode(
                            datoFom = it.datoFom.toNoString(),
                            datoTil = it.datoTom?.toNoString(),
                            rolle = "BIDRAGSMOTTAKER",
                            inntektType = "UTVIDET_BARNETRYGD",
                            belop = it.belop,
                        ),
                    ),
                )
            }

    // TODO SivilstandKode
    private fun sivilstandTypeToSivilstandKode(sivilstandType: SivilstandType): SivilstandKode =
        if (sivilstandType == SivilstandType.GIFT) {
            SivilstandKode.GIFT
        } else {
            SivilstandKode.ENSLIG
        }

    private fun prepareSivilstand(sivilstand: List<SivilstandModel>): List<Grunnlag> =
        sivilstand.map {
            Grunnlag(
                referanse = "Mottatt_Sivilstand_" + it.datoFom.toCompactString(),
                type = GrunnlagType.SIVILSTAND,
                innhold = POJONode(
                    SivilstandNode(
                        datoFom = it.datoFom.toNoString(),
                        datoTil = it.datoTom?.toNoString(),
                        sivilstandKode = sivilstandTypeToSivilstandKode(it.sivilstandType).name,
                    ),
                ),
            )
        }

    fun splitPeriods(husstandsBarnPerioder: List<HusstandsBarnPeriodeModel>): List<BarnPeriodeNode> {
        if (husstandsBarnPerioder.isEmpty()) return emptyList()

        val timesMap = HashMap<LocalDate, PointInTimeInfo>()
            .toSortedMap()

        husstandsBarnPerioder.forEach {
            val startDate = it.datoFom

            if (timesMap.contains(startDate)) {
                val existingStart = timesMap[startDate]!!
                existingStart.heads += 1
            } else {
                timesMap[startDate] = PointInTimeInfo(1, 0)
            }

            val endDate = it.datoTom ?: INFINITY
            if (timesMap.contains(endDate)) {
                val existingEnd = timesMap[endDate]!!
                existingEnd.tails += 1
            } else {
                timesMap[endDate] = PointInTimeInfo(0, 1)
            }
        }

        val list = timesMap.map { PointInTime(it.key, it.value) }.toList()

        val r = mutableListOf<BarnPeriodeNode>()

        var lastStartDate: LocalDate = list[0].point
        var currentPeriods: Long = list[0].info.heads

        for (i in 1 until list.size) {
            val nextDate = if (list[i].point == INFINITY) null else list[i].point
            r.add(BarnPeriodeNode(lastStartDate.toNoString(), nextDate?.toNoString(), currentPeriods.toDouble()))
            lastStartDate = list[i].point
            currentPeriods = currentPeriods + list[i].info.heads - list[i].info.tails
        }

        return r
    }

    fun toBehandlingBeregningModel(behandling: Behandling): Either<NonEmptyList<String>, BehandlingBeregningModel> =
        BehandlingBeregningModel.invoke(
            behandlingId = behandling.id,
            virkningsDato = behandling.virkningsDato,
            datoTom = behandling.datoTom,
            sivilstand = behandling.sivilstand,
            inntekter = behandling.inntekter.filter { it.taMed }.toSet(),
            barnetillegg = behandling.barnetillegg,
            utvidetbarnetrygd = behandling.utvidetbarnetrygd,
            husstandsBarn = behandling.husstandsBarn,
            roller = behandling.roller,
        )

    fun toPayload(b: BehandlingBeregningModel, soknadsBarn: Rolle): BeregnForskuddPayload =
        BeregnForskuddPayload(
            beregnDatoFra = b.virkningsDato.toNoString(), // TODO kanskje behandling.datoFom?
            beregnDatoTil = b.datoTom.toNoString(),
            grunnlagListe = prepareSoknadsBarn(soknadsBarn) +
                prepareBarnIHusstand(b) +
                prepareBostatus(b.husstandsBarnPerioder, soknadsBarn.ident, soknadsBarn) +
                prepareInntekterForBeregning(
                    b.inntekter,
                    b.barnetillegg,
                    b.utvidetbarnetrygd,
                ) +
                prepareSivilstand(b.sivilstand),

        )
}

data class PointInTimeInfo(
    var heads: Long = 0,
    var tails: Long = 0,
)

data class PointInTime(
    val point: LocalDate,
    val info: PointInTimeInfo,
)

data class BarnPeriodeNode(
    val datoFom: String,
    val datoTil: String? = null,
    val antall: Double,
)

data class BostatusNode(
    val datoFom: String,
    val datoTil: String? = null,
    val rolle: String,
    val bostatusKode: String?,
    val soknadsbarnId: Int,
)

data class SoknadsBarnNode(
    val soknadsbarnId: Int,
    val fodselsdato: String?,
)

data class InntektNode(
    val datoFom: String?,
    val datoTil: String?,
    val rolle: String?,
    val inntektType: String?,
    val belop: BigDecimal?,
)

data class SivilstandNode(
    val datoFom: String?,
    val datoTil: String?,
    val sivilstandKode: String?,
    val rolle: String = "BIDRAGSMOTTAKER",
)
