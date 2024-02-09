package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering

<<<<<<< HEAD
// TODO: Endre navn til BehandlingDto når v2-migreringen er ferdigstilt
fun Behandling.tilBehandlingDtoV2(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag>,
    ikkeAktiverteEndringerIGrunnlagsdata: List<Grunnlag>,
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
            virkningsdato = virkningsdato,
            årsak = aarsak,
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
                inntekter.filter { it.inntektsrapportering == Inntektsrapportering.BARNETILLEGG }
                    .tilInntektDtoV2().toSet(),
            barnetilsyn =
                inntekter.filter { it.inntektsrapportering == Inntektsrapportering.BARNETILSYN }.tilInntektDtoV2()
                    .toSet(),
            kontantstøtte =
                inntekter.filter { it.inntektsrapportering == Inntektsrapportering.KONTANTSTØTTE }
                    .tilInntektDtoV2().toSet(),
            småbarnstillegg =
                inntekter.filter { it.inntektsrapportering == Inntektsrapportering.SMÅBARNSTILLEGG }
                    .tilInntektDtoV2().toSet(),
            månedsinntekter =
                inntekter.filter { it.inntektsrapportering == Inntektsrapportering.AINNTEKT }
                    .tilInntektDtoV2().toSet(),
            årsinntekter =
                inntekter.filter { !eksplisitteYtelser.contains(it.inntektsrapportering) }.tilInntektDtoV2()
                    .toSet(),
            notat =
                BehandlingNotatDto(
                    medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                    kunINotat = inntektsbegrunnelseKunINotat,
                ),
        ),
    aktiveGrunnlagsdata = gjeldendeAktiveGrunnlagsdata.map(Grunnlag::toDto).toSet(),
    ikkeAktiverteEndringerIGrunnlagsdata = ikkeAktiverteEndringerIGrunnlagsdata.map(Grunnlag::toDto).toSet(),
)

val eksplisitteYtelser =
    setOf(Inntektsrapportering.BARNETILLEGG, Inntektsrapportering.KONTANTSTØTTE, Inntektsrapportering.SMÅBARNSTILLEGG)
=======
fun Behandling.tilBehandlingDtoV2(opplysninger: List<Grunnlag>) =
    BehandlingDtoV2(
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
                inntekter = inntekter.tilInntektDtoV2().toSet(),
                notat =
                    BehandlingNotatDto(
                        medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                        kunINotat = inntektsbegrunnelseKunINotat,
                    ),
            ),
        opplysninger = opplysninger.map(Grunnlag::toDto),
    )
>>>>>>> main
