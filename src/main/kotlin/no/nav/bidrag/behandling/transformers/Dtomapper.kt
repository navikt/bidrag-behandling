package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.Utgift
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
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragUtgifterDto
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsmedlemDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.egetBarnErEnesteVoksenIHusstanden
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftResponse
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.service.NotatService
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
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
import no.nav.bidrag.behandling.transformers.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.transformers.behandling.toSivilstand
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatusperiode
import no.nav.bidrag.behandling.transformers.samvær.tilDto
import no.nav.bidrag.behandling.transformers.underhold.tilFaktiskeTilsynsutgiftDtos
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDtos
import no.nav.bidrag.behandling.transformers.underhold.tilTilleggsstønadDtos
import no.nav.bidrag.behandling.transformers.utgift.hentValideringsfeil
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.behandling.transformers.utgift.tilMaksGodkjentBeløpDto
import no.nav.bidrag.behandling.transformers.utgift.tilSærbidragKategoriDto
import no.nav.bidrag.behandling.transformers.utgift.tilTotalBeregningDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.notat.BoforholdBarn
import no.nav.bidrag.transport.notat.NotatAndreVoksneIHusstanden
import no.nav.bidrag.transport.notat.NotatAndreVoksneIHusstandenDetaljerDto
import no.nav.bidrag.transport.notat.NotatRolleDto
import no.nav.bidrag.transport.notat.NotatVoksenIHusstandenDetaljerDto
import no.nav.bidrag.transport.notat.OpplysningerBruktTilBeregning
import no.nav.bidrag.transport.notat.OpplysningerFraFolkeregisteret
import no.nav.bidrag.transport.notat.OpplysningerFraFolkeregisteretMedDetaljer
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class Dtomapper(
    val tilgangskontrollService: TilgangskontrollService,
    val validering: ValiderBeregning,
    val validerBehandlingService: ValiderBehandlingService,
    val vedtakGrunnlagMapper: VedtakGrunnlagMapper,
    val beregnBarnebidragApi: BeregnBarnebidragApi,
) {
    fun tilDto(
        behandling: Behandling,
        inkluderHistoriskeInntekter: Boolean = false,
    ) = behandling.tilDto(behandling.ikkeAktiveGrunnlagsdata(), inkluderHistoriskeInntekter)

    fun tilUnderholdDto(underholdskostnad: Underholdskostnad) = underholdskostnad.tilDto()

    fun tilUnderholdskostnadsperioderForBehandlingMedKunEttSøknadsbarn(behandling: Behandling) = behandling.tilBeregnetUnderholdskostnad()

    fun tilAktivereGrunnlagResponseV2(behandling: Behandling) =
        AktivereGrunnlagResponseV2(
            boforhold = behandling.tilBoforholdV2(),
            inntekter = behandling.tilInntektDtoV2(behandling.grunnlagListe.toSet().hentSisteAktiv(), true),
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

    fun tilAndreVoksneIHusstanden(behandling: Behandling) = behandling.andreVoksneIHusstanden()

    fun tilBoforholdBarn(
        husstandsmedlem: Husstandsmedlem,
        boforhold: List<BoforholdResponseV2>,
    ) = husstandsmedlem.boforholdBarn(boforhold)

    fun henteBegrensetAntallAndreVoksne(
        grunnlag: Set<Grunnlag>,
        periode: ÅrMånedsperiode,
        erAktivert: Boolean = true,
    ) = grunnlag.hentBegrensetAndreVoksneHusstandForPeriode(periode, erAktivert)

    fun endringerIAndreVoksneIBpsHusstand(
        ikkeAktiveGrunnlag: List<Grunnlag>,
        aktiveGrunnlag: List<Grunnlag>,
    ) = ikkeAktiveGrunnlag.henteEndringerIAndreVoksneIBpsHusstand(aktiveGrunnlag)

    private fun Set<Underholdskostnad>.tilDtos() = this.map { it.tilDto() }.toSet()

    private fun Underholdskostnad.tilDto(): UnderholdDto {
        // Vil aldri ha flere enn èn rolle per behandling
        val rolleSøknadsbarn = this.person.rolle.firstOrNull()
        return UnderholdDto(
            id = this.id!!,
            harTilsynsordning = this.harTilsynsordning,
            gjelderBarn = this.person.tilPersoninfoDto(this.behandling),
            faktiskTilsynsutgift = this.faktiskeTilsynsutgifter.tilFaktiskeTilsynsutgiftDtos(),
            stønadTilBarnetilsyn =
                rolleSøknadsbarn?.let { this.barnetilsyn.tilStønadTilBarnetilsynDtos() }
                    ?: emptySet(),
            tilleggsstønad = rolleSøknadsbarn?.let { this.tilleggsstønad.tilTilleggsstønadDtos() } ?: emptySet(),
            underholdskostnad = rolleSøknadsbarn?.let { this.behandling.tilBeregnetUnderholdskostnad() } ?: emptySet(),
            begrunnelse =
                NotatService.henteUnderholdsnotat(
                    this.behandling,
                    rolleSøknadsbarn ?: this.behandling.bidragsmottaker!!,
                ),
        )
    }

    private fun Behandling.tilBeregnetUnderholdskostnad(): Set<UnderholdskostnadDto> {
        // TODO: Beregning støtter per nå kun ett søknadsbarn. Skal støtte flere søknadsbarn i fremtiden.
        val grunnlag =
            vedtakGrunnlagMapper.byggGrunnlagForBeregning(
                this,
                this.søknadsbarn.first(),
            )

        val u = beregnBarnebidragApi.beregnUnderholdskostnad(grunnlag)
        u.forEach { it.type }

        val nt = beregnBarnebidragApi.beregnNettoTilsynsutgift(grunnlag).finnAlleDelberegningUnderholdskostnad()
        nt.forEach { it.underholdskostnad }

        return beregnBarnebidragApi
            .beregnUnderholdskostnad(grunnlag)
            .finnAlleDelberegningUnderholdskostnad()
            .tilUnderholdskostnadDto()
    }

    private fun Person.tilPersoninfoDto(behandling: Behandling): PersoninfoDto {
        val rolle =
            behandling.roller.find { r ->
                this.rolle
                    .map { it.id }
                    .toSet()
                    .contains(r.id)
            }

        val personinfo =
            this.ident?.let { vedtakGrunnlagMapper.mapper.personService.hentPerson(it) }
                ?: rolle?.ident?.let { vedtakGrunnlagMapper.mapper.personService.hentPerson(it) }

        return PersoninfoDto(
            id = this.id,
            ident = rolle?.ident?.let { Personident(it) } ?: this.ident?.let { Personident(it) },
            navn = personinfo?.navn ?: this.navn,
            fødselsdato = personinfo?.fødselsdato ?: this.fødselsdato,
            kilde = rolle?.ident?.let { Kilde.OFFENTLIG } ?: Kilde.MANUELL,
            medIBehandlingen = rolle?.ident != null,
        )
    }

    fun Utgift.tilOppdaterUtgiftResponse(utgiftspostId: Long? = null) =
        if (behandling.avslag != null) {
            OppdatereUtgiftResponse(
                avslag = behandling.avslag,
                begrunnelse = henteNotatinnhold(behandling, NotatType.UTGIFTER),
                valideringsfeil = behandling.utgift.hentValideringsfeil(),
            )
        } else {
            OppdatereUtgiftResponse(
                avslag = validering.run { behandling.tilSærbidragAvslagskode() },
                oppdatertUtgiftspost = utgiftsposter.find { it.id == utgiftspostId }?.tilDto(),
                utgiftposter = utgiftsposter.sorter().map { it.tilDto() },
                maksGodkjentBeløp = tilMaksGodkjentBeløpDto(),
                begrunnelse = henteNotatinnhold(behandling, NotatType.UTGIFTER),
                beregning = tilBeregningDto(),
                valideringsfeil = behandling.utgift.hentValideringsfeil(),
                totalBeregning = behandling.utgift?.tilTotalBeregningDto() ?: emptyList(),
            )
        }

    private fun Behandling.tilSamværDto() =
        if (tilType() == TypeBehandling.BIDRAG) {
            samvær.map { it.tilDto() }
        } else {
            null
        }

    fun Behandling.tilUtgiftDto() =
        utgift?.let { utgift ->
            val valideringsfeil = utgift.hentValideringsfeil()
            if (avslag != null) {
                SærbidragUtgifterDto(
                    avslag = avslag,
                    kategori = tilSærbidragKategoriDto(),
                    begrunnelse = BegrunnelseDto(henteNotatinnhold(this, NotatType.UTGIFTER) ?: ""),
                    valideringsfeil = valideringsfeil,
                    totalBeregning = utgift.tilTotalBeregningDto(),
                )
            } else {
                SærbidragUtgifterDto(
                    avslag = validering.run { tilSærbidragAvslagskode() },
                    beregning = utgift.tilBeregningDto(),
                    kategori = tilSærbidragKategoriDto(),
                    maksGodkjentBeløp = utgift.tilMaksGodkjentBeløpDto(),
                    begrunnelse =
                        BegrunnelseDto(
                            innhold = henteNotatinnhold(this, NotatType.UTGIFTER),
                            gjelder = this.henteRolleForNotat(NotatType.UTGIFTER, null).tilDto(),
                        ),
                    utgifter = utgift.utgiftsposter.sorter().map { it.tilDto() },
                    valideringsfeil = valideringsfeil,
                    totalBeregning = utgift.tilTotalBeregningDto(),
                )
            }
        } ?: if (erSærbidrag()) {
            SærbidragUtgifterDto(
                avslag = avslag,
                kategori = tilSærbidragKategoriDto(),
                begrunnelse =
                    BegrunnelseDto(
                        innhold = henteNotatinnhold(this, NotatType.UTGIFTER),
                        gjelder = this.henteRolleForNotat(NotatType.UTGIFTER, null).tilDto(),
                    ),
                valideringsfeil = utgift.hentValideringsfeil(),
            )
        } else {
            null
        }

    private fun tilgangskontrollerePersoninfo(
        personinfo: Personinfo,
        saksnummer: Saksnummer,
        skjuleIdentitietHvisBeskyttet: Boolean = false,
    ): Personinfo {
        val erBeskytta = personinfo.ident?.let { tilgangskontrollService.harBeskyttelse(it) } ?: false

        personinfo.ident?.let {
            if (!tilgangskontrollService.harTilgang(it, saksnummer) || skjuleIdentitietHvisBeskyttet && erBeskytta) {
                return Personinfo(
                    null,
                    "Person skjermet, født ${personinfo.fødselsdato?.year}",
                    null,
                    erBeskyttet = true,
                )
            }
        }

        return Personinfo(
            personinfo.ident,
            personinfo.navn ?: hentPersonVisningsnavn(personinfo.ident?.verdi),
            personinfo.fødselsdato,
            erBeskytta,
        )
    }

    private fun Husstandsmedlem.boforholdBarn(opplysningerBoforhold: List<BoforholdResponseV2>): BoforholdBarn {
        val tilgangskontrollertPersoninfo =
            tilgangskontrollerePersoninfo(
                this.tilPersoninfo(),
                Saksnummer(this.behandling.saksnummer),
                true,
            )
        return BoforholdBarn(
            gjelder =
                NotatRolleDto(
                    rolle = null,
                    navn = tilgangskontrollertPersoninfo.navn,
                    fødselsdato = tilgangskontrollertPersoninfo.fødselsdato,
                    ident = tilgangskontrollertPersoninfo.ident,
                    erBeskyttet = tilgangskontrollertPersoninfo.erBeskyttet,
                ),
            kilde = kilde,
            medIBehandling = behandling.roller.any { it.ident == this.ident },
            opplysningerFraFolkeregisteret =
                opplysningerBoforhold
                    .filter {
                        it.gjelderPersonId == this.ident
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
                        kilde = periode.kilde,
                    )
                },
        )
    }

    private fun Behandling.andreVoksneIHusstanden(): NotatAndreVoksneIHusstanden =
        NotatAndreVoksneIHusstanden(
            opplysningerFraFolkeregisteret =
                grunnlag
                    .find { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type && it.erBearbeidet }
                    .konvertereData<Set<Bostatus>>()
                    ?.map {
                        val periode = ÅrMånedsperiode(it.periodeFom!!, it.periodeTom)
                        OpplysningerFraFolkeregisteretMedDetaljer(
                            periode = ÅrMånedsperiode(it.periodeFom!!, it.periodeTom),
                            status = it.bostatus!!,
                            detaljer =
                                NotatAndreVoksneIHusstandenDetaljerDto(
                                    henteAndreVoksneIHusstanden(grunnlag, periode, true).size,
                                    husstandsmedlemmer =
                                        henteBegrensetAntallAndreVoksne(grunnlag, periode, true).map { voksen ->

                                            val navn =
                                                if (voksen.erBeskyttet) {
                                                    val fødselssår =
                                                        voksen.fødselsdato?.let { ", født ${voksen.fødselsdato.year}" } ?: ""
                                                    "Person skjermet$fødselssår"
                                                } else {
                                                    voksen.navn
                                                }

                                            NotatVoksenIHusstandenDetaljerDto(
                                                navn = navn,
                                                fødselsdato = if (voksen.erBeskyttet) null else voksen.fødselsdato,
                                                harRelasjonTilBp = voksen.harRelasjonTilBp,
                                                erBeskyttet = voksen.erBeskyttet,
                                            )
                                        },
                                ),
                        )
                    }?.toList() ?: emptyList(),
            opplysningerBruktTilBeregning =
                husstandsmedlem.voksneIHusstanden?.perioder?.sortedBy { it.datoFom }?.map { periode ->
                    OpplysningerBruktTilBeregning(
                        periode =
                            ÅrMånedsperiode(
                                periode.datoFom!!,
                                periode.datoTom,
                            ),
                        status = periode.bostatus,
                        kilde = periode.kilde,
                    )
                } ?: emptyList(),
        )

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
    private fun Behandling.tilDto(
        ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
        inkluderHistoriskeInntekter: Boolean,
    ): BehandlingDtoV2 {
        val kanIkkeBehandlesBegrunnelse =
            validerBehandlingService.kanBehandlesINyLøsning(tilKanBehandlesINyLøsningRequest())
        val kanBehandles = kanIkkeBehandlesBegrunnelse == null
        return BehandlingDtoV2(
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
                    begrunnelse = BegrunnelseDto(henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT)),
                ),
            boforhold = tilBoforholdV2(),
            inntekter =
                tilInntektDtoV2(
                    grunnlag.hentSisteAktiv(),
                    inkluderHistoriskeInntekter = inkluderHistoriskeInntekter,
                ),
            underholdskostnader = underholdskostnader.tilDtos(),
            aktiveGrunnlagsdata = grunnlag.hentSisteAktiv().tilAktiveGrunnlagsdata(),
            utgift = tilUtgiftDto(),
            samvær = tilSamværDto(),
            ikkeAktiverteEndringerIGrunnlagsdata = if (kanBehandles) ikkeAktiverteEndringerIGrunnlagsdata else IkkeAktiveGrunnlagsdata(),
            feilOppståttVedSisteGrunnlagsinnhenting =
                grunnlagsinnhentingFeilet?.let {
                    val typeRef: TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>> =
                        object : TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>>() {}

                    objectmapper.readValue(it, typeRef).tilGrunnlagsinnhentingsfeil(this)
                },
            kanBehandlesINyLøsning = kanBehandles,
            kanIkkeBehandlesBegrunnelse = kanIkkeBehandlesBegrunnelse,
        )
    }

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
            beregnetBoforhold = behandling.tilBeregnetBoforhold(),
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
                    innhold = henteNotatinnhold(this, NotatType.BOFORHOLD),
                    gjelder = this.henteRolleForNotat(NotatType.BOFORHOLD, null).tilDto(),
                ),
            egetBarnErEnesteVoksenIHusstanden = egetBarnErEnesteVoksenIHusstanden,
            beregnetBoforhold = tilBeregnetBoforhold(),
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

    private fun Behandling.tilBeregnetBoforhold() =
        if (tilType() == TypeBehandling.BIDRAG) {
            try {
                BeregnApi().beregnBoforhold(
                    BeregnGrunnlag(
                        grunnlagListe =
                            vedtakGrunnlagMapper.mapper
                                .run {
                                    tilGrunnlagBostatus() + tilPersonobjekter()
                                }.toList(),
                        periode = ÅrMånedsperiode(virkningstidspunkt!!, null),
                        søknadsbarnReferanse = "",
                    ),
                )
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

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

    private fun RelatertPersonGrunnlagDto.tilAndreVoksneIHusstandenDetaljerDto(saksnummer: Saksnummer): AndreVoksneIHusstandenDetaljerDto {
        val tilgangskontrollPersoninfo = tilgangskontrollerePersoninfo(this.tilPersoninfo(), saksnummer)
        return AndreVoksneIHusstandenDetaljerDto(
            tilgangskontrollPersoninfo.navn!!,
            tilgangskontrollPersoninfo.fødselsdato,
            this.relasjon != Familierelasjon.INGEN && this.relasjon != Familierelasjon.UKJENT,
            relasjon = this.relasjon,
            erBeskyttet = tilgangskontrollPersoninfo.erBeskyttet,
        )
    }

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

private fun Husstandsmedlem.tilPersoninfo() =
    Personinfo(
        this.ident?.let { Personident(it) },
        this.navn,
        this.fødselsdato ?: rolle?.fødselsdato,
    )

private fun RelatertPersonGrunnlagDto.tilPersoninfo() =
    Personinfo(
        this.gjelderPersonId?.let { Personident(it) },
        this.navn,
        this.fødselsdato,
    )

data class Personinfo(
    val ident: Personident?,
    val navn: String?,
    val fødselsdato: LocalDate?,
    val erBeskyttet: Boolean = false,
)
