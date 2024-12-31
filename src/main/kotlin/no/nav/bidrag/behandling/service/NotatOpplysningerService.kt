package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragDokumentConsumer
import no.nav.bidrag.behandling.consumer.BidragDokumentProduksjonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.barn
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBarnetilleggDto
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBidragsevneDto
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBidragspliktigesBeregnedeTotalbidragDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragKategoriDto
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragUtgifterDto
import no.nav.bidrag.behandling.dto.v2.behandling.TotalBeregningUtgifterDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.samvær.SamværDto
import no.nav.bidrag.behandling.service.NotatService.Companion.henteInntektsnotat
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.Personinfo
import no.nav.bidrag.behandling.transformers.behandling.filtrerSivilstandGrunnlagEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.behandling.hentAlleBearbeidaBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentBeregnetInntekterForRolle
import no.nav.bidrag.behandling.transformers.behandling.notatTittel
import no.nav.bidrag.behandling.transformers.behandling.tilReferanseId
import no.nav.bidrag.behandling.transformers.ekskluderYtelserFørVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.erHistorisk
import no.nav.bidrag.behandling.transformers.grunnlag.erBarnTilBMUnder12År
import no.nav.bidrag.behandling.transformers.inntekt.bestemOpprinneligTomVisningsverdi
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.sorterEtterDato
import no.nav.bidrag.behandling.transformers.sorterEtterDatoOgBarn
import no.nav.bidrag.behandling.transformers.sortert
import no.nav.bidrag.behandling.transformers.tilDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.utgift.tilSærbidragKategoriDto
import no.nav.bidrag.behandling.transformers.årsinntekterSortert
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.finnVisningsnavn
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.DatoperiodeDto
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.inntekt.util.InntektUtil
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.dokument.JournalpostType
import no.nav.bidrag.transport.dokument.OpprettDokumentDto
import no.nav.bidrag.transport.dokument.OpprettJournalpostRequest
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.notat.Arbeidsforhold
import no.nav.bidrag.transport.notat.InntekterPerRolle
import no.nav.bidrag.transport.notat.NotatBegrunnelseDto
import no.nav.bidrag.transport.notat.NotatBehandlingDetaljerDto
import no.nav.bidrag.transport.notat.NotatBeregnetInntektDto
import no.nav.bidrag.transport.notat.NotatBoforholdDto
import no.nav.bidrag.transport.notat.NotatDelberegningBarnetilleggDto
import no.nav.bidrag.transport.notat.NotatDelberegningBidragsevneDto
import no.nav.bidrag.transport.notat.NotatDelberegningBidragspliktigesBeregnedeTotalbidragDto
import no.nav.bidrag.transport.notat.NotatGebyrRolleDto
import no.nav.bidrag.transport.notat.NotatGebyrRolleDto.NotatGebyrInntektDto
import no.nav.bidrag.transport.notat.NotatInntektDto
import no.nav.bidrag.transport.notat.NotatInntekterDto
import no.nav.bidrag.transport.notat.NotatInntektspostDto
import no.nav.bidrag.transport.notat.NotatMaksGodkjentBeløpDto
import no.nav.bidrag.transport.notat.NotatMalType
import no.nav.bidrag.transport.notat.NotatOffentligeOpplysningerUnderhold
import no.nav.bidrag.transport.notat.NotatOffentligeOpplysningerUnderholdBarn
import no.nav.bidrag.transport.notat.NotatOffentligeOpplysningerUnderholdBarn.NotatBarnetilsynOffentligeOpplysninger
import no.nav.bidrag.transport.notat.NotatPersonDto
import no.nav.bidrag.transport.notat.NotatResultatBeregningInntekterDto
import no.nav.bidrag.transport.notat.NotatResultatBidragsberegningBarnDto
import no.nav.bidrag.transport.notat.NotatResultatBidragsberegningBarnDto.ResultatBarnebidragsberegningPeriodeDto
import no.nav.bidrag.transport.notat.NotatResultatBidragsberegningBarnDto.ResultatBarnebidragsberegningPeriodeDto.BidragPeriodeBeregningsdetaljer
import no.nav.bidrag.transport.notat.NotatResultatBidragsberegningBarnDto.ResultatBarnebidragsberegningPeriodeDto.BidragPeriodeBeregningsdetaljer.NotatBeregningsdetaljerSamværsfradrag
import no.nav.bidrag.transport.notat.NotatResultatForskuddBeregningBarnDto
import no.nav.bidrag.transport.notat.NotatResultatSærbidragsberegningDto
import no.nav.bidrag.transport.notat.NotatSamværDto
import no.nav.bidrag.transport.notat.NotatSivilstand
import no.nav.bidrag.transport.notat.NotatSærbidragKategoriDto
import no.nav.bidrag.transport.notat.NotatSærbidragUtgifterDto
import no.nav.bidrag.transport.notat.NotatTotalBeregningUtgifterDto
import no.nav.bidrag.transport.notat.NotatUnderholdBarnDto
import no.nav.bidrag.transport.notat.NotatUnderholdBarnDto.NotatFaktiskTilsynsutgiftDto
import no.nav.bidrag.transport.notat.NotatUnderholdBarnDto.NotatTilleggsstønadDto
import no.nav.bidrag.transport.notat.NotatUnderholdDto
import no.nav.bidrag.transport.notat.NotatUtgiftBeregningDto
import no.nav.bidrag.transport.notat.NotatUtgiftspostDto
import no.nav.bidrag.transport.notat.NotatVedtakDetaljerDto
import no.nav.bidrag.transport.notat.NotatVirkningstidspunktDto
import no.nav.bidrag.transport.notat.OpplysningerBruktTilBeregning
import no.nav.bidrag.transport.notat.OpplysningerFraFolkeregisteret
import no.nav.bidrag.transport.notat.VedtakNotatDto
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

