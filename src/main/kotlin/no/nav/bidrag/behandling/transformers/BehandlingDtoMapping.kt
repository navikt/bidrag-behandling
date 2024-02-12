package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn

fun Behandling.tilBehandlingDtoV2(opplysninger: List<BehandlingGrunnlag>) =
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
        opplysninger = opplysninger.map(BehandlingGrunnlag::toDto),
    )
