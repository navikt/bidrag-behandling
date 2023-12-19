package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.behandling.BehandlingNotatInnholdDto
import no.nav.bidrag.behandling.dto.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.behandling.InntekterDto
import no.nav.bidrag.behandling.dto.behandling.RolleDto
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn

fun Behandling.tilBehandlingDto(opplysninger: List<Opplysninger>) =
    BehandlingDto(
        id = id!!,
        vedtakstype = vedtakstype,
        erVedtakFattet = vedtaksid != null,
        datoFom = datoFom,
        datoTom = datoTom,
        virkningsdato = virkningsdato,
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
                    it.opprettetDato,
                )
            }.toSet(),
        søknadRefId = soknadRefId,
        grunnlagspakkeid = grunnlagspakkeid,
        årsak = aarsak,
        boforhold =
            BoforholdDto(
                husstandsbarn = husstandsbarn.toHusstandsBarnDto(this),
                sivilstand = sivilstand.toSivilstandDto(),
            ),
        inntekter =
            InntekterDto(
                inntekter = inntekter.toInntektDto(),
                utvidetbarnetrygd = utvidetBarnetrygd.toUtvidetBarnetrygdDto(),
                barnetillegg = barnetillegg.toBarnetilleggDto(),
            ),
        opplysninger = opplysninger.map(Opplysninger::toDto),
        notat =
            BehandlingNotatDto(
                virkningstidspunkt =
                    BehandlingNotatInnholdDto(
                        medIVedtaket = virkningstidspunktsbegrunnelseIVedtakOgNotat,
                        kunINotat = virkningstidspunktbegrunnelseKunINotat,
                    ),
                boforhold =
                    BehandlingNotatInnholdDto(
                        medIVedtaket = boforholdsbegrunnelseIVedtakOgNotat,
                        kunINotat = boforholdsbegrunnelseKunINotat,
                    ),
                inntekt =
                    BehandlingNotatInnholdDto(
                        medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                        kunINotat = inntektsbegrunnelseKunINotat,
                    ),
            ),
    )