private val log = KotlinLogging.logger {}

@Service
class NotatOpplysningerService(
    private val behandlingService: BehandlingService,
    private val beregningService: BeregningService,
    private val bidragDokumentProduksjonConsumer: BidragDokumentProduksjonConsumer,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val mapper: Dtomapper,
) {
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0),
    )
    @Transactional
    fun opprettNotat(
        behandlingId: Long,
        oppdaterDatoDokument: Boolean = false,
    ): String {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val notatDto = hentNotatOpplysninger(behandlingId)
        val notatPdf = bidragDokumentProduksjonConsumer.opprettNotat(notatDto)
        log.info { "Oppretter notat for behandling $behandlingId i sak ${behandling.saksnummer}" }
        val forespørsel =
            OpprettJournalpostRequest(
                skalFerdigstilles = true,
                datoDokument = oppdaterDatoDokument.ifTrue { behandling.vedtakstidspunkt },
                journalposttype = JournalpostType.NOTAT,
                journalførendeEnhet = behandling.behandlerEnhet,
                tilknyttSaker = listOf(behandling.saksnummer),
                gjelderIdent = behandling.bidragsmottaker!!.ident,
                referanseId = behandling.tilReferanseId(),
                saksbehandlerIdent = behandling.vedtakFattetAv ?: TokenUtils.hentSaksbehandlerIdent(),
                dokumenter =
                    listOf(
                        OpprettDokumentDto(
                            fysiskDokument = notatPdf,
                            tittel = behandling.notatTittel(),
                        ),
                    ),
            )
        val response =
            bidragDokumentConsumer.opprettJournalpost(forespørsel)
        lagreJournalpostId(behandling, response.journalpostId)
        secureLogger.info {
            "Opprettet notat for behandling $behandlingId i sak ${behandling.saksnummer} " +
                "med journalpostId ${response.journalpostId} med forespørsel $forespørsel"
        }
        log.info {
            "Opprettet notat for behandling $behandlingId i sak ${behandling.saksnummer} " +
                "med journalpostId ${response.journalpostId}"
        }
        return response.journalpostId ?: ""
    }

    private fun lagreJournalpostId(
        behandling: Behandling,
        journalpostId: String?,
    ) {
        behandling.notatJournalpostId = journalpostId
    }

    fun hentNotatOpplysninger(behandlingId: Long): VedtakNotatDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        return hentNotatOpplysningerForBehandling(behandling)
    }

    fun hentNotatOpplysningerForBehandling(behandling: Behandling): VedtakNotatDto {
        val opplysningerBoforhold =
            behandling.grunnlag
                .hentSisteAktiv()
                .hentAlleBearbeidaBoforhold(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.husstandsmedlem,
                    Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(behandling)!!,
                )

        val opplysningerSivilstand =
            behandling.grunnlag
                .hentSisteAktiv()
                .find { it.rolle.id == behandling.bidragsmottaker!!.id && it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }
                ?.konvertereData<List<SivilstandGrunnlagDto>>()
                ?.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(behandling.virkningstidspunktEllerSøktFomDato)
                ?: emptyList()

        val alleArbeidsforhold: List<ArbeidsforholdGrunnlagDto> =
            behandling.grunnlag
                .hentSisteAktiv()
                .filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }
                .flatMap { r ->
                    r.konvertereData<List<ArbeidsforholdGrunnlagDto>>() ?: emptyList()
                }

        return VedtakNotatDto(
            saksnummer = behandling.saksnummer,
            medInnkreving = behandling.innkrevingstype == Innkrevingstype.MED_INNKREVING,
            type =
                when (behandling.tilType()) {
                    TypeBehandling.FORSKUDD -> NotatMalType.FORSKUDD
                    TypeBehandling.SÆRBIDRAG -> NotatMalType.SÆRBIDRAG
                    TypeBehandling.BIDRAG -> NotatMalType.BIDRAG
                },
            behandling = behandling.tilNotatBehandlingDetaljer(),
            saksbehandlerNavn =
                TokenUtils
                    .hentSaksbehandlerIdent()
                    ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) },
            virkningstidspunkt = behandling.tilVirkningstidspunkt(),
            utgift = mapper.run { behandling.tilUtgiftDto()?.tilNotatUtgiftDto(behandling) },
            samvær = mapper.run { behandling.tilSamværDto() }?.tilNotatSamværDto(behandling) ?: emptyList(),
            underholdskostnader =
                NotatUnderholdDto(
                    offentligeOpplysninger = behandling.tilUnderholdOpplysning(),
                    offentligeOpplysningerV2 = behandling.tilUnderholdOpplysningV2(),
                    underholdskostnaderBarn =
                        mapper.run { behandling.underholdskostnader.tilDtos() }.map {
                            NotatUnderholdBarnDto(
                                gjelderBarn = it.gjelderBarn.tilNotatRolle(behandling),
                                harTilsynsordning = it.harTilsynsordning,
                                begrunnelse = NotatBegrunnelseDto(it.begrunnelse),
                                stønadTilBarnetilsyn =
                                    it.stønadTilBarnetilsyn.map {
                                        NotatUnderholdBarnDto.NotatStønadTilBarnetilsynDto(
                                            periode = DatoperiodeDto(it.periode.fom, it.periode.tom),
                                            skolealder = it.skolealder ?: Skolealder.IKKE_ANGITT,
                                            tilsynstype = it.tilsynstype ?: Tilsynstype.IKKE_ANGITT,
                                            kilde = it.kilde,
                                        )
                                    },
                                tilleggsstønad =
                                    it.tilleggsstønad.map {
                                        NotatTilleggsstønadDto(
                                            periode = DatoperiodeDto(it.periode.fom, it.periode.tom),
                                            dagsats = it.dagsats,
                                            total = it.total,
                                        )
                                    },
                                underholdskostnad =
                                    it.underholdskostnad.map {
                                        NotatUnderholdBarnDto.NotatUnderholdskostnadBeregningDto(
                                            periode = DatoperiodeDto(it.periode.fom, it.periode.tom),
                                            forbruk = it.forbruk,
                                            boutgifter = it.boutgifter,
                                            stønadTilBarnetilsyn = it.stønadTilBarnetilsyn,
                                            tilsynsutgifter = it.tilsynsutgifter,
                                            barnetrygd = it.barnetrygd,
                                            total = it.total,
                                            beregningsdetaljer =
                                                it.beregningsdetaljer?.let {
                                                    NotatUnderholdBarnDto.NotatUnderholdskostnadPeriodeBeregningsdetaljer(
                                                        tilsynsutgifterBarn =
                                                            it.tilsynsutgifterBarn.map {
                                                                NotatUnderholdBarnDto.NotatTilsynsutgiftBarn(
                                                                    gjelderBarn = it.gjelderBarn.tilNotatRolle(behandling),
                                                                    totalTilsynsutgift = it.totalTilsynsutgift,
                                                                    beløp = it.beløp,
                                                                    kostpenger = it.kostpenger,
                                                                    tilleggsstønad = it.tilleggsstønad,
                                                                )
                                                            },
                                                        justertBruttoTilsynsutgift = it.justertBruttoTilsynsutgift,
                                                        erBegrensetAvMaksTilsyn = it.erBegrensetAvMaksTilsyn,
                                                        bruttoTilsynsutgift = it.bruttoTilsynsutgift,
                                                        fordelingFaktor = it.fordelingFaktor,
                                                        maksfradragAndel = it.maksfradragAndel,
                                                        skattefradrag = it.skattefradrag,
                                                        skattefradragMaksFradrag = it.skattefradragMaksFradrag,
                                                        skattefradragPerBarn = it.skattefradragPerBarn,
                                                        skattefradragTotalTilsynsutgift = it.skattefradragTotalTilsynsutgift,
                                                        skattesatsFaktor = it.skattesatsFaktor,
                                                        sumTilsynsutgifter = it.sumTilsynsutgifter,
                                                        totalTilsynsutgift = it.totalTilsynsutgift,
                                                        antallBarnBMUnderTolvÅr = it.antallBarnBMUnderTolvÅr,
                                                        antallBarnBMBeregnet = it.antallBarnBMBeregnet,
                                                        nettoTilsynsutgift = it.nettoTilsynsutgift,
                                                        sjablonMaksFradrag = it.sjablonMaksFradrag,
                                                        sjablonMaksTilsynsutgift = it.sjablonMaksTilsynsutgift,
                                                    )
                                                },
                                        )
                                    },
                                faktiskTilsynsutgift =
                                    it.faktiskTilsynsutgift
                                        .map {
                                            NotatFaktiskTilsynsutgiftDto(
                                                periode = DatoperiodeDto(it.periode.fom, it.periode.tom),
                                                utgift = it.utgift,
                                                total = it.total,
                                                kostpenger = it.kostpenger,
                                                kommentar = it.kommentar,
                                            )
                                        },
                            )
                        },
                ),
            gebyr =
                mapper.run { behandling.mapGebyr() }?.gebyrRoller?.map {
                    NotatGebyrRolleDto(
                        rolle = it.rolle.tilNotatRolle(),
                        inntekt =
                            NotatGebyrInntektDto(
                                skattepliktigInntekt = it.inntekt.skattepliktigInntekt,
                                maksBarnetillegg = it.inntekt.maksBarnetillegg,
                            ),
                        beregnetIlagtGebyr = it.beregnetIlagtGebyr,
                        beløpGebyrsats = it.beløpGebyrsats,
                        endeligIlagtGebyr = it.endeligIlagtGebyr,
                        begrunnelse = it.begrunnelse,
                    )
                },
            boforhold =
                NotatBoforholdDto(
                    begrunnelse = behandling.tilNotatBoforhold(),
                    sivilstand = behandling.tilSivilstand(opplysningerSivilstand),
                    andreVoksneIHusstanden = mapper.tilAndreVoksneIHusstanden(behandling),
                    beregnetBoforhold = mapper.run { behandling.tilBeregnetBoforhold() },
                    barn =
                        behandling.husstandsmedlem.barn
                            .toSet()
                            .sortert()
                            .map { mapper.tilBoforholdBarn(it, opplysningerBoforhold) },
                ),
            personer = behandling.roller.map(Rolle::tilNotatRolle),
            inntekter =
                NotatInntekterDto(
                    notat = behandling.tilNotatInntekt(behandling.bidragsmottaker!!),
                    notatPerRolle = behandling.roller.map { r -> behandling.tilNotatInntekt(r) }.toSet(),
                    inntekterPerRolle =
                        behandling.roller.map { rolle ->
                            behandling.hentInntekterForIdent(
                                rolle.ident!!,
                                rolle,
                                alleArbeidsforhold.filter { rolle.ident == it.partPersonId },
                                bareMedIBeregning = true,
                            )
                        },
                    offentligeInntekterPerRolle =
                        behandling.roller.map { rolle ->
                            behandling.hentInntekterForIdent(
                                rolle.ident!!,
                                rolle,
                                alleArbeidsforhold.filter { rolle.ident == it.partPersonId },
                                filtrerBareOffentlige = true,
                            )
                        },
                ),
            vedtak = behandling.hentBeregning(),
        )
    }

    private fun Behandling.tilUnderholdOpplysningV2(): NotatOffentligeOpplysningerUnderhold {
        val opplysningerAndreBarnTilBM =
            grunnlag
                .hentSisteAktiv()
                .find { it.rolle.id == bidragsmottaker!!.id && it.type == Grunnlagsdatatype.ANDRE_BARN }
                ?.konvertereData<List<RelatertPersonGrunnlagDto>>()
                ?.filter { it.erBarnTilBMUnder12År(virkningstidspunkt!!) }
                ?: emptyList()

        val opplysningerTilleggstønad =
            grunnlag
                .hentSisteAktiv()
                .find { it.rolle.id == bidragsmottaker!!.id && it.type == Grunnlagsdatatype.TILLEGGSSTØNAD && !it.erBearbeidet }
                ?.konvertereData<List<TilleggsstønadGrunnlagDto>>()
                ?: emptyList()
        return NotatOffentligeOpplysningerUnderhold(
            tilUnderholdOpplysning(),
            opplysningerAndreBarnTilBM.map {
                val tilgangskontrollertPersoninfo =
                    mapper.tilgangskontrollerePersoninfo(
                        Personinfo(Personident(it.gjelderPersonId!!), null, it.fødselsdato),
                        Saksnummer(saksnummer),
                        true,
                    )
                NotatPersonDto(
                    navn = tilgangskontrollertPersoninfo.navn,
                    fødselsdato = tilgangskontrollertPersoninfo.fødselsdato,
                    ident = tilgangskontrollertPersoninfo.ident,
                    erBeskyttet = tilgangskontrollertPersoninfo.erBeskyttet,
                )
            },
            opplysningerTilleggstønad.find { it.partPersonId == bidragsmottaker!!.ident }?.harInnvilgetVedtak ?: false,
        )
    }

    private fun Behandling.hentBeregning(): NotatVedtakDetaljerDto {
        val resultat =
            try {
                when (tilType()) {
                    TypeBehandling.FORSKUDD ->
                        beregningService.beregneForskudd(this).tilDto().map { beregning ->
                            NotatResultatForskuddBeregningBarnDto(
                                barn = roller.find { it.ident == beregning.barn.ident!!.verdi }!!.tilNotatRolle(),
                                perioder =
                                    beregning.perioder.map {
                                        NotatResultatForskuddBeregningBarnDto.NotatResultatPeriodeDto(
                                            periode = it.periode,
                                            beløp = it.beløp,
                                            resultatKode = it.resultatKode,
                                            regel = it.regel,
                                            sivilstand = it.sivilstand,
                                            inntekt = it.inntekt,
                                            vedtakstype = vedtakstype,
                                            antallBarnIHusstanden = it.antallBarnIHusstanden,
                                        )
                                    },
                            )
                        }

                    TypeBehandling.SÆRBIDRAG ->
                        beregningService.beregneSærbidrag(this).tilDto(this).let {
                            listOf(
                                NotatResultatSærbidragsberegningDto(
                                    periode = it.periode,
                                    resultat = it.resultat,
                                    resultatKode = it.resultatKode,
                                    bpsAndel = it.bpsAndel,
                                    forskuddssats = it.forskuddssats,
                                    maksGodkjentBeløp = it.maksGodkjentBeløp,
                                    beregning =
                                        it.beregning?.let {
                                            NotatResultatSærbidragsberegningDto.UtgiftBeregningDto(
                                                beløpDirekteBetaltAvBp = it.beløpDirekteBetaltAvBp,
                                                totalBeløpBetaltAvBp = it.totalBeløpBetaltAvBp,
                                                totalGodkjentBeløp = it.totalGodkjentBeløp,
                                                totalKravbeløp = it.totalKravbeløp,
                                                totalGodkjentBeløpBp = it.totalGodkjentBeløpBp,
                                            )
                                        },
                                    inntekter =
                                        it.inntekter?.let {
                                            NotatResultatBeregningInntekterDto(
                                                inntektBM = it.inntektBM,
                                                inntektBP = it.inntektBP,
                                                inntektBarn = it.inntektBarn,
                                                barnEndeligInntekt = it.barnEndeligInntekt,
                                            )
                                        },
                                    delberegningUtgift = it.delberegningUtgift,
                                    delberegningBidragspliktigesBeregnedeTotalbidrag =
                                        it.delberegningBidragspliktigesBeregnedeTotalBidrag?.tilNotatDto(),
                                    delberegningBidragsevne =
                                        it.delberegningBidragsevne?.tilNotatDto(),
                                    antallBarnIHusstanden = it.antallBarnIHusstanden,
                                    voksenIHusstanden = it.voksenIHusstanden,
                                    enesteVoksenIHusstandenErEgetBarn = it.enesteVoksenIHusstandenErEgetBarn,
                                    erDirekteAvslag = it.erDirekteAvslag,
                                ),
                            )
                        }
                    TypeBehandling.BIDRAG ->
                        beregningService.beregneBidrag(this).tilDto().let {
                            it.resultatBarn.map { beregning ->
                                NotatResultatBidragsberegningBarnDto(
                                    barn = roller.find { it.ident == beregning.barn.ident!!.verdi }!!.tilNotatRolle(),
                                    perioder =
                                        beregning.perioder.map {
                                            ResultatBarnebidragsberegningPeriodeDto(
                                                periode = it.periode,
                                                underholdskostnad = it.underholdskostnad,
                                                bpsAndelU = it.bpsAndelU,
                                                bpsAndelBeløp = it.bpsAndelBeløp,
                                                samværsfradrag = it.samværsfradrag,
                                                beregnetBidrag = it.beregnetBidrag,
                                                faktiskBidrag = it.faktiskBidrag,
                                                resultatKode = it.resultatKode,
                                                erDirekteAvslag = it.erDirekteAvslag,
                                                beregningsdetaljer =
                                                    it.beregningsdetaljer?.let {
                                                        BidragPeriodeBeregningsdetaljer(
                                                            bpHarEvne = it.bpHarEvne,
                                                            antallBarnIHusstanden = it.antallBarnIHusstanden,
                                                            forskuddssats = it.forskuddssats,
                                                            barnetilleggBM = it.barnetilleggBM.tilNotatDto(),
                                                            barnetilleggBP = it.barnetilleggBP.tilNotatDto(),
                                                            voksenIHusstanden = it.voksenIHusstanden,
                                                            enesteVoksenIHusstandenErEgetBarn = it.enesteVoksenIHusstandenErEgetBarn,
                                                            bpsAndel = it.bpsAndel,
                                                            inntekter =
                                                                it.inntekter?.let {
                                                                    NotatResultatBeregningInntekterDto(
                                                                        inntektBM = it.inntektBM,
                                                                        inntektBP = it.inntektBP,
                                                                        inntektBarn = it.inntektBarn,
                                                                        barnEndeligInntekt = it.barnEndeligInntekt,
                                                                    )
                                                                },
                                                            delberegningBidragsevne =
                                                                it.delberegningBidragsevne?.tilNotatDto(),
                                                            samværsfradrag =
                                                                it.samværsfradrag?.let {
                                                                    NotatBeregningsdetaljerSamværsfradrag(
                                                                        samværsfradrag = it.samværsfradrag,
                                                                        samværsklasse = it.samværsklasse,
                                                                        gjennomsnittligSamværPerMåned = it.gjennomsnittligSamværPerMåned,
                                                                    )
                                                                },
                                                            sluttberegning = it.sluttberegning,
                                                            delberegningUnderholdskostnad = it.delberegningUnderholdskostnad,
                                                            delberegningBidragspliktigesBeregnedeTotalBidrag =
                                                                it.delberegningBidragspliktigesBeregnedeTotalBidrag
                                                                    ?.tilNotatDto(),
                                                        )
                                                    },
                                            )
                                        },
                                )
                            }
                        }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        return NotatVedtakDetaljerDto(
            erFattet = erVedtakFattet,
            fattetTidspunkt = vedtakstidspunkt,
            fattetAvSaksbehandler = vedtakFattetAv?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) },
            resultat = resultat,
        )
    }
}

