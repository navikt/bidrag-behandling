package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.behandling.InntekterDto
import no.nav.bidrag.behandling.dto.behandling.RolleDto
import no.nav.bidrag.behandling.dto.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn

fun Behandling.tilBehandlingDto(opplysninger: List<Grunnlag>) =
    BehandlingDto(
        id = id!!,
        vedtakstype = vedtakstype,
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
            InntekterDto(
                inntekter = inntekter.toInntektDto(),
                utvidetbarnetrygd = utvidetBarnetrygd.toUtvidetBarnetrygdDto(),
                barnetillegg = barnetillegg.toBarnetilleggDto(),
                småbarnstillegg = emptySet(),
                kontantstøtte = emptySet(),
                notat =
                    BehandlingNotatDto(
                        medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                        kunINotat = inntektsbegrunnelseKunINotat,
                    ),
            ),
        opplysninger = opplysninger.map(Grunnlag::toDto),
    )
