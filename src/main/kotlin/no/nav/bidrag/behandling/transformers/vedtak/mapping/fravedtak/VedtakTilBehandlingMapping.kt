package no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.service.UnderholdService
import no.nav.bidrag.behandling.transformers.behandling.tilNotat
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.beregning.erAvslagSomInneholderUtgifter
import no.nav.bidrag.behandling.transformers.byggResultatSærbidragsberegning
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
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.BarnetilsynMedStønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.FaktiskUtgiftPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilleggsstønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
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
) {
    fun VedtakDto.tilBehandling(
        vedtakId: Long,
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
        val stønadsendringstype = stønadsendringListe.firstOrNull()?.type
        val behandling =
            Behandling(
                id = if (lesemodus) 1 else null,
                søknadstype = søknadstype,
                vedtakstype = vedtakType ?: type,
                opprinneligVedtakstype = opprinneligVedtakstype,
                virkningstidspunkt = virkningstidspunkt ?: hentSøknad().søktFraDato,
                kategori = grunnlagListe.særbidragskategori?.kategori?.name,
                kategoriBeskrivelse = grunnlagListe.særbidragskategori?.beskrivelse,
                opprinneligVirkningstidspunkt =
                    virkningstidspunkt
                        ?: hentSøknad().søktFraDato,
                opprinneligVedtakstidspunkt = opprinneligVedtakstidspunkt.toMutableSet(),
                innkrevingstype =
                    this.stønadsendringListe.firstOrNull()?.innkreving
                        ?: this.engangsbeløpListe.firstOrNull()?.innkreving
                        ?: Innkrevingstype.MED_INNKREVING,
                årsak = hentVirkningstidspunkt()?.årsak,
                avslag = avslagskode(),
                klageMottattdato = if (!lesemodus) mottattdato else hentSøknad().klageMottattDato,
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
                soknadRefId = søknadRefId,
                refVedtaksid = vedtakId,
                behandlerEnhet = enhet ?: enhetsnummer?.verdi!!,
                opprettetAv = opprettetAv,
                opprettetAvNavn = opprettetAvNavn,
                kildeapplikasjon = if (lesemodus) kildeapplikasjon else TokenUtils.hentApplikasjonsnavn()!!,
                datoTom = null,
                saksnummer = saksnummer!!,
                soknadsid = søknadId ?: this.søknadId!!,
            )

        behandling.roller = grunnlagListe.mapRoller(behandling, lesemodus)
        oppdaterDirekteOppgjørBeløp(behandling, lesemodus)
        grunnlagListe.oppdaterRolleGebyr(behandling)

        behandling.inntekter = grunnlagListe.mapInntekter(behandling, lesemodus)
        behandling.husstandsmedlem = grunnlagListe.mapHusstandsmedlem(behandling, lesemodus)
        behandling.sivilstand = grunnlagListe.mapSivilstand(behandling, lesemodus)
        behandling.utgift = grunnlagListe.mapUtgifter(behandling, lesemodus)
        behandling.samvær = grunnlagListe.mapSamvær(behandling, lesemodus)
        behandling.underholdskostnader = grunnlagListe.mapUnderholdskostnad(behandling, lesemodus)
        behandling.grunnlag = grunnlagListe.mapGrunnlag(behandling, lesemodus)

        notatMedType(NotatGrunnlag.NotatType.BOFORHOLD, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.BOFORHOLD, it))
        }
        notatMedType(Notattype.UTGIFTER, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.UTGIFTER, it))
        }
        notatMedType(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, it))
        }
        behandling.roller.forEach { r ->
            notatMedType(NotatGrunnlag.NotatType.INNTEKT, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.INNTEKT, it, r))
            }
        }
        behandling.roller.forEach { r ->
            notatMedType(NotatGrunnlag.NotatType.SAMVÆR, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.SAMVÆR, it, r))
            }
        }

        behandling.roller.forEach { r ->
            notatMedType(
                NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
                false,
                grunnlagListe.hentPerson(r.ident)?.referanse,
            )?.let {
                behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD, it, r))
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

    private fun List<GrunnlagDto>.mapUnderholdskostnad(
        behandling: Behandling,
        lesemodus: Boolean,
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
                            underholdService.opprettEllerOppdaterUnderholdskostnad(
                                behandling,
                                BarnDto(personident = Personident(rolle.ident!!)),
                            )
                        }

                    underholdskostnad.tilleggsstønad.addAll(
                        filtrerBasertPåEgenReferanse(Grunnlagstype.TILLEGGSSTØNAD_PERIODE)
                            .filter {
                                hentPersonMedReferanse(it.gjelderBarnReferanse)!!.personIdent == rolle.ident
                            }.map { it.innholdTilObjekt<TilleggsstønadPeriode>() }
                            .mapTillegsstønad(underholdskostnad, lesemodus),
                    )

                    underholdskostnad.faktiskeTilsynsutgifter.addAll(
                        filtrerBasertPåEgenReferanse(Grunnlagstype.FAKTISK_UTGIFT_PERIODE)
                            .filter {
                                hentPersonMedReferanse(it.gjelderBarnReferanse)!!.personIdent == rolle.ident
                            }.map { it.innholdTilObjekt<FaktiskUtgiftPeriode>() }
                            .mapFaktiskTilsynsutgift(underholdskostnad, lesemodus),
                    )

                    underholdskostnad.barnetilsyn.addAll(
                        filtrerBasertPåEgenReferanse(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE)
                            .filter { ts ->
                                hentPersonMedReferanse(ts.gjelderBarnReferanse)!!.personIdent == rolle.ident
                            }.map { it.innholdTilObjekt<BarnetilsynMedStønadPeriode>() }
                            .mapBarnetilsyn(underholdskostnad, lesemodus),
                    )
                    underholdskostnad.harTilsynsordning =
                        underholdskostnad.barnetilsyn.isNotEmpty() ||
                        underholdskostnad.faktiskeTilsynsutgifter.isNotEmpty() ||
                        underholdskostnad.tilleggsstønad.isNotEmpty()
                    underholdskostnad
                }.toMutableSet()

        val underholdskostnadAndreBarn =
            filtrerBasertPåEgenReferanse(Grunnlagstype.FAKTISK_UTGIFT_PERIODE)
                .filter {
                    val gjelderBarnIdent = hentPersonMedReferanse(it.gjelderBarnReferanse)!!.personIdent
                    behandling.roller.none { it.ident == gjelderBarnIdent }
                }.groupBy { it.gjelderBarnReferanse }
                .map { (gjelderBarnReferanse, grunnlag) ->
                    val innhold = grunnlag.innholdTilObjekt<FaktiskUtgiftPeriode>()
                    val gjelderBarn = hentPersonMedReferanse(gjelderBarnReferanse)!!.personObjekt

                    val underholdskostnad =
                        if (lesemodus) {
                            Underholdskostnad(
                                id = 1,
                                behandling = behandling,
                                person =
                                    Person(
                                        id = 1,
                                        ident = gjelderBarn.ident?.verdi,
                                        navn = gjelderBarn.navn,
                                        fødselsdato = gjelderBarn.fødselsdato,
                                    ),
                            )
                        } else {
                            underholdService.opprettEllerOppdaterUnderholdskostnad(
                                behandling,
                                BarnDto(
                                    personident = gjelderBarn.ident,
                                    navn = gjelderBarn.navn,
                                    fødselsdato = gjelderBarn.fødselsdato,
                                ),
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
            filtrerOgKonverterBasertPåEgenReferanse<no.nav.bidrag.transport.behandling.felles.grunnlag.Person>(
                Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
            ).filter { !faktiskPeriodeGjelderReferanser.contains(it.referanse) }
                .map {
                    val gjelderBarn = hentPersonMedReferanse(it.referanse)!!.personObjekt
                    if (lesemodus) {
                        Underholdskostnad(
                            id = 1,
                            behandling = behandling,
                            person =
                                Person(
                                    id = 1,
                                    ident = gjelderBarn.ident?.verdi,
                                    navn = gjelderBarn.navn,
                                    fødselsdato = gjelderBarn.fødselsdato,
                                ),
                        )
                    } else {
                        underholdService.opprettEllerOppdaterUnderholdskostnad(
                            behandling,
                            BarnDto(
                                personident = gjelderBarn.ident,
                                navn = gjelderBarn.navn,
                                fødselsdato = gjelderBarn.fødselsdato,
                            ),
                        )
                    }
                }

        return (underholdskostnadAndreBarn + underholdskostnadSøknadsbarn + underholdskostnadAndreBarnBMUtenTilsynsutgifer).toMutableSet()
    }

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
                kilde = Kilde.OFFENTLIG,
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
