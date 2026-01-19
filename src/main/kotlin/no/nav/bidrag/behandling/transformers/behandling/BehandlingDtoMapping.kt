package no.nav.bidrag.behandling.transformers.behandling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.minified.BehandlingSimple
import no.nav.bidrag.behandling.database.datamodell.minified.RolleSimple
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.database.datamodell.tilPersonident
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterOpphørsdatoRequestDto
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsinnhentingsfeil
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.SivilstandAktivGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.SjekkRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.StønadTilBarnetilsynAktiveGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.SøknadDetaljerDto
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.inntekt.BeregnetInntekterDto
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektBarn
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV3
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilV2Dto
import no.nav.bidrag.behandling.dto.v2.validering.VirkningstidspunktFeilDto
import no.nav.bidrag.behandling.dto.v2.validering.VirkningstidspunktFeilV2Dto
import no.nav.bidrag.behandling.service.NotatService
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.service.VirkningstidspunktService
import no.nav.bidrag.behandling.service.hentAlleSaker
import no.nav.bidrag.behandling.service.hentAlleStønaderForBidragspliktig
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.service.hentSak
import no.nav.bidrag.behandling.transformers.barn
import no.nav.bidrag.behandling.transformers.bestemRollerSomKanHaInntekter
import no.nav.bidrag.behandling.transformers.bestemRollerSomMåHaMinstEnInntekt
import no.nav.bidrag.behandling.transformers.ekskluderYtelserFørVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.finnCutoffDatoFom
import no.nav.bidrag.behandling.transformers.finnEksisterendeVedtakMedOpphør
import no.nav.bidrag.behandling.transformers.finnHullIPerioder
import no.nav.bidrag.behandling.transformers.finnOverlappendePerioderInntekt
import no.nav.bidrag.behandling.transformers.harUgyldigSluttperiode
import no.nav.bidrag.behandling.transformers.hentNesteEtterfølgendeVedtak
import no.nav.bidrag.behandling.transformers.inntekstrapporteringerSomKreverGjelderBarn
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.kanSkriveVurderingAvSkolegang
import no.nav.bidrag.behandling.transformers.kanSkriveVurderingAvSkolegangAlle
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.opphørSisteTilDato
import no.nav.bidrag.behandling.transformers.sorterEtterDato
import no.nav.bidrag.behandling.transformers.sorterEtterDatoOgBarn
import no.nav.bidrag.behandling.transformers.tilInntektberegningDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.toHusstandsmedlem
import no.nav.bidrag.behandling.transformers.utgift.tilSærbidragKategoriDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnFra
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.behandling.transformers.årsinntekterSortert
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.beregn.core.util.sluttenAvForrigeMåned
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.commons.service.forsendelse.bidragspliktig
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.behandling.tilStønadstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.organisasjon.dto.SaksbehandlerDto
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.felles.toYearMonth
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.collections.forEach
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

fun Behandling.toSimple() =
    BehandlingSimple(
        id = id!!,
        virkningstidspunkt = virkningstidspunkt,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        saksnummer = saksnummer,
        vedtakstype = vedtakstype,
        søknadstype = søknadstype,
        harPrivatAvtaleAndreBarn = privatAvtale.any { it.rolle == null },
        omgjøringsdetaljer = omgjøringsdetaljer,
        stønadstype = stonadstype,
        engangsbeløptype = engangsbeloptype,
        forholdsmessigFordeling = forholdsmessigFordeling,
        roller = roller.map { RolleSimple(it.rolletype, it.ident!!, it.virkningstidspunktRolle) },
    )

fun oppdaterBehandlingEtterOppdatertRoller(
    behandling: Behandling,
    underholdService: UnderholdService,
    virkningstidspunktService: VirkningstidspunktService,
    rollerSomLeggesTil: List<OpprettRolleDto>,
    rollerSomSkalSlettes: List<OpprettRolleDto>,
) {
    slettNotatSomTilhørerRolleSomSlettes(behandling, rollerSomSkalSlettes)
    slettGrunnlagSomTilhørerRolleSomSlettes(behandling, rollerSomSkalSlettes)
    slettPrivatAvtaleSomTilhørerRolleSomSlettes(behandling, rollerSomSkalSlettes)
    slettInntekterSomTilhørerRolleSomSlettes(behandling, rollerSomSkalSlettes)
    oppdatereSamværForRoller(behandling, rollerSomLeggesTil, rollerSomSkalSlettes)
    oppdaterUnderholdskostnadForRoller(behandling, underholdService, rollerSomLeggesTil, rollerSomSkalSlettes)
    oppdatereHusstandsmedlemmerForRoller(behandling, rollerSomLeggesTil)
    oppdaterOpphørForRoller(behandling, virkningstidspunktService, rollerSomLeggesTil)
}

private fun slettInntekterSomTilhørerRolleSomSlettes(
    behandling: Behandling,
    rollerSomSkalSlettes: List<OpprettRolleDto>,
) {
    rollerSomSkalSlettes.forEach { rolle ->
        behandling.inntekter
            .filter { it.ident == rolle.ident!!.verdi }
            .forEach {
                it.inntektsposter.clear()
                behandling.inntekter.remove(it)
            }
    }
}

private fun slettPrivatAvtaleSomTilhørerRolleSomSlettes(
    behandling: Behandling,
    rollerSomSkalSlettes: List<OpprettRolleDto>,
) {
    rollerSomSkalSlettes.forEach { rolle ->
        behandling.privatAvtale.removeIf {
            it.rolle != null && it.rolle!!.ident == rolle.ident!!.verdi &&
                it.rolle!!.stønadstype == rolle.stønadstype
        }
    }
}

private fun slettGrunnlagSomTilhørerRolleSomSlettes(
    behandling: Behandling,
    rollerSomSkalSlettes: List<OpprettRolleDto>,
) {
    rollerSomSkalSlettes.forEach { rolle ->
        behandling.grunnlag.removeIf {
            (it.rolle.ident == rolle.ident!!.verdi && it.rolle.stønadstype == rolle.stønadstype) ||
                it.gjelder == rolle.ident!!.verdi
        }
    }
}

private fun slettNotatSomTilhørerRolleSomSlettes(
    behandling: Behandling,
    rollerSomSkalSlettes: List<OpprettRolleDto>,
) {
    rollerSomSkalSlettes.forEach { rolle ->
        val rolleBarn = behandling.roller.find { it.ident == rolle.ident!!.verdi }
        val notater = behandling.notater.filter { it.rolle.ident == rolle.ident!!.verdi }
        notater.forEach { notat ->
            if (notat.type == Notattype.UNDERHOLDSKOSTNAD) {
                behandling.notater
                    .find {
                        it.type == Notattype.UNDERHOLDSKOSTNAD &&
                            it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER &&
                            notat.erDelAvBehandlingen == it.erDelAvBehandlingen
                    }?.let {
                        it.innhold += "<br> ${notat.innhold}"
                    }
            }
            rolleBarn!!.notat.remove(notat)
            behandling.notater.remove(notat)
        }
    }
}

