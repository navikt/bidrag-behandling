package no.nav.bidrag.behandling.consumer

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.grunnlag.PersonStønad
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Formål
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.request.GrunnlagRequestDto
import no.nav.bidrag.transport.behandling.grunnlag.request.HentGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@Component
class BidragGrunnlagConsumer(
    @Value("\${BIDRAG_GRUNNLAG_URL}") private val bidragGrunnlagUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-grunnlag") {
    private val bidragGrunnlagUri get() = UriComponentsBuilder.fromUri(bidragGrunnlagUrl)

    companion object {
        fun henteGrunnlagRequestobjekterForBehandling(behandling: Behandling): MutableMap<PersonStønad, List<GrunnlagRequestDto>> {
            val behandlingstype = behandling.tilType()

            val requestobjekterGrunnlag: MutableMap<PersonStønad, List<GrunnlagRequestDto>> =
                behandling.alleBidragsmottakere
                    .associate {
                        PersonStønad(Personident(it.ident!!), it.stønadstype) to
                            oppretteGrunnlagsobjekter(
                                Personident(it.ident!!),
                                Rolletype.BIDRAGSMOTTAKER,
                                behandling,
                            )
                    }.toMutableMap()

            if (listOf(TypeBehandling.BIDRAG, TypeBehandling.SÆRBIDRAG).contains(behandlingstype)) {
                requestobjekterGrunnlag[PersonStønad(Personident(behandling.bidragspliktig!!.ident!!), null)] =
                    oppretteGrunnlagsobjekter(
                        Personident(behandling.bidragspliktig!!.ident!!),
                        Rolletype.BIDRAGSPLIKTIG,
                        behandling,
                    )
            }

            behandling.søknadsbarn
                .filter { sb -> sb.ident != null }
                .map { PersonStønad(Personident(it.ident!!), it.stønadstype) }
                .forEach {
                    requestobjekterGrunnlag[it] =
                        oppretteGrunnlagsobjekter(
                            Personident(it.personident.verdi),
                            Rolletype.BARN,
                            behandling,
                        )
                }
            return requestobjekterGrunnlag
        }

        fun oppretteGrunnlagsobjekter(
            personident: Personident,
            rolletype: Rolletype,
            behandling: Behandling,
        ): List<GrunnlagRequestDto> =
            henteGrunnlag(
                personident,
                Grunnlagsobjektvelger.requestobjekter(behandling.tilType(), rolletype),
                behandling.virkningstidspunktEllerSøktFomDato,
            )

        private fun henteGrunnlag(
            personident: Personident,
            grunnlagstyper: Set<GrunnlagRequestType>,
            virkningstidspunktEllerSøktFra: LocalDate,
        ) = grunnlagstyper
            .map {
                val fradato = finneFraDato(it, virkningstidspunktEllerSøktFra)
                val tildato = LocalDate.now().plusDays(1)

                GrunnlagRequestDto(
                    type = it,
                    personId = personident.verdi,
                    periodeFra = fradato,
                    periodeTil = tildato,
                )
            }.toList()
            .sortedBy { personident }

        private fun finneFraDato(
            type: GrunnlagRequestType,
            virkningstidspunktEllerSøktFra: LocalDate,
        ): LocalDate {
            val fradato = setOf(LocalDate.now(), virkningstidspunktEllerSøktFra).min()

            return when (type) {
                GrunnlagRequestType.SKATTEGRUNNLAG -> {
                    fradato.minusYears(3).withMonth(1).withDayOfMonth(1)
                }

                GrunnlagRequestType.AINNTEKT -> {
                    fradato.minusYears(1).withMonth(1).withDayOfMonth(1)
                }

                GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG -> {
                    if (fradato.isBefore(LocalDate.now().minusYears(5))) {
                        LocalDate.now().minusYears(5)
                    } else {
                        fradato
                    }
                }

                else -> {
                    fradato
                }
            }
        }
    }

    @Retryable(maxAttempts = 2, backoff = Backoff(delay = 100, maxDelay = 300, multiplier = 2.0))
    suspend fun henteGrunnlag(
        grunnlag: List<GrunnlagRequestDto>,
        formål: Formål,
    ): HentetGrunnlag {
        return try {
            HentetGrunnlag(
                postForNonNullEntity(
                    bidragGrunnlagUri.pathSegment("hentgrunnlag").build().toUri(),
                    HentGrunnlagRequestDto(formaal = formål, grunnlagRequestDtoListe = grunnlag),
                ),
            )
        } catch (e: Exception) {
            logger.error(e) { "Feil oppstod ved henting av grunnlag med melding ${e.message}." }
            return HentetGrunnlag(null, tekniskFeil = "Feil ved henting av grunnlag")
        }
    }
}

data class HentetGrunnlag(
    val hentGrunnlagDto: HentGrunnlagDto?,
    val tekniskFeil: String? = null,
)
