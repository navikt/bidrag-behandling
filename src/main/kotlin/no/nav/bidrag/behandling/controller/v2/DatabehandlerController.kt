package no.nav.bidrag.behandling.controller.v2

import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.tilPersonGrunnlag
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.sivilstand.SivilstandApi
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
    @PostMapping("/databehandler/v2/sivilstand/{behandlingId}")
    fun konverterSivilstand(
        @PathVariable behandlingId: Long,
        @RequestBody request: List<SivilstandGrunnlagDto>,
    ): ResponseEntity<SivilstandBeregnet> {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        if (behandling.virkningstidspunkt == null) {
            return ResponseEntity.notFound().build()
        }
        return SivilstandApi.beregn(behandling.virkningstidspunkt!!, request)
            .let { ResponseEntity.ok(it) }
    }

    @Suppress("unused")
    @PostMapping("/databehandler/v2/inntekt/{behandlingId}")
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
                        referanse = ainntektGrunnlag.tilGrunnlagsreferanse(rolle.tilPersonGrunnlag().referanse),
                    )
                }
            },
        skattegrunnlagsliste =
            this.skattegrunnlagListe.filter { it.personId == rolle.ident }.map {
                SkattegrunnlagForLigningsår(
                    ligningsår = it.periodeFra.year,
                    skattegrunnlagsposter = it.skattegrunnlagspostListe,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilPersonGrunnlag().referanse),
                )
            },
        barnetilleggsliste =
            this.barnetilleggListe.filter { it.partPersonId == rolle.ident }.map {
                Barnetillegg(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløpBrutto,
                    barnPersonId = it.barnPersonId,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilPersonGrunnlag().referanse, ""),
                )
            },
        kontantstøtteliste =
            this.kontantstøtteListe.filter { it.partPersonId == rolle.ident }.map {
                Kontantstøtte(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløp.toBigDecimal(),
                    barnPersonId = it.barnPersonId,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilPersonGrunnlag().referanse, ""),
                )
            },
        utvidetBarnetrygdliste =
            this.utvidetBarnetrygdListe.filter { it.personId == rolle.ident }.map {
                UtvidetBarnetrygd(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløp,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilPersonGrunnlag().referanse),
                )
            },
        småbarnstilleggliste =
            this.småbarnstilleggListe.filter { it.personId == rolle.ident }.map {
                Småbarnstillegg(
                    periodeFra = it.periodeFra,
                    periodeTil = it.periodeTil,
                    beløp = it.beløp,
                    referanse = it.tilGrunnlagsreferanse(rolle.tilPersonGrunnlag().referanse),
                )
            },
    )