private fun oppdaterOpphørForRoller(
    behandling: Behandling,
    virkningstidspunktService: VirkningstidspunktService,
    rollerSomLeggesTil: List<OpprettRolleDto>,
) {
    if (behandling.tilType() == TypeBehandling.BIDRAG) {
        rollerSomLeggesTil.forEach { r ->
            val rolle = behandling.roller.find { it.ident == r.ident!!.verdi && it.stønadstype == r.stønadstype }!!
            behandling.finnEksisterendeVedtakMedOpphør(rolle)?.let {
                val opphørsdato = if (it.opphørsdato.isAfter(behandling.virkningstidspunkt!!)) it.opphørsdato else null
                if (opphørsdato != null) {
                    virkningstidspunktService.oppdaterOpphørsdato(
                        behandling.id!!,
                        OppdaterOpphørsdatoRequestDto(
                            rolle.id!!,
                            opphørsdato,
                        ),
                    )
                }
            }
        }
    }
}

private fun oppdatereHusstandsmedlemmerForRoller(
    behandling: Behandling,
    rollerSomLeggesTil: List<OpprettRolleDto>,
) {
    rollerSomLeggesTil
        .filter { it.rolletype == Rolletype.BARN }
        .filter { nyRolle -> behandling.husstandsmedlem.any { it.ident == nyRolle.ident?.verdi } }
        .forEach { nyRolle ->
            val rolle = behandling.finnRolle(nyRolle.ident!!.verdi, nyRolle.behandlingstema!!.tilStønadstype())
            // Oppdater rolle slik at husstandsmedlemmen blir låst til rollen i behandlingen
            val husstandsmedlem = behandling.husstandsmedlem.find { it.ident == nyRolle.ident.verdi }!!
            husstandsmedlem.rolle = rolle
            husstandsmedlem.kilde = Kilde.OFFENTLIG
        }

    val nyeRollerSomIkkeHarHusstandsmedlemmer =
        rollerSomLeggesTil
            .filter { it.rolletype == Rolletype.BARN }
            .filter { nyRolle ->
                val stønadstype = nyRolle.behandlingstema!!.tilStønadstype()
                val rolle = behandling.finnRolle(nyRolle.ident!!.verdi, stønadstype)
                behandling.husstandsmedlem.none { it.rolle?.ident == rolle!!.ident && it.rolle?.stønadstype == stønadstype }
            }
    behandling.husstandsmedlem.addAll(
        nyeRollerSomIkkeHarHusstandsmedlemmer.map {
            secureLogger.debug { "Legger til husstandsmedlem med ident ${it.ident?.verdi} i behandling ${behandling.id}" }
            it.toHusstandsmedlem(behandling)
        },
    )
}

fun oppdaterUnderholdskostnadForRoller(
    behandling: Behandling,
    underholdService: UnderholdService,
    rollerSomLeggesTil: List<OpprettRolleDto>,
    rollerSomSkalSlettes: List<OpprettRolleDto>,
) {
    if (behandling.tilType() == TypeBehandling.BIDRAG) {
        rollerSomLeggesTil
            .filter { it.rolletype == Rolletype.BARN }
            .filter { rolle ->
                behandling.underholdskostnader.none { u ->
                    u.rolle?.ident == rolle.ident!!.verdi &&
                        u.rolle!!.stønadstype == rolle.stønadstype
                }
            }.forEach { rolle ->
                underholdService.oppretteUnderholdskostnad(
                    behandling,
                    BarnDto(personident = rolle.ident, stønadstype = rolle.stønadstype),
                    kilde = Kilde.OFFENTLIG,
                )
            }
        rollerSomSkalSlettes.forEach { rolle ->
            underholdService.endreUnderholdskostnadTilAndreBarn(behandling, rolle)
        }
    }
}

fun oppdatereSamværForRoller(
    behandling: Behandling,
    rollerSomLeggesTil: List<OpprettRolleDto>,
    rollerSomSlettes: List<OpprettRolleDto>,
) {
    if (behandling.tilType() == TypeBehandling.BIDRAG) {
        rollerSomLeggesTil
            .filter { it.rolletype == Rolletype.BARN }
            .filter { rolle ->
                behandling.samvær.none { s ->
                    s.rolle.ident == rolle.ident!!.verdi &&
                        s.rolle.stønadstype == rolle.`stønadstype`
                }
            }.forEach { rolle ->
                behandling.samvær.add(
                    Samvær(
                        behandling,
                        rolle =
                            behandling.roller.find {
                                it.ident == rolle.ident?.verdi &&
                                    it.stønadstype == rolle.`stønadstype`
                            }!!,
                    ),
                )
            }

        rollerSomSlettes.forEach { rolle ->
            behandling.samvær.removeIf { s ->
                s.rolle.ident == rolle.ident!!.verdi &&
                    s.rolle.stønadstype == rolle.`stønadstype`
            }
        }
    }
}

fun BehandlingSimple.kanFatteVedtakBegrunnelse(): String? {
    if (!erBidrag() || listOf(Vedtakstype.ALDERSJUSTERING, Vedtakstype.INNKREVING).contains(vedtakstype)) {
        return null
    }
    if (søknadsbarn.size > 1 && !UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN.isEnabled) {
        return "Kan ikke fatte vedtak for bidrag med flere barn"
    }

    val stønaderBp =
        hentAlleStønaderForBidragspliktig(bidragspliktig!!.personident)
            ?: return if (søknadsbarn.size == 1) null else "Kan ikke fatte vedtak for bidrag med flere barn"
    if (UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN.isEnabled &&
        !UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN_LØPENDE_BIDRAG.isEnabled
    ) {
        if (roller.mapNotNull { it.virkningstidspunkt ?: virkningstidspunkt }.toSet().size > 1) {
            return "Kan ikke fatte vedtak når søknadsbarna har ulike virkningstidspunkt"
        }
        val sakerBp =
            hentAlleSaker(bidragspliktig!!.ident).filter {
                it.saksnummer.verdi != saksnummer &&
                    it.bidragspliktig?.fødselsnummer?.verdi == bidragspliktig!!.ident
            }
        if (sakerBp.isNotEmpty()) {
            return "Kan ikke fatte vedtak når BP har flere saker"
        }
        val gjeldendeSak = hentSak(saksnummer) ?: return "Kan ikke fatte vedtak for behandling som ikke inneholder alle barna i saken"
        if (gjeldendeSak.barn.size != søknadsbarn.size) {
            return "Kan ikke fatte vedtak for behandling som ikke inneholder alle barna i saken"
        }
        if (harPrivatAvtaleAndreBarn) {
            return "Kan ikke fatte vedtak når det er lagt inn privat avtale for andre barn"
        }
    }
    val harBPStønadForFlereBarn =
        stønaderBp
            .stønader
            .filter { it.kravhaver.verdi != søknadsbarn.first().ident }
            .any { it.type != Stønadstype.FORSKUDD }
    if (harBPStønadForFlereBarn && !UnleashFeatures.FATTE_VEDTAK_BARNEBIDRAG_FLERE_BARN.isEnabled) {
        return "Kan ikke fatte vedtak hvor BP har løpende bidrag for andre barn"
    }

    return null
}

fun BehandlingSimple.kanFatteVedtak(): Boolean = kanFatteVedtakBegrunnelse() == null

