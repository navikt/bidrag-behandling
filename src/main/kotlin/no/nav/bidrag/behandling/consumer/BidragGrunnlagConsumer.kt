package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.vedtak.Formål
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

@Component
class BidragGrunnlagConsumer(
    @Value("\${BIDRAG_GRUNNLAG_URL}") private val bidragGrunnlagUrl: URI,
    @Qualifier("azure") restTemplate: RestTemplate,
) : AbstractRestClient(restTemplate, "bidrag-grunnlag") {
    private val bidragGrunnlagUri get() = UriComponentsBuilder.fromUri(bidragGrunnlagUrl)

    companion object {
        val grunnlagstyperBm =
            setOf(
                GrunnlagRequestType.AINNTEKT,
                GrunnlagRequestType.SKATTEGRUNNLAG,
                GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG,
                GrunnlagRequestType.BARNETILLEGG,
                GrunnlagRequestType.HUSSTANDSMEDLEMMER_OG_EGNE_BARN,
                GrunnlagRequestType.SIVILSTAND,
                GrunnlagRequestType.ARBEIDSFORHOLD,
            )

        val grunnlagstyperBarn =
            setOf(
                GrunnlagRequestType.ARBEIDSFORHOLD,
                GrunnlagRequestType.SKATTEGRUNNLAG,
                GrunnlagRequestType.AINNTEKT,
            )

        fun oppretteGrunnlagsobjekterBarn(personidentBarn: Personident): List<GrunnlagRequestDto> =
            henteGrunnlag(
                personidentBarn,
                grunnlagstyperBarn,
            )

        fun oppretteGrunnlagsobjekterBm(personidentBm: Personident): List<GrunnlagRequestDto> =
            henteGrunnlag(
                personidentBm,
                grunnlagstyperBm,
            )

        private fun henteGrunnlag(
            personident: Personident,
            grunnlagstyper: Set<GrunnlagRequestType>,
        ) = grunnlagstyper.map {
            val fraDato = finneFraDato(it)
            val tilDato = finneTilDato(it)

            GrunnlagRequestDto(
                type = it,
                personId = personident.verdi,
                periodeFra = fraDato,
                periodeTil = tilDato,
            )
        }.toList().sortedBy { personident }

        private fun finneFraDato(type: GrunnlagRequestType): LocalDate {
            val dagensDato = LocalDate.now()
            return dagensDato.minusMonths(
                when (type) {
                    GrunnlagRequestType.SKATTEGRUNNLAG -> 36
                    GrunnlagRequestType.AINNTEKT -> {
                        if (dagensDato.dayOfMonth > 6) 12 else 13
                    }

                    else -> 0
                },
            )
        }

        private fun finneTilDato(type: GrunnlagRequestType): LocalDate {
            val dagensDato = LocalDate.now()
            return dagensDato.minusMonths(
                when (type) {
                    GrunnlagRequestType.AINNTEKT -> {
                        if (dagensDato.dayOfMonth > 6) 0 else 1
                    }

                    else -> 0
                },
            )
        }
    }

    fun henteGrunnlagRequestobjekterForBehandling(behandling: Behandling): MutableMap<Personident, List<GrunnlagRequestDto>> {
        val requestobjekterGrunnlag: MutableMap<Personident, List<GrunnlagRequestDto>> =
            mutableMapOf(
                Personident(
                    behandling.bidragsmottaker!!.ident!!,
                ) to oppretteGrunnlagsobjekterBm(Personident(behandling.bidragsmottaker!!.ident!!)),
            )

        behandling.søknadsbarn.filter { sb -> sb.ident != null }.map { Personident(it.ident!!) }
            .forEach {
                requestobjekterGrunnlag[it] =
                    oppretteGrunnlagsobjekterBarn(Personident(it.verdi))
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