private fun DelberegningBarnetilleggDto.tilNotatDto() =
    NotatDelberegningBarnetilleggDto(
        barnetillegg =
            barnetillegg.map {
                NotatDelberegningBarnetilleggDto.NotatBarnetilleggDetaljerDto(
                    bruttoBeløp = it.bruttoBeløp,
                    nettoBeløp = it.nettoBeløp,
                    visningsnavn = it.visningsnavn,
                )
            },
        sumNettoBeløp = sumNettoBeløp,
        sumBruttoBeløp = sumBruttoBeløp,
        skattFaktor = skattFaktor,
        delberegningSkattesats = delberegningSkattesats,
    )

private fun Behandling.tilNotatBoforhold(): NotatBegrunnelseDto =
    NotatBegrunnelseDto(
        innhold = henteNotatinnhold(this, NotatType.BOFORHOLD),
        gjelder = Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(this)!!.tilNotatRolle(),
    )

private fun Behandling.tilNotatVirkningstidspunkt() =
    NotatBegrunnelseDto(
        innhold = henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT),
        gjelder = this.bidragsmottaker!!.tilNotatRolle(),
    )

private fun Behandling.tilNotatInntekt(rolle: Rolle): NotatBegrunnelseDto =
    NotatBegrunnelseDto(
        innhold = henteInntektsnotat(this, rolle.id!!),
        gjelder = rolle.tilNotatRolle(),
    )