fun Behandling.kanFatteVedtak(): Boolean = toSimple().kanFatteVedtak()

fun Behandling.kanFatteVedtakBegrunnelse(): String? = toSimple().kanFatteVedtakBegrunnelse()

fun Behandling.tilBehandlingDetaljerDtoV2() =
    BehandlingDetaljerDtoV2(
        id = id!!,
        type = tilType(),
        vedtakstype = vedtakstype,
        opprinneligVedtakstype = omgjøringsdetaljer?.opprinneligVedtakstype,
        stønadstype = stonadstype,
        engangsbeløptype = engangsbeloptype,
        erKlageEllerOmgjøring = erKlageEllerOmgjøring,
        opprettetTidspunkt = opprettetTidspunkt,
        erVedtakFattet = vedtaksid != null,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        søktAv = soknadFra,
        saksnummer = saksnummer,
        søknadsid = soknadsid!!,
        behandlerenhet = behandlerEnhet,
        roller =
            roller
                .map {
                    it.tilDto()
                }.toSet(),
        søknadRefId = omgjøringsdetaljer?.soknadRefId,
        vedtakRefId = omgjøringsdetaljer?.omgjørVedtakId,
        virkningstidspunkt = virkningstidspunkt,
        årsak = årsak,
        avslag = avslag,
        opprettetAv =
            SaksbehandlerDto(
                opprettetAv,
                opprettetAvNavn,
            ),
        kategori =
            when (engangsbeloptype) {
                Engangsbeløptype.SÆRBIDRAG -> tilSærbidragKategoriDto()
                else -> null
            },
    )

fun Person.tilRolle(behandling: Behandling) =
    Rolle(
        behandling,
        Rolletype.BARN,
        ident,
        fødselsdato,
        LocalDateTime.now(),
        -1,
        navn ?: hentPersonVisningsnavn(ident),
    )

fun Person.tilDto(stønadstype: Stønadstype? = null) =
    RolleDto(
        id!!,
        Rolletype.BARN,
        ident,
        navn ?: hentPersonVisningsnavn(ident),
        fødselsdato,
        harInnvilgetTilleggsstønad = false,
        delAvOpprinneligBehandling = false,
        erRevurdering = false,
        stønadstype = stønadstype,
        saksnummer = "",
    )

fun Rolle.tilDto() =
    RolleDto(
        id!!,
        rolletype,
        ident,
        navn ?: hentPersonVisningsnavn(ident),
        fødselsdato,
        harInnvilgetTilleggsstønad = this.harInnvilgetTilleggsstønad(),
        delAvOpprinneligBehandling = forholdsmessigFordeling?.delAvOpprinneligBehandling == true,
        erRevurdering = forholdsmessigFordeling?.erRevurdering == true,
        stønadstype = stønadstype ?: behandling.stonadstype,
        saksnummer = forholdsmessigFordeling?.tilhørerSak ?: behandling.saksnummer,
        beregnFraDato = if (rolletype == Rolletype.BARN) finnBeregnFra() else null,
        bidragsmottaker =
            if (rolletype == Rolletype.BARN) {
                forholdsmessigFordeling?.bidragsmottaker ?: behandling.bidragsmottaker?.ident
            } else {
                null
            },
    )

fun Rolle.tilSøknadsdetaljerDto(søknadsid: Long): SøknadDetaljerDto {
    val søknadsdetaljer = forholdsmessigFordeling?.søknaderUnderBehandling?.find { it.søknadsid == søknadsid }
    val barn = behandling.søknadsbarnForSøknad(søknadsid)
    return SøknadDetaljerDto(
        søknadsid = søknadsid,
        saksnummer = sakForSøknad(søknadsid),
        barn = if (rolletype != Rolletype.BARN) barn.map { it.tilDto() } else emptyList(),
        søktFomDato = søknadsdetaljer?.søknadFomDato ?: behandling.søktFomDato,
        mottattDato = søknadsdetaljer?.mottattDato ?: behandling.mottattdato,
        søktAvType = søknadsdetaljer?.søktAvType ?: behandling.soknadFra,
        behandlingstype = søknadsdetaljer?.behandlingstype ?: behandling.søknadstype,
        behandlingstema = søknadsdetaljer?.behandlingstema ?: behandling.behandlingstema,
    )
}

fun Rolle.harInnvilgetTilleggsstønad(): Boolean? {
    val tilleggsstønad =
        this.behandling.grunnlag
            .filter { Grunnlagsdatatype.TILLEGGSSTØNAD == it.type && !it.erBearbeidet }
            .filter { this == it.rolle }

    if (tilleggsstønad.isNotEmpty()) {
        return tilleggsstønad
            .maxBy { it.innhentet }
            .konvertereData<Set<TilleggsstønadGrunnlagDto>>()
            ?.firstOrNull()
            ?.harInnvilgetVedtak
    }
    return null
}

fun Map<Grunnlagsdatatype, GrunnlagFeilDto?>.tilGrunnlagsinnhentingsfeil(behandling: Behandling) =
    this
        .map { feil ->
            Grunnlagsinnhentingsfeil(
                rolle =
                    feil.value?.let { p -> behandling.roller.find { p.personId == it.ident }?.tilDto() }
                        ?: behandling.bidragsmottaker!!.tilDto(),
                feilmelding = feil.value?.feilmelding ?: "Uspesifisert feil oppstod ved innhenting av grunnlag",
                grunnlagsdatatype = feil.key,
                periode = feil.value?.periodeFra?.let { Datoperiode(feil.value?.periodeFra!!, feil.value?.periodeTil) },
            )
        }.toSet()

fun Grunnlag?.toSivilstand(): SivilstandAktivGrunnlagDto? {
    if (this == null) return null
    val harUgyldigStatus =
        behandling.sivilstand.any { it.sivilstand == Sivilstandskode.UKJENT } || behandling.sivilstand.isEmpty()
    val sortertGrunnlag = konvertereData<List<SivilstandGrunnlagDto>>()?.sortedBy { it.gyldigFom }
    val filtrertGrunnlag =
        harUgyldigStatus.ifTrue { sortertGrunnlag }
            ?: sortertGrunnlag?.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(behandling.virkningstidspunktEllerSøktFomDato)

    return SivilstandAktivGrunnlagDto(
        grunnlag = filtrertGrunnlag?.toSet() ?: emptySet(),
        innhentetTidspunkt = innhentet,
    )
}

fun Set<Grunnlag>.tilBarnetilsynAktiveGrunnlagDto(): StønadTilBarnetilsynAktiveGrunnlagDto? {
    if (this.isEmpty()) return null
    return StønadTilBarnetilsynAktiveGrunnlagDto(
        grunnlag =
            this
                .flatMap { it.konvertereData<Set<BarnetilsynGrunnlagDto>>() ?: emptySet() }
                .toSet()
                .groupBy { it.barnPersonId }
                .map { (personidentBarn, barnetilsyn) ->
                    Personident(personidentBarn) to barnetilsyn.toSet()
                }.toMap(),
        innhentetTidspunkt = first().innhentet,
    )
}

