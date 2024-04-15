package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.dto.v1.notat.Arbeidsforhold
import no.nav.bidrag.behandling.dto.v1.notat.Boforhold
import no.nav.bidrag.behandling.dto.v1.notat.BoforholdBarn
import no.nav.bidrag.behandling.dto.v1.notat.Inntekter
import no.nav.bidrag.behandling.dto.v1.notat.InntekterPerRolle
import no.nav.bidrag.behandling.dto.v1.notat.Notat
import no.nav.bidrag.behandling.dto.v1.notat.NotatBeregnetInntektDto
import no.nav.bidrag.behandling.dto.v1.notat.NotatDto
import no.nav.bidrag.behandling.dto.v1.notat.NotatInntektDto
import no.nav.bidrag.behandling.dto.v1.notat.NotatInntektspostDto
import no.nav.bidrag.behandling.dto.v1.notat.NotatResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.notat.OpplysningerBruktTilBeregning
import no.nav.bidrag.behandling.dto.v1.notat.OpplysningerFraFolkeregisteret
import no.nav.bidrag.behandling.dto.v1.notat.PersonNotatDto
import no.nav.bidrag.behandling.dto.v1.notat.SivilstandNotat
import no.nav.bidrag.behandling.dto.v1.notat.Vedtak
import no.nav.bidrag.behandling.dto.v1.notat.Virkningstidspunkt
import no.nav.bidrag.behandling.dto.v1.notat.tilNotatKilde
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.transformers.behandling.hentBeregnetInntekter
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.tilDto
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth

@Service
class NotatOpplysningerService(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
    private val beregningService: BeregningService,
) {
    fun hentNotatOpplysninger(behandlingId: Long): NotatDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        val opplysningerBoforhold =
            grunnlagService.hentSistInnhentet(
                behandlingId,
                behandling.bidragsmottaker!!.id!!,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, true),
            )
                ?.konverterData<List<BoforholdResponse>>() ?: emptyList()
        val opplysningerSivilstand =
            grunnlagService.hentSistInnhentet(
                behandlingId,
                behandling.bidragsmottaker!!.id!!,
                Grunnlagstype(Grunnlagsdatatype.SIVILSTAND, false),
            )
                ?.konverterData<List<SivilstandGrunnlagDto>>() ?: emptyList()

        val alleArbeidsforhold: List<ArbeidsforholdGrunnlagDto> =
            behandling.roller.filter { it.ident != null }.flatMap { r ->
                grunnlagService.hentSistInnhentet(
                    behandlingId,
                    r.id!!,
                    Grunnlagstype(Grunnlagsdatatype.ARBEIDSFORHOLD, false),
                )
                    .konverterData<List<ArbeidsforholdGrunnlagDto>>() ?: emptyList()
            }

        return NotatDto(
            saksnummer = behandling.saksnummer,
            saksbehandlerNavn =
                TokenUtils.hentSaksbehandlerIdent()
                    ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) },
            virkningstidspunkt = behandling.tilVirkningstidspunkt(),
            boforhold =
                Boforhold(
                    notat = behandling.tilNotatBoforhold(),
                    sivilstand = behandling.tilSivilstand(opplysningerSivilstand),
                    barn =
                        behandling.husstandsbarn.sortedBy { it.ident }
                            .map { it.tilBoforholdBarn(opplysningerBoforhold) },
                ),
            roller = behandling.roller.map(Rolle::tilNotatRolle),
            inntekter =
                Inntekter(
                    notat = behandling.tilNotatInntekt(),
                    inntekterPerRolle =
                        behandling.roller.map { r ->
                            behandling.hentInntekterForIdent(
                                r.ident!!,
                                r,
                                alleArbeidsforhold.filter { r.ident == it.partPersonId },
                            )
                        },
                ),
            vedtak = behandling.hentBeregning(),
        )
    }

    private fun Behandling.hentBeregning(): Vedtak {
        val resultat =
            try {
                beregningService.beregneForskudd(id!!).tilDto()
            } catch (e: Exception) {
                emptyList()
            }
        return Vedtak(
            erFattet = erVedtakFattet,
            fattetTidspunkt = vedtakstidspunkt,
            fattetAvSaksbehandler = vedtakFattetAv?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) },
            resultat =
                resultat.map { resultat ->
                    NotatResultatBeregningBarnDto(
                        barn = roller.find { it.ident == resultat.barn.ident!!.verdi }!!.tilNotatRolle(),
                        perioder =
                            resultat.perioder.map {
                                NotatResultatBeregningBarnDto.NotatResultatPeriodeDto(
                                    periode = it.periode,
                                    beløp = it.beløp,
                                    resultatKode = it.resultatKode,
                                    regel = it.regel,
                                    sivilstand = it.sivilstand,
                                    inntekt = it.inntekt,
                                    antallBarnIHusstanden = it.antallBarnIHusstanden,
                                )
                            },
                    )
                },
        )
    }
}

