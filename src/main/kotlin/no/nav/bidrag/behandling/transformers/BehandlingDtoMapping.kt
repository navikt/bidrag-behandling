package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataEndretDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
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
        BoforholdDto(
            husstandsbarn = husstandsbarn.toHusstandsBarnDto(this),
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
                    .sortedByDescending { it.datoFom ?: it.opprinneligFom }
                    .toSet(),
            beregnetInntekter = hentBeregnetInntekter(),
            notat =
                BehandlingNotatDto(
                    medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
                    kunINotat = inntektsbegrunnelseKunINotat,
                ),
            valideringsfeil = hentInntekterValideringsfeil(),
        ),
    aktiveGrunnlagsdata = gjeldendeAktiveGrunnlagsdata.map(Grunnlag::toDto).toSet(),
    ikkeAktiverteEndringerIGrunnlagsdata = ikkeAktiverteEndringerIGrunnlagsdata,
)

fun Behandling.hentInntekterValideringsfeil(): InntektValideringsfeilDto {
    return InntektValideringsfeilDto(
        årsinntekter =
            inntekter.mapValideringsfeilForÅrsinntekter(
                virkningstidspunktEllerSøktFomDato,
                roller,
            ).takeIf { it.isNotEmpty() },
        barnetillegg =
            inntekter.mapValideringsfeilForYtelseSomGjelderBarn(
                Inntektsrapportering.BARNETILLEGG,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ).takeIf { it.isNotEmpty() },
        småbarnstillegg =
            inntekter.mapValideringsfeilForYtelse(
                Inntektsrapportering.SMÅBARNSTILLEGG,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ),
        utvidetBarnetrygd =
            inntekter.mapValideringsfeilForYtelse(
                Inntektsrapportering.UTVIDET_BARNETRYGD,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ),
        kontantstøtte =
            inntekter.mapValideringsfeilForYtelseSomGjelderBarn(
                Inntektsrapportering.KONTANTSTØTTE,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ).takeIf { it.isNotEmpty() },
    )
}

fun Set<Inntekt>.mapValideringsfeilForÅrsinntekter(
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
): Set<InntektValideringsfeil> {
    val inntekterSomSkalSjekkes = filter { !eksplisitteYtelser.contains(it.type) }.filter { it.taMed }
    return roller.map { rolle ->
        val inntekterTaMed = inntekterSomSkalSjekkes.filter { it.ident == rolle.ident }
        if (inntekterTaMed.isEmpty() && (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER || rolle.rolletype == Rolletype.BIDRAGSPLIKTIG)) {
            InntektValideringsfeil(
                hullIPerioder = emptyList(),
                overlappendePerioder = emptySet(),
                fremtidigPeriode = false,
                manglerPerioder = true,
                ident = rolle.ident!!,
                rolle = rolle.rolletype,
            )
        } else {
            InntektValideringsfeil(
                hullIPerioder = inntekterTaMed.finnHullIPerioder(virkningstidspunkt),
                overlappendePerioder = inntekterTaMed.finnOverlappendePerioder(),
                fremtidigPeriode = inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
                manglerPerioder =
                    (rolle.rolletype != Rolletype.BARN)
                        .ifTrue { this.isEmpty() } ?: false,
                ident = rolle.ident!!,
                rolle = rolle.rolletype,
            )
        }
    }.filter { it.harFeil }.toSet()
}

fun Set<Inntekt>.mapValideringsfeilForYtelse(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
    gjelderBarn: String? = null,
) = filter { it.taMed }.filter { it.type == type }.let { inntekterTaMed ->
    val inntektGjelderIdent = inntekterTaMed.firstOrNull()?.ident
    val gjelderRolle = roller.find { it.ident == inntektGjelderIdent }
    val gjelderIdent = gjelderRolle?.ident ?: inntektGjelderIdent ?: ""
    InntektValideringsfeil(
        overlappendePerioder = inntekterTaMed.finnOverlappendePerioder(),
        fremtidigPeriode =
            inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
        ident = gjelderIdent,
        rolle = gjelderRolle?.rolletype,
        gjelderBarn = gjelderBarn,
        erYtelse = true,
    ).takeIf { it.harFeil }
}

fun Set<Inntekt>.mapValideringsfeilForYtelseSomGjelderBarn(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
) = filter { inntekstrapporteringerSomKreverGjelderBarn.contains(type) }
    .groupBy { it.gjelderBarn }.map { (gjelderBarn, inntekter) ->
        inntekter.toSet().mapValideringsfeilForYtelse(
            type,
            virkningstidspunkt,
            roller,
            gjelderBarn,
        )
    }.filterNotNull().toSet()

fun List<Inntekt>.inneholderFremtidigPeriode(virkningstidspunkt: LocalDate) =
    any {
        it.datoFom!!.isAfter(maxOf(virkningstidspunkt.withDayOfMonth(1), LocalDate.now().withDayOfMonth(1)))
    }

fun Behandling.hentBeregnetInntekter() =
    BeregnApi().beregnInntekt(tilInntektberegningDto()).inntektPerBarnListe.sortedBy {
        it.inntektGjelderBarnIdent?.verdi
    }