fun Behandling.tilInntektDtoV3(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag> = emptyList(),
    rolle: Rolle,
) = InntekterDtoV3(
    barnetillegg =
        rolle.barn.filter { it.rolletype != Rolletype.BARN || it.kreverGrunnlagForBeregning }.map { barn ->
            InntektBarn(
                gjelderBarn = barn.tilDto(),
                inntekter =
                    inntekter
                        .filter { it.type == Inntektsrapportering.BARNETILLEGG }
                        .filter { it.gjelderBarn == barn.ident && it.ident == rolle.ident }
                        .sorterEtterDatoOgBarn()
                        .ekskluderYtelserFørVirkningstidspunkt()
                        .tilInntektDtoV2()
                        .toSet(),
            )
        },
    utvidetBarnetrygd =
        inntekter
            .filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
            .filter { it.ident == rolle.ident }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    kontantstøtte =
        rolle.barn.filter { it.rolletype != Rolletype.BARN || it.kreverGrunnlagForBeregning }.map { barn ->
            InntektBarn(
                gjelderBarn = barn.tilDto(),
                inntekter =
                    inntekter
                        .filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
                        .filter { it.gjelderBarn == barn.ident && it.ident == rolle.ident }
                        .sorterEtterDatoOgBarn()
                        .ekskluderYtelserFørVirkningstidspunkt()
                        .tilInntektDtoV2()
                        .toSet(),
            )
        },
    småbarnstillegg =
        inntekter
            .filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
            .filter { it.ident == rolle.ident }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    månedsinntekter =
        gjeldendeAktiveGrunnlagsdata
            .filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER && it.erBearbeidet }
            .flatMap { grunnlag ->
                grunnlag.konvertereData<SummerteInntekter<SummertMånedsinntekt>>()?.inntekter?.map {
                    it.tilInntektDtoV2(
                        grunnlag.rolle.ident!!,
                    )
                } ?: emptyList()
            }.filter { it.ident.verdi == rolle.ident }
            .toSet(),
    årsinntekter =
        inntekter
            .filter { it.ident == rolle.ident }
            .toSet()
            .årsinntekterSortert(inkluderHistoriskeInntekter = true)
            .tilInntektDtoV2()
            .toSet(),
    beregnetInntekt =
        BeregnetInntekterDto(
            rolle.tilPersonident()!!,
            rolle.rolletype,
            hentBeregnetInntekterForRolle(rolle),
        ),
    begrunnelse =
        NotatService.henteInntektsnotat(this, rolle.id!!)?.let {
            BegrunnelseDto(
                innhold = it,
                gjelder = rolle.tilDto(),
            )
        },
    begrunnelseFraOpprinneligVedtak =
        NotatService.henteInntektsnotat(this, rolle.id!!, false).takeIfNotNullOrEmpty {
            BegrunnelseDto(
                innhold = it,
                gjelder = rolle.tilDto(),
            )
        },
    valideringsfeil = hentInntekterValideringsfeilV2(rolle),
)

fun List<Inntekt>.filtrerInntektGjelderBarn(rolle: Rolle?) =
    filter { rolle == null || it.ident == rolle.ident }
        .filter {
            if (rolle == null || rolle.rolletype != Rolletype.BIDRAGSMOTTAKER) {
                true
            } else {
                it.gjelderSøknadsbarn?.bidragsmottaker?.ident == rolle.ident
            }
        }

fun Behandling.tilInntektDtoV2(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag> = emptyList(),
    inkluderHistoriskeInntekter: Boolean = true,
) = InntekterDtoV2(
    barnetillegg =
        inntekter
            .filter { it.type == Inntektsrapportering.BARNETILLEGG }
            .sorterEtterDatoOgBarn()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    utvidetBarnetrygd =
        inntekter
            .filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    kontantstøtte =
        inntekter
            .filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
            .sorterEtterDatoOgBarn()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    småbarnstillegg =
        inntekter
            .filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    månedsinntekter =
        gjeldendeAktiveGrunnlagsdata
            .filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER && it.erBearbeidet }
            .flatMap { grunnlag ->
                grunnlag.konvertereData<SummerteInntekter<SummertMånedsinntekt>>()?.inntekter?.map {
                    it.tilInntektDtoV2(
                        grunnlag.rolle.ident!!,
                    )
                } ?: emptyList()
            }.toSet(),
    årsinntekter =
        inntekter
            .årsinntekterSortert(inkluderHistoriskeInntekter = inkluderHistoriskeInntekter)
            .tilInntektDtoV2()
            .toSet(),
    beregnetInntekter =
        roller
            .map {
                BeregnetInntekterDto(
                    it.tilPersonident()!!,
                    it.rolletype,
                    hentBeregnetInntekterForRolle(it),
                )
            },
    begrunnelser =
        this.roller
            .mapNotNull { r ->
                val inntektsnotat = NotatService.henteInntektsnotat(this, r.id!!)
                inntektsnotat?.let {
                    BegrunnelseDto(
                        innhold = it,
                        gjelder = r.tilDto(),
                    )
                }
            }.toSet(),
    begrunnelserFraOpprinneligVedtak =
        this.roller
            .mapNotNull { r ->
                val inntektsnotat = NotatService.henteInntektsnotat(this, r.id!!, false)
                inntektsnotat.takeIfNotNullOrEmpty {
                    BegrunnelseDto(
                        innhold = it,
                        gjelder = r.tilDto(),
                    )
                }
            }.toSet(),
    valideringsfeil = hentInntekterValideringsfeil(),
)

fun Rolle.hentVirkningstidspunktValideringsfeilRolle(): VirkningstidspunktFeilV2Dto {
    val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
        behandling.erKlageEllerOmgjøring &&
            behandling.omgjøringsdetaljer?.opprinneligVirkningstidspunkt != null &&
            virkningstidspunktRolle.isAfter(behandling.omgjøringsdetaljer!!.opprinneligVirkningstidspunkt) == true
    val begrunnelseVirkningstidspunkt =
        NotatService.henteNotatinnhold(behandling, NotatType.VIRKNINGSTIDSPUNKT, this).takeIf { it.isNotEmpty() }
            ?: NotatService.henteNotatinnhold(behandling, NotatType.VIRKNINGSTIDSPUNKT)
    val avslagRolle = if (avslag == null && årsak == null) behandling.avslag else avslag
    val årsakRolle = if (avslag == null && årsak == null) behandling.årsak else årsak
    return VirkningstidspunktFeilV2Dto(
        gjelder = tilDto(),
        manglerÅrsakEllerAvslag = avslagRolle == null && årsakRolle == null,
        manglerVurderingAvSkolegang =
            if (behandling.kanSkriveVurderingAvSkolegang(this) && !behandling.erKlageEllerOmgjøring) {
                NotatService
                    .henteNotatinnhold(
                        behandling,
                        rolle = this,
                        notattype = NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                    ).isEmpty()
            } else {
                false
            },
        manglerOpphørsdato =
            if (stønadstype == Stønadstype.BIDRAG18AAR && avslagRolle == null) {
                opphørsdato == null
            } else {
                false
            },
        kanIkkeSetteOpphørsdatoEtterEtterfølgendeVedtak =
            if (avslagRolle == null && behandling.erKlageEllerOmgjøring) {
                val etterfølgendeVedtak = behandling.hentNesteEtterfølgendeVedtak(this)
                val virkningstidspunktEtterfølgendeVedtak = etterfølgendeVedtak?.virkningstidspunkt
                virkningstidspunktEtterfølgendeVedtak != null && opphørsdato != null &&
                    opphørsdato!!.toYearMonth() > virkningstidspunktEtterfølgendeVedtak
            } else {
                false
            },
        manglerBegrunnelse =
            if (behandling.vedtakstype == Vedtakstype.OPPHØR || avslagRolle != null) {
                begrunnelseVirkningstidspunkt.isEmpty()
            } else {
                false
            },
        virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig =
            if (behandling.erKlageEllerOmgjøring && behandling.erBidrag()) {
                false
            } else {
                erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt
            },
    )
}

