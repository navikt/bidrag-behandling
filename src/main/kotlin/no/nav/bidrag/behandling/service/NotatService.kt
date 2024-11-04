package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.behandling.henteRolleForNotat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

@Service
class NotatService {
    @Transactional
    fun oppdatereNotat(
        behandling: Behandling,
        notattype: Notattype,
        notattekst: String,
        rolleid: Long,
    ) {
        val rolle = behandling.roller.find { rolleid == it.id }

        if (rolle == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ikke rolle med id $rolleid i behandling ${behandling.id}",
            )
        }

        val eksisterendeNotat = behandling.notater.find { it.rolle.id == rolleid && it.type == notattype }

        eksisterendeNotat?.let {
            log.info { "Oppdaterer eksisterende notat av type $notattype for rolle med id $rolleid i behandling ${behandling.id}" }
            it.innhold = notattekst
        } ?: run {
            log.info { "Legger til notat av type $notattype for rolle med id $rolleid i behandling ${behandling.id}" }
            behandling.notater.add(
                Notat(
                    behandling = behandling,
                    rolle = rolle ?: behandling.rolleGrunnlagSkalHentesFor!!,
                    innhold = notattekst,
                    type = notattype,
                ),
            )
        }
    }

    companion object {
        fun henteNotatinnhold(
            behandling: Behandling,
            notattype: Notattype,
            rolle: Rolle? = null,
        ): String {
            val rolleid: Long? = behandling.henteRolleForNotat(notattype, rolle).id
            return behandling.notater.find { it.rolle.id == rolleid && it.type == notattype }?.innhold
                ?: henteNotatFraGammelStruktur(behandling, notattype) ?: ""
        }

        fun henteUnderholdsnotat(behandling: Behandling): String? =
            behandling.notater.find { it.rolle.id == behandling.bidragspliktig!!.id!! && Notattype.UNDERHOLDSKOSTNAD == it.type }?.innhold

        fun henteInntektsnotat(
            behandling: Behandling,
            rolleid: Long,
        ): String? =
            behandling.notater.find { it.rolle.id == rolleid && Notattype.INNTEKT == it.type }?.innhold
                ?: henteNotatFraGammelStruktur(behandling, Notattype.INNTEKT)

        // TODO: (202408707) Metoden slettes når alle notater har blitt mirgrert til ny datastruktur
        @Deprecated("Brukes kun i en overgangsperiode frem til notater i behandlingstabellen er migrert til notattabellen")
        private fun henteNotatFraGammelStruktur(
            behandling: Behandling,
            notattype: Notattype,
        ): String? {
            log.info { "Henter notat av type $notattype fra gammel datastruktur i behandling ${behandling.id}" }
            return when (notattype) {
                Notattype.BOFORHOLD -> behandling.boforholdsbegrunnelseKunINotat
                Notattype.INNTEKT -> behandling.inntektsbegrunnelseKunINotat
                Notattype.VIRKNINGSTIDSPUNKT -> behandling.virkningstidspunktbegrunnelseKunINotat
                Notattype.UTGIFTER -> behandling.utgiftsbegrunnelseKunINotat
                else -> null
            }
        }
    }
}
