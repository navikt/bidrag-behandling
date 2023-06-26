package no.nav.bidrag.behandling.controller

import arrow.core.Either
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.POJONode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import no.nav.bidrag.behandling.consumer.BeregnForskuddPayload
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.consumer.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.ForskuddDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.behandling.transformers.toNoString
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.math.BigDecimal
import java.time.LocalDate

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestController
class BehandlingBeregnForskuddController(
    private val behandlingService: BehandlingService,
    private val bidragBeregnForskuddConsumer: BidragBeregnForskuddConsumer,
) {

    @Suppress("unused")
    @PostMapping("/behandling/{behandlingId}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun beregnForskudd(@PathVariable behandlingId: Long): List<ForskuddDto> {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        val results = behandling
            .roller
            .filter { RolleType.BARN == it.rolleType }
            .map {
                Either.catch {
                    val payload = preparePayload(behandling, it)

                    if (false) printDebugPayload(payload)

                    bidragBeregnForskuddConsumer.beregnForskudd(payload)
                }
            }

        results
            .mapNotNull { it.leftOrNull() }
            .forEach {
                // LOGGING Errors here
                LOGGER.warn { it }
            }

        return results
            .mapNotNull { it.getOrNull() }
    }

    private fun printDebugPayload(payload: BeregnForskuddPayload) {
        val message = HttpEntity(payload)
        val objectMapper = ObjectMapper()

        objectMapper.writeValue(System.out, message.body)
    }

    fun prepareSoknadsBarn(behandling: Behandling, soknadBarn: Rolle): List<Grunnlag> =
        listOf(
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "SOKNADSBARN_INFO",
                innhold = POJONode(
                    SoknadsBarnNode(
                        soknadsbarnId = soknadBarn.soknadsLinje,
                        fodselsdato = soknadBarn.fodtDato?.toLocalDate()?.toNoString(),
                    ),
                ),
            ),
        )

    fun prepareBostatus(behandling: Behandling, soknadsBarn: Rolle): List<Grunnlag> =
        behandling
            .husstandsBarn
            .filter { soknadsBarn.ident == it.ident }
            .flatMap { it.perioder }
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1",
                    type = "BOSTATUS",
                    innhold = POJONode(
                        BostatusNode(
                            datoTil = it.fraDato.toLocalDate().toNoString(),
                            datoFom = it.tilDato.toLocalDate().toNoString(),
                            rolle = "SOKNADSBARN",
                            bostatusKode = "BOR_MED_FORELDRE", // TODO boStatus -> bostatusKode
                        ),
                    ),
                )
            }

    fun prepareBarnIHusstand(behandling: Behandling): List<Grunnlag> =
        splitPeriods(
            behandling
                .husstandsBarn
                .filter { it.medISaken }
                .map { it.perioder.filter { it.boStatus == BoStatusType.DOKUMENTERT_BOENDE_HOS_BM } }.flatten(),
        )
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1",
                    type = "BARN_I_HUSSTAND",
                    innhold = POJONode(it),
                )
            }

    fun prepareInntekterForBeregning(behandling: Behandling): List<Grunnlag> =
        behandling.inntekter
            .filter { it.taMed }
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1",
                    type = "INNTEKT",
                    innhold = POJONode(
                        InntektNode(
                            datoFom = it.datoFom?.toLocalDate()?.toNoString(),
                            datoTil = it.datoTom?.toLocalDate()?.toNoString(),
                            rolle = "BIDRAGSMOTTAKER",
                            inntektType = it.inntektType,
                            belop = it.belop,
                        ),
                    ),
                )
            } + behandling.barnetillegg.map {
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "INNTEKT",
                innhold = POJONode(
                    InntektNode(
                        datoFom = it.datoFom.toLocalDate().toNoString(),
                        datoTil = it.datoTom.toLocalDate().toNoString(),
                        rolle = "BIDRAGSMOTTAKER",
                        inntektType = "EKSTRA_SMAABARNSTILLEGG",
                        belop = it.barnetillegg,
                    ),
                ),
            )
        } + behandling.utvidetbarnetrygd.map {
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "INNTEKT",
                innhold = POJONode(
                    InntektNode(
                        datoFom = it.datoFom.toLocalDate().toNoString(),
                        datoTil = it.datoTom.toLocalDate().toNoString(),
                        rolle = "BIDRAGSMOTTAKER",
                        inntektType = "UTVIDET_BARNETRYGD",
                        belop = it.belop,
                    ),
                ),
            )
        }

    fun prepareSivilstand(behandling: Behandling): List<Grunnlag> =
        behandling.sivilstand.map {
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "SIVILSTAND",
                innhold = POJONode(
                    SivilstandNode(
                        datoFom = it.gyldigFraOgMed?.toLocalDate()?.toNoString(),
                        datoTil = it.datoTom?.toLocalDate()?.toNoString(),
                        sivilstandKode = it.sivilstandType.name,
                    ),
                ),
            )
        }

    fun splitPeriods(husstandsBarnPerioder: List<HusstandsBarnPeriode>): List<BarnPeriodeNode> {
        val timesMap = HashMap<LocalDate, PointInTimeInfo>()
            .toSortedMap()

        husstandsBarnPerioder.forEach {
            val startDate = it.fraDato.toLocalDate()
            if (timesMap.contains(startDate)) {
                val existingStart = timesMap[startDate]!!
                existingStart.heads += 1
            } else {
                timesMap[startDate] = PointInTimeInfo(1, 0)
            }

            val endDate = it.tilDato.toLocalDate()
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
            r.add(BarnPeriodeNode(lastStartDate.toNoString(), list[i].point.toNoString(), currentPeriods.toDouble()))
            lastStartDate = list[i].point
            currentPeriods = currentPeriods + list[i].info.heads - list[i].info.tails
        }

        return r
    }

    fun preparePayload(behandling: Behandling, soknadsBarn: Rolle): BeregnForskuddPayload =
        BeregnForskuddPayload(
            beregnDatoFra = behandling.virkningsDato?.toLocalDate()?.toNoString(), // TODO kanskje behandling.datoFom?
            beregnDatoTil = behandling.datoTom.toLocalDate().toNoString(),
            grunnlagListe = prepareSoknadsBarn(behandling, soknadsBarn) +
                prepareBarnIHusstand(behandling) +
                prepareBostatus(behandling, soknadsBarn) +
                prepareInntekterForBeregning(behandling) +
                prepareSivilstand(behandling),

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
    val datoTil: String,
    val antall: Double,
)

data class BostatusNode(
    val datoFom: String,
    val datoTil: String,
    val rolle: String,
    val bostatusKode: String,
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