private fun Behandling.tilSivilstand(sivilstandOpplysninger: List<SivilstandGrunnlagDto>) =
    NotatSivilstand(
        opplysningerBruktTilBeregning =
            sivilstand
                .sortedBy { it.datoFom }
                .map(Sivilstand::tilSivilstandsperiode),
        opplysningerFraFolkeregisteret =
            sivilstandOpplysninger
                .map { periode ->
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
                datoFom,
                datoTom,
            ),
        status = sivilstand,
        kilde = kilde,
    )

private fun SærbidragUtgifterDto.tilNotatUtgiftDto(behandling: Behandling) =
    NotatSærbidragUtgifterDto(
        beregning = beregning?.tilNotatBeregningDto(),
        begrunnelse =
            NotatBegrunnelseDto(
                innhold = begrunnelse.innhold,
                gjelder = behandling.bidragsmottaker!!.tilNotatRolle(),
            ),
        utgifter = utgifter.map { it.tilNotatDto() },
        maksGodkjentBeløp =
            maksGodkjentBeløp?.let {
                NotatMaksGodkjentBeløpDto(
                    taMed = it.taMed,
                    beløp = it.beløp,
                    begrunnelse = it.begrunnelse,
                )
            },
        totalBeregning = totalBeregning.map { it.tilNotatDto() },
    )