fun Behandling.hentVirkningstidspunktValideringsfeilV2(): List<VirkningstidspunktFeilV2Dto> =
    if (erBidrag()) {
        søknadsbarn
            .map {
                it.hentVirkningstidspunktValideringsfeilRolle()
            }.filter { it.harFeil }
    } else {
        val begrunnelseVirkningstidspunkt = NotatService.henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT)
        val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
            erKlageEllerOmgjøring &&
                omgjøringsdetaljer?.opprinneligVirkningstidspunkt != null &&
                virkningstidspunkt?.isAfter(omgjøringsdetaljer!!.opprinneligVirkningstidspunkt) == true
        listOf(
            VirkningstidspunktFeilV2Dto(
                gjelder = bidragsmottaker!!.tilDto(),
                manglerÅrsakEllerAvslag = avslag == null && årsak == null,
                manglerVirkningstidspunkt = virkningstidspunkt == null,
                manglerVurderingAvSkolegang =
                    if (kanSkriveVurderingAvSkolegangAlle() && !erKlageEllerOmgjøring) {
                        søknadsbarn.filter { kanSkriveVurderingAvSkolegang(it) }.any {
                            NotatService
                                .henteNotatinnhold(
                                    this,
                                    rolle = it,
                                    notattype = NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                                ).isEmpty()
                        }
                    } else {
                        false
                    },
                manglerBegrunnelse =
                    if (vedtakstype == Vedtakstype.OPPHØR || avslag != null) {
                        begrunnelseVirkningstidspunkt.isEmpty()
                    } else {
                        false
                    },
                virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig =
                    if (erKlageEllerOmgjøring && erBidrag()) {
                        false
                    } else {
                        erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt
                    },
            ),
        ).filter { it.harFeil }
    }

fun Behandling.hentVirkningstidspunktValideringsfeil(): VirkningstidspunktFeilDto {
    val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
        erKlageEllerOmgjøring &&
            omgjøringsdetaljer?.opprinneligVirkningstidspunkt != null &&
            virkningstidspunkt?.isAfter(omgjøringsdetaljer!!.opprinneligVirkningstidspunkt) == true
    val begrunnelseVirkningstidspunkt = NotatService.henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT)

    return VirkningstidspunktFeilDto(
        manglerÅrsakEllerAvslag = avslag == null && årsak == null,
        manglerVirkningstidspunkt = virkningstidspunkt == null,
        manglerVurderingAvSkolegang =
            if (kanSkriveVurderingAvSkolegangAlle() && !erKlageEllerOmgjøring) {
                søknadsbarn.filter { kanSkriveVurderingAvSkolegang(it) }.any {
                    NotatService
                        .henteNotatinnhold(
                            this,
                            rolle = it,
                            notattype = NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                        ).isEmpty()
                }
            } else {
                false
            },
        manglerOpphørsdato =
            if (stonadstype == Stønadstype.BIDRAG18AAR && avslag == null) {
                søknadsbarn.filter { it.opphørsdato == null }.map { it.tilDto() }
            } else {
                emptyList()
            },
        kanIkkeSetteOpphørsdatoEtterEtterfølgendeVedtak =
            if (avslag == null && erKlageEllerOmgjøring) {
                søknadsbarn
                    .filter { it.beregnTil != BeregnTil.INNEVÆRENDE_MÅNED }
                    .filter {
                        val etterfølgendeVedtak = hentNesteEtterfølgendeVedtak(it)
                        val virkningstidspunktEtterfølgendeVedtak = etterfølgendeVedtak?.virkningstidspunkt ?: return@filter false
                        it.opphørsdato != null && it.opphørsdato!!.toYearMonth() > virkningstidspunktEtterfølgendeVedtak
                    }.map { it.tilDto() }
            } else {
                emptyList()
            },
        manglerBegrunnelse =
            if (vedtakstype == Vedtakstype.OPPHØR || avslag != null) {
                begrunnelseVirkningstidspunkt.isEmpty()
            } else {
                false
            },
        virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig =
            if (erKlageEllerOmgjøring && erBidrag()) {
                false
            } else {
                erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt
            },
    )
}

fun Behandling.hentInntekterValideringsfeilV2(rolle: Rolle): InntektValideringsfeilV2Dto =
    InntektValideringsfeilV2Dto(
        årsinntekter =
            inntekter
                .filter { it.ident == rolle.ident }
                .mapValideringsfeilForÅrsinntekterV2(
                    eldsteVirkningstidspunkt,
                    rolle,
                    tilType(),
                ),
        barnetillegg =
            inntekter
                .toList()
                .filtrerInntektGjelderBarn(rolle)
                .mapValideringsfeilForYtelseSomGjelderBarn(
                    Inntektsrapportering.BARNETILLEGG,
                    eldsteVirkningstidspunkt,
                    roller,
                ).takeIf { it.isNotEmpty() },
        småbarnstillegg =
            inntekter
                .filter { it.ident == rolle.ident }
                .mapValideringsfeilForYtelse(
                    Inntektsrapportering.SMÅBARNSTILLEGG,
                    eldsteVirkningstidspunkt,
                    roller,
                ).firstOrNull(),
        // Det er bare bidragsmottaker småbarnstillegg og utvidetbarnetrygd er relevant for. Antar derfor det alltid gjelder BM og velger derfor den første i listen
        utvidetBarnetrygd =
            inntekter
                .filter { it.ident == rolle.ident }
                .mapValideringsfeilForYtelse(
                    Inntektsrapportering.UTVIDET_BARNETRYGD,
                    eldsteVirkningstidspunkt,
                    roller,
                ).firstOrNull(),
        kontantstøtte =
            inntekter
                .toList()
                .filtrerInntektGjelderBarn(rolle)
                .mapValideringsfeilForYtelseSomGjelderBarn(
                    Inntektsrapportering.KONTANTSTØTTE,
                    eldsteVirkningstidspunkt,
                    roller,
                ).takeIf { it.isNotEmpty() },
    )

