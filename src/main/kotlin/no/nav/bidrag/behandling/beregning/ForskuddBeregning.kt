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
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.INFINITY
import no.nav.bidrag.behandling.transformers.toCompactString
import no.nav.bidrag.behandling.transformers.toNoString
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.Grunnlag
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ForskuddBeregning {
    fun lagePersonobjektForSøknadsbarn(
        soknadBarn: Rolle,
        fødselsdato: LocalDate,
    ): Grunnlag =
        Grunnlag(
            referanse = "Mottatt_SoknadsbarnInfo_SB" + soknadBarn.id,
            type = Grunnlagstype.PERSON,
            innhold =
            POJONode(
                SoknadsBarnNode(
                    ident = soknadBarn.ident ?: "",
                    navn = soknadBarn.navn ?: "",
                    fødselsdato = fødselsdato.toNoString(),
                ),
            ),
        )

    private fun prepareBostatus(
        husstandsBarnPerioder: List<HusstandsBarnPeriodeModel>,
        søknadsbarn: Grunnlag,
    ): List<Grunnlag> =
        husstandsBarnPerioder
            .filter { søknadsbarn.referanse == it.referanseTilBarn }
            .map {
                Grunnlag(
                    referanse = "Mottatt_Bostatus_" + it.datoFom.toCompactString(),
                    type = Grunnlagstype.BOSTATUS,
                    innhold =
                    POJONode(
                        BostatusNode(
                            datoFom = it.datoFom.toNoString(),
                            datoTil = it.datoTom?.toNoString(),
                            rolle = "SOKNADSBARN",
                            bostatusKode = it.bostatus?.name,
                        ),
                    ),
                )
            }

    private fun prepareBarnIHusstand(behandlingBeregningModel: BehandlingBeregningModel): List<Grunnlag> =
        splitPeriods(behandlingBeregningModel.husstandsBarnPerioder)
            .map {
                Grunnlag(
                    referanse = "Mottatt_BarnIHusstand_" + it.datoFom.replace("-", ""),
                    type = Grunnlagstype.BARN_I_HUSSTAND,
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
                    type = Grunnlagstype.INNTEKT,
                    innhold =
                    POJONode(
                        InntektNode(
                            datoFom = it.datoFom.toNoString(),
                            datoTil = it.datoTom?.toNoString(),
                            rolle = it.rolle,
                            inntektType = it.inntektType,
                            belop = it.belop,
                        ),
                    ),
                )
            } +
                barnetillegg
                    .map {
                        Grunnlag(
                            referanse = "Mottatt_Inntekt_TG" + it.datoFom.toCompactString(),
                            type = Grunnlagstype.INNTEKT,
                            innhold =
                            POJONode(
                                InntektNode(
                                    datoFom = it.datoFom.toNoString(),
                                    datoTil = it.datoTom?.toNoString(),
                                    rolle = "BIDRAGSMOTTAKER",
                                    inntektType = "EKSTRA_SMAABARNSTILLEGG",
                                    belop = it.belop,
                                ),
                            ),
                        )
                    } +
                utvidetbarnetrygd
                    .map {
                        Grunnlag(
                            referanse = "Mottatt_Inntekt_UB" + it.datoFom.toCompactString(),
                            type = Grunnlagstype.INNTEKT,
                            innhold =
                            POJONode(
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

    private fun prepareSivilstand(sivilstand: List<SivilstandModel>): List<Grunnlag> =
        sivilstand.map {
            Grunnlag(
                referanse = "Mottatt_Sivilstand_" + it.datoFom.toCompactString(),
                type = Grunnlagstype.SIVILSTAND,
                innhold =
                POJONode(
                    SivilstandNode(
                        datoFom = it.datoFom.toNoString(),
                        datoTil = it.datoTom?.toNoString(),
                        sivilstand = it.sivilstand.name,
                    ),
                ),
            )
        }

    fun splitPeriods(husstandsBarnPerioder: List<HusstandsBarnPeriodeModel>): List<BarnPeriodeNode> {
        if (husstandsBarnPerioder.isEmpty()) return emptyList()

        val timesMap =
            HashMap<LocalDate, PointInTimeInfo>()
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
            r.add(
                BarnPeriodeNode(
                    lastStartDate.toNoString(),
                    nextDate?.toNoString(),
                    currentPeriods.toDouble(),
                ),
            )
            lastStartDate = list[i].point
            currentPeriods = currentPeriods + list[i].info.heads - list[i].info.tails
        }

        return r.filter { it.antall != 0.0 }
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

    fun toPayload(
        b: BehandlingBeregningModel,
        søknadsbarn: Grunnlag,
    ): BeregnGrunnlag =
        BeregnGrunnlag(
            periode = ÅrMånedsperiode(b.virkningsDato, b.datoTom),
            søknadsbarnReferanse = søknadsbarn.referanse,
            grunnlagListe =
            listOf(søknadsbarn) +
                    prepareBarnIHusstand(b) +
                    prepareBostatus(b.husstandsBarnPerioder, søknadsbarn) +
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
)

data class SoknadsBarnNode(
    val ident: String,
    val navn: String,
    val fødselsdato: String,
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
    val sivilstand: String?,
    val rolle: String = "BIDRAGSMOTTAKER",
)
