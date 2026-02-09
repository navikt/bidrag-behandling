package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagBpsBarnUtenBidragsak
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderBarn
import no.nav.bidrag.behandling.database.datamodell.hentSisteIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.voksneIHusstanden
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.ManuellVedtakDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktBarnDtoV2
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDtoV3
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
import no.nav.bidrag.behandling.dto.v1.grunnlag.BpsBarnUtenLøpendeBidragDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.AktivereGrunnlagResponseV2
import no.nav.bidrag.behandling.dto.v2.behandling.AndreVoksneIHusstandenDetaljerDto
import no.nav.bidrag.behandling.dto.v2.behandling.AndreVoksneIHusstandenGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDto
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDtoV3
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrRolleV2Dto
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrSakDto
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
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdmessigFordelingDetaljerDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingBarnDto
import no.nav.bidrag.behandling.dto.v2.forholdsmessigfordeling.ForholdsmessigFordelingÅpenBehandlingDto
import no.nav.bidrag.behandling.dto.v2.gebyr.GebyrValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.gebyr.validerGebyr
import no.nav.bidrag.behandling.dto.v2.inntekt.BeregnetInntekterDto
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoRolle
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV3
import no.nav.bidrag.behandling.dto.v2.privatavtale.BeregnetPrivatAvtaleDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.BeregnetPrivatAvtalePeriodeDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleAndreBarnDetaljerDtoV2
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleAndreBarnDtoV2
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleBarnDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleBarnDtoV2
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleBarnInfoDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtaleDtoV3
import no.nav.bidrag.behandling.dto.v2.privatavtale.PrivatAvtalePeriodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.SamværDtoV2
import no.nav.bidrag.behandling.dto.v2.underhold.BeregnetUnderholdskostnad
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftResponse
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilV2Dto
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.service.BeregningService
import no.nav.bidrag.behandling.service.NotatService
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.service.hentVedtak
import no.nav.bidrag.behandling.transformers.behandling.erLik
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerInntekter
import no.nav.bidrag.behandling.transformers.behandling.hentEndringerSivilstand
import no.nav.bidrag.behandling.transformers.behandling.hentVirkningstidspunktValideringsfeil
import no.nav.bidrag.behandling.transformers.behandling.hentVirkningstidspunktValideringsfeilRolle
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIArbeidsforhold
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIBarnetilsyn
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIBoforhold
import no.nav.bidrag.behandling.transformers.behandling.henteEndringerIBoforholdBMSøknadsbarn
import no.nav.bidrag.behandling.transformers.behandling.henteRolleForNotat
import no.nav.bidrag.behandling.transformers.behandling.kanFatteVedtak
import no.nav.bidrag.behandling.transformers.behandling.kanFatteVedtakBegrunnelse
import no.nav.bidrag.behandling.transformers.behandling.tilBarnetilsynAktiveGrunnlagDto
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.behandling.tilGrunnlagsinnhentingsfeil
import no.nav.bidrag.behandling.transformers.behandling.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.behandling.tilInntektDtoV3
import no.nav.bidrag.behandling.transformers.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.transformers.behandling.tilRolle
import no.nav.bidrag.behandling.transformers.behandling.tilSøknadsdetaljerDto
import no.nav.bidrag.behandling.transformers.behandling.toSivilstand
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.boforhold.tilBostatusperiode
import no.nav.bidrag.behandling.transformers.samvær.tilDto
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDtos
import no.nav.bidrag.behandling.transformers.underhold.valider
import no.nav.bidrag.behandling.transformers.utgift.hentValideringsfeil
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.behandling.transformers.utgift.tilMaksGodkjentBeløpDto
import no.nav.bidrag.behandling.transformers.utgift.tilSærbidragKategoriDto
import no.nav.bidrag.behandling.transformers.utgift.tilTotalBeregningDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManuellVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.dokumentmaler.DokumentmalPersonDto
import no.nav.bidrag.transport.dokumentmaler.notat.BoforholdBarn
import no.nav.bidrag.transport.dokumentmaler.notat.NotatAndreVoksneIHusstanden
import no.nav.bidrag.transport.dokumentmaler.notat.NotatAndreVoksneIHusstandenDetaljerDto
import no.nav.bidrag.transport.dokumentmaler.notat.NotatVoksenIHusstandenDetaljerDto
import no.nav.bidrag.transport.dokumentmaler.notat.OpplysningerBruktTilBeregning
import no.nav.bidrag.transport.dokumentmaler.notat.OpplysningerFraFolkeregisteret
import no.nav.bidrag.transport.dokumentmaler.notat.OpplysningerFraFolkeregisteretMedDetaljer
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.felles.toYearMonth
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.collections.sortedBy