private fun Behandling.tilNotatBoforhold() =
    Notat(
        medIVedtaket = boforholdsbegrunnelseIVedtakOgNotat,
        intern = boforholdsbegrunnelseKunINotat,
    )

private fun Behandling.tilNotatVirkningstidspunkt() =
    Notat(
        medIVedtaket = virkningstidspunktsbegrunnelseIVedtakOgNotat,
        intern = virkningstidspunktbegrunnelseKunINotat,
    )

private fun Behandling.tilNotatInntekt() =
    Notat(
        medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
        intern = inntektsbegrunnelseKunINotat,
    )

private fun Behandling.tilSivilstand(sivilstandOpplysninger: List<SivilstandGrunnlagDto>) =
    SivilstandNotat(
        opplysningerBruktTilBeregning =
            sivilstand.sortedBy { it.datoFom }
                .map(Sivilstand::tilSivilstandsperiode),
        opplysningerFraFolkeregisteret =
            sivilstandOpplysninger.map { periode ->
                OpplysningerFraFolkeregisteret(
                    periode =
                        ÅrMånedsperiode(
                            periode.gyldigFom ?: LocalDate.MIN,
                            null,
                        ),
                    status = periode.type,
                )
            }.sortedBy { it.periode.fom },
    )

private fun Sivilstand.tilSivilstandsperiode() =
    OpplysningerBruktTilBeregning(
        periode =
            ÅrMånedsperiode(
                datoFom!!,
                datoTom,
            ),
        status = sivilstand,
        kilde = kilde.tilNotatKilde(),
    )

private fun Behandling.tilVirkningstidspunkt() =
    Virkningstidspunkt(
        søknadstype = vedtakstype.name,
        søktAv = soknadFra,
        avslag = avslag,
        årsak = årsak,
        mottattDato = YearMonth.from(mottattdato),
        søktFraDato = YearMonth.from(søktFomDato),
        virkningstidspunkt = virkningstidspunkt,
        notat = tilNotatVirkningstidspunkt(),
    )

private fun Husstandsbarn.tilBoforholdBarn(opplysningerBoforhold: List<BoforholdResponse>) =
    BoforholdBarn(
        gjelder =
            PersonNotatDto(
                rolle = null,
                navn = hentPersonVisningsnavn(ident) ?: navn,
                fødselsdato = fødselsdato,
                ident = ident?.let { Personident(it) },
            ),
        kilde = kilde.tilNotatKilde(),
        medIBehandling = behandling.roller.any { it.ident == this.ident },
        opplysningerFraFolkeregisteret =
            opplysningerBoforhold.filter {
                it.relatertPersonPersonId == this.ident
            }.map {
                OpplysningerFraFolkeregisteret(
                    periode =
                        ÅrMånedsperiode(
                            it.periodeFom,
                            it.periodeTom,
                        ),
                    status = it.bostatus,
                )
            },
        opplysningerBruktTilBeregning =
            perioder.sortedBy { it.datoFom }.map { periode ->
                OpplysningerBruktTilBeregning(
                    periode =
                        ÅrMånedsperiode(
                            periode.datoFom!!,
                            periode.datoTom,
                        ),
                    status = periode.bostatus,
                    kilde = periode.kilde.tilNotatKilde(),
                )
            },
    )

