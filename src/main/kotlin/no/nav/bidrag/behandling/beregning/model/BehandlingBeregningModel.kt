package no.nav.bidrag.behandling.beregning.model

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.mapOrAccumulate
import arrow.core.raise.zipOrAccumulate
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.transformers.toLocalDate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

data class BehandlingBeregningModel private constructor(
    val behandlingId: Long,
    val virkningsDato: LocalDate,
    val datoTom: LocalDate,
    val sivilstand: List<SivilstandModel>,
    val inntekter: List<InntektModel>,
    val barnetillegg: List<BarnetilleggModel>,
    val utvidetbarnetrygd: List<UtvidetbarnetrygdModel>,
    val husstandsBarnPerioder: List<HusstandsBarnPeriodeModel>,
) {
    companion object {
        operator fun invoke(
            behandlingId: Long?,
            virkningsDato: Date?,
            datoTom: Date?,
            sivilstand: Set<Sivilstand>,
            inntekter: Set<Inntekt>,
            barnetillegg: Set<Barnetillegg>,
            utvidetbarnetrygd: Set<Utvidetbarnetrygd>,
            husstandsBarn: Set<HusstandsBarn>,
        ): Either<NonEmptyList<String>, BehandlingBeregningModel> = either {
            zipOrAccumulate(
                {
                    ensure(behandlingId != null) { raise("Behandling id kan ikke være null") }
                    behandlingId
                },
                {
                    ensure(virkningsDato != null) { raise("Behandling virkningsDato kan ikke være null") }
                    virkningsDato.toLocalDate()
                },
                {
                    ensure(datoTom != null) { raise("Behandling datoTom kan ikke være null") }
                    datoTom.toLocalDate()
                },
                {
                    mapOrAccumulate(sivilstand) {
                        SivilstandModel(
                            it.datoFom?.toLocalDate() ?: raise("Sivilstands datoFom kan ikke være null"),
                            it.datoTom?.toLocalDate(),
                            it.sivilstandType,
                        )
                    }
                },
                {
                    mapOrAccumulate(inntekter.filter { it.taMed }) {
                        InntektModel(
                            inntektType = it.inntektType ?: "INNTEKTSOPPLYSNINGER_ARBEIDSGIVER", // TODO -> DETTE ER KUN MIDLERTIDIG
//                            inntektType = it.inntektType ?: raise("InntektType kan ikke være null"),
                            belop = it.belop,
                            datoFom = it.datoFom?.toLocalDate() ?: raise("Inntekts datoFom kan ikke være null"),
                            datoTom = it.datoTom?.toLocalDate(),
                        )
                    }
                },
                {
                    mapOrAccumulate(barnetillegg) {
                        BarnetilleggModel(
                            datoFom = it.datoFom?.toLocalDate() ?: raise("Barnetillegg datoFom kan ikke være null"),
                            datoTom = it.datoTom?.toLocalDate(),
                            belop = it.barnetillegg,
                        )
                    }
                },
                {
                    mapOrAccumulate(utvidetbarnetrygd) {
                        UtvidetbarnetrygdModel(
                            datoFom = it.datoFom?.toLocalDate() ?: raise("Utvidetbarnetrygd datoFom kan ikke være null"),
                            datoTom = it.datoTom?.toLocalDate(),
                            belop = it.belop,
                        )
                    }
                },
                {
                    mapOrAccumulate(
                        husstandsBarn.filter { it.medISaken }
                            .flatMap { it.perioder },
                    ) {
                        HusstandsBarnPeriodeModel(
                            datoFom = it.datoFom?.toLocalDate() ?: raise("HusstandsBarnPeriode datoFom kan ikke være null"),
                            datoTom = it.datoTom?.toLocalDate(),
                            ident = it.husstandsBarn.ident,
                            boStatus = it.boStatus,
                        )
                    }
                },
            ) { behandlingId, virkningsDato, datoTom, sivilstand, inntekter, barnetillegg, utvidetbarnetrygd, husstandsBarnPerioder ->
                BehandlingBeregningModel(
                    behandlingId,
                    virkningsDato,
                    datoTom,
                    sivilstand,
                    inntekter,
                    barnetillegg,
                    utvidetbarnetrygd,
                    husstandsBarnPerioder,
                )
            }
        }
    }
}

data class HusstandsBarnPeriodeModel(
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
    val ident: String?,
    val boStatus: BoStatusType,
    // TODO ENDRE til bostatusKode fra felles
    // import no.nav.bidrag.beregn.felles.enums.BostatusKode
)

data class SivilstandModel(
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
    val sivilstandType: SivilstandType,
)

data class InntektModel(
    val inntektType: String,
    val belop: BigDecimal,
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
)

data class BarnetilleggModel(
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
    val belop: BigDecimal,
)

data class UtvidetbarnetrygdModel(
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
    val belop: BigDecimal,
)
