package no.nav.bidrag.behandling.controller.v2

import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.opplysninger.BoforholdBearbeidetPeriode
import no.nav.bidrag.behandling.database.opplysninger.BoforholdHusstandBearbeidet
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.beregnSivilstandPerioder
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.boforhold.response.RelatertPerson
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.Ainntektspost
import no.nav.bidrag.transport.behandling.inntekt.request.Barnetillegg
import no.nav.bidrag.transport.behandling.inntekt.request.Kontantstøtte
import no.nav.bidrag.transport.behandling.inntekt.request.SkattegrunnlagForLigningsår
import no.nav.bidrag.transport.behandling.inntekt.request.Småbarnstillegg
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.request.UtvidetBarnetrygd
import no.nav.bidrag.transport.behandling.inntekt.response.TransformerInntekterResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDate

@BehandlingRestControllerV2
@Deprecated("Midlertidlig kontroller for proessering av grunnlagdata")
class DatabehandlerController(
    private val behandlingService: BehandlingService,
    private val inntektApi: InntektApi,
) {
    @Suppress("unused")
    @PostMapping("/databehandler/sivilstand/{behandlingId}")
    fun konverterSivilstand(
        @PathVariable behandlingId: Long,
        @RequestBody request: List<SivilstandGrunnlagDto>,
    ): SivilstandBeregnet {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        return behandling.beregnSivilstandPerioder(request)
    }

    @Suppress("unused")
    @PostMapping("/databehandler/bosstatus/{behandlingId}")
    fun konverterBosstatus(
        @PathVariable behandlingId: Long,
        @RequestBody request: List<RelatertPerson>,
    ): List<BoforholdHusstandBearbeidet> {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val beregnet =
            BoforholdApi.beregn(behandling.virkningstidspunkt ?: behandling.søktFomDato, request)
        return beregnet.groupBy { it.relatertPersonPersonId }.map {
            val rolle = behandling.roller.find { rolle -> rolle.ident == it.key }
            BoforholdHusstandBearbeidet(
                ident = it.key!!,
                foedselsdato = rolle?.foedselsdato ?: hentPersonFødselsdato(it.key),
                navn = rolle?.navn,
                perioder =
                    it.value.map {
                        BoforholdBearbeidetPeriode(
                            fraDato = it.periodeFom.atStartOfDay(),
                            tilDato = it.periodeTom?.atStartOfDay(),
                            bostatus = it.bostatus,
                        )
                    },
            )
        }
    }

    @Suppress("unused")
    @PostMapping("/databehandler/inntekt/{behandlingId}")
    fun konverterInntekter(
        @PathVariable behandlingId: Long,
        @RequestBody request: KonverterInntekterDto,
    ): ResponseEntity<TransformerInntekterResponse> {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val rolle = behandling.roller.find { it.ident == request.personId }
        return inntektApi.transformerInntekter(
            request.grunnlagDto.tilTransformerInntekterRequest(
                rolle!!,
            ),
        )
            .let { ResponseEntity.ok(it) }
    }
}

data class KonverterInntekterDto(val personId: String, val grunnlagDto: HentGrunnlagDto)

fun HentGrunnlagDto.tilTransformerInntekterRequest(rolle: Rolle) =
    TransformerInntekterRequest(
        ainntektHentetDato = LocalDate.now(),
        ainntektsposter =
            this.ainntektListe.filter { it.personId == rolle.ident }.flatMap { ainntektGrunnlag ->
                ainntektGrunnlag.ainntektspostListe.map {
                    Ainntektspost(
                        utbetalingsperiode = it.utbetalingsperiode,
                        opptjeningsperiodeFra = it.opptjeningsperiodeFra,
                        opptjeningsperiodeTil = it.opptjeningsperiodeTil,
                        beskrivelse = it.beskrivelse,
                        beløp = it.beløp,
                        referanse = ainntektGrunnlag.tilGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
                    )
                }
            },
        skattegrunnlagsliste =
            this.skattegrunnlagListe.filter { it.personId == rolle.ident }.map {
                SkattegrunnlagForLigningsår(
                    ligningsår = it.periodeFra.year,
                    skattegrunnlagsposter = it.skattegrunnlagspostListe,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
                )
            },
        barnetilleggsliste =
            this.barnetilleggListe.filter { it.partPersonId == rolle.ident }.map {
                Barnetillegg(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløpBrutto,
                    barnPersonId = it.barnPersonId,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse, ""),
                )
            },
        kontantstøtteliste =
            this.kontantstøtteListe.filter { it.partPersonId == rolle.ident }.map {
                Kontantstøtte(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløp.toBigDecimal(),
                    barnPersonId = it.barnPersonId,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse, ""),
                )
            },
        utvidetBarnetrygdliste =
            this.utvidetBarnetrygdListe.filter { it.personId == rolle.ident }.map {
                UtvidetBarnetrygd(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløp,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
                )
            },
        småbarnstilleggliste =
            this.småbarnstilleggListe.filter { it.personId == rolle.ident }.map {
                Småbarnstillegg(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløp,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
                )
            },
    )