private fun Rolle.tilNotatRolle() =
    PersonNotatDto(
        rolle = rolletype,
        navn = hentPersonVisningsnavn(ident),
        fødselsdato = foedselsdato,
        ident = ident?.let { Personident(it) },
    )

private fun Inntekt.tilNotatInntektDto() =
    NotatInntektDto(
        beløp = belop,
        periode = periode,
        opprinneligPeriode = opprinneligPeriode,
        type = type,
        kilde = kilde.tilNotatKilde(),
        medIBeregning = taMed,
        gjelderBarn =
            gjelderBarn?.let { gjelderBarn ->
                behandling?.roller?.find { it.ident == gjelderBarn }
            }?.tilNotatRolle(),
        inntektsposter =
            inntektsposter.map {
                NotatInntektspostDto(
                    it.kode,
                    it.inntektstype,
                    it.beløp,
                )
            },
    )

private fun Behandling.hentInntekterForIdent(
    ident: String,
    rolle: Rolle,
    arbeidsforhold: List<ArbeidsforholdGrunnlagDto>,
) = InntekterPerRolle(
    gjelder = rolle.tilNotatRolle(),
    beregnetInntekter =
        hentBeregnetInntekter().filter { it.inntektGjelderBarnIdent != null }.map { inntektPerBarn ->
            NotatBeregnetInntektDto(
                roller.find { it.ident == inntektPerBarn.inntektGjelderBarnIdent!!.verdi }!!.tilNotatRolle(),
                inntektPerBarn.summertInntektListe,
            )
        },
    årsinntekter =
        inntekter.sortedBy { it.datoFom }
            .filter { !eksplisitteYtelser.contains(it.type) }
            .sortedBy { it.type }
            .sortedByDescending { it.datoFom ?: it.opprinneligFom }
            .filter { it.ident == ident }
            .map {
                it.tilNotatInntektDto()
            },
    barnetillegg =
        if (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER) {
            inntekter.sortedWith(compareBy({ it.datoFom }, { it.gjelderBarn }))
                .filter { it.type == Inntektsrapportering.BARNETILLEGG }
                .map {
                    it.tilNotatInntektDto()
                }
        } else {
            emptyList()
        },
    småbarnstillegg =
        if (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER) {
            inntekter.sortedBy { it.datoFom }
                .filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
                .map {
                    it.tilNotatInntektDto()
                }
        } else {
            emptyList()
        },
    kontantstøtte =
        if (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER) {
            inntekter.sortedWith(compareBy({ it.datoFom }, { it.gjelderBarn }))
                .filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
                .map {
                    it.tilNotatInntektDto()
                }
        } else {
            emptyList()
        },
    utvidetBarnetrygd =
        if (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER) {
            inntekter.sortedBy { it.datoFom }
                .filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
                .map {
                    it.tilNotatInntektDto()
                }
        } else {
            emptyList()
        },
    arbeidsforhold =
        arbeidsforhold.filter { it.partPersonId == ident }
            .map {
                Arbeidsforhold(
                    periode = ÅrMånedsperiode(it.startdato!!, it.sluttdato),
                    arbeidsgiver = it.arbeidsgiverNavn ?: "-",
                    stillingProsent = it.ansettelsesdetaljerListe?.firstOrNull()?.avtaltStillingsprosent?.toString(),
                    lønnsendringDato = it.ansettelsesdetaljerListe?.firstOrNull()?.sisteLønnsendringDato,
                )
            },
)
