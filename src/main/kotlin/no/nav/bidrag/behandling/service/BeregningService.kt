package no.nav.bidrag.behandling.service

import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import com.fasterxml.jackson.databind.node.POJONode
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.database.datamodell.validere
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegning
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.transformers.tilBeregnGrunnlag
import no.nav.bidrag.behandling.valideringAvBehandlingFeilet
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.Grunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val LOGGER = KotlinLogging.logger {}

private fun Rolle.tilPersonident() = ident?.let { Personident(it) }

private fun Rolle.mapTilResultatBarn() =
    no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle(tilPersonident(), hentNavn(), foedselsdato)

@Service
class BeregningService(
    private val behandlingService: BehandlingService,
    private val bidragBeregnForskuddConsumer: BidragBeregnForskuddConsumer,
) {
    fun beregneForskudd(behandlingsid: Long): no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegning {
        val respons =
            either {
                val behandling =
                    behandlingService.hentBehandlingById(behandlingsid).validere().bind()
                val resultat =
                    behandling.getSøknadsbarn().mapOrAccumulate {
                        val fødselsdato =
                            finneFødselsdato(it.ident, it.foedselsdato)
                                // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                                ?: fantIkkeFødselsdatoTilSøknadsbarn(behandlingsid)

                        val rolleBm =
                            behandling.roller.first { r -> r.rolletype == Rolletype.BIDRAGSMOTTAKER }
                        val bm =
                            lagePersonobjekt(
                                rolleBm.ident,
                                rolleBm.navn,
                                rolleBm.foedselsdato,
                                "bidragsmottaker",
                            )
                        val søknadsbarn =
                            lagePersonobjekt(it.ident, it.navn, fødselsdato, "søkandsbarn-${it.id}")
                        val øvrigeBarnIHusstand =
                            oppretteGrunnlagForHusstandsbarn(behandling, it.ident!!)
                        val beregnForskudd =
                            behandling.tilBeregnGrunnlag(bm, søknadsbarn, øvrigeBarnIHusstand)

                        try {
                            no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn(
                                it.mapTilResultatBarn(),
                                bidragBeregnForskuddConsumer.beregnForskudd(beregnForskudd),
                            )
                        } catch (e: HttpClientErrorException) {
                            LOGGER.warn { e }
                            val errors =
                                e.responseHeaders?.get("error")?.joinToString("\r\n") { message ->
                                    val split = message.split(":")
                                    if (split.size > 1) {
                                        split.takeLast(split.size - 1)
                                            .joinToString(" ").trim()
                                    } else {
                                        split.first()
                                    }
                                }
                            raise(errors ?: e.message!!)
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

        return no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegning(respons.getOrNull() ?: emptyList())
    }

    private fun oppretteGrunnlagForHusstandsbarn(
        behandling: Behandling,
        personidentSøknadsbarn: String,
    ): Set<Grunnlag> {
        return behandling.husstandsbarn.filter { barn -> barn.ident != personidentSøknadsbarn }
            .map {
                val fødselsdato =
                    finneFødselsdato(it.ident, it.foedselsdato)
                        // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                        ?: fantIkkeFødselsdatoTilSøknadsbarn(behandling.id!!)

                lagePersonobjekt(it.ident, it.navn, fødselsdato, "husstandsbarn-${it.id}")
            }.toSet()
    }

    private fun lagePersonobjekt(
        ident: String?,
        navn: String?,
        fødselsdato: LocalDate,
        referanse: String,
    ): Grunnlag {
        val personident = ident ?: ""

        return Grunnlag(
            referanse = "person-$referanse",
            type = Grunnlagstype.PERSON,
            innhold =
                POJONode(
                    Person(
                        ident = Personident(personident),
                        navn = navn ?: hentPersonVisningsnavn(ident) ?: "",
                        fødselsdato = fødselsdato,
                    ),
                ),
        )
    }

    private fun finneFødselsdato(
        ident: String?,
        fødselsdato: LocalDate?,
    ): LocalDate? {
        return if (fødselsdato == null && ident != null) {
            hentPersonFødselsdato(ident)
        } else {
            fødselsdato
        }
    }
}
