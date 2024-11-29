package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterManueltGebyrDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.validerSann
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GebyrService(
    private val vedtakGrunnlagMapper: VedtakGrunnlagMapper,
) {
    @Transactional
    fun oppdaterGebyrEtterEndringÅrsakAvslag(behandling: Behandling) {
        behandling
            .roller
            .filter { it.harGebyrsøknad }
            .forEach { rolle ->
                rolle.manueltOverstyrtGebyr =
                    (rolle.manueltOverstyrtGebyr ?: RolleManueltOverstyrtGebyr()).let {
                        it.copy(
                            overstyrGebyr = behandling.avslag != null,
                            ilagtGebyr =
                                (behandling.avslag == null).ifTrue {
                                    val beregning = vedtakGrunnlagMapper.beregnGebyr(behandling, rolle)
                                    !beregning.ilagtGebyr
                                },
                        )
                    }
            }
    }

    @Transactional
    fun oppdaterManueltOverstyrtGebyr(
        behandling: Behandling,
        request: OppdaterManueltGebyrDto,
    ) {
        val rolle =
            behandling.roller.find { it.id == request.rolleId }
                ?: ugyldigForespørsel("Fant ikke rolle ${request.rolleId} i behandling ${behandling.id}")
        val beregning = vedtakGrunnlagMapper.beregnGebyr(behandling, rolle)
        behandling.validerOppdatering(request)
        rolle.manueltOverstyrtGebyr =
            (rolle.manueltOverstyrtGebyr ?: RolleManueltOverstyrtGebyr()).let {
                it.copy(
                    overstyrGebyr = request.overstyrtGebyr != null,
                    ilagtGebyr = request.overstyrtGebyr?.ilagtGebyr ?: (behandling.avslag == null).ifTrue { !beregning.ilagtGebyr },
                    begrunnelse = request.overstyrtGebyr?.begrunnelse ?: it.begrunnelse,
                )
            }
    }

    private fun Behandling.validerOppdatering(request: OppdaterManueltGebyrDto) {
        val feilListe = mutableSetOf<String>()

        feilListe.validerSann(tilType() == TypeBehandling.BIDRAG, "Kan bare oppdatere gebyr på en bidragsbehandling")

        val rolle =
            roller.find { it.id == request.rolleId }
                ?: ugyldigForespørsel("Fant ikke rolle ${request.rolleId} i behandling $id")

        feilListe.validerSann(
            rolle.harGebyrsøknad,
            "Kan ikke endre gebyr på en rolle som ikke har gebyrsøknad",
        )

        if (avslag == null) {
            feilListe.validerSann(
                request.overstyrtGebyr?.ilagtGebyr == null,
                "Kan ikke sette gebyr til samme som beregnet gebyr når det ikke er avslag",
            )
        }

        if (feilListe.isNotEmpty()) {
            ugyldigForespørsel(feilListe.toSet().joinToString("\n"))
        }
    }
}
