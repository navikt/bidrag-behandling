package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Samværskalkulator
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.repository.SamværRepository
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.valider
import no.nav.bidrag.behandling.samværIkkeFunnet
import no.nav.bidrag.behandling.transformers.samvær.tilOppdaterSamværResponseDto
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class SamværService(
    private val samværRepository: SamværRepository,
    private val behandlingService: BehandlingService,
) {
    fun oppdaterSamvær(
        behandlingsid: Long,
        forespørsel: OppdaterSamværDto,
    ): OppdaterSamværResponsDto {
        // val

        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        forespørsel.valider()
        log.info { "Oppdaterer samvær for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer samvær for behandling $behandlingsid, forespørsel=$forespørsel" }
        val oppdaterSamvær =
            behandling.samvær.find { it.rolle.ident == forespørsel.gjelderBarn } ?: samværIkkeFunnet(behandlingsid, forespørsel.gjelderBarn)

        forespørsel.run {
            if (slettPeriode != null) {
                behandling.samvær.remove(oppdaterSamvær)
                log.info { "Slett samvær ${oppdaterSamvær.id} fra behandling $behandlingsid" }
            } else if (periode != null) {
                if (periode.id == null) {
                    val nyPeriode = Samværsperiode(oppdaterSamvær, periode.periode.fom, periode.periode.til, periode.samværsklasse)
                    oppdaterSamvær.perioder.add(nyPeriode)
                } else {
                    val oppdaterPeriode =
                        oppdaterSamvær.perioder.find { it.id == periode.id }
                            ?: samværIkkeFunnet(behandlingsid, forespørsel.gjelderBarn)
                    oppdaterPeriode.datoFom = periode.periode.fom
                    oppdaterPeriode.datoTom = periode.periode.til
                    oppdaterPeriode.samværsklasse = periode.samværsklasse
                }
            }
        }

        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    fun beregnSamværsklasse(kalkulator: Samværskalkulator): Samværsklasse = Samværsklasse.INGEN_SAMVÆR
}