@Component
class Dtomapper(
    val tilgangskontrollService: TilgangskontrollService,
    val validering: ValiderBeregning,
    val validerBehandlingService: ValiderBehandlingService,
    val vedtakGrunnlagMapper: VedtakGrunnlagMapper,
    val beregnBarnebidragApi: BeregnBarnebidragApi,
    @Lazy
    val beregningService: BeregningService? = null,
    val vedtakTilBehandlingMapping: VedtakTilBehandlingMapping? = null,
) {
    fun tilDto(
        behandling: Behandling,
        lesemodus: Boolean = false,
    ) = behandling.tilDto(behandling.ikkeAktiveGrunnlagsdata(), lesemodus)

    fun hentManuelleVedtakForBehandling(
        behandling: Behandling,
        barnIdent: String,
        rolle: Rolle?,
        privatAvtale: PrivatAvtale? = null,
    ): List<ManuellVedtakDto> {
        val grunnlag =
            behandling.grunnlag.hentSisteGrunnlagSomGjelderBarn(
                barnIdent,
                Grunnlagsdatatype.MANUELLE_VEDTAK,
            )
        return grunnlag
            .konvertereData<List<ManuellVedtakGrunnlag>>()
            ?.filter {
                if (behandling.erKlageEllerOmgjøring) {
                    val vedtaksliste = behandling.omgjøringsdetaljer?.omgjortVedtaksliste?.map { it.vedtaksid } ?: emptyList()
                    !vedtaksliste.contains(it.vedtaksid)
                } else if (behandling.erInnkreving || privatAvtale != null) {
                    it.innkrevingstype == Innkrevingstype.UTEN_INNKREVING
                } else {
                    true
                }
            }?.map {
                ManuellVedtakDto(
                    // Rolle == null betyr at det er simulering av privat avtale for andre barn til BP
                    valgt = rolle?.beregningGrunnlagFraVedtak == it.vedtaksid || privatAvtale?.grunnlagFraVedtak?.vedtak == it.vedtaksid,
                    vedtaksid = it.vedtaksid,
                    barnId = rolle?.id ?: -1,
                    fattetTidspunkt = it.fattetTidspunkt,
                    virkningsDato = it.virkningsDato,
                    vedtakstype = it.vedtakstype,
                    privatAvtale = it.privatAvtale,
                    begrensetRevurdering = it.begrensetRevurdering,
                    resultatSistePeriode = it.resultatSistePeriode,
                    manglerGrunnlag = it.manglerGrunnlag,
                    innkrevingstype = it.innkrevingstype,
                )
            }?.sortedByDescending { it.fattetTidspunkt } ?: emptyList()
    }

    fun tilUnderholdDto(underholdskostnad: Underholdskostnad) = underholdskostnad.tilDto()

    fun tilUnderholdskostnadsperioderForBehandlingMedKunEttSøknadsbarn(behandling: Behandling) = behandling.tilBeregnetUnderholdskostnad()

    fun tilFaktiskTilsynsutgiftDto(faktiskTilsynsutgift: FaktiskTilsynsutgift) = faktiskTilsynsutgift.tilDto()

    fun tilTilleggsstønadDto(tilleggsstønad: Tilleggsstønad) = tilleggsstønad.tilDto()

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

    fun Set<Underholdskostnad>.tilDtos() =
        this
            .filter { it.rolle == null || it.gjelderAndreBarn || it.rolle?.kreverGrunnlagForBeregning == true }
            .map { it.tilDto() }
            .sortedWith(
                compareByDescending<UnderholdDto> { it.gjelderBarn.kilde == Kilde.OFFENTLIG }
                    .thenByDescending { it.gjelderBarn.kilde == Kilde.MANUELL }
                    .thenBy { it.gjelderBarn.fødselsdato }
                    .thenBy { it.gjelderBarn.sortKey },
            ).toSet()

    private fun Underholdskostnad.tilDto(): UnderholdDto {
        // Vil aldri ha flere enn èn rolle per behandling
        val rolleSøknadsbarn = this.rolle?.takeIf { it.rolletype == Rolletype.BARN }
        val rolleBidragsmottaker =
            this.rolle?.takeIf { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
                ?: rolleSøknadsbarn?.bidragsmottaker
                ?: behandling.bidragsmottaker
        val beregnetUnderholdskostnad =
            if (behandling.erDirekteAvslag()) {
                emptySet()
            } else {
                this.behandling
                    .tilBeregnetUnderholdskostnad()
                    .perioderForBarn(personIdent)
            }

        return UnderholdDto(
            id = this.id!!,
            harTilsynsordning = this.harTilsynsordning,
            gjelderBarn = rolleSøknadsbarn?.tilPersoninfoDto() ?: this.person?.tilPersoninfoDto(rolleSøknadsbarn, kilde)!!,
            faktiskTilsynsutgift = this.faktiskeTilsynsutgifter.tilFaktiskeTilsynsutgiftDtos(),
            stønadTilBarnetilsyn = this.barnetilsyn.tilStønadTilBarnetilsynDtos(),
            tilleggsstønad = this.tilleggsstønad.tilTilleggsstønadDtos(),
            underholdskostnad = beregnetUnderholdskostnad,
            beregnetUnderholdskostnad = beregnetUnderholdskostnad,
            begrunnelse =
                NotatService.henteUnderholdsnotat(
                    this.behandling,
                    rolleSøknadsbarn ?: rolleBidragsmottaker!!,
                ),
            begrunnelseFraOpprinneligVedtak =
                if (behandling.erKlageEllerOmgjøring) {
                    NotatService
                        .henteUnderholdsnotat(
                            this.behandling,
                            rolleSøknadsbarn ?: rolleBidragsmottaker!!,
                            false,
                        ).takeIfNotNullOrEmpty { it }
                } else {
                    null
                },
            valideringsfeil = this.valider().takeIf { it.harFeil },
        )
    }

    fun Set<BeregnetUnderholdskostnad>.perioderForBarn(personIdent: String?) =
        find { bu ->
            bu.gjelderBarn.ident?.verdi == personIdent
        }?.perioder ?: emptySet()

    fun Behandling.tilBeregnetPrivatAvtale(
        gjelderBarn: Rolle,
        filtrerFraVirkningstidspunkt: Boolean = true,
    ): BeregnetPrivatAvtaleDto {
        val privatAvtaleBeregning = vedtakGrunnlagMapper.tilBeregnetPrivatAvtale(this, gjelderBarn)

        val gjelderBarnReferanse = privatAvtaleBeregning.hentAllePersoner().find { it.personIdent == gjelderBarn.ident }?.referanse
        return BeregnetPrivatAvtaleDto(
            gjelderBarn = gjelderBarn.tilPersoninfoDto(),
            gjelderBarnReferanse?.let {
                privatAvtaleBeregning
                    .finnAlleDelberegningerPrivatAvtalePeriode(gjelderBarnReferanse)
                    .filter {
                        !filtrerFraVirkningstidspunkt || it.periode.til == null ||
                            it.periode.fom.isAfter(gjelderBarn.virkningstidspunkt!!.toYearMonth())
                    }.map {
                        BeregnetPrivatAvtalePeriodeDto(
                            periode =
                                if (filtrerFraVirkningstidspunkt) {
                                    Datoperiode(
                                        maxOf(it.periode.fom.atDay(1), gjelderBarn.virkningstidspunkt!!).toYearMonth(),
                                        it.periode.til,
                                    )
                                } else {
                                    Datoperiode(it.periode.fom, it.periode.til)
                                },
                            beløp = it.indeksregulertBeløp,
                            indeksprosent = it.indeksreguleringFaktor ?: BigDecimal.ZERO,
                        )
                    }
            } ?: emptyList(),
        )
    }

    fun Behandling.tilBeregnetUnderholdskostnad(): Set<BeregnetUnderholdskostnad> =
        this.søknadsbarn
            .map {
                val underholdBeregning =
                    if (grunnlagslisteFraVedtak.isNullOrEmpty()) {
                        val grunnlag =
                            vedtakGrunnlagMapper
                                .byggGrunnlagForBeregning(
                                    this,
                                    it,
                                ).beregnGrunnlag!!
                                .copy(
                                    opphørsdato = it.opphørsdatoYearMonth,
                                )

                        beregnBarnebidragApi.beregnNettoTilsynsutgiftOgUnderholdskostnad(grunnlag) +
                            grunnlag.grunnlagListe.hentAllePersoner()
                    } else {
                        grunnlagslisteFraVedtak!!
                    } as List<GrunnlagDto>

                BeregnetUnderholdskostnad(
                    it.tilPersoninfoDto(),
                    underholdBeregning
                        .finnAlleDelberegningUnderholdskostnad(it)
                        .tilUnderholdskostnadDto(underholdBeregning, erBisysVedtak),
                )
            }.toSet()

    private fun Rolle.tilPersoninfoDto(kilde: Kilde? = null): PersoninfoDto {
        val personinfo =
            this.ident?.let { vedtakGrunnlagMapper.mapper.personService.hentPerson(it) }

        return PersoninfoDto(
            id = this.id,
            ident = ident?.let { Personident(it) } ?: this.ident?.let { Personident(it) },
            navn = personinfo?.visningsnavn ?: this.navn,
            fødselsdato = personinfo?.fødselsdato ?: this.fødselsdato,
            kilde = kilde,
            medIBehandlingen = ident != null,
            stønadstype = stønadstype,
        )
    }

    private fun Person.tilPersoninfoDto(
        rolle: Rolle? = null,
        kilde: Kilde? = null,
    ): PersoninfoDto {
        val personinfo =
            this.ident?.let { vedtakGrunnlagMapper.mapper.personService.hentPerson(it) }
                ?: rolle?.ident?.let { vedtakGrunnlagMapper.mapper.personService.hentPerson(it) }

        return PersoninfoDto(
            id = id,
            ident = rolle?.ident?.let { Personident(it) } ?: this.ident?.let { Personident(it) },
            navn = hentPersonVisningsnavn(personinfo?.ident?.verdi) ?: this.navn,
            fødselsdato = personinfo?.fødselsdato ?: this.fødselsdato,
            kilde = kilde,
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

    fun Behandling.tilSamværDto() =
        if (tilType() == TypeBehandling.BIDRAG) {
            samvær
                .filter { it.rolle.kreverGrunnlagForBeregning }
                .sortedWith(
                    sorterPersonEtterEldsteFødselsdato({ it.rolle.fødselsdato }, { it.rolle.identifikator }),
                ).map { it.tilDto() }
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
                    begrunnelse = BegrunnelseDto(henteNotatinnhold(this, NotatType.UTGIFTER)),
                    begrunnelseFraOpprinneligVedtak = utgiftBegrunnelseFraOpprinneligVedtak(),
                    valideringsfeil = valideringsfeil,
                    totalBeregning = utgift.tilTotalBeregningDto(),
                )
            } else {
                SærbidragUtgifterDto(
                    avslag = validering.run { tilSærbidragAvslagskode() },
                    beregning = utgift.tilBeregningDto(),
                    kategori = tilSærbidragKategoriDto(),
                    maksGodkjentBeløp = utgift.tilMaksGodkjentBeløpDto(),
                    begrunnelse = BegrunnelseDto(henteNotatinnhold(this, NotatType.UTGIFTER)),
                    begrunnelseFraOpprinneligVedtak = utgiftBegrunnelseFraOpprinneligVedtak(),
                    utgifter = utgift.utgiftsposter.sorter().map { it.tilDto() },
                    valideringsfeil = valideringsfeil,
                    totalBeregning = utgift.tilTotalBeregningDto(),
                )
            }
        } ?: if (erSærbidrag()) {
            SærbidragUtgifterDto(
                avslag = avslag,
                kategori = tilSærbidragKategoriDto(),
                begrunnelse = BegrunnelseDto(henteNotatinnhold(this, NotatType.UTGIFTER)),
                begrunnelseFraOpprinneligVedtak = utgiftBegrunnelseFraOpprinneligVedtak(),
                valideringsfeil = utgift.hentValideringsfeil(),
            )
        } else {
            null
        }

    fun Behandling.utgiftBegrunnelseFraOpprinneligVedtak() =
        if (erKlageEllerOmgjøring) {
            henteNotatinnhold(this, NotatType.UTGIFTER, null, false)
                .takeIfNotNullOrEmpty { BegrunnelseDto(it) }
        } else {
            null
        }

    fun tilgangskontrollerePersoninfo(
        personinfo: Personinfo,
        saksnummer: Saksnummer,
        skjuleIdentitietHvisBeskyttet: Boolean = false,
    ): Personinfo {
        val erBeskytta = personinfo.ident?.let { tilgangskontrollService.harBeskyttelse(it) } ?: false

        personinfo.ident?.let {
            if (!tilgangskontrollService.harTilgang(it, saksnummer) || (skjuleIdentitietHvisBeskyttet && erBeskytta)) {
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

    fun Tilleggsstønad.tilDto() =
        TilleggsstønadDto(
            id = this.id!!,
            periode = DatoperiodeDto(this.fom, this.tom),
            dagsats = this.beløp,
            beløp = beløp,
            beløpstype = beløpstype,
            total = beregnBarnebidragApi.beregnMånedsbeløpTilleggsstønad(beløp, this.beløpstype).nærmesteHeltall,
        )

    fun Set<Tilleggsstønad>.tilTilleggsstønadDtos() = this.sortedBy { it.fom }.map { it.tilDto() }.toSet()

    fun FaktiskTilsynsutgift.tilDto() =
        FaktiskTilsynsutgiftDto(
            id = this.id!!,
            periode = DatoperiodeDto(this.fom, this.tom),
            utgift = this.tilsynsutgift,
            kostpenger = this.kostpenger ?: BigDecimal.ZERO,
            kommentar = this.kommentar,
            total =
                beregnBarnebidragApi.beregnMånedsbeløpFaktiskeUtgifter(
                    faktiskUtgift = this.tilsynsutgift,
                    kostpenger = this.kostpenger ?: BigDecimal.ZERO,
                ) ?: BigDecimal.ZERO,
        )

    fun Set<FaktiskTilsynsutgift>.tilFaktiskeTilsynsutgiftDtos() = sortedBy { it.fom }.map { it.tilDto() }.toSet()

    private fun Husstandsmedlem.boforholdBarn(opplysningerBoforhold: List<BoforholdResponseV2>): BoforholdBarn {
        val tilgangskontrollertPersoninfo =
            tilgangskontrollerePersoninfo(
                this.tilPersoninfo(),
                Saksnummer(this.behandling.saksnummer),
                true,
            )
        val søknadsbarn = behandling.søknadsbarn.find { it.ident == tilgangskontrollertPersoninfo.ident?.verdi }
        return BoforholdBarn(
            gjelder =
                DokumentmalPersonDto(
                    rolle = null,
                    navn = tilgangskontrollertPersoninfo.navn,
                    fødselsdato = tilgangskontrollertPersoninfo.fødselsdato,
                    ident = tilgangskontrollertPersoninfo.ident,
                    erBeskyttet = tilgangskontrollertPersoninfo.erBeskyttet,
                    revurdering = søknadsbarn?.forholdsmessigFordeling?.erRevurdering == true,
                    saksnummer = søknadsbarn?.saksnummer,
                    bidragsmottakerIdent = søknadsbarn?.bidragsmottaker?.ident,
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
                    ?.sortedBy { it.periodeFom }
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
        val roller =
            behandling.roller.filter { it.rolletype != Rolletype.BARN || it.avslag == null }.sortedBy {
                if (it.rolletype == Rolletype.BARN) {
                    1
                } else {
                    -1
                }
            }
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
            husstandsmedlemBM = sisteInnhentedeIkkeAktiveGrunnlag.henteEndringerIBoforholdBMSøknadsbarn(aktiveGrunnlag, behandling),
            husstandsmedlem =
                sisteInnhentedeIkkeAktiveGrunnlag.henteEndringerIBoforhold(aktiveGrunnlag, behandling),
            andreVoksneIHusstanden =
                sisteInnhentedeIkkeAktiveGrunnlag.henteEndringerIAndreVoksneIBpsHusstand(aktiveGrunnlag),
            sivilstand =
                sisteInnhentedeIkkeAktiveGrunnlag.hentEndringerSivilstand(
                    aktiveGrunnlag,
                    behandling.virkningstidspunktEllerSøktFomDato,
                ),
            stønadTilBarnetilsyn =
                sisteInnhentedeIkkeAktiveGrunnlag.henteEndringerIBarnetilsyn(
                    aktiveGrunnlag.toSet(),
                    behandling,
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

    private fun Behandling.hentAldersjusteringBeregning(): List<ResultatBidragsberegningBarn> {
        if (vedtakstype != Vedtakstype.ALDERSJUSTERING) return emptyList()
        return try {
            beregningService!!.beregneBidrag(this).resultatBarn
        } catch (e: Exception) {
            emptyList()
        }
    }

    // TODO: Endre navn til BehandlingDto når v2-migreringen er ferdigstilt
    @Suppress("ktlint:standard:value-argument-comment")
    private fun Behandling.tilDto(
        ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata,
        lesemodus: Boolean = false,
    ): BehandlingDtoV2 {
        val kanIkkeBehandlesBegrunnelse =
            if (!lesemodus) validerBehandlingService.kanBehandlesINyLøsning(tilKanBehandlesINyLøsningRequest()) else null
        val kanBehandles = kanIkkeBehandlesBegrunnelse == null
        val aldersjusteringGrunnlag = grunnlagslisteFraVedtak?.finnAldersjusteringDetaljerGrunnlag()
        val aldersjusteringBeregning = hentAldersjusteringBeregning()
        val erAldersjusteringOgErAldersjustert =
            vedtakstype == Vedtakstype.ALDERSJUSTERING &&
                (
                    hentAldersjusteringBeregning()
                        .any { it.resultat.beregnetBarnebidragPeriodeListe.isNotEmpty() } ||
                        (
                            aldersjusteringGrunnlag != null &&
                                aldersjusteringGrunnlag.aldersjustert
                        )
                )
        val grunnlagFraVedtak = aldersjusteringGrunnlag?.grunnlagFraVedtak
        this.grunnlagslisteFraVedtak = this.grunnlagslisteFraVedtak ?: aldersjusteringBeregning.firstOrNull()?.resultat?.grunnlagListe
        val behandlingDto =
            BehandlingDtoV2(
                id = id!!,
                lasterGrunnlag = metadata?.lasterGrunnlagAsync() == true,
                kanFatteVedtak = kanFatteVedtak(),
                kanFatteVedtakBegrunnelse = kanFatteVedtakBegrunnelse(),
                type = tilType(),
                lesemodus = lesemodusVedtak,
                erBisysVedtak = erBisysVedtak,
                forholdsmessigFordeling =
                    forholdsmessigFordeling?.let {
                        val barnDto =
                            søknadsbarn.map { barn ->
                                val bm = barn.bidragsmottaker
                                ForholdsmessigFordelingBarnDto(
                                    ident = barn.ident!!,
                                    navn = barn.navn ?: "",
                                    fødselsdato = barn.fødselsdato,
                                    saksnr = barn.forholdsmessigFordeling?.tilhørerSak ?: "",
                                    bidragsmottaker = bm!!.tilDto(),
                                    sammeSakSomBehandling = barn.forholdsmessigFordeling?.tilhørerSak == saksnummer,
                                    erRevurdering = barn.forholdsmessigFordeling?.erRevurdering == true,
                                    harLøpendeBidrag = barn.forholdsmessigFordeling?.harLøpendeBidrag == true,
                                    innkrevesFraDato = barn.innkrevesFraDato?.toYearMonth(),
                                    stønadstype = barn.stønadstype,
                                    opphørsdato = barn.opphørsdato?.toYearMonth(),
                                    eldsteSøktFraDato =
                                        barn.forholdsmessigFordeling!!
                                            .søknaderUnderBehandling
                                            .filter { it.søknadFomDato != null }
                                            .minOfOrNull { it.søknadFomDato!! },
                                    åpneBehandlinger =
                                        barn.forholdsmessigFordeling!!.søknaderUnderBehandling.map {
                                            ForholdsmessigFordelingÅpenBehandlingDto(
                                                søktFraDato = it.søknadFomDato,
                                                mottattDato = it.mottattDato,
                                                stønadstype = barn.stønadstype ?: Stønadstype.BIDRAG,
                                                behandlerEnhet = barn.forholdsmessigFordeling?.behandlerenhet ?: "",
                                                behandlingId = barn.forholdsmessigFordeling?.behandlingsid,
                                                søknadsid = it.søknadsid,
                                                medInnkreving = barn.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                                                søktAvType = it.søktAvType,
                                                behandlingstype = it.behandlingstype,
                                                behandlingstema = it.behandlingstema,
                                            )
                                        },
                                    enhet = barn.forholdsmessigFordeling?.behandlerenhet ?: "",
                                )
                            }
                        ForholdmessigFordelingDetaljerDto(barn = barnDto)
                    },
                erVedtakUtenBeregning =
                    (vedtakstype == Vedtakstype.ALDERSJUSTERING && !erAldersjusteringOgErAldersjustert) || erVedtakUtenBeregning,
                grunnlagFraVedtaksid = grunnlagFraVedtak,
                medInnkreving = innkrevingstype == Innkrevingstype.MED_INNKREVING,
                innkrevingstype = innkrevingstype ?: Innkrevingstype.MED_INNKREVING,
                vedtakstype = vedtakstype,
                opprinneligVedtakstype = omgjøringsdetaljer?.opprinneligVedtakstype,
                stønadstype = stonadstype,
                engangsbeløptype = engangsbeloptype,
                erKlageEllerOmgjøring = erKlageEllerOmgjøring,
                opprettetTidspunkt = opprettetTidspunkt,
                erVedtakFattet = vedtaksid != null,
                erDelvedtakFattet = vedtakDetaljer?.fattetVedtak?.isNotEmpty() == true,
                søktFomDato = søktFomDato,
                mottattdato = mottattdato,
                klageMottattdato = omgjøringsdetaljer?.klageMottattdato,
                søktAv = soknadFra,
                saksnummer = saksnummer,
                søknadsid = soknadsid,
                behandlerenhet = behandlerEnhet,
                gebyr = mapGebyr(),
                gebyrV2 = mapGebyrV2(),
                gebyrV3 = mapGebyrV3(),
                roller =
                    roller
                        .sortedWith(
                            sorterPersonEtterEldsteFødselsdato({ it.fødselsdato }, { it.identifikator }),
                        ).map { it.tilDto() }
                        .toSet(),
                bpsBarnUtenLøpendeBidrag = bpsBarnUtenLøpendeBidrag(),
                søknadRefId = omgjøringsdetaljer?.soknadRefId,
                vedtakRefId = omgjøringsdetaljer?.omgjørVedtakId,
                omgjørVedtakId = omgjøringsdetaljer?.omgjørVedtakId,
                opprinneligVedtakId = omgjøringsdetaljer?.opprinneligVedtakId,
                sisteVedtakBeregnetUtNåværendeMåned =
                    omgjøringsdetaljer?.sisteVedtakBeregnetUtNåværendeMåned ?: omgjøringsdetaljer?.opprinneligVedtakId,
                virkningstidspunktV3 =
                    VirkningstidspunktDtoV3(
                        false,
                        erVirkningstidspunktLiktForAlle,
                        erAvslagForAlle,
                        BeregnTil.INNEVÆRENDE_MÅNED,
                        null,
                        eldsteVirkningstidspunkt.toYearMonth(),
                        emptyList(),
                    ),
                inntekter = InntekterDtoV2(valideringsfeil = InntektValideringsfeilDto()),
                inntekterV2 =
                    roller.sorterForInntektsbildet().map {
                        InntekterDtoRolle(
                            gjelder = it.tilDto(),
                            inntekter =
                                InntekterDtoV3(
                                    valideringsfeil = InntektValideringsfeilV2Dto(),
                                    beregnetInntekt = BeregnetInntekterDto(it.personident!!, it.rolletype, emptyList()),
                                ),
                        )
                    },
                boforhold = BoforholdDtoV2(begrunnelse = BegrunnelseDto("")),
                aktiveGrunnlagsdata = AktiveGrunnlagsdata(),
                ikkeAktiverteEndringerIGrunnlagsdata = IkkeAktiveGrunnlagsdata(),
                skalInnkrevingKunneUtsettes = skalInnkrevingKunneUtsettes(),
            )
        if (vedtakstype == Vedtakstype.INDEKSREGULERING) {
            return behandlingDto
        }
        return behandlingDto.copy(
            virkningstidspunktV3 =
                VirkningstidspunktDtoV3(
                    erLikForAlle = this.sammeVirkningstidspunktForAlle,
                    erVirkningstidspunktLiktForAlle = erVirkningstidspunktLiktForAlle,
                    erAvslagForAlle = erAvslagForAlle,
                    eldsteVirkningstidspunkt = eldsteVirkningstidspunkt.toYearMonth(),
                    beregnTil = søknadsbarn.first().beregnTil,
                    etterfølgendeVedtak = hentNesteEtterfølgendeVedtakFelles(),
                    barn = mapVirkningstidspunktAlleBarnV3(),
                ),
            boforhold = tilBoforholdV2(),
            inntekter =
                tilInntektDtoV2(
                    grunnlag.hentSisteAktiv(),
                ),
            inntekterV2 = mapInntekterV2(),
            underholdskostnader = tilUnderholdskostnadDto(this, aldersjusteringBeregning, lesemodus),
            aktiveGrunnlagsdata = grunnlag.hentSisteAktiv().tilAktiveGrunnlagsdata(),
            utgift = tilUtgiftDto(),
            samvær = tilSamværDto(),
            samværV2 =
                SamværDtoV2(
                    this.sammeSamværForAlle,
                    tilSamværDto() ?: emptyList(),
                ),
            ikkeAktiverteEndringerIGrunnlagsdata = if (kanBehandles) ikkeAktiverteEndringerIGrunnlagsdata else IkkeAktiveGrunnlagsdata(),
            feilOppståttVedSisteGrunnlagsinnhenting =
                grunnlagsinnhentingFeilet?.let {
                    val typeRef: TypeReference<Map<Grunnlagsdatatype, GrunnlagFeilDto>> =
                        object : TypeReference<Map<Grunnlagsdatatype, GrunnlagFeilDto>>() {}

                    objectmapper.readValue(it, typeRef).tilGrunnlagsinnhentingsfeil(this)
                },
            kanBehandlesINyLøsning = kanBehandles,
            kanIkkeBehandlesBegrunnelse = kanIkkeBehandlesBegrunnelse,
            privatAvtaleV3 =
                if (erBidrag() && bidragspliktig != null) {
                    tilPrivatAvtaleDtoV3()
                } else {
                    null
                },
        )
    }

    fun Behandling.mapInntekterV2() =
        roller
            .sorterForInntektsbildet()
            .filter {
                // Skal alltid vise inntekter for BM/BP (ikke barn).
                // Men hvis barn ikke krever grunnlag pga avslag og ikke løpende bidrag så skal det ikke vises i innteksbilde
                it.rolletype != Rolletype.BARN ||
                    it.kreverGrunnlagForBeregning
            }.map {
                InntekterDtoRolle(
                    gjelder = it.tilDto(),
                    inntekter =
                        tilInntektDtoV3(
                            grunnlag.hentSisteAktiv(),
                            it,
                        ),
                )
            }

    fun Behandling.tilPrivatAvtaleDtoV3(): PrivatAvtaleDtoV3 {
        val søknadsbarnPA =
            søknadsbarn
                .sortedWith(
                    sorterPersonEtterEldsteFødselsdato({ it.fødselsdato }, { it.identifikator }),
                ).map { barn ->
                    val privatAvtale = privatAvtale.find { it.rolle?.erSammeRolle(barn) == true }
                    PrivatAvtaleBarnInfoDto(
                        gjelderBarn = barn.tilPersoninfoDto(Kilde.OFFENTLIG),
                        privatAvtale = privatAvtale?.tilDtoV2(),
                        perioderLøperBidrag =
                            if (erInnkreving) {
                                emptyList()
                            } else {
                                finnPerioderHvorDetLøperBidrag(barn)
                            },
                        begrunnelse =
                            henteNotatinnhold(
                                this,
                                NotatType.PRIVAT_AVTALE,
                                barn,
                                true,
                            ),
                        begrunnelseFraOpprinneligVedtak =
                            if (erKlageEllerOmgjøring) {
                                henteNotatinnhold(
                                    this,
                                    NotatType.PRIVAT_AVTALE,
                                    barn,
                                    false,
                                ).takeIfNotNullOrEmpty { it }
                            } else {
                                null
                            },
                    )
                }

        val andreBarnUtenLøpendeBidrag =
            bpsBarnUtenLøpendeBidrag().map { barn ->
                val privatAvtale = privatAvtale.find { it.person?.ident == barn.ident }
                PrivatAvtaleAndreBarnDtoV2(
                    PersoninfoDto(
                        null,
                        barn.ident?.let { Personident(it) },
                        barn.navn,
                        barn.fødselsdato,
                        medIBehandlingen = false,
                        kilde = Kilde.OFFENTLIG,
                    ),
                    privatAvtale?.tilDtoV2(),
                    saksnummer = barn.saksnummer,
                    enhet = barn.enhet,
                )
            }
        val identerAndreBarn = andreBarnUtenLøpendeBidrag.mapNotNull { it.gjelderBarn.ident?.verdi }
        val barnManueltLagtInn =
            privatAvtale
                .filter { it.rolle == null && !identerAndreBarn.contains(it.person!!.ident!!) }
                .map {
                    PrivatAvtaleAndreBarnDtoV2(
                        gjelderBarn = it.person!!.tilPersoninfoDto(kilde = Kilde.MANUELL),
                        privatAvtale = it.tilDtoV2(),
                    )
                }
        val andreBarn =
            PrivatAvtaleAndreBarnDetaljerDtoV2(
                manglerBegrunnelse = manglerPrivatAvtaleBegrunnelseAndreBarn(),
                barn = barnManueltLagtInn + andreBarnUtenLøpendeBidrag,
                begrunnelse =
                    henteNotatinnhold(
                        this,
                        NotatType.PRIVAT_AVTALE,
                        bidragspliktig!!,
                        true,
                    ),
                begrunnelseFraOpprinneligVedtak =
                    if (erKlageEllerOmgjøring) {
                        henteNotatinnhold(
                            this,
                            NotatType.PRIVAT_AVTALE,
                            bidragspliktig!!,
                            false,
                        ).takeIfNotNullOrEmpty { it }
                    } else {
                        null
                    },
            )

        return PrivatAvtaleDtoV3(søknadsbarn = søknadsbarnPA, andreBarn)
    }

    private fun Behandling.mapVirkningstidspunktAlleBarnV3(): List<VirkningstidspunktBarnDtoV2> =
        if (tilType() == TypeBehandling.BIDRAG) {
            søknadsbarn
                .sortedWith(
                    sorterPersonEtterEldsteFødselsdato({ it.fødselsdato }, { it.identifikator }),
                ).map {
                    val eldsteSøknad = it.forholdsmessigFordeling?.eldsteSøknad
                    val notat = henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT, it)
                    val bidragetHarOpphørt = finnEksisterendeVedtakMedOpphør(it) != null && it.virkningstidspunkt == it.opphørsdato
                    VirkningstidspunktBarnDtoV2(
                        rolle = it.tilDto(),
                        beregnTil = it.beregnTil ?: BeregnTil.INNEVÆRENDE_MÅNED,
                        beregnTilDato = finnBeregnTilDatoBehandling(it),
                        virkningstidspunkt = it.virkningstidspunkt ?: virkningstidspunkt,
                        opprinneligVedtakstidspunkt =
                            omgjøringsdetaljer?.sisteVedtakstidspunktBeregnetUtNåværendeMåned?.toLocalDate()
                                ?: omgjøringsdetaljer?.omgjortVedtakstidspunktListe?.minOrNull()?.toLocalDate(),
                        omgjortVedtakVedtakstidspunkt = omgjøringsdetaljer?.omgjortVedtakVedtakstidspunkt?.toLocalDate(),
                        opprinneligVirkningstidspunkt =
                            it.opprinneligVirkningstidspunkt
                                ?: omgjøringsdetaljer?.opprinneligVirkningstidspunkt,
                        manuelleVedtak = hentManuelleVedtakForBehandling(this, it.ident!!, it),
                        etterfølgendeVedtak = hentNesteEtterfølgendeVedtak(it),
                        årsak = if (it.årsak == null && it.avslag == null) årsak else it.årsak,
                        avslag = if (it.årsak == null && it.avslag == null) avslag else it.avslag,
                        grunnlagFraVedtak =
                            it.grunnlagFraVedtak ?: it.grunnlagFraVedtakForInnkreving?.vedtak,
                        kanSkriveVurderingAvSkolegang = kanSkriveVurderingAvSkolegang(it),
                        begrunnelse =
                            if (notat.isEmpty()) {
                                BegrunnelseDto(
                                    henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT),
                                )
                            } else {
                                BegrunnelseDto(notat)
                            },
                        begrunnelseVurderingAvSkolegang =
                            if (it.stønadstypeBarnEllerBehandling == Stønadstype.BIDRAG18AAR) {
                                BegrunnelseDto(
                                    henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG, it),
                                )
                            } else {
                                null
                            },
                        begrunnelseVurderingAvSkolegangFraOpprinneligVedtak =
                            if (it.stønadstypeBarnEllerBehandling == Stønadstype.BIDRAG18AAR) {
                                BegrunnelseDto(
                                    henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG, it, false),
                                )
                            } else {
                                null
                            },
                        løpendeBidragPeriode = finnPeriodeLøpendePeriodeInnenforSøktFomDato(it),
                        harLøpendeForskudd = finnesLøpendeForskuddForRolle(it),
                        harLøpendeBidrag = finnesLøpendeBidragForRolle(it),
                        medInnkreving = it.innkrevingstype == Innkrevingstype.MED_INNKREVING,
                        eksisterendeOpphør = finnEksisterendeVedtakMedOpphør(it),
                        opphørsdato = it.opphørsdato,
                        globalOpphørsdato = globalOpphørsdato,
                        valideringsfeil = hentVirkningstidspunktValideringsfeil(),
                        valideringsfeilV2 = it.hentVirkningstidspunktValideringsfeilRolle(),
                        vedtakstype =
                            eldsteSøknad?.behandlingstype?.tilVedtakstype() ?: it.behandling.vedtakstype,
                        mottattdato = eldsteSøknad?.mottattDato ?: it.behandling.mottattdato,
                        søktAv = eldsteSøknad?.søktAvType ?: it.behandling.soknadFra,
                        søktFomDato = eldsteSøknad?.søknadFomDato ?: it.behandling.søktFomDato,
                        // Betyr at revurderingsbarn bidrag opphører i løpet av beregningsperioden. Da skal det ikke være mulig å endre virkningstidspunkt eller opphør
                        // Systemet (VedtakHendelseLytteren) skal oppdatere opphør når det fattes ny opphørsvedtak
                        // Gjelder bare for revurderingsbarn
                        kanEndreVirkningstidspunkt =
                            !it.erRevurderingsbarn || !bidragetHarOpphørt,
                        kanEndreVirkningstidspunktOpphør =
                            !it.erRevurderingsbarn || !bidragetHarOpphørt,
                        kanVelgeOpphør = !erIForholdsmessigFordeling || !it.løperBidragEtterEldsteVirkning,
                        begrunnelseFraOpprinneligVedtak =
                            if (erKlageEllerOmgjøring) {
                                henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT, it, false)
                                    .takeIfNotNullOrEmpty { BegrunnelseDto(it) }
                            } else {
                                null
                            },
                    )
                }
        } else {
            listOf(
                VirkningstidspunktBarnDtoV2(
                    rolle = bidragsmottaker!!.tilDto(),
                    virkningstidspunkt = virkningstidspunkt,
                    opprinneligVirkningstidspunkt = omgjøringsdetaljer?.opprinneligVirkningstidspunkt,
                    opprinneligVedtakstidspunkt = omgjøringsdetaljer?.omgjortVedtakstidspunktListe?.minOrNull()?.toLocalDate(),
                    årsak = årsak,
                    avslag = avslag,
                    begrunnelse = BegrunnelseDto(henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT)),
                    harLøpendeBidrag = finnesLøpendeBidragForRolle(søknadsbarn.first()),
                    harLøpendeForskudd = finnesLøpendeForskuddForRolle(søknadsbarn.first()),
                    opphørsdato = globalOpphørsdato,
                    valideringsfeil = hentVirkningstidspunktValideringsfeil(),
                    mottattdato = mottattdato,
                    søktAv = soknadFra,
                    søktFomDato = søktFomDato,
                    vedtakstype = vedtakstype,
                    begrunnelseFraOpprinneligVedtak =
                        if (erKlageEllerOmgjøring) {
                            henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT, null, false)
                                .takeIfNotNullOrEmpty { BegrunnelseDto(it) }
                        } else {
                            null
                        },
                ),
            )
        }

    fun tilUnderholdskostnadDto(
        behandling: Behandling,
        beregning: List<ResultatBidragsberegningBarn> = emptyList(),
        lesemodus: Boolean = false,
    ): Set<UnderholdDto> =
        if (behandling.vedtakstype == Vedtakstype.ALDERSJUSTERING && !lesemodus) {
            vedtakTilBehandlingMapping!!
                .run {
                    behandling.søknadsbarn.mapIndexed { index, rolle ->
                        val beregningBarn = beregning.find { it.barn.ident!!.verdi == rolle.ident } ?: return@mapIndexed null
                        if (beregningBarn.resultat.beregnetBarnebidragPeriodeListe.isEmpty()) {
                            return@mapIndexed null
                        }
                        val grunnlagFraVedtak = hentVedtak(rolle.grunnlagFraVedtak)!!
                        val underholdskostnad =
                            Underholdskostnad(
                                id = index.toLong(),
                                behandling = behandling,
                                rolle = rolle,
                            )
                        grunnlagFraVedtak.grunnlagListe.hentUnderholdskostnadPerioder(
                            underholdskostnad,
                            true,
                            rolle,
                            ÅrMånedsperiode(behandling.virkningstidspunkt!!, rolle.opphørsdato),
                        )
                        underholdskostnad
                    }
                }.filterNotNull()
                .toSet()
                .tilDtos()
        } else {
            behandling.underholdskostnader.tilDtos()
        }

    fun Behandling.mapGebyrV3() =
        if (roller.any { it.harGebyrsøknad }) {
            val gebyrSaker = roller.flatMap { it.gebyrSøknader }.map { it.saksnummer }.distinct()
            val saker =
                gebyrSaker.map { sak ->
                    GebyrSakDto(
                        saksnummer = sak,
                        gebyr18År =
                            mapGebyrForSak(sak, true).sortedByDescending { it.rolle.rolletype },
                        gebyrRoller = mapGebyrForSak(sak, false).sortedByDescending { it.rolle.rolletype },
                    )
                }
            GebyrDtoV3(
                saker = saker.sortedBy { it.saksnummer },
            )
        } else {
            GebyrDtoV3(
                saker = emptyList(),
            )
        }

    private fun Behandling.mapGebyrForSak(
        sak: String,
        gjelder18ÅrSøknad: Boolean,
    ): List<GebyrRolleV2Dto> =
        roller
            .filter { it.gebyr != null }
            .flatMap { rolle ->
                val gebyr = rolle.gebyr!!.finnGebyrForSak(sak).filter { it.gjelder18ÅrSøknad == gjelder18ÅrSøknad }
                gebyr.map {
                    GebyrRolleV2Dto(
                        rolle = rolle.tilDto(),
                        gebyrDetaljer =
                            vedtakGrunnlagMapper
                                .beregnGebyr(this, rolle)
                                .tilDto(rolle, it.søknadsid),
                        valideringsfeil =
                            GebyrValideringsfeilDto(
                                gjelder = rolle.tilDto(),
                                søknad = rolle.tilSøknadsdetaljerDto(it.søknadsid),
                                manglerBegrunnelse =
                                    if (it.manueltOverstyrtGebyr?.overstyrGebyr == true) {
                                        it.manueltOverstyrtGebyr?.begrunnelse.isNullOrEmpty()
                                    } else {
                                        false
                                    },
                            ),
                    )
                }
            }

    fun Behandling.mapGebyrV2() =
        if (roller.any { it.harGebyrsøknad }) {
            GebyrDtoV2(
                harFlereSøknader = roller.flatMap { it.gebyrSøknader.map { it.søknadsid } }.distinct().size > 1,
                gebyrRoller =
                    roller.sortedBy { it.rolletype }.filter { it.harGebyrsøknad }.map { rolle ->
                        GebyrRolleDto(
                            rolle = rolle.tilDto(),
                            gebyrDetaljer =
                                rolle.gebyrSøknader.map {
                                    vedtakGrunnlagMapper
                                        .beregnGebyr(this, rolle)
                                        .tilDto(rolle, it.søknadsid)
                                },
                            valideringsfeil = validerGebyr().filter { it.gjelder.ident == rolle.ident }.takeIf { it.isNotEmpty() },
                        )
                    },
            )
        } else {
            GebyrDtoV2(
                harFlereSøknader = false,
                gebyrRoller = emptyList(),
            )
        }

    fun Behandling.mapGebyr() =
        if (roller.any { it.harGebyrsøknad }) {
            GebyrDto(
                gebyrRoller =
                    roller.sortedBy { it.rolletype }.filter { it.harGebyrsøknad }.flatMap { rolle ->
                        rolle.gebyrSøknader.map {
                            vedtakGrunnlagMapper
                                .beregnGebyr(this, rolle)
                                .tilDto(rolle, it.søknadsid)
                        }
                    },
                valideringsfeil = validerGebyr().takeIf { it.isNotEmpty() },
            )
        } else {
            GebyrDto(
                gebyrRoller = emptyList(),
                valideringsfeil = null,
            )
        }

    private fun Behandling.bpsBarnUtenLøpendeBidrag(): Set<BpsBarnUtenLøpendeBidragDto> =
        grunnlag
            .hentSisteGrunnlagBpsBarnUtenBidragsak()
            ?.map {
                BpsBarnUtenLøpendeBidragDto(
                    ident = it.ident.verdi,
                    fødselsdato = it.fødselsdato,
                    navn = it.navn,
                    saksnummer = it.saksnummer,
                    enhet = it.enhet,
                )
            }?.toSet() ?: emptySet()

    fun PrivatAvtale.tilDtoV2(): PrivatAvtaleBarnDtoV2 =
        PrivatAvtaleBarnDtoV2(
            id = id!!,
            skalIndeksreguleres = skalIndeksreguleres,
            avtaleDato = utledetAvtaledato,
            avtaleType = avtaleType,
            erSøknadsbarn = rolle != null,
            gjelderUtland = utenlandsk,
            manuelleVedtakUtenInnkreving =
                if (behandling.erBidrag()) {
                    hentManuelleVedtakForBehandling(behandling, personIdent!!, rolle, this)
                } else {
                    null
                },
            valideringsfeil = validerePrivatAvtale().takeIf { it.harFeil },
            beregnetPrivatAvtale =
                if (skalIndeksreguleres &&
                    perioderInnkreving.isNotEmpty()
                ) {
                    behandling.tilBeregnetPrivatAvtale(rolle ?: person?.tilRolle(behandling)!!, false)
                } else {
                    null
                },
            perioder =
                perioderInnkreving.sortedBy { it.fom }.map {
                    PrivatAvtalePeriodeDto(
                        id = it.id,
                        periode =
                            no.nav.bidrag.behandling.dto.v2.behandling
                                .DatoperiodeDto(it.fom, it.tom),
                        beløp = it.beløp,
                        valutakode = it.valutakode,
                        samværsklasse = it.samværsklasse,
                    )
                },
        )

    fun PrivatAvtale.tilDto(): PrivatAvtaleBarnDto =
        PrivatAvtaleBarnDto(
            id = id!!,
            perioderLøperBidrag =
                if (behandling.erInnkreving) {
                    emptyList()
                } else {
                    rolle?.let { behandling.finnPerioderHvorDetLøperBidrag(it) }
                        ?: emptyList()
                },
            gjelderBarn = person?.tilPersoninfoDto() ?: rolle!!.tilPersoninfoDto(),
            skalIndeksreguleres = skalIndeksreguleres,
            avtaleDato = utledetAvtaledato,
            avtaleType = avtaleType,
            erSøknadsbarn = rolle != null,
            manuelleVedtakUtenInnkreving =
                if (behandling.erBidrag()) {
                    hentManuelleVedtakForBehandling(behandling, personIdent!!, rolle, this)
                } else {
                    null
                },
            begrunnelse =
                henteNotatinnhold(
                    this.behandling,
                    NotatType.PRIVAT_AVTALE,
                    rolle ?: this.behandling.bidragspliktig!!,
                    true,
                ),
            begrunnelseFraOpprinneligVedtak =
                if (behandling.erKlageEllerOmgjøring) {
                    henteNotatinnhold(
                        this.behandling,
                        NotatType.PRIVAT_AVTALE,
                        rolle ?: this.behandling.bidragspliktig!!,
                        false,
                    ).takeIfNotNullOrEmpty { it }
                } else {
                    null
                },
            valideringsfeil = validerePrivatAvtale().takeIf { it.harFeil },
            beregnetPrivatAvtale =
                if (skalIndeksreguleres &&
                    perioderInnkreving.isNotEmpty()
                ) {
                    behandling.tilBeregnetPrivatAvtale(rolle ?: person?.tilRolle(behandling)!!, false)
                } else {
                    null
                },
            perioder =
                perioderInnkreving.sortedBy { it.fom }.map {
                    PrivatAvtalePeriodeDto(
                        id = it.id,
                        periode =
                            no.nav.bidrag.behandling.dto.v2.behandling
                                .DatoperiodeDto(it.fom, it.tom),
                        beløp = it.beløp,
                        valutakode = it.valutakode,
                        samværsklasse = it.samværsklasse,
                    )
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
            begrunnelse = henteNotatinnhold(this.behandling, NotatType.BOFORHOLD),
            egetBarnErEnesteVoksenIHusstanden = behandling.egetBarnErEnesteVoksenIHusstanden,
            valideringsfeil =
                BoforholdValideringsfeil(
                    andreVoksneIHusstanden =
                        behandling.husstandsmedlem.voksneIHusstanden
                            ?.validereAndreVoksneIHusstanden(behandling.eldsteVirkningstidspunkt),
                    husstandsmedlem =
                        behandling.husstandsmedlem.barn
                            .toSet()
                            .validerBoforhold(behandling.eldsteVirkningstidspunkt)
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
            begrunnelseFraOpprinneligVedtak =
                if (erKlageEllerOmgjøring) {
                    henteNotatinnhold(this, NotatType.BOFORHOLD, null, false).takeIfNotNullOrEmpty {
                        BegrunnelseDto(
                            innhold = it,
                            gjelder = this.henteRolleForNotat(NotatType.BOFORHOLD, null).tilDto(),
                        )
                    }
                } else {
                    null
                },
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

    fun Behandling.tilBeregnetBoforhold() =
        if (tilType() == TypeBehandling.BIDRAG) {
            try {
                BeregnApi().beregnBoforhold(
                    BeregnGrunnlag(
                        grunnlagListe =
                            vedtakGrunnlagMapper.mapper
                                .run {
                                    tilGrunnlagBostatus() + tilPersonobjekter()
                                }.toList(),
                        periode = ÅrMånedsperiode(virkningstidspunkt!!, finnBeregnTilDatoBehandling()),
                        opphørsdato = globalOpphørsdatoYearMonth,
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
            stønadstype = rolle?.stønadstype,
            fødselsdato = tilgangskontrollertPersoninfo.fødselsdato,
        )
    }

    private fun Set<Grunnlag>.hentAlleAndreVoksneHusstandForPeriode(
        periode: ÅrMånedsperiode,
        erAktivert: Boolean = true,
    ): List<AndreVoksneIHusstandenDetaljerDto> {
        val grunnlag = if (erAktivert) hentSisteAktiv() else hentSisteIkkeAktiv()
        val behandling = firstOrNull()?.behandling ?: return emptyList()

        val boforholdAndreVoksneIHusstanden =
            grunnlag.find { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && !it.erBearbeidet }

        return boforholdAndreVoksneIHusstanden
            .konvertereData<List<RelatertPersonGrunnlagDto>>()
            ?.filter { it.relasjon != Familierelasjon.BARN }
            ?.filter {
                it.fødselsdato == null ||
                    it.fødselsdato!!
                        .withDayOfMonth(1)
                        .isBefore(behandling.eldsteVirkningstidspunkt.minusYears(18))
            }?.filter {
                it.borISammeHusstandDtoListe.any { p ->
                    val periodeBorHosBP =
                        if (p.periodeFra!!.withDayOfMonth(1) == p.periodeTil?.withDayOfMonth(1)) {
                            ÅrMånedsperiode(p.periodeFra!!.withDayOfMonth(1), p.periodeTil?.withDayOfMonth(1))
                        } else {
                            ÅrMånedsperiode(p.periodeFra!!.withDayOfMonth(1), p.periodeTil?.withDayOfMonth(1)?.minusDays(1))
                        }
                    val periodeBPErInnenfor =
                        periodeBorHosBP.fom >= periode.fom &&
                            periodeBorHosBP.til != null &&
                            periode.til != null &&
                            periodeBorHosBP.tilEllerMax() <= periode.tilEllerMax()
                    val periodeBPLøpendeErInnenfor =
                        periodeBorHosBP.fom >= periode.fom && periodeBorHosBP.til == null && periode.til == null
                    periode.omsluttesAv(periodeBorHosBP) || periodeBPErInnenfor || periodeBPLøpendeErInnenfor
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
            husstandsmedlemBM = filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN && it.erBearbeidet }.tilHusstandsmedlem(),
            stønadTilBarnetilsyn =
                filter { it.type == Grunnlagsdatatype.BARNETILSYN && it.erBearbeidet }
                    .toSet()
                    .tilBarnetilsynAktiveGrunnlagDto(),
        )

    private fun List<Grunnlag>.tilAndreVoksneIHusstanden(erAktivert: Boolean) =
        AndreVoksneIHusstandenGrunnlagDto(
            perioder = tilPeriodeAndreVoksneIHusstanden(erAktivert),
            innhentet = LocalDateTime.now(),
        )

    private fun List<Grunnlag>.tilPeriodeAndreVoksneIHusstanden(erAktivert: Boolean = true): Set<PeriodeAndreVoksneIHusstanden> =
        find { Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN == it.type && it.erBearbeidet }
            .konvertereData<Set<Bostatus>>()
            ?.sortedBy { it.periodeFom }
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
