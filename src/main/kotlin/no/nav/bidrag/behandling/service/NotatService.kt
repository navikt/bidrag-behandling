package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.behandling.henteRolleForNotat
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

@Service
class NotatService {
    @Transactional
    fun oppdatereNotat(
        behandling: Behandling,
        notattype: Notattype,
        notattekst: String,
        rolle: Rolle,
    ) {
        val eksisterendeNotat = behandling.notater.find { it.rolle == rolle && it.type == notattype }

        eksisterendeNotat?.let {
            log.info { "Oppdaterer eksisterende notat av type $notattype for rolle med id ${rolle.id} i behandling ${behandling.id}" }
            it.innhold = notattekst
        } ?: run {
            log.info { "Legger til notat av type $notattype for rolle med id ${rolle.id} i behandling ${behandling.id}" }
            behandling.notater.add(
                Notat(
                    behandling = behandling,
                    rolle = rolle,
                    innhold = notattekst,
                    type = notattype,
                ),
            )
        }
    }

    @Transactional
    fun sletteNotat(
        behandling: Behandling,
        notattype: Notattype,
        rolle: Rolle,
    ) {
        val notat = behandling.notater.find { it.rolle == rolle && it.type == notattype }
        notat?.let {
            log.info { "Sletter notat av type $notattype for rolle med id ${rolle.id} i behandling ${behandling.id}" }
            rolle.notat.remove(notat)
            behandling.notater.remove(notat)
        } ?: {
            log.info { "Fant ingen notat av type $notattype for rolle med id ${rolle.id} i behandling ${behandling.id}" }
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

        fun henteSamværsnotat(
            behandling: Behandling,
            rolle: Rolle,
        ): String? = behandling.notater.find { it.rolle.id == rolle!!.id && Notattype.SAMVÆR == it.type }?.innhold

        fun henteNotatForTypeOgRolle(
            behandling: Behandling,
            type: Notattype,
            rolle: Rolle,
        ): String? = behandling.notater.find { it.rolle.id == rolle.id && type == it.type }?.innhold

        fun henteUnderholdsnotat(
            behandling: Behandling,
            rolle: Rolle,
        ): String? = behandling.notater.find { it.rolle.id == rolle.id!! && Notattype.UNDERHOLDSKOSTNAD == it.type }?.innhold

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
