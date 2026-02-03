package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.SamværRepository
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværsperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.SletteSamværsperiodeElementDto
import no.nav.bidrag.behandling.dto.v2.samvær.valider
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.samvær.tilOppdaterSamværResponseDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnFra
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.delberegningSamværsklasse
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.toLocalDate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import kotlin.text.compareTo

private val log = KotlinLogging.logger {}

@Service
@Import(BeregnSamværsklasseApi::class)
class SamværService(
    private val samværRepository: SamværRepository,
    private val behandlingRepository: BehandlingRepository,
    private val notatService: NotatService,
    private val beregnSamværsklasseApi: BeregnSamværsklasseApi,
) {
    @Transactional
    fun oppdaterSamvær(
        behandlingsid: Long,
        request: OppdaterSamværDto,
    ): Samvær {
        val behandling = behandlingRepository.findBehandlingById(behandlingsid).get()
        secureLogger.debug { "Oppdaterer samvær for behandling $behandlingsid, forespørsel=$request" }
        if (request.sammeForAlle && !behandling.sammeSamværForAlle) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Ugyldig data ved oppdatering av samvær: Kan ikke oppdatere til samme for alle når det ikke valgt til å være samme",
            )
        }
        val samværBarn = behandling.samvær.finnSamværForBarn(request.barnId, request.gjelderBarn)
        oppdaterSamvær(request, samværBarn)

        if (request.sammeForAlle) {
            behandling.samvær.filter { it.id != samværBarn.id }.forEach { oppdaterSamvær ->
                kopierSamværPerioderOgBegrunnelse(
                    samværBarn,
                    oppdaterSamvær,
                    erPerioderOppdatert = request.periode != null,
                    erBegrunnelseOppdatert =
                        request.oppdatereBegrunnelse != null,
                )
            }
        }

        return samværBarn
    }

    @Transactional
    fun brukSammeSamværForAlleBarn(behandlingId: Long) {
        val behandling = behandlingRepository.findBehandlingById(behandlingId).get()
        val yngsteBarn = behandling.søknadsbarn.minBy { it.fødselsdato }

        val samværYngsteBarn = behandling.samvær.finnSamværForBarn(yngsteBarn.id, yngsteBarn.ident!!)
        var nyNotat = yngsteBarn.notat.find { it.type == NotatGrunnlag.NotatType.SAMVÆR }?.innhold ?: ""
        behandling.søknadsbarn.forEach {
            if (it.id != yngsteBarn.id) {
                val begrunnelse = it.notat.find { it.type == NotatGrunnlag.NotatType.SAMVÆR }?.innhold ?: ""
                nyNotat +=
                    begrunnelse.replace(nyNotat, "").let {
                        if (it.isNotEmpty()) {
                            "<br> $it"
                        } else {
                            ""
                        }
                    }
            }
        }
        behandling.søknadsbarn.forEach {
            val samværBarn = behandling.samvær.finnSamværForBarn(it.id, it.ident!!)
            val perioderKopiert =
                samværYngsteBarn.perioder.map {
                    Samværsperiode(fom = it.fom, tom = it.tom, samvær = samværBarn, samværsklasse = it.samværsklasse)
                }
            samværBarn.perioder.clear()
            samværBarn.perioder.addAll(perioderKopiert.toMutableSet())
        }
        behandling.søknadsbarn.forEach {
            notatService.oppdatereNotat(behandling, NotatGrunnlag.NotatType.SAMVÆR, nyNotat, it)
        }
    }

    private fun kopierSamværPerioderOgBegrunnelse(
        fraSamvær: Samvær,
        oppdaterSamvær: Samvær,
        erPerioderOppdatert: Boolean = true,
        erBegrunnelseOppdatert: Boolean = true,
    ) {
        if (erPerioderOppdatert) {
            val nyePerioder =
                fraSamvær.perioder.map {
                    val erSistePeriode = it == fraSamvær.perioder.maxByOrNull { periode -> periode.fom }
                    Samværsperiode(
                        oppdaterSamvær,
                        it.fom,
                        if (erSistePeriode) justerPeriodeTomOpphørsdato(oppdaterSamvær.rolle.opphørsdato) else it.tom,
                        it.samværsklasse,
                        beregningJson = it.beregningJson,
                    )
                }

            oppdaterSamvær.perioder.clear()
            oppdaterSamvær.perioder.addAll(nyePerioder)
        }

        if (erBegrunnelseOppdatert) {
            val begrunnelseFraSamvær = henteNotatinnhold(fraSamvær.behandling, NotatType.SAMVÆR, fraSamvær.rolle, true)
            notatService.oppdatereNotat(
                oppdaterSamvær.behandling,
                NotatType.SAMVÆR,
                begrunnelseFraSamvær,
                oppdaterSamvær.rolle,
            )
        }
    }

    private fun oppdaterSamvær(
        request: OppdaterSamværDto,
        oppdaterSamvær: Samvær,
    ) {
        request.valider(oppdaterSamvær.rolle.opphørsdato)

        request.run {
            periode?.let { oppdaterPeriode(it, oppdaterSamvær) }
            oppdatereBegrunnelse?.let {
                notatService.oppdatereNotat(
                    oppdaterSamvær.behandling,
                    NotatType.SAMVÆR,
                    it.nyBegrunnelse,
                    oppdaterSamvær.rolle,
                )
            }
        }
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
        val oppdaterSamvær =
            behandling.samvær.finnSamværForBarn(request.gjelderBarnId, request.gjelderBarn)
        val slettPeriode =
            oppdaterSamvær.hentPeriode(request.samværsperiodeId)

        oppdaterSamvær.perioder.remove(slettPeriode)
        // Find the previous period and set tom to null if it exists and is not null
//        oppdaterSamvær.perioder
//            .filter { it.fom < slettPeriode.fom }
//            .maxByOrNull { it.fom }
//            ?.takeIf { it.tom != null }
//            ?.let { it.tom = null }
        secureLogger.debug { "Slettet samværsperiode ${slettPeriode.id} fra samvær ${oppdaterSamvær.id} i behandling $behandlingsid" }
        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    fun beregnSamværsklasse(kalkulator: SamværskalkulatorDetaljer): DelberegningSamværsklasse =
        beregnSamværsklasseApi.beregnSamværsklasse(kalkulator).delberegningSamværsklasse

    private fun SamværskalkulatorDetaljer?.tilJsonString() = this?.let { commonObjectmapper.writeValueAsString(it) }

    private fun MutableSet<Samvær>.finnSamværForBarn(
        barnId: Long? = null,
        gjelderBarn: String,
    ) = find { (barnId == null && it.rolle.ident == gjelderBarn) || it.rolle.id == barnId }
        ?: ugyldigForespørsel("Fant ikke samvær for barn $gjelderBarn i behandling med id ${firstOrNull()?.behandling?.id}")

    private fun Samvær.hentPeriode(id: Long) =
        perioder.find { it.id == id }
            ?: ugyldigForespørsel("Fant ikke samværsperiode med id $id i samvær $id")

    fun rekalkulerPerioderSamvær(
        behandling: Behandling,
        opphørSlettet: Boolean = false,
        forrigeVirkningstidspunkt: LocalDate? = null,
    ) {
        behandling.samvær.forEach {
            val virkningstidspunkt = it.rolle.finnBeregnFra().toLocalDate()
            // Antar at opphørsdato er måneden perioden skal opphøre
            val beregnTil = behandling.finnBeregnTilDatoBehandling(it.rolle)
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
            it.perioder
                .filter { it.fom > beregnTil }
                .forEach { periode ->
                    it.perioder.remove(periode)
                }
            if (opphørsdato != null) {
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
