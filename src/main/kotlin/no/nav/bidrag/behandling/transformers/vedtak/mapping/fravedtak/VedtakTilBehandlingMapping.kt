package no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingMetadataDo
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtalePeriode
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.json.Klagedetaljer
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.behandling.LesemodusVedtak
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilPerson
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.service.hentVedtak
import no.nav.bidrag.behandling.transformers.behandling.tilNotat
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.beregning.erAvslagSomInneholderUtgifter
import no.nav.bidrag.behandling.transformers.byggResultatSærbidragsberegning
import no.nav.bidrag.behandling.transformers.erAldersjusteringNyLøsning
import no.nav.bidrag.behandling.transformers.erUnder12År
import no.nav.bidrag.behandling.transformers.finnAldersjusteringDetaljerGrunnlag
import no.nav.bidrag.behandling.transformers.sorter
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.BarnetilsynMedStønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.FaktiskUtgiftPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetAndreBarnTilBidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtaleGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtalePeriodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilleggsstønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.særbidragskategori
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftDirekteBetalt
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftMaksGodkjentBeløp
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftsposter
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.behandlingId
import no.nav.bidrag.transport.behandling.vedtak.response.finnSistePeriode
import no.nav.bidrag.transport.behandling.vedtak.response.saksnummer
import no.nav.bidrag.transport.behandling.vedtak.response.søknadId
import no.nav.bidrag.transport.behandling.vedtak.response.typeBehandling
import no.nav.bidrag.transport.behandling.vedtak.response.virkningstidspunkt
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