fun Behandling.hentInntekterValideringsfeil(rolle: Rolle? = null): InntektValideringsfeilDto =
    InntektValideringsfeilDto(
        årsinntekter =
            inntekter
                .filter { rolle == null || it.ident == rolle.ident }
                .mapValideringsfeilForÅrsinntekter(
                    eldsteVirkningstidspunkt,
                    roller,
                    tilType(),
                ).takeIf { it.isNotEmpty() },
        barnetillegg =
            if (rolle != null) {
                rolle.barn
                    .mapNotNull { barn ->
                        inntekter
                            .filter { it.gjelderBarn == barn.ident }
                            .mapValideringsfeilForYtelseSomGjelderBarn(
                                Inntektsrapportering.BARNETILLEGG,
                                eldsteVirkningstidspunkt,
                                roller,
                            ).takeIf { it.isNotEmpty() }
                    }.flatMap { it }
            } else {
                inntekter
                    .toList()
                    .filtrerInntektGjelderBarn(rolle)
                    .mapValideringsfeilForYtelseSomGjelderBarn(
                        Inntektsrapportering.BARNETILLEGG,
                        eldsteVirkningstidspunkt,
                        roller,
                    ).takeIf { it.isNotEmpty() }
            },
        småbarnstillegg =
            inntekter
                .filter { rolle == null || it.ident == rolle.ident }
                .mapValideringsfeilForYtelse(
                    Inntektsrapportering.SMÅBARNSTILLEGG,
                    eldsteVirkningstidspunkt,
                    roller,
                ).firstOrNull(),
        // Det er bare bidragsmottaker småbarnstillegg og utvidetbarnetrygd er relevant for. Antar derfor det alltid gjelder BM og velger derfor den første i listen
        utvidetBarnetrygd =
            inntekter
                .filter { rolle == null || it.ident == rolle.ident }
                .mapValideringsfeilForYtelse(
                    Inntektsrapportering.UTVIDET_BARNETRYGD,
                    eldsteVirkningstidspunkt,
                    roller,
                ).firstOrNull(),
        kontantstøtte =
            if (rolle != null) {
                rolle.barn
                    .mapNotNull { barn ->
                        inntekter
                            .filter { it.gjelderBarn == barn.ident }
                            .mapValideringsfeilForYtelseSomGjelderBarn(
                                Inntektsrapportering.KONTANTSTØTTE,
                                eldsteVirkningstidspunkt,
                                roller,
                            ).takeIf { it.isNotEmpty() }
                    }.flatMap { it }
            } else {
                inntekter
                    .toList()
                    .filtrerInntektGjelderBarn(rolle)
                    .mapValideringsfeilForYtelseSomGjelderBarn(
                        Inntektsrapportering.KONTANTSTØTTE,
                        eldsteVirkningstidspunkt,
                        roller,
                    ).takeIf { it.isNotEmpty() }
            },
    )

fun Collection<Inntekt>.mapValideringsfeilForÅrsinntekterV2(
    virkningstidspunkt: LocalDate,
    rolle: Rolle,
    behandlingType: TypeBehandling = TypeBehandling.FORSKUDD,
): InntektValideringsfeil? {
    val inntekterSomSkalSjekkes = filter { !eksplisitteYtelser.contains(it.type) }.filter { it.taMed }
    val rollerSomKreverMinstEnInntekt = bestemRollerSomMåHaMinstEnInntekt(behandlingType)
    val opphørsdato = rolle.behandling.globalOpphørsdato
    val inntekterTaMed = inntekterSomSkalSjekkes.filter { it.ident == rolle.ident }

    return if (inntekterTaMed.isEmpty() && (rollerSomKreverMinstEnInntekt.contains(rolle.rolletype))) {
        InntektValideringsfeil(
            hullIPerioder = emptyList(),
            overlappendePerioder = emptySet(),
            fremtidigPeriode = false,
            manglerPerioder = true,
            rolle = rolle.tilDto(),
        )
    } else {
        val hullIPerioder =
            if (rolle.rolletype == Rolletype.BARN) {
                // Kan ha hull i perioder hvis det er barn
                // Feks at barnet bare har inntekt fra sommerjobb
                emptyList()
            } else {
                inntekterTaMed.finnHullIPerioder(virkningstidspunkt, opphørsdato)
            }
        InntektValideringsfeil(
            hullIPerioder = hullIPerioder,
            overlappendePerioder = inntekterTaMed.finnOverlappendePerioderInntekt(),
            fremtidigPeriode = inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
            ugyldigSluttPeriode = inntekterTaMed.harUgyldigSluttperiode(opphørsdato),
            manglerPerioder =
                (rolle.rolletype != Rolletype.BARN)
                    .ifTrue { this.isEmpty() } == true,
            rolle = rolle.tilDto(),
            ingenLøpendePeriode =
                if (opphørsdato == null ||
                    opphørsdato.opphørSisteTilDato().isAfter(LocalDate.now().sluttenAvForrigeMåned)
                ) {
                    hullIPerioder.any { it.til == null }
                } else {
                    false
                },
        )
    }.takeIf { it.harFeil }
}

fun Collection<Inntekt>.mapValideringsfeilForÅrsinntekter(
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
    behandlingType: TypeBehandling = TypeBehandling.FORSKUDD,
): Set<InntektValideringsfeil> {
    val inntekterSomSkalSjekkes = filter { !eksplisitteYtelser.contains(it.type) }.filter { it.taMed }
    val rollerSomKreverMinstEnInntekt = bestemRollerSomMåHaMinstEnInntekt(behandlingType)
    return roller
        .filter { bestemRollerSomKanHaInntekter(behandlingType).contains(it.rolletype) }
        .map { rolle ->
            val opphørsdato = rolle.behandling.globalOpphørsdato
            val inntekterTaMed = inntekterSomSkalSjekkes.filter { it.ident == rolle.ident }

            if (inntekterTaMed.isEmpty() && (rollerSomKreverMinstEnInntekt.contains(rolle.rolletype))) {
                InntektValideringsfeil(
                    hullIPerioder = emptyList(),
                    overlappendePerioder = emptySet(),
                    fremtidigPeriode = false,
                    manglerPerioder = true,
                    rolle = rolle.tilDto(),
                )
            } else {
                val hullIPerioder =
                    if (rolle.rolletype == Rolletype.BARN) {
                        // Kan ha hull i perioder hvis det er barn
                        // Feks at barnet bare har inntekt fra sommerjobb
                        emptyList()
                    } else {
                        inntekterTaMed.finnHullIPerioder(virkningstidspunkt, opphørsdato)
                    }
                InntektValideringsfeil(
                    hullIPerioder = hullIPerioder,
                    overlappendePerioder = inntekterTaMed.finnOverlappendePerioderInntekt(),
                    fremtidigPeriode = inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
                    ugyldigSluttPeriode = inntekterTaMed.harUgyldigSluttperiode(opphørsdato),
                    manglerPerioder =
                        (rolle.rolletype != Rolletype.BARN)
                            .ifTrue { this.isEmpty() } == true,
                    rolle = rolle.tilDto(),
                    ingenLøpendePeriode =
                        if (opphørsdato == null ||
                            opphørsdato.opphørSisteTilDato().isAfter(LocalDate.now().sluttenAvForrigeMåned)
                        ) {
                            hullIPerioder.any { it.til == null }
                        } else {
                            false
                        },
                )
            }
        }.filter { it.harFeil }
        .toSet()
}

