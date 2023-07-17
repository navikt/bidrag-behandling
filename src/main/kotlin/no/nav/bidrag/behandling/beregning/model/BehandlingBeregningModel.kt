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
                            it.gyldigFraOgMed?.toLocalDate() ?: raise("Sivilstands gyldigFraOgMed kan ikke være null"),
                            it.datoTom?.toLocalDate() ?: raise("Sivilstands datoTom kan ikke være null"),
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
                            datoTom = it.datoTom?.toLocalDate() ?: raise("Inntekts datoTom kan ikke være null"),
                        )
                    }
                },
                {
                    mapOrAccumulate(barnetillegg) {
                        BarnetilleggModel(
                            datoFom = it.datoFom?.toLocalDate() ?: raise("Barnetillegg datoFom kan ikke være null"),
                            datoTom = it.datoTom?.toLocalDate() ?: raise("Barnetillegg datoTom kan ikke være null"),
                            belop = it.barnetillegg,
                        )
                    }
                },
                {
                    mapOrAccumulate(utvidetbarnetrygd) {
                        UtvidetbarnetrygdModel(
                            datoFom = it.datoFom?.toLocalDate() ?: raise("Utvidetbarnetrygd datoFom kan ikke være null"),
                            datoTom = it.datoTom?.toLocalDate() ?: raise("Utvidetbarnetrygd datoTom kan ikke være null"),
                            belop = it.belop,
                        )
                    }
                },
                {
                    mapOrAccumulate(
                        husstandsBarn.filter { it.medISaken }
                            .map { it.perioder.filter { it.boStatus == BoStatusType.DOKUMENTERT_BOENDE_HOS_BM } }
                            .flatten(),
                    ) {
                        HusstandsBarnPeriodeModel(
                            fraDato = it.fraDato?.toLocalDate() ?: raise("HusstandsBarnPeriode fraDato kan ikke være null"),
                            tilDato = it.tilDato?.toLocalDate() ?: raise("HusstandsBarnPeriode tilDato kan ikke være null"),
                            ident = it.husstandsBarn.ident,
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
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val ident: String?,
)

data class SivilstandModel(
    val gyldigFraOgMed: LocalDate,
    val datoTom: LocalDate,
    val sivilstandType: SivilstandType,
)

data class InntektModel(
    val inntektType: String,
    val belop: BigDecimal,
    val datoFom: LocalDate,
    val datoTom: LocalDate,
)

data class BarnetilleggModel(
    val datoFom: LocalDate,
    val datoTom: LocalDate,
    val belop: BigDecimal,
)

data class UtvidetbarnetrygdModel(
    val datoFom: LocalDate,
    val datoTom: LocalDate,
    val belop: BigDecimal,
)