private fun DelberegningBidragsevneDto.tilNotatDto() =
    NotatDelberegningBidragsevneDto(
        sumInntekt25Prosent = sumInntekt25Prosent,
        bidragsevne = bidragsevne,
        utgifter =
            NotatDelberegningBidragsevneDto.NotatBidragsevneUtgifterBolig(
                boutgiftBeløp = utgifter.boutgiftBeløp,
                borMedAndreVoksne = utgifter.borMedAndreVoksne,
                underholdBeløp = utgifter.underholdBeløp,
            ),
        skatt =
            NotatDelberegningBidragsevneDto.NotatSkattBeregning(
                skattAlminneligInntekt = skatt.skattAlminneligInntekt,
                sumSkatt = skatt.sumSkatt,
                trinnskatt = skatt.trinnskatt,
                trygdeavgift = skatt.trygdeavgift,
            ),
        underholdEgneBarnIHusstand =
            NotatDelberegningBidragsevneDto.NotatUnderholdEgneBarnIHusstand(
                antallBarnIHusstanden = underholdEgneBarnIHusstand.antallBarnIHusstanden,
                årsbeløp = underholdEgneBarnIHusstand.årsbeløp,
                sjablon = underholdEgneBarnIHusstand.sjablon,
            ),
    )

private fun Behandling.tilUnderholdOpplysning(): List<NotatOffentligeOpplysningerUnderholdBarn> {
    val opplysningerBarnetilsyn =
        grunnlag
            .hentSisteAktiv()
            .find { it.rolle.id == bidragsmottaker!!.id && it.type == Grunnlagsdatatype.BARNETILSYN && !it.erBearbeidet }
            ?.konvertereData<List<BarnetilsynGrunnlagDto>>()
            ?: emptyList()

    val opplysningerTilleggstønad =
        grunnlag
            .hentSisteAktiv()
            .find { it.rolle.id == bidragsmottaker!!.id && it.type == Grunnlagsdatatype.TILLEGGSSTØNAD && !it.erBearbeidet }
            ?.konvertereData<List<TilleggsstønadGrunnlagDto>>()
            ?: emptyList()
    return søknadsbarn.map { rolle ->
        NotatOffentligeOpplysningerUnderholdBarn(
            gjelder = rolle.behandling.bidragsmottaker!!.tilNotatRolle(),
            gjelderBarn = rolle.tilNotatRolle(),
            barnetilsyn =
                opplysningerBarnetilsyn.filter { it.barnPersonId == rolle.ident }.map {
                    NotatBarnetilsynOffentligeOpplysninger(
                        periode = ÅrMånedsperiode(it.periodeFra, it.periodeTil),
                    )
                },
            harTilleggsstønad =
                opplysningerTilleggstønad.find { it.partPersonId == rolle.ident }?.harInnvilgetVedtak ?: false,
        )
    }
}

