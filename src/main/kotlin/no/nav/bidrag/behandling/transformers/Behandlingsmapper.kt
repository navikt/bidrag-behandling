package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveGrunnlagsdata
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.service.GrunnlagService
import no.nav.bidrag.behandling.service.NotatService
import no.nav.bidrag.behandling.transformers.behandling.tilAndreVoksneIHusstanden
import no.nav.bidrag.behandling.transformers.behandling.tilBoforholdV2
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.behandling.tilGrunnlagsinnhentingsfeil
import no.nav.bidrag.behandling.transformers.behandling.tilHusstandsmedlem
import no.nav.bidrag.behandling.transformers.behandling.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.behandling.toSivilstand
import no.nav.bidrag.behandling.transformers.utgift.tilUtgiftDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import org.springframework.stereotype.Component

@Component
class Behandlingsmapper(
    val grunnlagService: GrunnlagService,
) {
    fun tilDto(
        behandling: Behandling,
        inkludereIkkeAktiverteEndringerIGrunnlagsdata: Boolean = false,
        inkluderHistoriskeInntekter: Boolean = false,
    ): BehandlingDtoV2 {
        val ikkeAktiverteEndringerIGrunnlagsdata =
            if (inkludereIkkeAktiverteEndringerIGrunnlagsdata) {
                grunnlagService.henteNyeGrunnlagsdataMedEndringsdiff(
                    behandling,
                )
            } else {
                IkkeAktiveGrunnlagsdata()
            }

        return behandling.tilDto(ikkeAktiverteEndringerIGrunnlagsdata, inkluderHistoriskeInntekter)
    }

    fun tilAktiveGrunnlagsdata(grunnlag: List<Grunnlag>) = grunnlag.tilAktiveGrunnlagsdata()
}

// TODO: Endre navn til BehandlingDto når v2-migreringen er ferdigstilt
@Suppress("ktlint:standard:value-argument-comment")
private fun Behandling.tilDto(
    ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
    inkluderHistoriskeInntekter: Boolean,
) = BehandlingDtoV2(
    id = id!!,
    type = tilType(),
    vedtakstype = vedtakstype,
    opprinneligVedtakstype = opprinneligVedtakstype,
    stønadstype = stonadstype,
    engangsbeløptype = engangsbeloptype,
    erKlageEllerOmgjøring = erKlageEllerOmgjøring,
    opprettetTidspunkt = opprettetTidspunkt,
    erVedtakFattet = vedtaksid != null,
    søktFomDato = søktFomDato,
    mottattdato = mottattdato,
    klageMottattdato = klageMottattdato,
    søktAv = soknadFra,
    saksnummer = saksnummer,
    søknadsid = soknadsid,
    behandlerenhet = behandlerEnhet,
    roller =
        roller.map { it.tilDto() }.toSet(),
    søknadRefId = soknadRefId,
    vedtakRefId = refVedtaksid,
    virkningstidspunkt =
        VirkningstidspunktDto(
            virkningstidspunkt = virkningstidspunkt,
            opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
            årsak = årsak,
            avslag = avslag,
            begrunnelse = BegrunnelseDto(NotatService.henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT)),
        ),
    boforhold = tilBoforholdV2(),
    inntekter =
        tilInntektDtoV2(
            grunnlag.hentSisteAktiv(),
            inkluderHistoriskeInntekter = inkluderHistoriskeInntekter,
        ),
    aktiveGrunnlagsdata = grunnlag.hentSisteAktiv().tilAktiveGrunnlagsdata(),
    utgift = tilUtgiftDto(),
    ikkeAktiverteEndringerIGrunnlagsdata = ikkeAktiverteEndringerIGrunnlagsdata,
    feilOppståttVedSisteGrunnlagsinnhenting =
        grunnlagsinnhentingFeilet?.let {
            val typeRef: TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>> =
                object : TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>>() {}

            objectmapper.readValue(it, typeRef).tilGrunnlagsinnhentingsfeil(this)
        },
)

private fun List<Grunnlag>.tilAktiveGrunnlagsdata() =
    AktiveGrunnlagsdata(
        arbeidsforhold =
            filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }
                .mapNotNull { it.konvertereData<Set<ArbeidsforholdGrunnlagDto>>() }
                .flatten()
                .toSet(),
        husstandsmedlem =
            filter { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }.tilHusstandsmedlem(),
        andreVoksneIHusstanden = tilAndreVoksneIHusstanden(true),
        sivilstand =
            find { it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }.toSivilstand(),
    )