fun List<Inntekt>.mapValideringsfeilForYtelse(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
    gjelderBarn: String? = null,
) = filter { it.taMed }
    .filter { it.type == type }
    .groupBy { it.ident }
    .map { (inntektGjelderIdent, inntekterTaMed) ->
        val gjelderRolle = roller.find { it.ident == inntektGjelderIdent }
        val gjelderIdent = gjelderRolle?.ident ?: inntektGjelderIdent
        InntektValideringsfeil(
            overlappendePerioder = inntekterTaMed.finnOverlappendePerioderInntekt(),
            fremtidigPeriode =
                inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
            ugyldigSluttPeriode = inntekterTaMed.harUgyldigSluttperiode(inntekterTaMed.firstOrNull()?.opphørsdato),
            ident = gjelderIdent,
            rolle = gjelderRolle?.tilDto(),
            gjelderBarn = gjelderBarn,
            erYtelse = true,
        ).takeIf { it.harFeil }
    }

fun Collection<Inntekt>.mapValideringsfeilForYtelseSomGjelderBarn(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
) = filter { inntekstrapporteringerSomKreverGjelderBarn.contains(type) }
    .groupBy { it.gjelderBarn }
    .flatMap { (gjelderBarn, inntekter) ->
        inntekter.mapValideringsfeilForYtelse(
            type,
            virkningstidspunkt,
            roller,
            gjelderBarn,
        )
    }.filterNotNull()
    .toSet()

fun List<Inntekt>.inneholderFremtidigPeriode(virkningstidspunkt: LocalDate) =
    any {
        it.datoFom!!.isAfter(maxOf(virkningstidspunkt.withDayOfMonth(1), LocalDate.now().withDayOfMonth(1)))
    }

fun Behandling.hentBeregnetInntekterForRolle(rolle: Rolle) =
    BeregnApi()
        .beregnInntekt(tilInntektberegningDto(rolle))
        .inntektPerBarnListe
        .sortedBy {
            it.inntektGjelderBarnIdent?.verdi
        }.map {
            it.copy(
                summertInntektListe =
                    it.summertInntektListe.map { delberegning ->
                        delberegning.copy(
                            barnetillegg = delberegning.barnetillegg?.nærmesteHeltall,
                            småbarnstillegg = delberegning.småbarnstillegg?.nærmesteHeltall,
                            kontantstøtte = delberegning.kontantstøtte?.nærmesteHeltall,
                            utvidetBarnetrygd = delberegning.utvidetBarnetrygd?.nærmesteHeltall,
                            skattepliktigInntekt = delberegning.skattepliktigInntekt?.nærmesteHeltall,
                            totalinntekt = delberegning.totalinntekt.nærmesteHeltall,
                        )
                    },
            )
        }

fun Behandling.tilReferanseId() = "bidrag_behandling_${id}_${opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)}"

fun Behandling.tilNotat(
    notattype: Notattype,
    tekst: String,
    rolle: Rolle? = null,
    delAvBehandling: Boolean = true,
): Notat {
    val gjelder = this.henteRolleForNotat(notattype, rolle)
    return Notat(behandling = this, rolle = gjelder, type = notattype, innhold = tekst, erDelAvBehandlingen = delAvBehandling)
}

fun Behandling.henteRolleForNotat(
    notattype: Notattype,
    forRolle: Rolle?,
): Rolle =
    when (notattype) {
        Notattype.BOFORHOLD -> {
            Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(this)!!
        }

        Notattype.UTGIFTER -> {
            this.bidragsmottaker!!
        }

        Notattype.VIRKNINGSTIDSPUNKT -> {
            forRolle ?: this.bidragsmottaker!!
        }

        Notattype.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG -> {
            forRolle ?: this.bidragsmottaker!!
        }

        Notattype.INNTEKT -> {
            if (forRolle == null) {
                log.warn { "Notattype $notattype krever spesifisering av hvilken rolle notatet gjelder." }
                this.bidragsmottaker!!
            } else {
                forRolle
            }
        }

        Notattype.UNDERHOLDSKOSTNAD -> {
            if (forRolle == null) {
                log.warn { "Notattype $notattype krever spesifisering av hvilken rolle notatet gjelder." }
                this.bidragsmottaker!!
            } else {
                forRolle
            }
        }

        Notattype.SAMVÆR -> {
            forRolle!!
        }

        Notattype.PRIVAT_AVTALE -> {
            if (forRolle == null) {
                log.warn { "Notattype $notattype krever spesifisering av hvilken rolle notatet gjelder." }
                this.bidragspliktig!!
            } else {
                forRolle
            }
        }
    }

fun Behandling.notatTittel(): String {
    val prefiks =
        when (stonadstype) {
            Stønadstype.FORSKUDD -> {
                "Bidragsforskudd"
            }

            Stønadstype.BIDRAG -> {
                "Barnebidrag"
            }

            Stønadstype.BIDRAG18AAR -> {
                "Barnebidrag 18 år"
            }

            Stønadstype.EKTEFELLEBIDRAG -> {
                "Ektefellebidrag"
            }

            Stønadstype.OPPFOSTRINGSBIDRAG -> {
                "Oppfostringbidrag"
            }

            Stønadstype.MOTREGNING -> {
                "Motregning"
            }

            else -> {
                when (engangsbeloptype) {
                    Engangsbeløptype.SÆRBIDRAG, Engangsbeløptype.SÆRTILSKUDD, Engangsbeløptype.SAERTILSKUDD -> {
                        "Særbidrag ${kategoriTilTittel()}".trim()
                    }

                    Engangsbeløptype.DIREKTE_OPPGJØR, Engangsbeløptype.DIREKTE_OPPGJØR -> {
                        "Direkte oppgjør"
                    }

                    Engangsbeløptype.ETTERGIVELSE -> {
                        "Ettergivelse"
                    }

                    Engangsbeløptype.ETTERGIVELSE_TILBAKEKREVING -> {
                        "Ettergivelse tilbakekreving"
                    }

                    Engangsbeløptype.GEBYR_MOTTAKER -> {
                        "Gebyr"
                    }

                    Engangsbeløptype.GEBYR_SKYLDNER -> {
                        "Gebyr"
                    }

                    Engangsbeløptype.TILBAKEKREVING -> {
                        "Tilbakekreving"
                    }

                    else -> {
                        null
                    }
                }
            }
        }
    return "${prefiks?.let { "$prefiks, " }}Saksbehandlingsnotat"
}

fun Behandling.kategoriTilTittel() =
    if (engangsbeloptype == Engangsbeløptype.SÆRBIDRAG) {
        if (særbidragKategori == Særbidragskategori.ANNET) {
            kategoriBeskrivelse
        } else {
            særbidragKategori.visningsnavn.intern.lowercase()
        }
    } else {
        ""
    }