private fun TotalBeregningUtgifterDto.tilNotatDto() =
    NotatTotalBeregningUtgifterDto(
        betaltAvBp,
        utgiftstype,
        totalKravbeløp,
        totalGodkjentBeløp,
    )

private fun UtgiftspostDto.tilNotatDto() =
    NotatUtgiftspostDto(
        begrunnelse = begrunnelse,
        dato = dato,
        type = type,
        kravbeløp = kravbeløp,
        godkjentBeløp = godkjentBeløp,
        betaltAvBp = betaltAvBp,
    )

private fun UtgiftBeregningDto.tilNotatBeregningDto() =
    NotatUtgiftBeregningDto(
        beløpDirekteBetaltAvBp = beløpDirekteBetaltAvBp,
        totalBeløpBetaltAvBp = totalBeløpBetaltAvBp,
        totalGodkjentBeløp = totalGodkjentBeløp,
        totalKravbeløp = totalKravbeløp,
        totalGodkjentBeløpBp = totalGodkjentBeløpBp,
    )

private fun SærbidragKategoriDto.tilNotatSærbidragKategoriDto() =
    NotatSærbidragKategoriDto(
        kategori = kategori,
        beskrivelse = beskrivelse,
    )

private fun Behandling.tilNotatBehandlingDetaljer() =
    NotatBehandlingDetaljerDto(
        søknadstype = vedtakstype.name,
        vedtakstype = vedtakstype,
        opprinneligVedtakstype = opprinneligVedtakstype,
        søktAv = soknadFra,
        mottattDato = mottattdato,
        klageMottattDato = klageMottattdato,
        søktFraDato = YearMonth.from(søktFomDato),
        virkningstidspunkt = virkningstidspunkt,
        avslag = avslag,
        kategori = tilSærbidragKategoriDto().tilNotatSærbidragKategoriDto(),
    )

