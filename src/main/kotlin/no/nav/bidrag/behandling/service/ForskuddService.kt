package no.nav.bidrag.behandling.service

import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import com.fasterxml.jackson.databind.node.POJONode
import mu.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.validere
import no.nav.bidrag.behandling.dto.beregning.Forskuddsberegningrespons
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
import java.time.format.DateTimeFormatter

private val LOGGER = KotlinLogging.logger {}

@Service
class ForskuddService(
    private val behandlingService: BehandlingService,
    private val bidragBeregnForskuddConsumer: BidragBeregnForskuddConsumer,
    private val bidragPersonConsumer: BidragPersonConsumer,
) {
    fun beregneForskudd(behandlingsid: Long): Forskuddsberegningrespons {
        val respons =
            either {
                val behandling = behandlingService.hentBehandlingById(behandlingsid).validere().bind()
                val resultat =
                    behandling.getSøknadsbarn().mapOrAccumulate {
                        val fødselsdato = finneFødselsdato(it.ident, it.foedselsdato)

                        // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                        if (fødselsdato == null) {
                            fantIkkeFødselsdatoTilSøknadsbarn(behandlingsid)
                        }

                        val rolleBm = behandling.roller.filter { r -> r.rolletype == Rolletype.BIDRAGSMOTTAKER }.first()
                        val bm =
                            lagePersonobjekt(
                                rolleBm.ident,
                                rolleBm.navn,
                                rolleBm.foedselsdato,
                                "bidragsmottaker",
                            )
                        val søknadsbarn = lagePersonobjekt(it.ident, it.navn, fødselsdato, "søknadsbarn-${it.id}")
                        val øvrigeBarnIHusstand = oppretteGrunnlagForHusstandsbarn(behandling, it.ident!!)
                        var beregnForskudd = behandling.tilBeregnGrunnlag(bm, søknadsbarn, øvrigeBarnIHusstand)

                        try {
                            bidragBeregnForskuddConsumer.beregnForskudd(beregnForskudd)
                        } catch (e: HttpClientErrorException) {
                            LOGGER.warn { e }
                            val errors = e.responseHeaders?.get("error")?.joinToString("\r\n")
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

        return Forskuddsberegningrespons(respons.getOrNull())
    }

    private fun oppretteGrunnlagForHusstandsbarn(
        behandling: Behandling,
        personidentSøknadsbarn: String,
    ): Set<Grunnlag> {
        val grunnlag = HashSet<Grunnlag>()
        behandling.husstandsbarn.filter { barn -> barn.ident != personidentSøknadsbarn }.forEach {
            val fødselsdato = finneFødselsdato(it.ident, it.foedselsdato)

            // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
            if (fødselsdato == null) {
                fantIkkeFødselsdatoTilSøknadsbarn(behandling.id!!)
            }
            grunnlag.add(lagePersonobjekt(it.ident, it.navn, fødselsdato, "husstandsbarn-${it.id}"))
        }

        return grunnlag
    }

    private fun lagePersonobjekt(
        ident: String?,
        navn: String?,
        fødselsdato: LocalDate,
        referanse: String,
    ): Grunnlag {
        var personident = ident ?: ""

        return Grunnlag(
            referanse = "person-$referanse",
            type = Grunnlagstype.PERSON,
            innhold =
                POJONode(
                    Person(
                        ident = Personident(personident),
                        navn = navn ?: "",
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
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            LocalDate.parse(bidragPersonConsumer.hentPerson(ident).fødselsdato, formatter)
        } else {
            fødselsdato
        }
    }
}
