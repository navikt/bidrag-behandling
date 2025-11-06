package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterGebyrDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.validerSann
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GebyrService(
    private val vedtakGrunnlagMapper: VedtakGrunnlagMapper,
) {
    @Transactional
    fun rekalkulerGebyr(behandling: Behandling): Boolean =
        behandling
            .roller
            .filter { it.harGebyrsøknad }
            .map { rolle ->
                val beregning = vedtakGrunnlagMapper.beregnGebyr(behandling, rolle)
                val manueltOverstyrtGebyr = rolle.manueltOverstyrtGebyr ?: GebyrRolle()
                val beregnetGebyrErEndret = manueltOverstyrtGebyr.beregnetIlagtGebyr != beregning.ilagtGebyr
                if (beregnetGebyrErEndret) {
                    rolle.manueltOverstyrtGebyr =
                        rolle.hentEllerOpprettGebyr().let {
                            it.copy(
                                overstyrGebyr = false,
                                ilagtGebyr = beregning.ilagtGebyr,
                                beregnetIlagtGebyr = beregning.ilagtGebyr,
                                begrunnelse = null,
                                gebyrSøknader =
                                    it.gebyrSøknader
                                        .map {
                                            it.copy(
                                                manueltOverstyrtGebyr =
                                                    RolleManueltOverstyrtGebyr(
                                                        overstyrGebyr = false,
                                                        ilagtGebyr = beregning.ilagtGebyr,
                                                        beregnetIlagtGebyr = beregning.ilagtGebyr,
                                                        begrunnelse = null,
                                                    ),
                                            )
                                        }.toMutableSet(),
                            )
                        }
                }
                beregnetGebyrErEndret
            }.any { it }

    @Transactional
    fun oppdaterGebyrEtterEndringÅrsakAvslag(behandling: Behandling) {
        behandling
            .roller
            .filter { it.harGebyrsøknad }
            .forEach { rolle ->
                val beregning = vedtakGrunnlagMapper.beregnGebyr(behandling, rolle)
                rolle.manueltOverstyrtGebyr =
                    rolle.hentEllerOpprettGebyr().let {
                        it.copy(
                            overstyrGebyr = beregning.ilagtGebyr,
                            ilagtGebyr = beregning.ilagtGebyr,
                            beregnetIlagtGebyr = beregning.ilagtGebyr,
                            begrunnelse = null,
                            gebyrSøknader =
                                it.gebyrSøknader
                                    .map {
                                        it.copy(
                                            manueltOverstyrtGebyr =
                                                RolleManueltOverstyrtGebyr(
                                                    overstyrGebyr = beregning.ilagtGebyr,
                                                    ilagtGebyr = beregning.ilagtGebyr,
                                                    beregnetIlagtGebyr = beregning.ilagtGebyr,
                                                    begrunnelse = null,
                                                ),
                                        )
                                    }.toMutableSet(),
                        )
                    }
            }
    }

    @Transactional
    fun oppdaterManueltOverstyrtGebyr(
        behandling: Behandling,
        request: OppdaterGebyrDto,
    ) {
        val rolle =
            behandling.roller.find { it.id == request.rolleId }
                ?: ugyldigForespørsel("Fant ikke rolle ${request.rolleId} i behandling ${behandling.id}")
        val beregning = vedtakGrunnlagMapper.beregnGebyr(behandling, rolle)
        val søknadsid = request.søknadsid ?: behandling.soknadsid!!
        behandling.validerOppdatering(request)
        rolle.oppdaterGebyr(
            søknadsid,
            RolleManueltOverstyrtGebyr(
                overstyrGebyr = request.overstyrGebyr,
                ilagtGebyr = request.overstyrGebyr != beregning.ilagtGebyr,
                beregnetIlagtGebyr = beregning.ilagtGebyr,
                begrunnelse = request.begrunnelse,
            ),
        )
//        rolle.manueltOverstyrtGebyr =
//            rolle.hentEllerOpprettGebyr().let {
//                val gebyrSøknad = it.gebyrForSøknad(søknadsid, rolle.sakForSøknad(søknadsid))
//                gebyrSøknad.manueltOverstyrtGebyr =
//
//                    RolleManueltOverstyrtGebyr(
//                        overstyrGebyr = request.overstyrGebyr,
//                        ilagtGebyr = request.overstyrGebyr != beregning.ilagtGebyr,
//                        beregnetIlagtGebyr = beregning.ilagtGebyr,
//                        begrunnelse = if (!request.overstyrGebyr) null else request.begrunnelse ?: it.begrunnelse,
//                    )
//                it.gebyrSøknader.add(gebyrSøknad)
// //                it.gebyrSøknad = it.gebyrSøknad.removeDuplicates()
//                // TODO: Fjern meg etterpå
//                it.copy(
//                    overstyrGebyr = request.overstyrGebyr,
//                    ilagtGebyr = request.overstyrGebyr != beregning.ilagtGebyr,
//                    beregnetIlagtGebyr = beregning.ilagtGebyr,
//                    begrunnelse = if (!request.overstyrGebyr) null else request.begrunnelse ?: it.begrunnelse,
//                )
//            }
    }

    private fun Behandling.validerOppdatering(request: OppdaterGebyrDto) {
        val feilListe = mutableSetOf<String>()

        feilListe.validerSann(tilType() == TypeBehandling.BIDRAG, "Kan bare oppdatere gebyr på en bidragsbehandling")

        val rolle =
            roller.find { it.id == request.rolleId }
                ?: ugyldigForespørsel("Fant ikke rolle ${request.rolleId} i behandling $id")

        feilListe.validerSann(
            rolle.harGebyrsøknad,
            "Kan ikke endre gebyr på en rolle som ikke har gebyrsøknad",
        )
        feilListe.validerSann(
            request.overstyrGebyr || request.begrunnelse.isNullOrEmpty(),
            "Kan ikke sette begrunnelse hvis gebyr ikke er overstyrt",
        )

        if (feilListe.isNotEmpty()) {
            ugyldigForespørsel(feilListe.toSet().joinToString("\n"))
        }
    }
}
