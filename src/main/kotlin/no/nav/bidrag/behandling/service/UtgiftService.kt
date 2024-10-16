package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.UtgiftRepository
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftResponse
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.utgift.tilUtgiftspost
import no.nav.bidrag.behandling.transformers.valider
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger {}

@Service
class UtgiftService(
    private val behandlingRepository: BehandlingRepository,
    private val notatService: NotatService,
    private val utgiftRepository: UtgiftRepository,
    private val mapper: Dtomapper,
) {
    @Transactional
    fun oppdatereUtgift(
        behandlingsid: Long,
        request: OppdatereUtgiftRequest,
    ): OppdatereUtgiftResponse {
        val behandling =
            behandlingRepository.findBehandlingById(behandlingsid).getOrNull()
                ?: behandlingNotFoundException(behandlingsid)
        log.info { "Oppdaterer utgift for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer utgift for behandling $behandlingsid, forespørsel=$request" }
        request.valider(behandling)
        val utgift = behandling.utgift ?: Utgift(behandling = behandling)
        utgift.beløpDirekteBetaltAvBp = request.beløpDirekteBetaltAvBp ?: utgift.beløpDirekteBetaltAvBp
        utgift.oppdaterMaksGodkjentBeløp(request)
        request.henteOppdatereNotat()?.let {
            notatService.oppdatereNotat(
                behandling,
                NotatGrunnlag.NotatType.UTGIFTER,
                it.henteNyttNotat() ?: "",
                behandling.bidragsmottaker!!.id!!,
            )
        }
        behandling.avslag = request.avslag
        if (request.nyEllerEndretUtgift != null) {
            utgift.lagreHistorikk()
            val nyUtgiftspost = request.nyEllerEndretUtgift.tilUtgiftspost(utgift)
            val eksisterendeUtgiftspost = utgift.utgiftsposter.find { it.id == request.nyEllerEndretUtgift.id }
            if (eksisterendeUtgiftspost != null) {
                nyUtgiftspost.id = eksisterendeUtgiftspost.id
                utgift.utgiftsposter.remove(eksisterendeUtgiftspost)
            }

            log.info {
                "${nyUtgiftspost.id?.let { "Oppdatert" } ?: "Opprettet"} utgift med dato ${nyUtgiftspost.dato} " +
                    "og type ${nyUtgiftspost.type} i behandling $behandlingsid"
            }
            secureLogger.info {
                "${nyUtgiftspost.id?.let { "Oppdatert" } ?: "Opprettet"} " +
                    "utgift $nyUtgiftspost i behandling $behandlingsid"
            }

            utgift.utgiftsposter.add(nyUtgiftspost)
            behandling.utgift = utgiftRepository.save(utgift)
            val utgiftspostId =
                request.nyEllerEndretUtgift.id ?: behandling.utgift!!
                    .utgiftsposter
                    .maxBy { it.id!! }
                    .id
            return mapper.run { utgift.tilOppdaterUtgiftResponse(utgiftspostId) }
        } else if (request.sletteUtgift != null) {
            utgift.lagreHistorikk()
            utgift.utgiftsposter.find { it.id == request.sletteUtgift }?.let {
                log.info { "Sletter utgift med dato ${it.dato} fra behandling $behandlingsid" }
                secureLogger.info { "Sletter utgift $it fra behandling $behandlingsid" }
                utgift.utgiftsposter.remove(it)
            }
            if (utgift.utgiftsposter.isEmpty()) {
                log.info { "Alle utgifter er slettet. Sletter utgift maks godkjent beløp" }
                utgift.slettMaksGodkjentBeløp()
            }
        }

        return mapper.run { utgift.tilOppdaterUtgiftResponse() }
    }

    private fun Utgift.oppdaterMaksGodkjentBeløp(request: OppdatereUtgiftRequest) {
        if (request.maksGodkjentBeløp == null) return
        if (request.maksGodkjentBeløp.taMed) {
            maksGodkjentBeløpTaMed = true
            maksGodkjentBeløp = request.maksGodkjentBeløp.beløp
            maksGodkjentBeløpBegrunnelse = request.maksGodkjentBeløp.begrunnelse
        } else {
            maksGodkjentBeløpTaMed = false
        }
    }

    private fun Utgift.slettMaksGodkjentBeløp() {
        maksGodkjentBeløpTaMed = false
        maksGodkjentBeløp = null
        maksGodkjentBeløpBegrunnelse = null
    }

    private fun Utgift.lagreHistorikk() {
        forrigeUtgiftsposterHistorikk = commonObjectmapper.writeValueAsString(utgiftsposter)
    }

    private fun Utgift.gjenopprettHistorikk(): Utgift {
        val nåværendeHistorikk = commonObjectmapper.writeValueAsString(utgiftsposter)
        utgiftsposter.clear()
        utgiftsposter.addAll(hentForrigeLagredePerioder())
        forrigeUtgiftsposterHistorikk = nåværendeHistorikk
        return utgiftRepository.save(this)
    }

    private fun Utgift.hentForrigeLagredePerioder(): List<Utgiftspost> {
        val forrigePerioder: Set<JsonNode> =
            commonObjectmapper.readValue(
                forrigeUtgiftsposterHistorikk
                    ?: oppdateringAvBoforholdFeilet("Mangler forrige perioder for husstandsmedlem $id i behandling ${behandling.id}"),
            )
        return forrigePerioder.map {
            Utgiftspost(
                utgift = this,
                dato = LocalDate.parse(it[Utgiftspost::dato.name].textValue()),
                godkjentBeløp = it[Utgiftspost::godkjentBeløp.name].decimalValue(),
                kravbeløp = it[Utgiftspost::kravbeløp.name].decimalValue(),
                kommentar = it[Utgiftspost::kommentar.name].textValue(),
                type = it[Utgiftspost::type.name].textValue(),
            )
        }
    }
}
