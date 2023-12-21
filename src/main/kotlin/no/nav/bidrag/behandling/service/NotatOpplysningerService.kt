package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.hentData
import no.nav.bidrag.behandling.database.opplysninger.BoforholdBearbeidet
import no.nav.bidrag.behandling.database.opplysninger.BoforholdHusstandBearbeidet
import no.nav.bidrag.behandling.database.opplysninger.InntektsopplysningerBearbeidet
import no.nav.bidrag.behandling.database.opplysninger.SivilstandBearbeidet
import no.nav.bidrag.behandling.dto.notat.Arbeidsforhold
import no.nav.bidrag.behandling.dto.notat.Barnetillegg
import no.nav.bidrag.behandling.dto.notat.Boforhold
import no.nav.bidrag.behandling.dto.notat.BoforholdBarn
import no.nav.bidrag.behandling.dto.notat.Inntekter
import no.nav.bidrag.behandling.dto.notat.InntekterPerRolle
import no.nav.bidrag.behandling.dto.notat.InntekterSomLeggesTilGrunn
import no.nav.bidrag.behandling.dto.notat.Notat
import no.nav.bidrag.behandling.dto.notat.NotatDto
import no.nav.bidrag.behandling.dto.notat.OpplysningerBruktTilBeregning
import no.nav.bidrag.behandling.dto.notat.OpplysningerFraFolkeregisteret
import no.nav.bidrag.behandling.dto.notat.ParterISøknad
import no.nav.bidrag.behandling.dto.notat.SivilstandNotat
import no.nav.bidrag.behandling.dto.notat.UtvidetBarnetrygd
import no.nav.bidrag.behandling.dto.notat.Virkningstidspunkt
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdDto
import org.springframework.stereotype.Service
import java.time.YearMonth

@Service
class NotatOpplysningerService(
    private val behandlingService: BehandlingService,
    private val opplysningerService: OpplysningerService,
) {
    fun hentNotatOpplysninger(behandlingId: Long): NotatDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val opplysningerBoforhold =
            opplysningerService.hentSistAktiv(behandlingId, OpplysningerType.BOFORHOLD_BEARBEIDET)
                ?.hentData()
                ?: BoforholdBearbeidet()

        val opplysningerInntekt: InntektsopplysningerBearbeidet =
            opplysningerService.hentSistAktiv(behandlingId, OpplysningerType.INNTEKT_BEARBEIDET)
                .hentData() ?: InntektsopplysningerBearbeidet()
        return NotatDto(
            saksnummer = behandling.saksnummer,
            saksbehandlerNavn =
                TokenUtils.hentSaksbehandlerIdent()
                    ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) },
            virkningstidspunkt = behandling.tilVirkningstidspunkt(),
            boforhold =
                Boforhold(
                    notat = behandling.tilNotatBoforhold(),
                    sivilstand = behandling.tilSivilstand(opplysningerBoforhold.sivilstand),
                    barn =
                        behandling.husstandsbarn.sortedBy { it.ident }
                            .map { it.tilBoforholdBarn(opplysningerBoforhold.husstand) },
                ),
            parterISøknad = behandling.roller.map(Rolle::tilPartISøknad),
            inntekter =
                Inntekter(
                    notat = behandling.tilNotatInntekt(),
                    inntekterPerRolle =
                        behandling.roller.map {
                            behandling.hentInntekterForIdent(
                                it.ident!!,
                                it.rolletype,
                                opplysningerInntekt.arbeidsforhold,
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

private fun Behandling.tilSivilstand(sivilstandOpplysninger: List<SivilstandBearbeidet>) =
    SivilstandNotat(
        opplysningerBruktTilBeregning =
            sivilstand.sortedBy { it.datoFom }
                .map(Sivilstand::tilSivilstandsperiode),
        opplysningerFraFolkeregisteret =
            sivilstandOpplysninger.map { periode ->
                OpplysningerFraFolkeregisteret(
                    periode =
                        ÅrMånedsperiode(
                            periode.datoFom,
                            periode.datoTom,
                        ),
                    status = periode.sivilstand,
                )
            }.sortedBy { it.periode?.fom },
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
        virkningstidspunkt = virkningsdato,
        notat = tilNotatVirkningstidspunkt(),
    )

private fun Husstandsbarn.tilBoforholdBarn(opplysningerBoforhold: List<BoforholdHusstandBearbeidet>) =
    BoforholdBarn(
        navn = navn!!,
        fødselsdato =
            foedselsdato
                ?: hentPersonFødselsdato(ident),
        opplysningerFraFolkeregisteret =
            opplysningerBoforhold.filter {
                it.ident == this.ident
            }.flatMap {
                it.perioder.map { periode ->
                    OpplysningerFraFolkeregisteret(
                        periode =
                            ÅrMånedsperiode(
                                periode.fraDato.toLocalDate(),
                                periode.tilDato?.toLocalDate(),
                            ),
                        status = periode.bostatus,
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
        fødselsdato = foedselsdato ?: hentPersonFødselsdato(ident),
        personident = ident?.let { Personident(it) },
    )

private fun Behandling.hentInntekterForIdent(
    ident: String,
    rolle: Rolletype,
    arbeidsforhold: List<ArbeidsforholdDto>,
) = InntekterPerRolle(
    rolle = rolle,
    inntekterSomLeggesTilGrunn =
        inntekter.sortedBy { it.datoFom }
            .filter { it.ident == ident && it.taMed }
            .map {
                InntekterSomLeggesTilGrunn(
                    beløp = it.belop,
                    periode = ÅrMånedsperiode(it.datoFom, it.datoTom),
                    beskrivelse = it.inntektstype.name,
                    inntektType = it.inntektstype,
                )
            },
    barnetillegg =
        if (rolle == Rolletype.BIDRAGSMOTTAKER) {
            barnetillegg.sortedBy { it.datoFom }
                .map {
                    Barnetillegg(
                        periode =
                            ÅrMånedsperiode(
                                it.datoFom!!.toLocalDate(),
                                it.datoTom?.toLocalDate(),
                            ),
                        beløp = it.barnetillegg,
                    )
                }
        } else {
            emptyList()
        },
    utvidetBarnetrygd =
        if (rolle == Rolletype.BIDRAGSMOTTAKER) {
            utvidetBarnetrygd.sortedBy { it.datoFom }
                .map {
                    UtvidetBarnetrygd(
                        periode =
                            ÅrMånedsperiode(
                                it.datoFom!!,
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
                    stillingProsent = it.ansettelsesdetaljer?.firstOrNull()?.avtaltStillingsprosent?.toString(),
                    lønnsendringDato = it.ansettelsesdetaljer?.firstOrNull()?.sisteLønnsendringDato,
                )
            },
)