@Component
class VedtakTilBehandlingMapping(
    val validerBeregning: ValiderBeregning,
    private val underholdService: UnderholdService,
    private val personRepository: PersonRepository,
    private val behandlingRepository: BehandlingRepository,
) {
    fun VedtakDto.tilBehandling(
        vedtakId: Int,
        påklagetVedtak: Int = vedtakId,
        lesemodus: Boolean = true,
        vedtakType: Vedtakstype? = null,
        mottattdato: LocalDate? = null,
        søktFomDato: LocalDate? = null,
        soknadFra: SøktAvType? = null,
        søknadRefId: Long? = null,
        søknadId: Long? = null,
        enhet: String? = null,
        opprinneligVedtakstidspunkt: Set<LocalDateTime> = emptySet(),
        opprinneligVedtakstype: Vedtakstype? = null,
        søknadstype: BisysSøknadstype? = null,
        erBisysVedtak: Boolean = false,
        erOrkestrertVedtak: Boolean = false,
    ): Behandling {
        val opprettetAv =
            if (lesemodus) {
                this.opprettetAv
            } else {
                TokenUtils.hentSaksbehandlerIdent()
                    ?: TokenUtils.hentApplikasjonsnavn()!!
            }
        val opprettetAvNavn =
            if (lesemodus) {
                this.opprettetAvNavn
            } else {
                TokenUtils
                    .hentSaksbehandlerIdent()
                    ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
            }
        // TODO: Hvordan håndteres dette når vi begynner med flere stønadsendringer i samme vedtak?
        val stønadsendringstype = stønadsendringListe.firstOrNull()?.type
        val innkrevingstype =
            this.stønadsendringListe.firstOrNull()?.innkreving
                ?: this.engangsbeløpListe.firstOrNull()?.innkreving
                ?: Innkrevingstype.MED_INNKREVING
        val virkningstidspunkt = virkningstidspunkt ?: hentSøknad().søktFraDato
        val behandling =
            Behandling(
                id = if (lesemodus) 1 else null,
                søknadstype = søknadstype,
                vedtakstype = vedtakType ?: type,
                virkningstidspunkt = virkningstidspunkt,
                kategori = grunnlagListe.særbidragskategori?.kategori?.name,
                kategoriBeskrivelse = grunnlagListe.særbidragskategori?.beskrivelse,
                innkrevingstype = innkrevingstype,
                årsak = hentVirkningstidspunkt()?.årsak,
                avslag = avslagskode(),
                søktFomDato = søktFomDato ?: hentSøknad().søktFraDato,
                soknadFra = soknadFra ?: hentSøknad().søktAv,
                mottattdato =
                    when (typeBehandling) {
                        TypeBehandling.SÆRBIDRAG -> hentSøknad().mottattDato
                        else -> mottattdato ?: hentSøknad().mottattDato
                    },
                // TODO: Er dette riktig? Hva skjer hvis det finnes flere stønadsendringer/engangsbeløp? Fungerer for Forskudd men todo fram fremtiden
                stonadstype = stønadsendringstype,
                engangsbeloptype = if (stønadsendringstype == null) engangsbeløpListe.firstOrNull()?.type else null,
                vedtaksid = null,
                behandlerEnhet = enhet ?: enhetsnummer?.verdi!!,
                opprettetAv = opprettetAv,
                opprettetAvNavn = opprettetAvNavn,
                kildeapplikasjon = if (lesemodus) kildeapplikasjon else TokenUtils.hentApplikasjonsnavn()!!,
                saksnummer = saksnummer!!,
                soknadsid = søknadId ?: this.søknadId,
            )

        behandling.roller = grunnlagListe.mapRoller(behandling, lesemodus, virkningstidspunkt)

        behandling.klagedetaljer =
            Klagedetaljer(
                opprinneligVedtakstype = opprinneligVedtakstype,
                påklagetVedtak = påklagetVedtak,
                innkrevingstype = innkrevingstype,
                refVedtaksid = if (!lesemodus) vedtakId else null,
                klageMottattdato = if (!lesemodus) mottattdato else hentSøknad().klageMottattDato,
                soknadRefId = søknadRefId,
                opprinneligVirkningstidspunkt = virkningstidspunkt,
                opprinneligVedtakstidspunkt = opprinneligVedtakstidspunkt.toMutableSet(),
            )

        if (!lesemodus) {
            behandlingRepository.save(behandling)
        }
        oppdaterDirekteOppgjørBeløp(behandling, lesemodus)
        grunnlagListe.oppdaterRolleGebyr(behandling)

        behandling.inntekter = grunnlagListe.mapInntekter(behandling, lesemodus)
        behandling.husstandsmedlem = grunnlagListe.mapHusstandsmedlem(behandling, lesemodus)
        behandling.sivilstand = grunnlagListe.mapSivilstand(behandling, lesemodus)
        behandling.utgift = grunnlagListe.mapUtgifter(behandling, lesemodus)
        behandling.samvær = grunnlagListe.mapSamvær(behandling, lesemodus)
        behandling.underholdskostnader = grunnlagListe.mapUnderholdskostnad(behandling, lesemodus, virkningstidspunkt)
        behandling.privatAvtale = grunnlagListe.mapPrivatAvtale(behandling, lesemodus)
        behandling.metadata = BehandlingMetadataDo()
        if (erBisysVedtak) {
            behandling.metadata!!.setKlagePåBisysVedtak()
        }
        behandling.grunnlag =
            if (type == Vedtakstype.INDEKSREGULERING) mutableSetOf() else grunnlagListe.mapGrunnlag(behandling, lesemodus)
        if (lesemodus) {
            behandling.lesemodusVedtak =
                LesemodusVedtak(
                    erAvvist = stønadsendringListe.all { it.beslutning == Beslutningstype.AVVIST },
                    opprettetAvBatch = kilde == Vedtakskilde.AUTOMATISK,
                    erOrkestrertVedtak = erOrkestrertVedtak,
                )
            behandling.grunnlagslisteFraVedtak = grunnlagListe
            behandling.erBisysVedtak = behandlingId == null && this.søknadId != null
            behandling.erVedtakUtenBeregning =
                stønadsendringListe.all { it.periodeListe.isEmpty() || it.finnSistePeriode()?.resultatkode == "IV" }
        }

        notatMedType(NotatType.BOFORHOLD, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatType.BOFORHOLD, it, delAvBehandling = lesemodus))
        }
        notatMedType(Notattype.UTGIFTER, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatType.UTGIFTER, it, delAvBehandling = lesemodus))
        }
        notatMedType(NotatType.VIRKNINGSTIDSPUNKT, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatType.VIRKNINGSTIDSPUNKT, it, delAvBehandling = lesemodus))
        }
        behandling.roller.forEach { r ->
            notatMedType(NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(
                    behandling.tilNotat(NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG, it, r, delAvBehandling = lesemodus),
                )
            }
        }
        behandling.roller.forEach { r ->
            notatMedType(NotatType.INNTEKT, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(behandling.tilNotat(NotatType.INNTEKT, it, r, delAvBehandling = lesemodus))
            }
        }
        behandling.roller.forEach { r ->
            notatMedType(NotatType.SAMVÆR, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(behandling.tilNotat(NotatType.SAMVÆR, it, r, delAvBehandling = lesemodus))
            }
        }
        behandling.roller.forEach { r ->
            notatMedType(NotatType.PRIVAT_AVTALE, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(behandling.tilNotat(NotatType.PRIVAT_AVTALE, it, r, delAvBehandling = lesemodus))
            }
        }
        behandling.roller.forEach { r ->
            notatMedType(
                NotatType.UNDERHOLDSKOSTNAD,
                false,
                grunnlagListe.hentPerson(r.ident)?.referanse,
            )?.let {
                behandling.notater.add(behandling.tilNotat(NotatType.UNDERHOLDSKOSTNAD, it, r, delAvBehandling = lesemodus))
            }
        }

        return behandling
    }

    fun VedtakDto.tilBeregningResultatSærbidrag(): ResultatSærbidragsberegningDto? =
        engangsbeløpListe.firstOrNull()?.let { engangsbeløp ->
            val behandling = tilBehandling(1)
            grunnlagListe.byggResultatSærbidragsberegning(
                behandling.virkningstidspunkt!!,
                engangsbeløp.beløp,
                Resultatkode.fraKode(engangsbeløp.resultatkode)!!,
                engangsbeløp.grunnlagReferanseListe,
                behandling.utgift?.tilBeregningDto() ?: UtgiftBeregningDto(),
                behandling.utgift
                    ?.utgiftsposter
                    ?.sorter()
                    ?.map { it.tilDto() } ?: emptyList(),
                behandling.utgift?.maksGodkjentBeløpTaMed.ifTrue { behandling.utgift?.maksGodkjentBeløp },
            )
        }

    private fun List<GrunnlagDto>.mapUtgifter(
        behandling: Behandling,
        lesemodus: Boolean,
    ): Utgift? {
        if (behandling.tilType() !== TypeBehandling.SÆRBIDRAG ||
            validerBeregning.run { behandling.tilSærbidragAvslagskode() }?.erAvslagSomInneholderUtgifter() == false
        ) {
            return null
        }
        val utgift =
            Utgift(
                behandling,
                beløpDirekteBetaltAvBp = utgiftDirekteBetalt!!.beløpDirekteBetalt,
                maksGodkjentBeløpTaMed = utgiftMaksGodkjentBeløp != null,
                maksGodkjentBeløp = utgiftMaksGodkjentBeløp?.beløp,
                maksGodkjentBeløpBegrunnelse = utgiftMaksGodkjentBeløp?.begrunnelse,
            )
        utgift.utgiftsposter =
            utgiftsposter
                .mapIndexed { index, it ->
                    Utgiftspost(
                        id = if (lesemodus) index.toLong() else null,
                        utgift = utgift,
                        dato = it.dato,
                        type = it.type,
                        godkjentBeløp = it.godkjentBeløp,
                        kravbeløp = it.kravbeløp,
                        kommentar = it.kommentar,
                        betaltAvBp = it.betaltAvBp,
                    )
                }.toMutableSet()

        return utgift
    }

    private fun List<GrunnlagDto>.mapPrivatAvtale(
        behandling: Behandling,
        lesemodus: Boolean,
    ): MutableSet<PrivatAvtale> =
        filtrerBasertPåEgenReferanse(Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG)
            .groupBy { if (it.gjelderBarnReferanse.isNullOrEmpty()) it.gjelderReferanse else it.gjelderBarnReferanse }
            .map {
                val privatAvtaleGrunnlag =
                    filtrerOgKonverterBasertPåFremmedReferanse<PrivatAvtaleGrunnlag>(
                        Grunnlagstype.PRIVAT_AVTALE_GRUNNLAG,
                        gjelderBarnReferanse = it.key,
                    ).firstOrNull()
                val personGrunnlag = hentPersonMedReferanse(it.key)!!
                val personFraVedtak = personGrunnlag.personObjekt
                val rolleSøknadsbarn = behandling.søknadsbarn.find { it.ident == personFraVedtak.ident?.verdi }
                val privatAvtale =
                    if (lesemodus) {
                        PrivatAvtale(
                            id = 1,
                            avtaleDato = privatAvtaleGrunnlag?.innhold?.avtaleInngåttDato,
                            skalIndeksreguleres = privatAvtaleGrunnlag?.innhold?.skalIndeksreguleres ?: false,
                            avtaleType = privatAvtaleGrunnlag?.innhold?.avtaleType ?: PrivatAvtaleType.PRIVAT_AVTALE,
                            behandling = behandling,
                            person =
                                Person(
                                    id = 1,
                                    ident = personFraVedtak.ident?.verdi,
                                    fødselsdato = personFraVedtak.fødselsdato,
                                    rolle = rolleSøknadsbarn?.let { mutableSetOf(it) } ?: mutableSetOf(),
                                ),
                        )
                    } else {
                        PrivatAvtale(
                            avtaleDato = privatAvtaleGrunnlag?.innhold?.avtaleInngåttDato,
                            avtaleType = privatAvtaleGrunnlag?.innhold?.avtaleType ?: PrivatAvtaleType.PRIVAT_AVTALE,
                            skalIndeksreguleres = privatAvtaleGrunnlag?.innhold?.skalIndeksreguleres ?: false,
                            behandling = behandling,
                            person =
                                personRepository.findFirstByIdent(personFraVedtak.ident?.verdi!!) ?: run {
                                    val person =
                                        Person(
                                            ident = personFraVedtak.ident?.verdi,
                                            fødselsdato =
                                                hentPersonFødselsdato(personFraVedtak.ident?.verdi)
                                                    ?: fantIkkeFødselsdatoTilPerson(behandling.id!!),
                                            rolle = rolleSøknadsbarn?.let { mutableSetOf(it) } ?: mutableSetOf(),
                                        )
                                    person.rolle.forEach { it.person = person }
                                    personRepository.save(person)
                                },
                        )
                    }
                it.value.forEach {
                    val grunnlag = it.innholdTilObjekt<PrivatAvtalePeriodeGrunnlag>()
                    val paPeriode =
                        PrivatAvtalePeriode(
                            id = if (lesemodus) 1 else null,
                            privatAvtale = privatAvtale,
                            fom = grunnlag.periode.fom.atDay(1),
                            tom = grunnlag.periode.til?.atEndOfMonth(),
                            beløp = grunnlag.beløp,
                        )
                    privatAvtale.perioder.add(paPeriode)
                }
                privatAvtale
            }.toMutableSet()

    private fun List<GrunnlagDto>.mapUnderholdskostnad(
        behandling: Behandling,
        lesemodus: Boolean,
        virkningstidspunkt: LocalDate,
    ): MutableSet<Underholdskostnad> {
        if (behandling.tilType() != TypeBehandling.BIDRAG) return mutableSetOf()
        val underholdskostnadSøknadsbarn =
            behandling.roller
                .filter { Rolletype.BARN == it.rolletype }
                .mapIndexed { index, rolle ->
                    val underholdskostnad =
                        if (lesemodus) {
                            Underholdskostnad(
                                id = index.toLong(),
                                behandling = behandling,
                                person =
                                    Person(
                                        id = index.toLong(),
                                        ident = rolle.ident!!,
                                        fødselsdato = rolle.fødselsdato,
                                        rolle = mutableSetOf(rolle),
                                    ),
                            )
                        } else {
                            underholdService.oppretteUnderholdskostnad(
                                behandling,
                                BarnDto(personident = Personident(rolle.ident!!)),
                            )
                        }

                    if (erAldersjusteringNyLøsning()) {
                        val detaljer = finnAldersjusteringDetaljerGrunnlag()!!
                        if (detaljer.grunnlagFraVedtak == null) {
                            return@mapIndexed underholdskostnad
                        }
                        val grunnlagFraVedtak = hentVedtak(detaljer.grunnlagFraVedtak)!!
                        grunnlagFraVedtak.grunnlagListe.hentUnderholdskostnadPerioder(underholdskostnad, lesemodus, rolle)
                    } else {
                        hentUnderholdskostnadPerioder(underholdskostnad, lesemodus, rolle)
                    }
                    underholdskostnad
                }.toMutableSet()
        val underholdskostnadAndreBarn =
            if (erAldersjusteringNyLøsning()) {
                val detaljer = finnAldersjusteringDetaljerGrunnlag()!!
                if (detaljer.grunnlagFraVedtak == null) {
                    return mutableSetOf()
                }
                val grunnlagFraVedtak = hentVedtak(detaljer.grunnlagFraVedtak)!!
                grunnlagFraVedtak.grunnlagListe.hentAndreBarnUnderholdskostnadPerioder(behandling, lesemodus, virkningstidspunkt)
            } else {
                hentAndreBarnUnderholdskostnadPerioder(
                    behandling,
                    lesemodus,
                    virkningstidspunkt,
                )
            }
        return (underholdskostnadAndreBarn + underholdskostnadSøknadsbarn).toMutableSet()
    }

    private fun List<GrunnlagDto>.hentAndreBarnUnderholdskostnadPerioder(
        behandling: Behandling,
        lesemodus: Boolean,
        virkningstidspunkt: LocalDate,
    ): MutableSet<Underholdskostnad> {
        val andreBarnTilBidragsmottakerGrunnlag = hentAndreBarnTilBidragsmottakerGrunnlagUnder12År(virkningstidspunkt)
        val andreBarnTilBidragsmottakerIdenter =
            andreBarnTilBidragsmottakerGrunnlag.mapNotNull {
                hentPersonMedReferanse(it.gjelderPerson)!!.personIdent
            }

        var indexU = 100L
        val underholdskostnadAndreBarn =
            filtrerBasertPåEgenReferanse(Grunnlagstype.FAKTISK_UTGIFT_PERIODE)
                .filter {
                    val gjelderBarnIdent = hentPersonMedReferanse(it.gjelderBarnReferanse)!!.personIdent
                    behandling.roller.none { it.ident == gjelderBarnIdent }
                }.groupBy { it.gjelderBarnReferanse }
                .map { (gjelderBarnReferanse, grunnlag) ->
                    val innhold = grunnlag.innholdTilObjekt<FaktiskUtgiftPeriode>()
                    val gjelderBarn = hentPersonMedReferanse(gjelderBarnReferanse)!!.personObjekt

                    val kilde =
                        if (andreBarnTilBidragsmottakerIdenter.contains(gjelderBarn.ident?.verdi)) {
                            Kilde.OFFENTLIG
                        } else {
                            Kilde.MANUELL
                        }
                    indexU += 1L
                    val underholdskostnad =
                        if (lesemodus) {
                            Underholdskostnad(
                                id = indexU,
                                behandling = behandling,
                                kilde = kilde,
                                person =
                                    Person(
                                        id = indexU,
                                        ident = gjelderBarn.ident?.verdi,
                                        navn = gjelderBarn.navn,
                                        fødselsdato = gjelderBarn.fødselsdato,
                                    ),
                            )
                        } else {
                            underholdService.oppretteUnderholdskostnad(
                                behandling,
                                BarnDto(
                                    personident = gjelderBarn.ident,
                                    navn = gjelderBarn.navn,
                                    fødselsdato = gjelderBarn.fødselsdato,
                                ),
                                kilde = kilde,
                            )
                        }
                    underholdskostnad.faktiskeTilsynsutgifter.addAll(
                        innhold.mapFaktiskTilsynsutgift(
                            underholdskostnad,
                            lesemodus,
                        ),
                    )
                    underholdskostnad
                }.toMutableSet()

        val faktiskPeriodeGjelderReferanser =
            filtrerBasertPåEgenReferanse(
                Grunnlagstype.FAKTISK_UTGIFT_PERIODE,
            ).map { it.gjelderBarnReferanse }

        val underholdskostnadAndreBarnBMUtenTilsynsutgifer =
            andreBarnTilBidragsmottakerGrunnlag
                .filter { !faktiskPeriodeGjelderReferanser.contains(it.gjelderPerson) }
                .filter { hentPersonMedReferanse(it.gjelderPerson)?.type != Grunnlagstype.PERSON_SØKNADSBARN }
                .map {
                    val gjelderBarn = hentPersonMedReferanse(it.gjelderPerson)!!.personObjekt
                    indexU += 1L
                    if (lesemodus) {
                        Underholdskostnad(
                            id = indexU,
                            behandling = behandling,
                            person =
                                Person(
                                    id = indexU,
                                    ident = gjelderBarn.ident?.verdi,
                                    navn = gjelderBarn.navn,
                                    fødselsdato = gjelderBarn.fødselsdato,
                                ),
                        )
                    } else {
                        underholdService.oppretteUnderholdskostnad(
                            behandling,
                            BarnDto(
                                personident = gjelderBarn.ident,
                                navn = if (gjelderBarn.ident != null) null else gjelderBarn.navn,
                                fødselsdato = gjelderBarn.fødselsdato,
                            ),
                        )
                    }
                }
        return (underholdskostnadAndreBarn + underholdskostnadAndreBarnBMUtenTilsynsutgifer).toMutableSet()
    }

    fun List<GrunnlagDto>.hentUnderholdskostnadPerioder(
        underholdskostnad: Underholdskostnad,
        lesemodus: Boolean,
        rolle: Rolle,
        filtrerEtterPeriode: ÅrMånedsperiode? = null,
    ) {
        underholdskostnad.tilleggsstønad.addAll(
            filtrerBasertPåEgenReferanse(Grunnlagstype.TILLEGGSSTØNAD_PERIODE)
                .filter {
                    hentPersonMedReferanse(it.gjelderBarnReferanse)!!.personIdent == rolle.ident
                }.map { it.innholdTilObjekt<TilleggsstønadPeriode>() }
                .mapTillegsstønad(underholdskostnad, lesemodus)
                .filter { filtrerEtterPeriode == null || ÅrMånedsperiode(it.fom, it.tom).overlapper(filtrerEtterPeriode) },
        )

        underholdskostnad.faktiskeTilsynsutgifter.addAll(
            filtrerBasertPåEgenReferanse(Grunnlagstype.FAKTISK_UTGIFT_PERIODE)
                .filter {
                    hentPersonMedReferanse(it.gjelderBarnReferanse)!!.personIdent == rolle.ident
                }.map { it.innholdTilObjekt<FaktiskUtgiftPeriode>() }
                .mapFaktiskTilsynsutgift(underholdskostnad, lesemodus)
                .filter { filtrerEtterPeriode == null || ÅrMånedsperiode(it.fom, it.tom).overlapper(filtrerEtterPeriode) },
        )

        underholdskostnad.barnetilsyn.addAll(
            filtrerBasertPåEgenReferanse(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE)
                .filter { ts ->
                    hentPersonMedReferanse(ts.gjelderBarnReferanse)!!.personIdent == rolle.ident
                }.map { it.innholdTilObjekt<BarnetilsynMedStønadPeriode>() }
                .mapBarnetilsyn(underholdskostnad, lesemodus)
                .filter { filtrerEtterPeriode == null || ÅrMånedsperiode(it.fom, it.tom).overlapper(filtrerEtterPeriode) },
        )
        underholdskostnad.harTilsynsordning =
            underholdskostnad.barnetilsyn.isNotEmpty() ||
            underholdskostnad.faktiskeTilsynsutgifter.isNotEmpty() ||
            underholdskostnad.tilleggsstønad.isNotEmpty()
    }

    private fun List<GrunnlagDto>.hentAndreBarnTilBidragsmottakerGrunnlagUnder12År(virkningstidspunkt: LocalDate) =
        filtrerBasertPåEgenReferanse(
            Grunnlagstype.INNHENTET_ANDRE_BARN_TIL_BIDRAGSMOTTAKER,
        ).firstOrNull()
            ?.innholdTilObjekt<InnhentetAndreBarnTilBidragsmottaker>()
            ?.grunnlag
            ?.filter { it.fødselsdato.erUnder12År(virkningstidspunkt) }
            ?: emptyList()

    private fun List<TilleggsstønadPeriode>.mapTillegsstønad(
        underholdskostnad: Underholdskostnad,
        lesemodus: Boolean,
    ): List<Tilleggsstønad> =
        mapIndexed { index, it ->
            Tilleggsstønad(
                id = if (lesemodus) index.toLong() else null,
                underholdskostnad = underholdskostnad,
                fom = it.periode.fom.atDay(1),
                tom =
                    it.periode.til
                        ?.minusMonths(1)
                        ?.atEndOfMonth(),
                dagsats = it.beløpDagsats,
            )
        }

    private fun List<FaktiskUtgiftPeriode>.mapFaktiskTilsynsutgift(
        underholdskostnad: Underholdskostnad,
        lesemodus: Boolean,
    ): List<FaktiskTilsynsutgift> =
        mapIndexed { index, it ->
            FaktiskTilsynsutgift(
                id = if (lesemodus) index.toLong() else null,
                underholdskostnad = underholdskostnad,
                fom = it.periode.fom.atDay(1),
                tom =
                    it.periode.til
                        ?.minusMonths(1)
                        ?.atEndOfMonth(),
                tilsynsutgift = it.faktiskUtgiftBeløp,
                kostpenger = it.kostpengerBeløp,
                kommentar = it.kommentar,
            )
        }

    private fun List<BarnetilsynMedStønadPeriode>.mapBarnetilsyn(
        underholdskostnad: Underholdskostnad,
        lesemodus: Boolean,
    ): List<Barnetilsyn> =
        mapIndexed { index, it ->
            Barnetilsyn(
                id = if (lesemodus) index.toLong() else null,
                underholdskostnad = underholdskostnad,
                fom = it.periode.fom.atDay(1),
                tom =
                    it.periode.til
                        ?.minusMonths(1)
                        ?.atEndOfMonth(),
                under_skolealder = it.skolealder == Skolealder.UNDER,
                omfang = it.tilsynstype,
                kilde = if (it.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
            )
        }

    private fun List<GrunnlagDto>.mapSamvær(
        behandling: Behandling,
        lesemodus: Boolean,
    ): MutableSet<Samvær> =
        filtrerBasertPåEgenReferanse(
            Grunnlagstype.SAMVÆRSPERIODE,
        ).groupBy { it.gjelderBarnReferanse }
            .map { (gjelderReferanse, perioder) ->
                val person = hentPersonMedReferanse(gjelderReferanse)!!
                val samvær =
                    Samvær(
                        id = if (lesemodus) 1 else null,
                        behandling = behandling,
                        rolle = behandling.roller.find { it.ident == person.personIdent }!!,
                        perioder = mutableSetOf(),
                    )

                samvær.perioder.addAll(
                    perioder.mapIndexed { index, it ->
                        val periodeInnhold = it.innholdTilObjekt<SamværsperiodeGrunnlag>()
                        val beregning =
                            finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                                Grunnlagstype.SAMVÆRSKALKULATOR,
                                it.grunnlagsreferanseListe,
                            ).firstOrNull()
                                ?.innholdTilObjekt<SamværskalkulatorDetaljer>()
                        Samværsperiode(
                            id = if (lesemodus) index.toLong() else null,
                            samvær = samvær,
                            fom = periodeInnhold.periode.fom.atDay(1),
                            tom =
                                periodeInnhold.periode.til
                                    ?.minusMonths(1)
                                    ?.atEndOfMonth(),
                            samværsklasse = periodeInnhold.samværsklasse,
                            beregningJson = beregning?.let { commonObjectmapper.writeValueAsString(it) },
                        )
                    },
                )
                samvær
            }.toMutableSet()
}