fun Set<BarnetilsynGrunnlagDto>.filtrerePerioderEtterVirkningstidspunkt(virkningstidspunkt: LocalDate): Set<BarnetilsynGrunnlagDto> =
    groupBy { it.barnPersonId }
        .flatMap { (_, perioder) ->
            val perioderFiltrert =
                perioder.sortedBy { it.periodeFra }.slice(
                    perioder
                        .map { it.periodeFra }
                        .hentIndekserEtterVirkningstidspunkt(virkningstidspunkt, null),
                )
            val cutoffPeriodeFom = finnCutoffDatoFom(virkningstidspunkt, null)
            perioderFiltrert.map { periode ->
                periode
                    .takeIf { it == perioderFiltrert.first() }
                    ?.copy(periodeFra = maxOf(periode.periodeFra, cutoffPeriodeFom)) ?: periode
            }
        }.toSet()

fun List<BoforholdResponseV2>.filtrerPerioderEtterVirkningstidspunktForBMsBoforhold(
    virkningstidspunkt: LocalDate,
): List<BoforholdResponseV2> =
    groupBy { it.gjelderPersonId }.flatMap { (_, perioder) ->
        val perioderFiltrert =
            perioder.sortedBy { it.periodeFom }.slice(
                perioder
                    .map { it.periodeFom }
                    .hentIndekserEtterVirkningstidspunkt(virkningstidspunkt, null),
            )
        val cutoffPeriodeFom = finnCutoffDatoFom(virkningstidspunkt, null)
        perioderFiltrert.map { periode ->
            periode
                .takeIf { it == perioderFiltrert.first() }
                ?.copy(periodeFom = maxOf(periode.periodeFom, cutoffPeriodeFom)) ?: periode
        }
    }

fun List<BoforholdResponseV2>.filtrerPerioderEtterVirkningstidspunkt(
    husstandsmedlemListe: Set<Husstandsmedlem>,
    virkningstidspunkt: LocalDate,
): List<BoforholdResponseV2> {
    return groupBy { it.gjelderPersonId }.flatMap { (barnId, perioder) ->
        val barn =
            husstandsmedlemListe.find { it.ident == barnId }
                ?: return@flatMap perioder
        val perioderFiltrert =
            perioder.sortedBy { it.periodeFom }.slice(
                perioder
                    .map { it.periodeFom }
                    .hentIndekserEtterVirkningstidspunkt(virkningstidspunkt, barn.fødselsdato),
            )
        val cutoffPeriodeFom = finnCutoffDatoFom(virkningstidspunkt, barn.fødselsdato)
        perioderFiltrert.map { periode ->
            periode
                .takeIf { it == perioderFiltrert.first() }
                ?.copy(periodeFom = maxOf(periode.periodeFom, cutoffPeriodeFom)) ?: periode
        }
    }
}

fun List<SivilstandGrunnlagDto>.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(
    virkningstidspunkt: LocalDate,
): List<SivilstandGrunnlagDto> {
    val filtrertGrunnlag =
        sortedBy { it.gyldigFom }.slice(map { it.gyldigFom }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt))
    return if (isEmpty()) {
        emptyList()
    } else {
        filtrertGrunnlag.ifEmpty {
            listOf(sortedBy { it.gyldigFom }.last())
        }
    }
}

fun List<LocalDate?>.hentIndekserEtterVirkningstidspunkt(
    virkningstidspunkt: LocalDate,
    fødselsdato: LocalDate? = null,
): List<Int> {
    val kanIkkeVæreTidligereEnnDato = finnCutoffDatoFom(virkningstidspunkt, fødselsdato)
    val datoerSortert = sortedBy { it }

    return datoerSortert.mapIndexedNotNull { index, dato ->
        index.takeIf {
            if (dato == null) return@takeIf true
            val erEtterVirkningstidspunkt = dato >= kanIkkeVæreTidligereEnnDato
            if (!erEtterVirkningstidspunkt) {
                val nesteDato = datoerSortert.drop(index + 1).firstOrNull()
                nesteDato == null || nesteDato > kanIkkeVæreTidligereEnnDato
            } else {
                true
            }
        }
    }
}

fun SivilstandBeregnet.filtrerSivilstandBeregnetEtterVirkningstidspunktV1(virkningstidspunkt: LocalDate): SivilstandBeregnet =
    copy(
        sivilstandListe =
            sivilstandListe.sortedBy { it.periodeFom }.slice(
                sivilstandListe
                    .map {
                        it.periodeFom
                    }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt),
            ),
    )

fun List<Sivilstand>.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(virkningstidspunkt: LocalDate): List<Sivilstand> =
    sortedBy {
        it.periodeFom
    }.slice(map { it.periodeFom }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt))

fun List<Grunnlag>.hentAlleBearbeidaBoforholdTilBMSøknadsbarn(virkniningstidspunkt: LocalDate) =
    asSequence()
        .filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN && it.erBearbeidet }
        .mapNotNull { it.konvertereData<List<BoforholdResponseV2>>() }
        .flatten()
        .distinct()
        .toList()
        .filtrerPerioderEtterVirkningstidspunktForBMsBoforhold(virkniningstidspunkt)
        .sortedBy { it.periodeFom }

fun List<Grunnlag>.hentAlleBearbeidaBoforhold(
    virkniningstidspunkt: LocalDate,
    husstandsmedlem: Set<Husstandsmedlem>,
    rolle: Rolle,
) = asSequence()
    .filter { (it.rolle.id == rolle.id) && it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    .mapNotNull { it.konvertereData<List<BoforholdResponseV2>>() }
    .flatten()
    .distinct()
    .toList()
    .filtrerPerioderEtterVirkningstidspunkt(
        husstandsmedlem,
        virkniningstidspunkt,
    ).sortedBy { it.periodeFom }

fun Set<Grunnlag>.hentAlleBearbeidaBarnetilsyn(
    virkniningstidspunkt: LocalDate,
    rolle: Rolle,
) = asSequence()
    .filter { (it.rolle.id == rolle.id) && it.type == Grunnlagsdatatype.BARNETILSYN && it.erBearbeidet }
    .mapNotNull { it.konvertereData<Set<BarnetilsynGrunnlagDto>>() }
    .flatten()
    .distinct()
    .toSet()
    .filtrerePerioderEtterVirkningstidspunkt(
        virkniningstidspunkt,
    ).sortedBy { it.periodeFra }
    .toSet()

fun BehandlingSimple.tilKanBehandlesINyLøsningRequest() =
    KanBehandlesINyLøsningRequest(
        engangsbeløpstype = engangsbeløptype,
        stønadstype = stønadstype,
        saksnummer = saksnummer,
        vedtakstype = vedtakstype,
        søknadstype = søknadstype,
        harReferanseTilAnnenBehandling = omgjøringsdetaljer != null,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        roller =
            roller.map {
                SjekkRolleDto(
                    rolletype = it.rolletype,
                    ident = Personident(it.ident),
                    erUkjent = false,
                )
            },
    )

fun Behandling.tilKanBehandlesINyLøsningRequest() =
    KanBehandlesINyLøsningRequest(
        engangsbeløpstype = engangsbeloptype,
        stønadstype = stonadstype,
        saksnummer = saksnummer,
        vedtakstype = vedtakstype,
        søknadstype = søknadstype,
        harReferanseTilAnnenBehandling = omgjøringsdetaljer != null,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        roller =
            roller.map {
                SjekkRolleDto(
                    rolletype = it.rolletype,
                    ident = Personident(it.ident!!),
                    erUkjent = false,
                )
            },
    )
