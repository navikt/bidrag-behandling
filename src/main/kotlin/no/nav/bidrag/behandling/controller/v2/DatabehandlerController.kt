package no.nav.bidrag.behandling.controller.v2

import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagDatakilde
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettAinntektGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettKontantstøtteGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSkattegrunnlagGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSmåbarnstilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettUtvidetbarnetrygGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.Ainntektspost
import no.nav.bidrag.transport.behandling.inntekt.request.Barnetillegg
import no.nav.bidrag.transport.behandling.inntekt.request.Kontantstøtte
import no.nav.bidrag.transport.behandling.inntekt.request.SkattegrunnlagForLigningsår
import no.nav.bidrag.transport.behandling.inntekt.request.Småbarnstillegg
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.request.UtvidetBarnetrygd
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
        return SivilstandApi.beregn(behandling.virkningstidspunktEllerSøktFomDato, request)
    }
}

fun HentGrunnlagDto.tilTransformerInntekterRequest(
    rolle: Rolle,
    roller: MutableSet<Rolle>,
) = TransformerInntekterRequest(
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
                    referanse = opprettAinntektGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
                )
            }
        },
    skattegrunnlagsliste =
        this.skattegrunnlagListe.filter { it.personId == rolle.ident }.map {
            SkattegrunnlagForLigningsår(
                ligningsår = it.periodeFra.year,
                skattegrunnlagsposter = it.skattegrunnlagspostListe,
                referanse =
                    opprettSkattegrunnlagGrunnlagsreferanse(
                        rolle.tilGrunnlagPerson().referanse,
                        it.periodeFra.year,
                    ),
            )
        },
    barnetilleggsliste =
        this.barnetilleggListe.filter { it.partPersonId == rolle.ident }.map {
            Barnetillegg(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløpBrutto,
                barnPersonId = it.barnPersonId,
                referanse =
                    opprettBarnetilleggGrunnlagsreferanse(
                        rolle.tilGrunnlagPerson().referanse,
                        GrunnlagDatakilde.PENSJON,
                    ),
            )
        },
    kontantstøtteliste =
        this.kontantstøtteListe.filter { it.partPersonId == rolle.ident }.map {
            Kontantstøtte(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløp.toBigDecimal(),
                barnPersonId = it.barnPersonId,
                referanse =
                    opprettKontantstøtteGrunnlagsreferanse(
                        rolle.tilGrunnlagPerson().referanse,
                    ),
            )
        },
    utvidetBarnetrygdliste =
        this.utvidetBarnetrygdListe.filter { it.personId == rolle.ident }.map {
            UtvidetBarnetrygd(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløp,
                referanse = opprettUtvidetbarnetrygGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
            )
        },
    småbarnstilleggliste =
        this.småbarnstilleggListe.filter { it.personId == rolle.ident }.map {
            Småbarnstillegg(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløp,
                referanse = opprettSmåbarnstilleggGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
            )
        },
)
