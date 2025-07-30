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
        val eksisterendeNotat = hentNotat(behandling, notattype, rolle)

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
            begrunnelseDelAvBehandlingen: Boolean = true,
        ): String {
            val rolleid = behandling.henteRolleForNotat(notattype, rolle).id!!
            return henteNotatinnholdRolleId(behandling, notattype, rolleid, begrunnelseDelAvBehandlingen)
        }

        fun henteNotatinnholdRolleId(
            behandling: Behandling,
            notattype: Notattype,
            rolleid: Long,
            begrunnelseDelAvBehandlingen: Boolean = true,
        ): String = hentNotatRolleId(behandling, notattype, rolleid, begrunnelseDelAvBehandlingen)?.innhold ?: ""

        fun hentNotat(
            behandling: Behandling,
            notattype: Notattype,
            rolle: Rolle? = null,
            begrunnelseDelAvBehandlingen: Boolean = true,
        ): Notat? {
            val rolleid = behandling.henteRolleForNotat(notattype, rolle).id!!
            return behandling.notater
                .find {
                    it.rolle.id == rolleid &&
                        it.type == notattype &&
                        it.erDelAvBehandlingen == begrunnelseDelAvBehandlingen
                }
        }

        fun hentNotatRolleId(
            behandling: Behandling,
            notattype: Notattype,
            rolleid: Long,
            begrunnelseDelAvBehandlingen: Boolean = true,
        ) = behandling.notater
            .find {
                it.rolle.id == rolleid &&
                    it.type == notattype &&
                    it.erDelAvBehandlingen == begrunnelseDelAvBehandlingen
            }

        fun henteSamværsnotat(
            behandling: Behandling,
            rolle: Rolle,
            begrunnelseDelAvBehandlingen: Boolean = true,
        ): String? = henteNotatinnhold(behandling, Notattype.SAMVÆR, rolle, begrunnelseDelAvBehandlingen)

        fun henteUnderholdsnotat(
            behandling: Behandling,
            rolle: Rolle,
            begrunnelseDelAvBehandlingen: Boolean = true,
        ): String? = henteNotatinnhold(behandling, Notattype.UNDERHOLDSKOSTNAD, rolle, begrunnelseDelAvBehandlingen)

        fun henteInntektsnotat(
            behandling: Behandling,
            rolleid: Long,
            begrunnelseDelAvBehandlingen: Boolean = true,
        ): String? = henteNotatinnholdRolleId(behandling, Notattype.INNTEKT, rolleid, begrunnelseDelAvBehandlingen)
    }
}