private fun Behandling.tilVirkningstidspunkt() =
    NotatVirkningstidspunktDto(
        søknadstype = vedtakstype.name,
        vedtakstype = vedtakstype,
        søktAv = soknadFra,
        avslag = avslag,
        årsak = årsak,
        mottattDato = mottattdato,
        søktFraDato = YearMonth.from(søktFomDato),
        virkningstidspunkt = virkningstidspunkt,
        begrunnelse = tilNotatVirkningstidspunkt(),
    )

private fun RolleDto.tilNotatRolle() =
    NotatPersonDto(
        rolle = rolletype,
        navn = navn,
        fødselsdato = fødselsdato,
        ident = ident?.let { Personident(ident) },
    )

private fun PersoninfoDto.tilNotatRolle(behandling: Behandling) =
    NotatPersonDto(
        rolle = if (medIBehandlingen == true) behandling.roller.find { it.ident == ident?.verdi }?.rolletype else null,
        navn = ident?.let { hentPersonVisningsnavn(it.verdi) } ?: navn,
        fødselsdato = fødselsdato,
        ident = ident,
    )

private fun Rolle.tilNotatRolle() =
    NotatPersonDto(
        rolle = rolletype,
        navn = hentPersonVisningsnavn(ident),
        fødselsdato = fødselsdato,
        ident = ident?.let { Personident(it) },
        innbetaltBeløp = innbetaltBeløp,
    )

private fun Inntekt.tilNotatInntektDto() =
    NotatInntektDto(
        beløp =
            maxOf(
                belop.nærmesteHeltall,
                BigDecimal.ZERO,
            ),
        // Kapitalinntekt kan ha negativ verdi. Dette skal ikke vises i frontend
        periode = periode,
        opprinneligPeriode =
            opprinneligFom?.let {
                ÅrMånedsperiode(
                    it,
                    bestemOpprinneligTomVisningsverdi(),
                )
            },
        type = type,
        kilde = kilde,
        medIBeregning = taMed,
        gjelderBarn =
            gjelderBarn
                ?.let { gjelderBarn ->
                    behandling?.roller?.find { it.ident == gjelderBarn }
                }?.tilNotatRolle(),
        historisk = erHistorisk(behandling!!.inntekter),
        inntektsposter =
            inntektsposter
                .map {
                    NotatInntektspostDto(
                        it.kode,
                        it.inntektstype,
                        InntektUtil.kapitalinntektFaktor(it.kode) * it.beløp.nærmesteHeltall,
                        visningsnavn = it.inntektstype?.visningsnavn?.intern ?: finnVisningsnavn(it.kode),
                    )
                }.sortedByDescending { it.beløp },
    )

private fun List<Inntekt>.inntekterForIdent(ident: String) = filter { it.ident == ident }

private fun List<Inntekt>.filtrerKilde(filtrerBareOffentlige: Boolean = false) =
    filter { !filtrerBareOffentlige || it.kilde == Kilde.OFFENTLIG }

