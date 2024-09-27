package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.AndreVoksneIHusstandenDetaljerDto
import no.nav.bidrag.behandling.dto.v2.behandling.AndreVoksneIHusstandenGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.HusstandsmedlemGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.PeriodeAndreVoksneIHusstanden
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsmedlemDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.egetBarnErEnesteVoksenIHusstanden
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.service.NotatService
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.behandling.erLik
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerInntekter
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerSivilstand
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIArbeidsforhold
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIBoforhold
import no.nav.bidrag.behandling.transformers.behandling.henteRolleForNotat
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.behandling.tilGrunnlagsinnhentingsfeil
import no.nav.bidrag.behandling.transformers.behandling.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.behandling.toSivilstand
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatusperiode
import no.nav.bidrag.behandling.transformers.utgift.tilUtgiftDto
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class Dtomapper(
    val tilgangskontrollService: TilgangskontrollService,
) {
    fun tilDto(
        behandling: Behandling,
        inkluderHistoriskeInntekter: Boolean = false,
    ): BehandlingDtoV2 = behandling.dto(behandling.ikkeAktiveGrunnlagsdata(), inkluderHistoriskeInntekter)

    fun tilAktivereGrunnlagResponseV2(behandling: Behandling) =
        AktivereGrunnlagResponseV2(
            boforhold = behandling.tilBoforholdV2(),
            inntekter = behandling.tilInntektDtoV2(behandling.grunnlagListe.toSet().hentSisteAktiv()),
            aktiveGrunnlagsdata =
                behandling.grunnlagListe
                    .toSet()
                    .hentSisteAktiv()
                    .tilAktiveGrunnlagsdata(),
            ikkeAktiverteEndringerIGrunnlagsdata = behandling.ikkeAktiveGrunnlagsdata(),
        )

    fun tilOppdatereBoforholdResponse(husstandsmedlem: Husstandsmedlem): OppdatereBoforholdResponse =
        husstandsmedlem.mapTilOppdatereBoforholdResponse()

    fun henteAndreVoksneIHusstanden(
        grunnlag: Set<Grunnlag>,
        periode: ÅrMånedsperiode,
        erAktivert: Boolean = true,
    ) = grunnlag.hentAlleAndreVoksneHusstandForPeriode(periode, erAktivert)

    fun henteBegrensetAntallAndreVoksne(
        grunnlag: Set<Grunnlag>,
        periode: ÅrMånedsperiode,
        erAktivert: Boolean = true,
    ) = grunnlag.hentBegrensetAndreVoksneHusstandForPeriode(periode, erAktivert)

    fun endringerIAndreVoksneIBpsHusstand(
        ikkeAktiveGrunnlag: List<Grunnlag>,
        aktiveGrunnlag: List<Grunnlag>,
    ) = ikkeAktiveGrunnlag.henteEndringerIAndreVoksneIBpsHusstand(aktiveGrunnlag)

    private fun tilgangskontrollerePersoninfo(
        personinfo: Personinfo,
        saksnummer: Saksnummer,
    ): Personinfo {
        personinfo.ident?.let {
            if (!tilgangskontrollService.harTilgang(
                    it,
                    saksnummer,
                )
            ) {
                return Personinfo(
                    null,
                    "Person med skjult identitet, født ${personinfo.fødselsdato?.year}",
                    null,
                    true,
                )
            }
        }

        return Personinfo(
            personinfo.ident,
            personinfo.navn ?: hentPersonVisningsnavn(personinfo.ident?.verdi)!!,
            personinfo.fødselsdato,
        )
    }

    private fun Behandling.ikkeAktiveGrunnlagsdata(): IkkeAktiveGrunnlagsdata {
        val behandling = this
        val roller = behandling.roller.sortedBy { if (it.rolletype == Rolletype.BARN) 1 else -1 }
        val inntekter = behandling.inntekter
        val sisteInnhentedeIkkeAktiveGrunnlag = behandling.grunnlagListe.toSet().hentSisteIkkeAktiv()
        val aktiveGrunnlag = behandling.grunnlagListe.toSet().hentSisteAktiv()
        return IkkeAktiveGrunnlagsdata(
            inntekter =
                IkkeAktiveInntekter(
                    årsinntekter =
                        roller
                            .flatMap {
                                sisteInnhentedeIkkeAktiveGrunnlag.hentEndringerInntekter(
                                    it,
                                    inntekter,
                                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                                )
                            }.toSet(),
                    småbarnstillegg =
                        roller
                            .flatMap {
                                sisteInnhentedeIkkeAktiveGrunnlag.hentEndringerInntekter(
                                    it,
                                    inntekter,
                                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                                )
                            }.toSet(),
                    utvidetBarnetrygd =
                        roller
                            .flatMap {
                                sisteInnhentedeIkkeAktiveGrunnlag.hentEndringerInntekter(
                                    it,
                                    inntekter,
                                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                                )
                            }.toSet(),
                    kontantstøtte =
                        roller
                            .flatMap {
                                sisteInnhentedeIkkeAktiveGrunnlag.hentEndringerInntekter(
                                    it,
                                    inntekter,
                                    Grunnlagsdatatype.KONTANTSTØTTE,
                                )
                            }.toSet(),
                    barnetillegg =
                        roller
                            .flatMap {
                                sisteInnhentedeIkkeAktiveGrunnlag.hentEndringerInntekter(
                                    it,
                                    inntekter,
                                    Grunnlagsdatatype.BARNETILLEGG,
                                )
                            }.toSet(),
                ),
            arbeidsforhold = sisteInnhentedeIkkeAktiveGrunnlag.henteEndringerIArbeidsforhold(aktiveGrunnlag),
            husstandsmedlem =
                sisteInnhentedeIkkeAktiveGrunnlag.henteEndringerIBoforhold(aktiveGrunnlag, behandling),
            andreVoksneIHusstanden =
                sisteInnhentedeIkkeAktiveGrunnlag.henteEndringerIAndreVoksneIBpsHusstand(aktiveGrunnlag),
            sivilstand =
                sisteInnhentedeIkkeAktiveGrunnlag.hentEndringerSivilstand(
                    aktiveGrunnlag,
                    behandling.virkningstidspunktEllerSøktFomDato,
                ),
        )
    }

    private fun List<Grunnlag>.henteEndringerIAndreVoksneIBpsHusstand(aktiveGrunnlag: List<Grunnlag>): AndreVoksneIHusstandenGrunnlagDto? {
        val aktivtGrunnlag =
            aktiveGrunnlag.find { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type && it.erBearbeidet }
        val nyttGrunnlag = find { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type && it.erBearbeidet }
        val aktiveData = aktivtGrunnlag.konvertereData<Set<Bostatus>>()?.toSet()
        val nyeData = nyttGrunnlag.konvertereData<Set<Bostatus>>()?.toSet()
        if (aktiveData != null && nyeData != null && !nyeData.erLik(aktiveData)) {
            return AndreVoksneIHusstandenGrunnlagDto(
                perioder =
                    nyeData
                        .asSequence()
                        .filter { it.bostatus != null }
                        .map {
                            PeriodeAndreVoksneIHusstanden(
                                periode = ÅrMånedsperiode(it.periodeFom!!, it.periodeTom),
                                status = it.bostatus!!,
                                totalAntallHusstandsmedlemmer =
                                    toSet()
                                        .hentAlleAndreVoksneHusstandForPeriode(
                                            ÅrMånedsperiode(it.periodeFom!!, it.periodeTom),
                                            false,
                                        ).size,
                                husstandsmedlemmer =
                                    toSet()
                                        .hentBegrensetAndreVoksneHusstandForPeriode(
                                            ÅrMånedsperiode(it.periodeFom!!, it.periodeTom),
                                            false,
                                        ),
                            )
                        }.toSet(),
                innhentet = nyttGrunnlag?.innhentet ?: LocalDateTime.now(),
            )
        }
        return null
    }

    // TODO: Endre navn til BehandlingDto når v2-migreringen er ferdigstilt
    @Suppress("ktlint:standard:value-argument-comment")
    private fun Behandling.dto(
        ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
        inkluderHistoriskeInntekter: Boolean,
    ) = BehandlingDtoV2(
        id = id!!,
        type = tilType(),
        vedtakstype = vedtakstype,
        opprinneligVedtakstype = opprinneligVedtakstype,
        stønadstype = stonadstype,
        engangsbeløptype = engangsbeloptype,
        erKlageEllerOmgjøring = erKlageEllerOmgjøring,
        opprettetTidspunkt = opprettetTidspunkt,
        erVedtakFattet = vedtaksid != null,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        klageMottattdato = klageMottattdato,
        søktAv = soknadFra,
        saksnummer = saksnummer,
        søknadsid = soknadsid,
        behandlerenhet = behandlerEnhet,
        roller =
            roller.map { it.tilDto() }.toSet(),
        søknadRefId = soknadRefId,
        vedtakRefId = refVedtaksid,
        virkningstidspunkt =
            VirkningstidspunktDto(
                virkningstidspunkt = virkningstidspunkt,
                opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
                årsak = årsak,
                avslag = avslag,
                begrunnelse = BegrunnelseDto(NotatService.henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT)),
            ),
        boforhold = tilBoforholdV2(),
        inntekter =
            tilInntektDtoV2(
                grunnlag.hentSisteAktiv(),
                inkluderHistoriskeInntekter = inkluderHistoriskeInntekter,
            ),
        aktiveGrunnlagsdata = grunnlag.hentSisteAktiv().tilAktiveGrunnlagsdata(),
        utgift = tilUtgiftDto(),
        ikkeAktiverteEndringerIGrunnlagsdata = ikkeAktiverteEndringerIGrunnlagsdata,
        feilOppståttVedSisteGrunnlagsinnhenting =
            grunnlagsinnhentingFeilet?.let {
                val typeRef: TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>> =
                    object : TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>>() {}

                objectmapper.readValue(it, typeRef).tilGrunnlagsinnhentingsfeil(this)
            },
    )

    private fun Husstandsmedlem.mapTilOppdatereBoforholdResponse() =
        OppdatereBoforholdResponse(
            oppdatertePerioderMedAndreVoksne =
                (rolle?.rolletype == Rolletype.BIDRAGSPLIKTIG).ifTrue { perioder.tilBostatusperiode() } ?: emptySet(),
            oppdatertHusstandsmedlem =
                (rolle?.rolletype != Rolletype.BIDRAGSPLIKTIG).ifTrue {
                    tilBostatusperiode()
                },
            egetBarnErEnesteVoksenIHusstanden = behandling.egetBarnErEnesteVoksenIHusstanden,
            valideringsfeil =
                BoforholdValideringsfeil(
                    andreVoksneIHusstanden =
                        behandling.husstandsmedlem.voksneIHusstanden
                            ?.validereAndreVoksneIHusstanden(behandling.virkningstidspunktEllerSøktFomDato),
                    husstandsmedlem =
                        behandling.husstandsmedlem.barn
                            .toSet()
                            .validerBoforhold(behandling.virkningstidspunktEllerSøktFomDato)
                            .filter { it.harFeil },
                ),
        )

    private fun Behandling.tilBoforholdV2() =
        BoforholdDtoV2(
            husstandsmedlem =
                husstandsmedlem.barn
                    .toSet()
                    .sortert()
                    .map { it.tilBostatusperiode() }
                    .toSet(),
            andreVoksneIHusstanden = husstandsmedlem.voksneIHusstanden?.perioder?.tilBostatusperiode() ?: emptySet(),
            sivilstand = sivilstand.toSivilstandDto(),
            begrunnelse =
                BegrunnelseDto(
                    innhold = NotatService.henteNotatinnhold(this, NotatType.BOFORHOLD),
                    gjelder = this.henteRolleForNotat(NotatType.BOFORHOLD, null).tilDto(),
                ),
            egetBarnErEnesteVoksenIHusstanden = egetBarnErEnesteVoksenIHusstanden,
            valideringsfeil =
                BoforholdValideringsfeil(
                    andreVoksneIHusstanden =
                        husstandsmedlem.voksneIHusstanden
                            ?.validereAndreVoksneIHusstanden(
                                virkningstidspunkt!!,
                            )?.takeIf { it.harFeil },
                    husstandsmedlem =
                        husstandsmedlem.barn
                            .toSet()
                            .validerBoforhold(virkningstidspunktEllerSøktFomDato)
                            .filter { it.harFeil },
                    sivilstand = sivilstand.validereSivilstand(virkningstidspunktEllerSøktFomDato).takeIf { it.harFeil },
                ),
        )

    private fun Husstandsmedlem.tilBostatusperiode(): HusstandsmedlemDtoV2 {
        val tilgangskontrollertPersoninfo =
            tilgangskontrollerePersoninfo(this.tilPersoninfo(), Saksnummer(this.behandling.saksnummer))

        return HusstandsmedlemDtoV2(
            id = this.id,
            kilde = this.kilde,
            medIBehandling =
                !this.ident.isNullOrBlank() &&
                    behandling.søknadsbarn
                        .map { it.ident }
                        .contains(this.ident),
            perioder =
                this.perioder
                    .sortedBy { it.datoFom }
                    .toSet()
                    .tilBostatusperiode(),
            ident = tilgangskontrollertPersoninfo.ident?.verdi,
            navn = tilgangskontrollertPersoninfo.navn,
            fødselsdato = tilgangskontrollertPersoninfo.fødselsdato,
        )
    }

    private fun Set<Grunnlag>.hentAlleAndreVoksneHusstandForPeriode(
        periode: ÅrMånedsperiode,
        erAktivert: Boolean = true,
    ): List<AndreVoksneIHusstandenDetaljerDto> {
        val grunnlag = if (erAktivert) hentSisteAktiv() else hentSisteIkkeAktiv()

        val boforholdAndreVoksneIHusstanden =
            grunnlag.find { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && !it.erBearbeidet }

        return boforholdAndreVoksneIHusstanden
            .konvertereData<List<RelatertPersonGrunnlagDto>>()
            ?.filter { it.relasjon != Familierelasjon.BARN }
            ?.filter {
                it.borISammeHusstandDtoListe.any { p ->
                    val periodeBorHosBP = ÅrMånedsperiode(p.periodeFra!!, p.periodeTil?.plusMonths(1))
                    periodeBorHosBP.fom <= periode.fom && periodeBorHosBP.tilEllerMax() <= periode.tilEllerMax()
                }
            }?.map { it.tilAndreVoksneIHusstandenDetaljerDto(Saksnummer(boforholdAndreVoksneIHusstanden?.behandling?.saksnummer!!)) }
            ?.sorter() ?: emptyList()
    }

    private fun RelatertPersonGrunnlagDto.tilAndreVoksneIHusstandenDetaljerDto(saksnummer: Saksnummer) =
        AndreVoksneIHusstandenDetaljerDto(
            tilgangskontrollerePersoninfo(this.tilPersoninfo(), saksnummer).navn!!,
            this.fødselsdato,
            this.relasjon != Familierelasjon.INGEN && this.relasjon != Familierelasjon.UKJENT,
            relasjon = this.relasjon,
        )

    private fun Set<Grunnlag>.hentBegrensetAndreVoksneHusstandForPeriode(
        periode: ÅrMånedsperiode,
        erAktivert: Boolean = true,
    ): List<AndreVoksneIHusstandenDetaljerDto> = hentAlleAndreVoksneHusstandForPeriode(periode, erAktivert).begrensAntallPersoner()

    private fun List<Grunnlag>.tilAktiveGrunnlagsdata() =
        AktiveGrunnlagsdata(
            arbeidsforhold =
                filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }
                    .mapNotNull { it.konvertereData<Set<ArbeidsforholdGrunnlagDto>>() }
                    .flatten()
                    .toSet(),
            husstandsmedlem =
                filter { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }.tilHusstandsmedlem(),
            andreVoksneIHusstanden = tilAndreVoksneIHusstanden(true),
            sivilstand =
                find { it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }.toSivilstand(),
        )

    private fun List<Grunnlag>.tilAndreVoksneIHusstanden(erAktivert: Boolean) =
        AndreVoksneIHusstandenGrunnlagDto(
            perioder = tilPeriodeAndreVoksneIHusstanden(erAktivert),
            innhentet = LocalDateTime.now(),
        )

    private fun List<Grunnlag>.tilPeriodeAndreVoksneIHusstanden(erAktivert: Boolean = true): Set<PeriodeAndreVoksneIHusstanden> =
        find { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type && it.erBearbeidet }
            .konvertereData<Set<Bostatus>>()
            ?.map {
                val periode = ÅrMånedsperiode(it.periodeFom!!, it.periodeTom)
                PeriodeAndreVoksneIHusstanden(
                    periode = ÅrMånedsperiode(it.periodeFom!!, it.periodeTom),
                    status = it.bostatus!!,
                    totalAntallHusstandsmedlemmer =
                        toSet()
                            .hentAlleAndreVoksneHusstandForPeriode(
                                periode,
                                erAktivert,
                            ).size,
                    husstandsmedlemmer = toSet().hentBegrensetAndreVoksneHusstandForPeriode(periode, erAktivert),
                )
            }?.toSet() ?: emptySet()
}

private fun List<Grunnlag>.tilHusstandsmedlem() =
    this
        .map {
            HusstandsmedlemGrunnlagDto(
                innhentetTidspunkt = it.innhentet,
                ident = it.gjelder,
                perioder =
                    it
                        .konvertereData<List<BoforholdResponseV2>>()
                        ?.map { boforholdrespons ->
                            HusstandsmedlemGrunnlagDto.BostatusperiodeGrunnlagDto(
                                boforholdrespons.periodeFom,
                                boforholdrespons.periodeTom,
                                boforholdrespons.bostatus,
                            )
                        }?.toSet() ?: emptySet(),
            )
        }.toSet()

fun Husstandsmedlem.tilPersoninfo() =
    Personinfo(
        this.ident?.let { Personident(it) },
        this.navn,
        this.fødselsdato ?: rolle?.fødselsdato,
    )

fun RelatertPersonGrunnlagDto.tilPersoninfo() =
    Personinfo(
        this.partPersonId?.let { Personident(it) },
        this.navn,
        this.fødselsdato,
    )

data class Personinfo(
    val ident: Personident?,
    val navn: String?,
    val fødselsdato: LocalDate?,
    val erMasktert: Boolean = false,
)