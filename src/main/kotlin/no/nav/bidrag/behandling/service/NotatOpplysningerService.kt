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
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragKategoriDto
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragUtgifterDto
import no.nav.bidrag.behandling.dto.v2.behandling.TotalBeregningUtgifterDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.service.NotatService.Companion.henteInntektsnotat
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.behandling.filtrerSivilstandGrunnlagEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.behandling.hentAlleBearbeidaBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentBeregnetInntekterForRolle
import no.nav.bidrag.behandling.transformers.behandling.notatTittel
import no.nav.bidrag.behandling.transformers.behandling.tilReferanseId
import no.nav.bidrag.behandling.transformers.ekskluderYtelserFørVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.erHistorisk
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
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.inntekt.util.InntektUtil
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
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
import no.nav.bidrag.transport.notat.NotatInntektDto
import no.nav.bidrag.transport.notat.NotatInntekterDto
import no.nav.bidrag.transport.notat.NotatInntektspostDto
import no.nav.bidrag.transport.notat.NotatMaksGodkjentBeløpDto
import no.nav.bidrag.transport.notat.NotatMalType
import no.nav.bidrag.transport.notat.NotatResultatForskuddBeregningBarnDto
import no.nav.bidrag.transport.notat.NotatResultatSærbidragsberegningDto
import no.nav.bidrag.transport.notat.NotatRolleDto
import no.nav.bidrag.transport.notat.NotatSivilstand
import no.nav.bidrag.transport.notat.NotatSærbidragKategoriDto
import no.nav.bidrag.transport.notat.NotatSærbidragUtgifterDto
import no.nav.bidrag.transport.notat.NotatTotalBeregningUtgifterDto
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
                    behandling.rolleGrunnlagSkalHentesFor!!,
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
            boforhold =
                NotatBoforholdDto(
                    begrunnelse = behandling.tilNotatBoforhold(),
                    sivilstand = behandling.tilSivilstand(opplysningerSivilstand),
                    andreVoksneIHusstanden = mapper.tilAndreVoksneIHusstanden(behandling),
                    barn =
                        behandling.husstandsmedlem.barn
                            .toSet()
                            .sortert()
                            .map { mapper.tilBoforholdBarn(it, opplysningerBoforhold) },
                ),
            roller = behandling.roller.map(Rolle::tilNotatRolle),
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
                                            NotatResultatSærbidragsberegningDto.ResultatSærbidragsberegningInntekterDto(
                                                inntektBM = it.inntektBM,
                                                inntektBP = it.inntektBP,
                                                inntektBarn = it.inntektBarn,
                                            )
                                        },
                                    delberegningUtgift = it.delberegningUtgift,
                                    antallBarnIHusstanden = it.antallBarnIHusstanden,
                                    voksenIHusstanden = it.voksenIHusstanden,
                                    enesteVoksenIHusstandenErEgetBarn = it.enesteVoksenIHusstandenErEgetBarn,
                                    erDirekteAvslag = it.erDirekteAvslag,
                                ),
                            )
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

private fun Behandling.tilNotatBoforhold(): NotatBegrunnelseDto =
    NotatBegrunnelseDto(
        innhold = henteNotatinnhold(this, NotatType.BOFORHOLD),
        gjelder = this.rolleGrunnlagSkalHentesFor!!.tilNotatRolle(),
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

private fun Rolle.tilNotatRolle() =
    NotatRolleDto(
        rolle = rolletype,
        navn = hentPersonVisningsnavn(ident),
        fødselsdato = fødselsdato,
        ident = ident?.let { Personident(it) },
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
