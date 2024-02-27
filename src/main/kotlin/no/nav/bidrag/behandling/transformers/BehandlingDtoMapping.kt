package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.grunnlag.SummerteMånedsOgÅrsinntekter
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataEndretDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering

// TODO: Endre navn til BehandlingDto når v2-migreringen er ferdigstilt
@Suppress("ktlint:standard:value-argument-comment")
fun Behandling.tilBehandlingDtoV2(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag>,
    ikkeAktiverteEndringerIGrunnlagsdata: Set<GrunnlagsdataEndretDto>,
) = BehandlingDtoV2(
    id = id!!,
    vedtakstype = vedtakstype,
    stønadstype = stonadstype,
    engangsbeløptype = engangsbeloptype,
    erVedtakFattet = vedtaksid != null,
    søktFomDato = søktFomDato,
    mottattdato = mottattdato,
    søktAv = soknadFra,
    saksnummer = saksnummer,
    søknadsid = soknadsid,
    behandlerenhet = behandlerEnhet,
    roller =
        roller.map {
            RolleDto(
                it.id!!,
                it.rolletype,
                it.ident,
                it.navn ?: hentPersonVisningsnavn(it.ident),
                it.foedselsdato,
            )
        }.toSet(),
    søknadRefId = soknadRefId,
    grunnlagspakkeid = grunnlagspakkeid,
    virkningstidspunkt =
        VirkningstidspunktDto(
            virkningstidspunkt = virkningstidspunkt,
            årsak = årsak,
            avslag = avslag,
            notat =
                BehandlingNotatDto(
                    medIVedtaket = virkningstidspunktsbegrunnelseIVedtakOgNotat,
                    kunINotat = virkningstidspunktbegrunnelseKunINotat,
                ),
        ),
    boforhold =
        BoforholdDto(
            husstandsbarn = husstandsbarn.toHusstandsBarnDto(this),
            sivilstand = sivilstand.toSivilstandDto(),
            notat =
                BehandlingNotatDto(
                    medIVedtaket = boforholdsbegrunnelseIVedtakOgNotat,
                    kunINotat = boforholdsbegrunnelseKunINotat,
                ),
        ),
    inntekter =
        InntekterDtoV2(
            barnetillegg =
                inntekter.filter { it.type == Inntektsrapportering.BARNETILLEGG }
                    .tilInntektDtoV2().toSet(),
            utvidetBarnetrygd =
                inntekter.filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }.tilInntektDtoV2()
                    .toSet(),
            kontantstøtte =
                inntekter.filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
                    .tilInntektDtoV2().toSet(),
            småbarnstillegg =
                inntekter.filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
                    .tilInntektDtoV2().toSet(),
            månedsinntekter =
                // TODO: Jan Kjetil. En hacky løsning
                gjeldendeAktiveGrunnlagsdata.filter { it.type == Grunnlagsdatatype.INNTEKT_BEARBEIDET }
                    .flatMap { grunnlag ->
                        grunnlag.konverterData<SummerteMånedsOgÅrsinntekter>()?.summerteMånedsinntekter?.map {
                            it.tilInntektDtoV2(
                                grunnlag.rolle.ident!!,
                            )
                        } ?: emptyList()
                    }.toSet(),
            årsinntekter =
                inntekter.filter { !eksplisitteYtelser.contains(it.type) }.tilInntektDtoV2()
                    .toSet(),
            notat =
                BehandlingNotatDto(
                    medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                    kunINotat = inntektsbegrunnelseKunINotat,
                ),
        ),
    aktiveGrunnlagsdata = gjeldendeAktiveGrunnlagsdata.map(Grunnlag::toDto).toSet(),
    ikkeAktiverteEndringerIGrunnlagsdata = ikkeAktiverteEndringerIGrunnlagsdata,
)

val eksplisitteYtelser =
    setOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
        Inntektsrapportering.SMÅBARNSTILLEGG,
        Inntektsrapportering.AINNTEKT,
    )
