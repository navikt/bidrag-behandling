package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.InntekterDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn

fun Behandling.tilBehandlingDto(opplysninger: List<Grunnlag>) =
    no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto(
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
            no.nav.bidrag.behandling.dto.v1.behandling.RolleDto(
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
        no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto(
            virkningsdato = virkningsdato,
            årsak = aarsak,
            notat =
            no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto(
                medIVedtaket = virkningstidspunktsbegrunnelseIVedtakOgNotat,
                kunINotat = virkningstidspunktbegrunnelseKunINotat,
            ),
        ),
        boforhold =
        no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto(
            husstandsbarn = husstandsbarn.toHusstandsBarnDto(this),
            sivilstand = sivilstand.toSivilstandDto(),
            notat =
            no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto(
                medIVedtaket = boforholdsbegrunnelseIVedtakOgNotat,
                kunINotat = boforholdsbegrunnelseKunINotat,
            ),
        ),
        inntekter =
        no.nav.bidrag.behandling.dto.v1.behandling.InntekterDto(
            inntekter = inntekter.toInntektDto(),
            utvidetbarnetrygd = utvidetBarnetrygd.toUtvidetBarnetrygdDto(),
            barnetillegg = barnetillegg.toBarnetilleggDto(),
            småbarnstillegg = emptySet(),
            kontantstøtte = emptySet(),
            notat =
            no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto(
                medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                kunINotat = inntektsbegrunnelseKunINotat,
            ),
        ),
        opplysninger = opplysninger.map(Grunnlag::toDto),
    )
