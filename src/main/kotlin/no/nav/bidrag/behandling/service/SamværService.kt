package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.SamværRepository
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværsperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.SletteSamværsperiodeElementDto
import no.nav.bidrag.behandling.dto.v2.samvær.valider
import no.nav.bidrag.behandling.transformers.samvær.tilOppdaterSamværResponseDto
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.delberegningSamværsklasse
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@Service
@Import(BeregnSamværsklasseApi::class)
class SamværService(
    private val samværRepository: SamværRepository,
    private val behandlingRepository: BehandlingRepository,
    private val notatService: NotatService,
    private val beregnSamværsklasseApi: BeregnSamværsklasseApi,
) {
    fun oppdaterSamvær(
        behandlingsid: Long,
        request: OppdaterSamværDto,
    ): OppdaterSamværResponsDto {
        val behandling = behandlingRepository.findBehandlingById(behandlingsid).get()
        log.info { "Oppdaterer samvær for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer samvær for behandling $behandlingsid, forespørsel=$request" }
        val oppdaterSamvær = behandling.samvær.finnSamværForBarn(request.gjelderBarn)
        request.valider(oppdaterSamvær.rolle.opphørsdato)

        request.run {
            periode?.let { oppdaterPeriode(it, oppdaterSamvær) }
            oppdatereBegrunnelse?.let {
                notatService.oppdatereNotat(
                    behandling,
                    NotatGrunnlag.NotatType.SAMVÆR,
                    it.henteNyttNotat() ?: "",
                    oppdaterSamvær.rolle,
                )
            }
        }

        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    private fun oppdaterPeriode(
        request: OppdaterSamværsperiodeDto,
        oppdaterSamvær: Samvær,
    ) {
        val oppdatertSamværsklasse =
            request.beregning?.let { beregnSamværsklasse(it).samværsklasse } ?: request.samværsklasse
        if (request.id == null) {
            val nyPeriode =
                Samværsperiode(
                    oppdaterSamvær,
                    request.periode.fom,
                    request.periode.tom ?: justerPeriodeTomOpphørsdato(oppdaterSamvær.rolle.opphørsdato),
                    oppdatertSamværsklasse!!,
                    beregningJson = request.beregning.tilJsonString(),
                )
            oppdaterSamvær.perioder
                .maxByOrNull { it.fom }
                ?.takeIf { it.fom.isBefore(nyPeriode.fom) && it.fom.isBefore(LocalDate.now().withDayOfMonth(1)) }
                ?.let {
                    it.tom = it.tom ?: nyPeriode.fom.minusDays(1)
                }
            oppdaterSamvær.perioder.add(nyPeriode)
        } else {
            val oppdaterPeriode =
                oppdaterSamvær.perioder.find { it.id == request.id }
                    ?: ugyldigForespørsel("Fant ikke samværsperiode med id ${request.id} i samvær ${oppdaterSamvær.id}")
            oppdaterPeriode.fom = request.periode.fom
            oppdaterPeriode.tom = request.periode.tom
            oppdaterPeriode.beregningJson = request.beregning.tilJsonString()
            oppdaterPeriode.samværsklasse = oppdatertSamværsklasse ?: oppdaterPeriode.samværsklasse
        }
    }

    fun slettPeriode(
        behandlingsid: Long,
        request: SletteSamværsperiodeElementDto,
    ): OppdaterSamværResponsDto {
        val behandling = behandlingRepository.findBehandlingById(behandlingsid).get()
        log.info { "Sletter samværsperiode $request for behandling $behandlingsid" }
        val oppdaterSamvær =
            behandling.samvær.finnSamværForBarn(request.gjelderBarn)
        val slettPeriode =
            oppdaterSamvær.hentPeriode(request.samværsperiodeId)

        oppdaterSamvær.perioder.remove(slettPeriode)
        log.info { "Slettet samværsperiode ${slettPeriode.id} fra samvær ${oppdaterSamvær.id} i behandling $behandlingsid" }
        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    fun beregnSamværsklasse(kalkulator: SamværskalkulatorDetaljer): DelberegningSamværsklasse =
        beregnSamværsklasseApi.beregnSamværsklasse(kalkulator).delberegningSamværsklasse

    private fun SamværskalkulatorDetaljer?.tilJsonString() = this?.let { commonObjectmapper.writeValueAsString(it) }

    private fun MutableSet<Samvær>.finnSamværForBarn(gjelderBarn: String) =
        find { it.rolle.ident == gjelderBarn }
            ?: ugyldigForespørsel("Fant ikke samvær for barn $gjelderBarn i behandling med id ${firstOrNull()?.behandling?.id}")

    private fun Samvær.hentPeriode(id: Long) =
        perioder.find { it.id == id }
            ?: ugyldigForespørsel("Fant ikke samværsperiode med id $id i samvær $id")

    fun rekalkulerPerioderSamvær(
        behandlingsid: Long,
        opphørSlettet: Boolean = false,
        forrigeVirkningstidspunkt: LocalDate? = null,
    ) {
        val behandling = behandlingRepository.findBehandlingById(behandlingsid).get()
        val virkningstidspunkt = behandling.virkningstidspunkt ?: return

        behandling.samvær.forEach {
            // Antar at opphørsdato er måneden perioden skal opphøre
            val opphørsdato = it.rolle.opphørsdato
            it.perioder
                .filter { it.fom < virkningstidspunkt }
                .forEach { periode ->
                    if (periode.tom != null && virkningstidspunkt >= periode.tom) {
                        it.perioder.remove(periode)
                    } else {
                        periode.fom = virkningstidspunkt
                    }
                }
            it.perioder.filter { it.fom == forrigeVirkningstidspunkt }.forEach { periode ->
                periode.fom = virkningstidspunkt
            }
            if (opphørsdato != null) {
                it.perioder
                    .filter { it.fom > opphørsdato }
                    .forEach { periode ->
                        it.perioder.remove(periode)
                    }
                it.perioder
                    .maxByOrNull { it.fom }
                    ?.let {
                        it.tom = it.samvær.rolle.opphørTilDato
                    }
            }

            if (opphørSlettet || opphørsdato != null) {
                it.perioder
                    .maxByOrNull { it.fom }
                    ?.let {
                        it.tom = it.samvær.rolle.opphørTilDato
                    }
            }
        }
    }
}
