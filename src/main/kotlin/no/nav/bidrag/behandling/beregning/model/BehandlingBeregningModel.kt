package no.nav.bidrag.behandling.beregning.model

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

fun Set<Rolle>.rolleType(ident: String): String {
    val rolleType = this.find { it.ident == ident }?.rolleType
    return when (rolleType) {
        Rolletype.BIDRAGSPLIKTIG -> "BIDRAGSPLIKTIG"
        Rolletype.BIDRAGSMOTTAKER -> "BIDRAGSMOTTAKER"
        else -> rolleType?.name ?: "BIDRAGSMOTTAKER"
    }
}

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
            roller: Set<Rolle>,
        ): Either<NonEmptyList<String>, BehandlingBeregningModel> =
            either {
                zipOrAccumulate(
                    {
                        ensure(behandlingId != null) { raise("Behandling id kan ikke være null") }
                        behandlingId
                    },
                    {
                        ensure(virkningsDato != null) { raise("Behandling virkningsDato må fylles ut") }
                        virkningsDato.toLocalDate()
                    },
                    {
                        ensure(datoTom != null) { raise("Behandling Dato Til må fylles ut") }
                        datoTom.toLocalDate()
                    },
                    {
                        mapOrAccumulate(sivilstand) {
                            SivilstandModel(
                                it.datoFom?.toLocalDate()
                                    ?: raise("Sivilstand Dato Fra må fylles ut"),
                                it.datoTom?.toLocalDate(),
                                it.sivilstand,
                            )
                        }
                    },
                    {
                        mapOrAccumulate(inntekter.filter { it.taMed }) {
                            InntektModel(
                                inntektType =
                                it.inntektType
                                    ?: raise("InntektType kan ikke være null"),
                                belop = it.belop,
                                rolle = roller.rolleType(it.ident),
                                datoFom =
                                it.datoFom?.toLocalDate()
                                    ?: raise("Inntekts Dato Fra må fylles ut"),
                                datoTom = it.datoTom?.toLocalDate(),
                            )
                        }
                    },
                    {
                        mapOrAccumulate(barnetillegg) {
                            BarnetilleggModel(
                                datoFom =
                                it.datoFom?.toLocalDate()
                                    ?: raise("Barnetillegg Dato Fra må fylles ut"),
                                datoTom = it.datoTom?.toLocalDate(),
                                belop = it.barnetillegg,
                            )
                        }
                    },
                    {
                        mapOrAccumulate(utvidetbarnetrygd) {
                            UtvidetbarnetrygdModel(
                                datoFom =
                                it.datoFom?.toLocalDate()
                                    ?: raise("Utvidetbarnetrygd Dato Fra må fylles ut"),
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
                                datoFom =
                                it.datoFom?.toLocalDate()
                                    ?: raise("HusstandsBarnPeriode Dato Fra må fylles ut"),
                                datoTom = it.datoTom?.toLocalDate(),
                                referanseTilBarn = it.husstandsBarn.ident,
                                bostatus = it.bostatus,
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
    val referanseTilBarn: String?,
    val bostatus: Bostatuskode?,
    // TODO ENDRE til bostatusKode fra felles
    // import no.nav.bidrag.beregn.felles.enums.BostatusKode
)

data class SivilstandModel(
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
    val sivilstand: Sivilstandskode,
)

data class InntektModel(
    val inntektType: String,
    val belop: BigDecimal,
    val rolle: String,
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
