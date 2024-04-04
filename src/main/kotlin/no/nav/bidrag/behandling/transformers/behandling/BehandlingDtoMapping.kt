package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
<<<<<<< HEAD:src/main/kotlin/no/nav/bidrag/behandling/transformers/behandling/BehandlingDtoMapping.kt
=======
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
>>>>>>> main:src/main/kotlin/no/nav/bidrag/behandling/transformers/BehandlingDtoMapping.kt
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataEndretDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.boforhold.toHusstandsBarnDtoV2
import no.nav.bidrag.behandling.transformers.grunnlag.toDto
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.tilInntektberegningDto
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import java.time.LocalDate

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
    virkningstidspunkt =
        VirkningstidspunktDto(
            virkningstidspunkt = virkningstidspunkt,
            opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
            årsak = årsak,
            avslag = avslag,
            notat =
                BehandlingNotatDto(
                    medIVedtaket = virkningstidspunktsbegrunnelseIVedtakOgNotat,
                    kunINotat = virkningstidspunktbegrunnelseKunINotat,
                ),
        ),
    boforhold =
        BoforholdDtoV2(
            husstandsbarn = husstandsbarn.toHusstandsBarnDtoV2(this),
            sivilstand = sivilstand.toSivilstandDto(),
            notat =
                BehandlingNotatDto(
                    medIVedtaket = boforholdsbegrunnelseIVedtakOgNotat,
                    kunINotat = boforholdsbegrunnelseKunINotat,
                ),
            valideringsfeil =
                BoforholdValideringsfeil(
                    husstandsbarn = husstandsbarn.validerBoforhold(virkningstidspunktEllerSøktFomDato).filter { it.harFeil },
                    sivilstand = sivilstand.validerSivilstand(virkningstidspunktEllerSøktFomDato).takeIf { it.harFeil },
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
                gjeldendeAktiveGrunnlagsdata.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER && it.erBearbeidet }
                    .flatMap { grunnlag ->
                        grunnlag.konverterData<SummerteInntekter<SummertMånedsinntekt>>()?.inntekter?.map {
                            it.tilInntektDtoV2(
                                grunnlag.rolle.ident!!,
                            )
                        } ?: emptyList()
                    }.toSet(),
            årsinntekter =
                inntekter.filter { !eksplisitteYtelser.contains(it.type) }.tilInntektDtoV2()
                    .sortedBy { it.rapporteringstype }
                    .sortedBy { it.datoFom }
                    .toSet(),
            beregnetInntekter = hentBeregnetInntekter(),
            notat =
                BehandlingNotatDto(
                    medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                    kunINotat = inntektsbegrunnelseKunINotat,
                ),
            valideringsfeil = hentValideringsfeil(),
        ),
    aktiveGrunnlagsdata = gjeldendeAktiveGrunnlagsdata.map(Grunnlag::toDto).toSet(),
    ikkeAktiverteEndringerIGrunnlagsdata = ikkeAktiverteEndringerIGrunnlagsdata,
)

fun Behandling.hentValideringsfeil(): InntektValideringsfeilDto {
    val inntekterIkkeYtelser = inntekter.filter { !eksplisitteYtelser.contains(it.type) }
    return InntektValideringsfeilDto(
        årsinntekter =
            InntektValideringsfeil(
                hullIPerioder = inntekterIkkeYtelser.finnHullIPerioder(virkningstidspunktEllerSøktFomDato),
                overlappendePerioder = inntekterIkkeYtelser.finnOverlappendePerioder(),
            ),
        barnetillegg =
            inntekter.mapValideringsfeilForType(
                Inntektsrapportering.BARNETILLEGG,
                virkningstidspunktEllerSøktFomDato,
            ),
        småbarnstillegg =
            inntekter.mapValideringsfeilForType(
                Inntektsrapportering.SMÅBARNSTILLEGG,
                virkningstidspunktEllerSøktFomDato,
            ),
        utvidetBarnetrygd =
            inntekter.mapValideringsfeilForType(
                Inntektsrapportering.UTVIDET_BARNETRYGD,
                virkningstidspunktEllerSøktFomDato,
            ),
        kontantstøtte =
            inntekter.mapValideringsfeilForType(
                Inntektsrapportering.KONTANTSTØTTE,
                virkningstidspunktEllerSøktFomDato,
            ),
    )
}

fun Set<Inntekt>.mapValideringsfeilForType(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
) = InntektValideringsfeil(
    hullIPerioder =
        filter { it.type == type }.finnHullIPerioder(
            virkningstidspunkt,
        ),
    overlappendePerioder = filter { it.type == type }.finnOverlappendePerioder(),
)

fun Behandling.hentBeregnetInntekter() =
    BeregnApi().beregnInntekt(tilInntektberegningDto()).inntektPerBarnListe.sortedBy {
        it.inntektGjelderBarnIdent?.verdi
    }
