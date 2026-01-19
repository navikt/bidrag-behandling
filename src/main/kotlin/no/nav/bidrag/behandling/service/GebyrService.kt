package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterGebyrDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.validerSann
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BeregnGebyrResultat
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
                val manueltOverstyrtGebyr = rolle.gebyr ?: GebyrRolle()
                val beregnetGebyrErEndret = manueltOverstyrtGebyr.beregnetIlagtGebyr != beregning.ilagtGebyr
                // TODO: FF - Rekalkuler gebyr slik at det blir manuelt overstyrt slik at BP bare får gebyr for ett av søknadene
                if (beregnetGebyrErEndret) {
                    resettGebyr(rolle, behandling, beregning)
                }
                beregnetGebyrErEndret
            }.any { it }

    @Transactional
    fun oppdaterGebyrEtterEndringÅrsakAvslag(behandling: Behandling) {
        behandling
            .roller
            .filter { it.harGebyrsøknad }
            .forEach { rolle ->
                resettGebyr(rolle, behandling)
            }
    }

    private fun resettGebyr(
        rolle: Rolle,
        behandling: Behandling,
        beregningInput: BeregnGebyrResultat? = null,
    ) {
        val beregning = beregningInput ?: vedtakGrunnlagMapper.beregnGebyr(behandling, rolle)
        rolle.gebyr =
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

        val gebyrSaker = rolle.gebyrSøknader.map { it.saksnummer }.distinct()
        gebyrSaker.forEach {
            rolle.oppdaterGebyrV2(
                it,
                null,
                RolleManueltOverstyrtGebyr(
                    overstyrGebyr = false,
                    ilagtGebyr = beregning.ilagtGebyr,
                    beregnetIlagtGebyr = beregning.ilagtGebyr,
                    begrunnelse = null,
                ),
            )
        }
    }

    @Transactional
    fun oppdaterManueltOverstyrtGebyrV2(
        behandling: Behandling,
        request: OppdaterGebyrDto,
    ) {
        val rolle =
            behandling.roller.find { it.id == request.rolleId }
                ?: ugyldigForespørsel("Fant ikke rolle ${request.rolleId} i behandling ${behandling.id}")
        val beregning = vedtakGrunnlagMapper.beregnGebyr(behandling, rolle)
        val søknadsid = request.søknadsid ?: behandling.soknadsid!!
        behandling.validerOppdatering(request)
        val sakForSøknad = rolle.gebyr!!.finnGebyrForSøknad(søknadsid)!!
        rolle.oppdaterGebyrV2(
            sakForSøknad.saksnummer,
            søknadsid,
            RolleManueltOverstyrtGebyr(
                overstyrGebyr = request.overstyrGebyr,
                ilagtGebyr = request.overstyrGebyr != beregning.ilagtGebyr,
                beregnetIlagtGebyr = beregning.ilagtGebyr,
                begrunnelse = request.begrunnelse,
            ),
        )
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
        val sakForSøknad =
            rolle.gebyr?.finnGebyrForSøknad(søknadsid)?.saksnummer ?: behandling.saksnummer

        rolle.oppdaterGebyrV2(
            sakForSøknad,
            søknadsid,
            RolleManueltOverstyrtGebyr(
                overstyrGebyr = request.overstyrGebyr,
                ilagtGebyr = request.overstyrGebyr != beregning.ilagtGebyr,
                beregnetIlagtGebyr = beregning.ilagtGebyr,
                begrunnelse = request.begrunnelse,
            ),
        )
    }

    private fun Behandling.validerOppdatering(request: OppdaterGebyrDto) {
        val feilListe = mutableSetOf<String>()

        feilListe.validerSann(tilType() == TypeBehandling.BIDRAG, "Kan bare oppdatere gebyr på en bidragsbehandling")

        val rolle =
            roller.find { it.id == request.rolleId }
                ?: ugyldigForespørsel("Fant ikke rolle ${request.rolleId} i behandling $id")

        if (request.søknadsid != null) {
            feilListe.validerSann(
                rolle.hentEllerOpprettGebyr().finnGebyrForSøknad(request.søknadsid) != null,
                "Fant ikke gebyr for søknad ${request.søknadsid} for rolle ${rolle.id} i behandling $id",
            )
        }

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