private fun List<SamværDto>.tilNotatSamværDto(behandling: Behandling) =
    map { samvær ->
        val gjelderBarn = behandling.søknadsbarn.find { it.ident == samvær.gjelderBarn }!!
        NotatSamværDto(
            gjelderBarn = gjelderBarn.tilNotatRolle(),
            perioder =
                samvær.perioder.map {
                    NotatSamværDto.NotatSamværsperiodeDto(
                        periode = DatoperiodeDto(it.periode.fom, it.periode.tom),
                        samværsklasse = it.samværsklasse,
                        gjennomsnittligSamværPerMåned = it.gjennomsnittligSamværPerMåned,
                        beregning = it.beregning,
                    )
                },
            begrunnelse =
                NotatBegrunnelseDto(
                    innhold = samvær.begrunnelse?.innhold,
                    gjelder = gjelderBarn.tilNotatRolle(),
                ),
        )
    }

private fun Behandling.hentInntekterForIdent(
    ident: String,
    rolle: Rolle,
    arbeidsforhold: List<ArbeidsforholdGrunnlagDto>,
    filtrerBareOffentlige: Boolean = false,
    bareMedIBeregning: Boolean = false,
) = InntekterPerRolle(
    gjelder = rolle.tilNotatRolle(),
    beregnetInntekter =
        if (filtrerBareOffentlige) {
            emptyList()
        } else {
            hentBeregnetInntekterForRolle(rolle)
                .filter { it.inntektGjelderBarnIdent != null }
                .map { inntektPerBarn ->
                    NotatBeregnetInntektDto(
                        roller.find { it.ident == inntektPerBarn.inntektGjelderBarnIdent!!.verdi }!!.tilNotatRolle(),
                        inntektPerBarn.summertInntektListe,
                    )
                }
        },
    årsinntekter =
        inntekter
            .årsinntekterSortert(!filtrerBareOffentlige, true)
            .inntekterForIdent(ident)
            .ekskluderYtelserFørVirkningstidspunkt()
            .filtrerKilde(filtrerBareOffentlige)
            .filter { !bareMedIBeregning || it.taMed }
            .map {
                it.tilNotatInntektDto()
            },
    barnetillegg =
        inntekter
            .filter { it.type == Inntektsrapportering.BARNETILLEGG }
            .inntekterForIdent(ident)
            .filtrerKilde(filtrerBareOffentlige)
            .ekskluderYtelserFørVirkningstidspunkt()
            .sorterEtterDatoOgBarn()
            .filter { !bareMedIBeregning || it.taMed }
            .map {
                it.tilNotatInntektDto()
            },
    småbarnstillegg =
        inntekter
            .sortedBy { it.datoFom }
            .filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
            .inntekterForIdent(ident)
            .filtrerKilde(filtrerBareOffentlige)
            .filter { !bareMedIBeregning || it.taMed }
            .ekskluderYtelserFørVirkningstidspunkt()
            .sorterEtterDato()
            .map {
                it.tilNotatInntektDto()
            },
    kontantstøtte =
        inntekter
            .filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
            .inntekterForIdent(ident)
            .filtrerKilde(filtrerBareOffentlige)
            .filter { !bareMedIBeregning || it.taMed }
            .ekskluderYtelserFørVirkningstidspunkt()
            .sorterEtterDatoOgBarn()
            .map {
                it.tilNotatInntektDto()
            },
    utvidetBarnetrygd =
        inntekter
            .filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
            .inntekterForIdent(ident)
            .filtrerKilde(filtrerBareOffentlige)
            .filter { !bareMedIBeregning || it.taMed }
            .ekskluderYtelserFørVirkningstidspunkt()
            .sorterEtterDato()
            .map {
                it.tilNotatInntektDto()
            },
    arbeidsforhold =
        arbeidsforhold
            .filter { it.partPersonId == ident }
            .map {
                Arbeidsforhold(
                    periode = ÅrMånedsperiode(it.startdato!!, it.sluttdato),
                    arbeidsgiver = it.arbeidsgiverNavn ?: "-",
                    stillingProsent =
                        it.ansettelsesdetaljerListe
                            ?.firstOrNull()
                            ?.avtaltStillingsprosent
                            ?.toString(),
                    lønnsendringDato = it.ansettelsesdetaljerListe?.firstOrNull()?.sisteLønnsendringDato,
                )
            },
)

private fun DelberegningBidragspliktigesBeregnedeTotalbidragDto.tilNotatDto() =
    NotatDelberegningBidragspliktigesBeregnedeTotalbidragDto(
        bidragspliktigesBeregnedeTotalbidrag = bidragspliktigesBeregnedeTotalbidrag,
        periode = periode,
        beregnetBidragPerBarnListe =
            beregnetBidragPerBarnListe.map {
                NotatDelberegningBidragspliktigesBeregnedeTotalbidragDto
                    .NotatBeregnetBidragPerBarnDto(
                        beregnetBidragPerBarn = it.beregnetBidragPerBarn,
                        personidentBarn = it.personidentBarn,
                    )
            },
    )
