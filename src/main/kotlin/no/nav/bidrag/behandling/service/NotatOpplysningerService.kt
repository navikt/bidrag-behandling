package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.dto.v1.notat.Arbeidsforhold
import no.nav.bidrag.behandling.dto.v1.notat.Barnetillegg
import no.nav.bidrag.behandling.dto.v1.notat.Boforhold
import no.nav.bidrag.behandling.dto.v1.notat.BoforholdBarn
import no.nav.bidrag.behandling.dto.v1.notat.Inntekter
import no.nav.bidrag.behandling.dto.v1.notat.InntekterPerRolle
import no.nav.bidrag.behandling.dto.v1.notat.InntekterSomLeggesTilGrunn
import no.nav.bidrag.behandling.dto.v1.notat.Notat
import no.nav.bidrag.behandling.dto.v1.notat.NotatDto
import no.nav.bidrag.behandling.dto.v1.notat.OpplysningerBruktTilBeregning
import no.nav.bidrag.behandling.dto.v1.notat.OpplysningerFraFolkeregisteret
import no.nav.bidrag.behandling.dto.v1.notat.ParterISøknad
import no.nav.bidrag.behandling.dto.v1.notat.SivilstandNotat
import no.nav.bidrag.behandling.dto.v1.notat.UtvidetBarnetrygd
import no.nav.bidrag.behandling.dto.v1.notat.Virkningstidspunkt
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import org.springframework.stereotype.Service
import java.time.YearMonth

@Service
class NotatOpplysningerService(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
) {
    fun hentNotatOpplysninger(behandlingId: Long): NotatDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        val opplysningerBoforhold =
            grunnlagService.hentSistInnhentet(
                behandlingId,
                behandling.roller
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.first().id!!,
                Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
            )
                ?.konverterData<List<RelatertPersonGrunnlagDto>>() ?: emptyList()
        val opplysningerSivilstand =
            grunnlagService.hentSistInnhentet(
                behandlingId,
                behandling.roller
                    .filter { Rolletype.BIDRAGSMOTTAKER == it.rolletype }.first().id!!,
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
            parterISøknad = behandling.roller.map(Rolle::tilPartISøknad),
            inntekter =
                Inntekter(
                    notat = behandling.tilNotatInntekt(),
                    inntekterPerRolle =
                        behandling.roller.map { r ->
                            behandling.hentInntekterForIdent(
                                r.ident!!,
                                r.rolletype,
                                alleArbeidsforhold.filter { r.ident == it.partPersonId },
                            )
                        },
                ),
            vedtak = emptyList(),
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
                            periode.gyldigFom!!,
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
        kilde = kilde.name,
    )

private fun Behandling.tilVirkningstidspunkt() =
    Virkningstidspunkt(
        søknadstype = vedtakstype.name,
        søktAv = soknadFra,
        mottattDato = YearMonth.from(mottattdato),
        søktFraDato = YearMonth.from(søktFomDato),
        virkningstidspunkt = virkningstidspunkt,
        notat = tilNotatVirkningstidspunkt(),
    )

private fun Husstandsbarn.tilBoforholdBarn(opplysningerBoforhold: List<RelatertPersonGrunnlagDto>) =
    BoforholdBarn(
        navn = navn!!,
        fødselsdato = fødselsdato,
        opplysningerFraFolkeregisteret =
            opplysningerBoforhold.filter {
                it.partPersonId == this.ident
            }.flatMap {
                it.borISammeHusstandDtoListe.map { periode ->
                    OpplysningerFraFolkeregisteret(
                        periode =
                            ÅrMånedsperiode(
                                periode.periodeFra!!,
                                periode.periodeTil,
                            ),
                        status = Bostatuskode.MED_FORELDER,
                    )
                }
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
                    kilde = periode.kilde.name,
                )
            },
    )

private fun Rolle.tilPartISøknad() =
    ParterISøknad(
        rolle = rolletype,
        navn = hentPersonVisningsnavn(ident),
        fødselsdato = foedselsdato,
        personident = ident?.let { Personident(it) },
    )

private fun Behandling.hentInntekterForIdent(
    ident: String,
    rolle: Rolletype,
    arbeidsforhold: List<ArbeidsforholdGrunnlagDto>,
) = InntekterPerRolle(
    rolle = rolle,
    inntekterSomLeggesTilGrunn =
        inntekter.sortedBy { it.datoFom }
            .filter { it.ident == ident && it.taMed }
            .map {
                InntekterSomLeggesTilGrunn(
                    beløp = it.belop,
                    periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom),
                    beskrivelse = it.type.name,
                    inntektType = it.type,
                )
            },
    barnetillegg =
        if (rolle == Rolletype.BIDRAGSMOTTAKER) {
            inntekter.sortedBy { it.datoFom }
                // TODO: Endre til
                .filter { it.type == Inntektsrapportering.BARNETILLEGG }
                .map {
                    Barnetillegg(
                        periode =
                            ÅrMånedsperiode(
                                it.datoFomEllerOpprinneligFom!!,
                                it.datoTom,
                            ),
                        beløp = it.belop,
                    )
                }
        } else {
            emptyList()
        },
    utvidetBarnetrygd =
        if (rolle == Rolletype.BIDRAGSMOTTAKER) {
            inntekter.sortedBy { it.datoFom }
                .filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
                .map {
                    UtvidetBarnetrygd(
                        periode =
                            ÅrMånedsperiode(
                                it.datoFomEllerOpprinneligFom!!,
                                it.datoTom,
                            ),
                        beløp = it.belop,
                    )
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
