package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Formål
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.request.GrunnlagRequestDto
import no.nav.bidrag.transport.behandling.grunnlag.request.HentGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate

@Component
class BidragGrunnlagConsumer(
    @Value("\${BIDRAG_GRUNNLAG_URL}") private val bidragGrunnlagUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-grunnlag") {
    private val bidragGrunnlagUri get() = UriComponentsBuilder.fromUri(bidragGrunnlagUrl)

    companion object {
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
        ) = grunnlagstyper.map {
            val fradato = finneFraDato(it, virkningstidspunktEllerSøktFra)
            val tildato = LocalDate.now().plusDays(1)

            GrunnlagRequestDto(
                type = it,
                personId = personident.verdi,
                periodeFra = fradato,
                periodeTil = tildato,
            )
        }.toList().sortedBy { personident }

        private fun finneFraDato(
            type: GrunnlagRequestType,
            virkningstidspunktEllerSøktFra: LocalDate,
        ): LocalDate {
            val fradato = setOf(LocalDate.now(), virkningstidspunktEllerSøktFra).min()

            return when (type) {
                GrunnlagRequestType.SKATTEGRUNNLAG -> fradato.minusYears(3).withMonth(1).withDayOfMonth(1)
                GrunnlagRequestType.AINNTEKT -> fradato.minusYears(1).withMonth(1).withDayOfMonth(1)
                GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG -> {
                    if (fradato.isBefore(LocalDate.now().minusYears(5))) {
                        LocalDate.now().minusYears(5)
                    } else {
                        fradato
                    }
                }

                else -> fradato
            }
        }
    }

    fun henteGrunnlagRequestobjekterForBehandling(behandling: Behandling): MutableMap<Personident, List<GrunnlagRequestDto>> {
        val behandlingstype = behandling.tilType()

        val requestobjekterGrunnlag: MutableMap<Personident, List<GrunnlagRequestDto>> =
            mutableMapOf(
                Personident(
                    behandling.bidragsmottaker!!.ident!!,
                ) to
                    oppretteGrunnlagsobjekter(
                        Personident(behandling.bidragsmottaker!!.ident!!),
                        Rolletype.BIDRAGSMOTTAKER,
                        behandling,
                    ),
            )
        behandling.søknadsbarn.filter { sb -> sb.ident != null }
            .map { Personident(it.ident!!) }
            .forEach {
                requestobjekterGrunnlag[it] =
                    oppretteGrunnlagsobjekter(
                        Personident(it.verdi),
                        Rolletype.BARN,
                        behandling,
                    )
                if (TypeBehandling.SÆRLIGE_UTGIFTER == behandlingstype) {
                    requestobjekterGrunnlag[Personident(behandling.bidragspliktig!!.ident!!)] =
                        oppretteGrunnlagsobjekter(
                            Personident(behandling.bidragspliktig!!.ident!!),
                            Rolletype.BIDRAGSPLIKTIG,
                            behandling,
                        )
                } else if (TypeBehandling.BIDRAG == behandlingstype) {
                    throw HttpClientErrorException(
                        HttpStatus.BAD_REQUEST,
                        "Behandlingstype ${TypeBehandling.BIDRAG} støttes foreløpig ikke i denne løsningen.",
                    )
                }
            }

        return requestobjekterGrunnlag
    }

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    fun henteGrunnlag(grunnlag: List<GrunnlagRequestDto>): HentGrunnlagDto {
        return postForNonNullEntity(
            bidragGrunnlagUri.pathSegment("hentgrunnlag").build().toUri(),
            HentGrunnlagRequestDto(formaal = Formål.FORSKUDD, grunnlagRequestDtoListe = grunnlag),
        )
    }
}
