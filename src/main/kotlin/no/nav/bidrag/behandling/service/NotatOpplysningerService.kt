package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragDokumentConsumer
import no.nav.bidrag.behandling.consumer.BidragDokumentProduksjonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
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
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.behandling.filtrerSivilstandPerioderEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.behandling.hentAlleBearbeidetBoforhold
import no.nav.bidrag.behandling.transformers.behandling.hentBeregnetInntekter
import no.nav.bidrag.behandling.transformers.behandling.notatTittel
import no.nav.bidrag.behandling.transformers.behandling.tilReferanseId
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.sorterEtterDato
import no.nav.bidrag.behandling.transformers.sorterEtterDatoOgBarn
import no.nav.bidrag.behandling.transformers.sortert
import no.nav.bidrag.behandling.transformers.tilDto
import no.nav.bidrag.behandling.transformers.årsinntekterSortert
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.dokument.JournalpostType
import no.nav.bidrag.transport.dokument.OpprettDokumentDto
import no.nav.bidrag.transport.dokument.OpprettJournalpostRequest
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
) {
    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    @Transactional
    fun opprettNotat(behandlingId: Long) {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val notatDto = hentNotatOpplysninger(behandlingId)
        val notatPdf = bidragDokumentProduksjonConsumer.opprettNotat(notatDto)
        log.info { "Oppretter notat for behandling $behandlingId i sak ${behandling.saksnummer}" }
        val forespørsel =
            OpprettJournalpostRequest(
                skalFerdigstilles = true,
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
    }

    private fun lagreJournalpostId(
        behandling: Behandling,
        journalpostId: String?,
    ) {
        behandling.notatJournalpostId = journalpostId
    }

    fun hentNotatOpplysninger(behandlingId: Long): NotatDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        val opplysningerBoforhold =
            behandling.grunnlagListe.hentSisteAktiv()
                .hentAlleBearbeidetBoforhold(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.husstandsbarn,
                    behandling.bidragsmottaker,
                )

        val opplysningerSivilstand =
            behandling.grunnlagListe.hentSisteAktiv()
                .find { it.rolle.id == behandling.bidragsmottaker!!.id && it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }
                ?.konverterData<List<SivilstandGrunnlagDto>>()
                ?.filtrerSivilstandPerioderEtterVirkningstidspunkt(behandling.virkningstidspunktEllerSøktFomDato)
                ?: emptyList()

        val alleArbeidsforhold: List<ArbeidsforholdGrunnlagDto> =
            behandling.grunnlagListe.hentSisteAktiv()
                .filter { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }.flatMap { r ->
                    r.konverterData<List<ArbeidsforholdGrunnlagDto>>() ?: emptyList()
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
                        behandling.husstandsbarn.sortert()
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
                resultat.map { beregning ->
                    NotatResultatBeregningBarnDto(
                        barn = roller.find { it.ident == beregning.barn.ident!!.verdi }!!.tilNotatRolle(),
                        perioder =
                            beregning.perioder.map {
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
        kilde = kilde,
    )

private fun Behandling.tilVirkningstidspunkt() =
    Virkningstidspunkt(
        søknadstype = vedtakstype.name,
        vedtakstype = vedtakstype,
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
        kilde = kilde,
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
                    kilde = periode.kilde,
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
        beløp = maxOf(belop.nærmesteHeltall, BigDecimal.ZERO), // Kapitalinntekt kan ha negativ verdi. Dette skal ikke vises i frontend
        periode = periode,
        opprinneligPeriode = opprinneligPeriode,
        type = type,
        kilde = kilde,
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
                    it.beløp.nærmesteHeltall,
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
        inntekter.årsinntekterSortert(false)
            .filter { it.ident == ident }
            .map {
                it.tilNotatInntektDto()
            },
    barnetillegg =
        if (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER) {
            inntekter
                .filter { it.type == Inntektsrapportering.BARNETILLEGG }
                .sorterEtterDatoOgBarn()
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
                .sorterEtterDato()
                .map {
                    it.tilNotatInntektDto()
                }
        } else {
            emptyList()
        },
    kontantstøtte =
        if (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER) {
            inntekter.filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
                .sorterEtterDatoOgBarn()
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
