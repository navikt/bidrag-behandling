package no.nav.bidrag.behandling.service

import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.datamodell.sivilstand
import no.nav.bidrag.behandling.database.datamodell.validere
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForBeregning
import no.nav.bidrag.behandling.valideringAvBehandlingFeilet
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import org.springframework.stereotype.Service

private val LOGGER = KotlinLogging.logger {}

private fun Rolle.tilPersonident() = ident?.let { Personident(it) }

private fun Rolle.mapTilResultatBarn() = ResultatRolle(tilPersonident(), hentNavn(), foedselsdato)

@Service
class BeregningService(
    private val behandlingService: BehandlingService,
) {
    private val beregnApi = BeregnForskuddApi()

    fun beregneForskudd(behandlingsid: Long): List<ResultatForskuddsberegningBarn> {
        val respons =
            either {
                val behandling =
                    behandlingService.hentBehandlingById(behandlingsid).validere().bind()
                val resultat =
                    behandling.søknadsbarn.mapOrAccumulate {
                        val beregnForskudd = behandling.byggGrunnlagForBeregning(it)

                        try {
                            ResultatForskuddsberegningBarn(
                                it.mapTilResultatBarn(),
                                beregnApi.beregn(beregnForskudd),
                            )
                        } catch (e: IllegalArgumentException) {
                            LOGGER.warn(e) { "Det skjedde en feil ved beregning av forskudd: ${e.message}" }
                            raise(e.message!!)
                        } catch (e: Exception) {
                            LOGGER.warn { e }
                            raise(e.message!!)
                        }
                    }.bind()

                return@either resultat
            }

        if (respons.isLeft()) {
            respons.leftOrNull()?.let {
                LOGGER.warn { "Validering av forskuddsbehandling feilet med: ${it.all}" }
                valideringAvBehandlingFeilet(it.all)
            }
        }

        return respons.getOrNull() ?: emptyList()
    }
}

fun Behandling.beregnSivilstandPerioder(grunnlagInput: List<SivilstandGrunnlagDto>? = null): SivilstandBeregnet {
    val grunnlag =
        grunnlagInput ?: grunnlagListe.sivilstand.konverterData<List<SivilstandGrunnlagDto>>()
            ?: emptyList()
//    val beregningGrunnlag =
//        grunnlag.mapIndexed { i, it ->
//            val rolle = roller.find { rolle -> rolle.ident == it.personId }!!
//            it.tilBeregningGrunnlagDto(
//                opprettInnhentetSivilstandGrunnlagsreferanse(
//                    rolle.tilGrunnlagPerson().referanse,
//                ),
//            )
//        }
    return SivilstandApi.beregn(virkningstidspunkt ?: søktFomDato, grunnlag)
}
