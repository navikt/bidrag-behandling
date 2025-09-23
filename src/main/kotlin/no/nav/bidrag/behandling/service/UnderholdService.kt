package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBarnetilsyn
import no.nav.bidrag.behandling.database.datamodell.henteNyesteAktiveGrunnlag
import no.nav.bidrag.behandling.database.datamodell.henteNyesteIkkeAktiveGrunnlag
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilPerson
import no.nav.bidrag.behandling.transformers.behandling.hentAlleBearbeidaBarnetilsyn
import no.nav.bidrag.behandling.transformers.underhold.aktivereBarnetilsynHvisIngenEndringerMåAksepteres
import no.nav.bidrag.behandling.transformers.underhold.erstatteOffentligePerioderIBarnetilsynstabellMedOppdatertGrunnlag
import no.nav.bidrag.behandling.transformers.underhold.harAndreBarnIUnderhold
import no.nav.bidrag.behandling.transformers.underhold.henteOgValidereUnderholdskostnad
import no.nav.bidrag.behandling.transformers.underhold.justerPerioderForOpphørsdato
import no.nav.bidrag.behandling.transformers.underhold.justerePerioder
import no.nav.bidrag.behandling.transformers.underhold.justerePerioderForBearbeidaBarnetilsynEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.underhold.tilBarnetilsyn
import no.nav.bidrag.behandling.transformers.underhold.validere
import no.nav.bidrag.behandling.transformers.underhold.validerePerioderStønadTilBarnetilsyn
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

private fun periodeFomJuli(year: Int) =
    LocalDate
        .of(year, 7, 1)

@Service
class UnderholdService(
    private val underholdskostnadRepository: UnderholdskostnadRepository,
    private val personRepository: PersonRepository,
    private val notatService: NotatService,
    private val personService: PersonService,
) {
    fun oppdatereBegrunnelse(
        behandling: Behandling,
        request: OppdatereBegrunnelseRequest,
    ) {
        val rolleSøknadsbarn =
            request.underholdsid?.let {
                henteOgValidereUnderholdskostnad(behandling, it).rolle
            }

        if (request.underholdsid == null) {
            val underholdHarAndreBarn =
                behandling.underholdskostnader.find { it.rolle == null } != null
            if (!underholdHarAndreBarn) {
                throw HttpClientErrorException(
                    HttpStatus.BAD_REQUEST,
                    "Kan ikke oppdatere begrunnelse i underhold for andre barn uten at andre barn er lagt til.",
                )
            }
        }

        notatService.oppdatereNotat(
            behandling,
            Notattype.UNDERHOLDSKOSTNAD,
            request.begrunnelse,
            rolleSøknadsbarn ?: behandling.bidragsmottaker!!,
        )
    }

    @Transactional
    fun oppdatereTilsynsordning(
        underholdskostnad: Underholdskostnad,
        harTilsynsordning: Boolean,
    ) {
        val harOffentligeOpplysninger = underholdskostnad.hentSisteBearbeidetBarnetilsyn()?.isNotEmpty() == true

        if (harOffentligeOpplysninger) {
            ugyldigForespørsel("Kan ikke endre tilsynsordning når det finnes offentlige opplysninger")
        } else if (!harTilsynsordning &&
            (
                underholdskostnad.barnetilsyn.isNotEmpty() ||
                    underholdskostnad.tilleggsstønad.isNotEmpty() ||
                    underholdskostnad.faktiskeTilsynsutgifter.isNotEmpty()
            )
        ) {
            ugyldigForespørsel(
                "Kan ikke sette harTilsynsordning til usann så lenge barnet er registrert med stønad til barnetilstyn, tilleggsstønad, eller faktiske tilsynsutgift",
            )
        }

        underholdskostnad.harTilsynsordning = harTilsynsordning
    }

    @Transactional
    fun oppretteUnderholdskostnad(
        behandling: Behandling,
        gjelderBarn: BarnDto,
        kilde: Kilde = Kilde.MANUELL,
    ): Underholdskostnad {
        gjelderBarn.validere(behandling, personService)

        return gjelderBarn.personident?.let { personidentBarn ->
            val rolleSøknadsbarn = behandling.søknadsbarn.find { it.ident == personidentBarn.verdi }
            val lagreKilde = if (rolleSøknadsbarn == null) kilde else null
            personRepository.findFirstByIdent(personidentBarn.verdi)?.let { eksisterendePerson ->
                rolleSøknadsbarn?.let { eksisterendePerson.rolle.add(it) }
                rolleSøknadsbarn?.person = eksisterendePerson
                lagreUnderholdskostnad(behandling, eksisterendePerson, rolleSøknadsbarn, lagreKilde)
            } ?: run {
                val person =
                    Person(
                        ident = personidentBarn.verdi,
                        fødselsdato =
                            hentPersonFødselsdato(personidentBarn.verdi)
                                ?: fantIkkeFødselsdatoTilPerson(behandling.id!!),
                        rolle = rolleSøknadsbarn?.let { mutableSetOf(it) } ?: mutableSetOf(),
                    )
                person.rolle.forEach { it.person = person }

                lagreUnderholdskostnad(behandling, person, rolleSøknadsbarn, kilde = lagreKilde)
            }
        } ?: run {
            lagreUnderholdskostnad(
                behandling,
                Person(navn = gjelderBarn.navn, fødselsdato = gjelderBarn.fødselsdato!!),
                kilde = Kilde.MANUELL,
            )
        }
    }

    fun oppdatereAutomatiskInnhentaStønadTilBarnetilsyn(
        behandling: Behandling,
        gjelderSøknadsbarn: Personident,
        overskriveManuelleOpplysninger: Boolean,
    ) {
        val ikkeAktiverteGrunnlag =
            behandling.grunnlag.hentAlleIkkeAktiv().filter { Grunnlagsdatatype.BARNETILSYN == it.type }

        val nyesteIkkeaktiverteGrunnlag =
            ikkeAktiverteGrunnlag
                .filter { !it.erBearbeidet }
                .maxByOrNull { it.innhentet }

        val ikkeaktivertBearbeidaGrunnlagForSøknadsbarn =
            ikkeAktiverteGrunnlag
                .filter { it.erBearbeidet }
                .find { it.gjelder == gjelderSøknadsbarn.verdi }

        if (nyesteIkkeaktiverteGrunnlag == null || ikkeaktivertBearbeidaGrunnlagForSøknadsbarn == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen grunnlag av type BARNETILSYN å aktivere for søknadsbarn i behandling $behandling.id",
            )
        }

        val data =
            behandling.grunnlag
                .hentAlleIkkeAktiv()
                .filter { it.gjelder == gjelderSøknadsbarn.verdi }
                .toSet()
                .hentAlleBearbeidaBarnetilsyn(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.bidragsmottaker!!,
                )

        val u = behandling.underholdskostnader.find { it.person.personident == gjelderSøknadsbarn }
        if (u == null) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Fant ingen underholdskostnad tilknyttet søknadsbarn i behandling $behandling.id i forbindelse med aktivering av BARNETILSYN.",
            )
        }

        if (overskriveManuelleOpplysninger) {
            u.barnetilsyn.clear()
            u.barnetilsyn.addAll(data.tilBarnetilsyn(u))
        } else {
            val gamleOffentligeBarnetilsyn = u.barnetilsyn.filter { it.kilde == Kilde.OFFENTLIG }
            u.barnetilsyn.removeAll(gamleOffentligeBarnetilsyn)
            u.barnetilsyn.addAll(data.tilBarnetilsyn(u))
        }

        ikkeaktivertBearbeidaGrunnlagForSøknadsbarn.aktiv = LocalDateTime.now()
        if (ikkeAktiverteGrunnlag.filter { it.erBearbeidet }.find { it.gjelder != gjelderSøknadsbarn.verdi } == null) {
            nyesteIkkeaktiverteGrunnlag.aktiv = LocalDateTime.now()
        }
    }

    @Transactional
    fun tilpasseUnderholdEtterVirkningsdato(
        behandling: Behandling,
        opphørSlettet: Boolean = false,
        forrigeVirkningstidspunkt: LocalDate? = null,
    ) {
        tilpasseAktiveBarnetilsynsgrunnlagEtterVirkningsdato(behandling)
        tilpasseIkkeaktiveBarnetilsynsgrunnlagEtterVirkningsdato(behandling)
        oppdatereUnderholdsperioderEtterEndretVirkningsdato(behandling, forrigeVirkningstidspunkt)
        behandling.aktivereBarnetilsynHvisIngenEndringerMåAksepteres()
    }

    @Transactional
    fun oppdatereStønadTilBarnetilsynManuelt(
        underholdskostnad: Underholdskostnad,
        request: StønadTilBarnetilsynDto,
    ) {
        request.validerePerioderStønadTilBarnetilsyn(underholdskostnad)
        val offentligBarnetilsyn = underholdskostnad.hentSisteBearbeidetBarnetilsyn() ?: emptyList()
        val overlapperMedOffentligPeriode =
            offentligBarnetilsyn.any {
                val periode = Datoperiode(it.periodeFra, it.periodeTil)
                val forespørselPeriode = Datoperiode(request.periode.fom, request.periode.tom)
                periode.inneholder(forespørselPeriode)
            }
        val kilde = if (overlapperMedOffentligPeriode) Kilde.OFFENTLIG else Kilde.MANUELL
        request.id?.let { id ->
            val barnetilsyn = underholdskostnad.barnetilsyn.find { id == it.id }!!

            barnetilsyn.kilde = kilde

            barnetilsyn.fom = request.periode.fom
            barnetilsyn.tom = request.periode.tom ?: justerPeriodeTomOpphørsdato(underholdskostnad.opphørsdato)
            barnetilsyn.under_skolealder =
                when (request.skolealder) {
                    Skolealder.UNDER -> true
                    Skolealder.OVER -> false
                    else -> null
                }
            barnetilsyn.omfang = request.tilsynstype ?: Tilsynstype.IKKE_ANGITT

            barnetilsyn
        } ?: run {
            val periodeJustert =
                request.periode.copy(
                    tom = request.periode.tom ?: justerPeriodeTomOpphørsdato(underholdskostnad.opphørsdato),
                )
            underholdskostnad.barnetilsyn.add(
                Barnetilsyn(
                    fom = periodeJustert.fom,
                    tom =
                        underholdskostnad.begrensTomDatoForTolvÅr(periodeJustert),
                    under_skolealder =
                        when (request.skolealder) {
                            Skolealder.UNDER -> true
                            Skolealder.OVER -> false
                            else -> null
                        },
                    omfang = request.tilsynstype ?: Tilsynstype.IKKE_ANGITT,
                    kilde = kilde,
                    underholdskostnad = underholdskostnad,
                ),
            )
            if (underholdskostnad.erPeriodeFørOgEtterFyltTolvÅr(periodeJustert) &&
                underholdskostnad.barnetilsyn.none {
                    Datoperiode(it.fom, it.tom) == Datoperiode(periodeFomJuli(periodeJustert.fom.year), periodeJustert.tom)
                }
            ) {
                underholdskostnad.barnetilsyn.add(
                    Barnetilsyn(
                        fom = periodeFomJuli(årstallNårBarnFyllerTolvÅr(underholdskostnad.person.fødselsdato)),
                        tom = periodeJustert.tom,
                        under_skolealder =
                            when (request.skolealder) {
                                Skolealder.UNDER -> true
                                Skolealder.OVER -> false
                                else -> null
                            },
                        omfang = request.tilsynstype ?: Tilsynstype.IKKE_ANGITT,
                        kilde = kilde,
                        underholdskostnad = underholdskostnad,
                    ),
                )
            }
            underholdskostnad.harTilsynsordning = true
        }
    }

    @Transactional
    fun oppdatereFaktiskeTilsynsutgifter(
        underholdskostnad: Underholdskostnad,
        request: OppdatereFaktiskTilsynsutgiftRequest,
    ) {
        request.validere(underholdskostnad)

        request.id?.let { id ->
            underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id }
            val faktiskTilsynsutgift = underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id }!!
            faktiskTilsynsutgift.fom = request.periode.fom
            faktiskTilsynsutgift.tom = request.periode.tom ?: justerPeriodeTomOpphørsdato(underholdskostnad.opphørsdato)
            faktiskTilsynsutgift.kostpenger = request.kostpenger
            faktiskTilsynsutgift.tilsynsutgift = request.utgift
            faktiskTilsynsutgift.kommentar = request.kommentar
            faktiskTilsynsutgift
        } ?: run {
            val periodeJustert =
                request.periode.copy(
                    tom = request.periode.tom ?: justerPeriodeTomOpphørsdato(underholdskostnad.opphørsdato),
                )
            underholdskostnad.faktiskeTilsynsutgifter.add(
                FaktiskTilsynsutgift(
                    fom = request.periode.fom,
                    tom = underholdskostnad.begrensTomDatoForTolvÅr(periodeJustert),
                    kostpenger = request.kostpenger,
                    tilsynsutgift = request.utgift,
                    kommentar = request.kommentar,
                    underholdskostnad = underholdskostnad,
                ),
            )
            if (underholdskostnad.erPeriodeFørOgEtterFyltTolvÅr(periodeJustert) &&
                underholdskostnad.faktiskeTilsynsutgifter.none {
                    Datoperiode(it.fom, it.tom) == Datoperiode(periodeFomJuli(periodeJustert.fom.year), periodeJustert.tom)
                }
            ) {
                underholdskostnad.faktiskeTilsynsutgifter.add(
                    FaktiskTilsynsutgift(
                        fom = periodeFomJuli(årstallNårBarnFyllerTolvÅr(underholdskostnad.person.fødselsdato)),
                        tom = periodeJustert.tom,
                        kostpenger = request.kostpenger,
                        tilsynsutgift = request.utgift,
                        kommentar = request.kommentar,
                        underholdskostnad = underholdskostnad,
                    ),
                )
            }
            underholdskostnad.harTilsynsordning = true
        }
    }

    @Transactional
    fun oppdatereTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        request: OppdatereTilleggsstønadRequest,
    ) {
        request.validere(underholdskostnad)

        request.id?.let { id ->
            val tilleggsstønad = underholdskostnad.tilleggsstønad.find { id == it.id }!!
            tilleggsstønad.fom = request.periode.fom
            tilleggsstønad.tom = request.periode.tom ?: justerPeriodeTomOpphørsdato(underholdskostnad.opphørsdato)
            tilleggsstønad.dagsats = request.dagsats
            tilleggsstønad.underholdskostnad = underholdskostnad
            tilleggsstønad
        } ?: run {
            val periodeJustert =
                request.periode.copy(
                    tom = request.periode.tom ?: justerPeriodeTomOpphørsdato(underholdskostnad.opphørsdato),
                )
            underholdskostnad.tilleggsstønad.add(
                Tilleggsstønad(
                    fom = request.periode.fom,
                    tom = underholdskostnad.begrensTomDatoForTolvÅr(periodeJustert),
                    dagsats = request.dagsats,
                    underholdskostnad = underholdskostnad,
                ),
            )
            if (underholdskostnad.erPeriodeFørOgEtterFyltTolvÅr(periodeJustert) &&
                underholdskostnad.tilleggsstønad.none {
                    Datoperiode(it.fom, it.tom) == Datoperiode(periodeFomJuli(periodeJustert.fom.year), periodeJustert.tom)
                }
            ) {
                underholdskostnad.tilleggsstønad.add(
                    Tilleggsstønad(
                        fom = periodeFomJuli(årstallNårBarnFyllerTolvÅr(underholdskostnad.person.fødselsdato)),
                        tom = periodeJustert.tom,
                        dagsats = request.dagsats,
                        underholdskostnad = underholdskostnad,
                    ),
                )
            }
            underholdskostnad.harTilsynsordning = true
        }
    }

    @Transactional
    fun sletteFraUnderhold(
        behandling: Behandling,
        request: SletteUnderholdselement,
    ) {
        request.validere(behandling)

        val underholdskostnad = behandling.underholdskostnader.find { request.idUnderhold == it.id }!!

        when (request.type) {
            Underholdselement.BARN -> sletteUnderholdskostnad(behandling, underholdskostnad)
            Underholdselement.FAKTISK_TILSYNSUTGIFT ->
                sletteFaktiskTilsynsutgift(
                    underholdskostnad,
                    request.idElement,
                )

            Underholdselement.TILLEGGSSTØNAD -> sletteTilleggsstønad(underholdskostnad, request.idElement)
            Underholdselement.STØNAD_TIL_BARNETILSYN ->
                sletteStønadTilBarnetilsyn(
                    underholdskostnad,
                    request.idElement,
                )
        }
    }

    private fun Underholdskostnad.begrensTomDatoForTolvÅr(periode: DatoperiodeDto): LocalDate? =
        if (erPeriodeFørOgEtterFyltTolvÅr(periode)) {
            periodeFomJuli(årstallNårBarnFyllerTolvÅr(person.fødselsdato)).minusDays(1)
        } else {
            periode.tom
        }

    private fun Underholdskostnad.erPeriodeFørOgEtterFyltTolvÅr(periode: DatoperiodeDto) =
        !erBarnOverTolvÅrForDato(periode.fom) && erBarnOverTolvÅrForDato(periode.tom ?: LocalDate.now())

    private fun årstallNårBarnFyllerTolvÅr(fødselsdato: LocalDate) = fødselsdato.plusYears(12).year

    private fun Underholdskostnad.erBarnOverTolvÅrForDato(dato: LocalDate?): Boolean {
        if (dato == null) return false
        val fødselsdato = person.fødselsdato
        val period = Period.between(fødselsdato.withMonth(7).withDayOfMonth(1), dato)
        return period.years >= 12
    }

    private fun sletteStønadTilBarnetilsyn(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ) {
        val stønadTilBarnetilsyn = underholdskostnad.barnetilsyn.find { idElement == it.id }
        underholdskostnad.barnetilsyn.remove(stønadTilBarnetilsyn)
    }

    private fun sletteTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ) {
        val tilleggsstønad = underholdskostnad.tilleggsstønad.find { idElement == it.id }
        underholdskostnad.tilleggsstønad.remove(tilleggsstønad)
    }

    private fun sletteUnderholdskostnad(
        behandling: Behandling,
        underholdskostnad: Underholdskostnad,
    ) {
        behandling.underholdskostnader.remove(underholdskostnad)
        underholdskostnad.person.underholdskostnad.remove(underholdskostnad)
        if (underholdskostnad.person.underholdskostnad.isEmpty() && underholdskostnad.rolle == null) {
            personRepository.deleteById(underholdskostnad.person.id!!)
            if (!behandling.harAndreBarnIUnderhold()) {
                notatService.sletteNotat(behandling, Notattype.UNDERHOLDSKOSTNAD, behandling.bidragsmottaker!!)
            }
        }
        underholdskostnadRepository.deleteById(underholdskostnad.id!!)
    }

    private fun sletteFaktiskTilsynsutgift(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ) {
        val faktiskTilsynsutgift = underholdskostnad.faktiskeTilsynsutgifter.find { idElement == it.id }
        underholdskostnad.faktiskeTilsynsutgifter.remove(faktiskTilsynsutgift)
    }

    private fun lagreUnderholdskostnad(
        behandling: Behandling,
        person: Person,
        rolle: Rolle? = null,
        kilde: Kilde? = null,
    ): Underholdskostnad {
        val underholdskostnad = Underholdskostnad(behandling = behandling, person = person, rolle = rolle, kilde = kilde)
        val lagreUnderholdskostnad = if (behandling.id != null) underholdskostnadRepository.save(underholdskostnad) else underholdskostnad
        behandling.underholdskostnader.add(lagreUnderholdskostnad)
        return lagreUnderholdskostnad
    }

    private fun oppdatereUnderholdsperioderEtterEndretVirkningsdato(
        b: Behandling,
        forrigeVirkningstidspunkt: LocalDate? = null,
    ) {
        b.underholdskostnader.forEach {
            it.erstatteOffentligePerioderIBarnetilsynstabellMedOppdatertGrunnlag()
            it.justerePerioder(forrigeVirkningstidspunkt)
        }
    }

    @Transactional
    fun oppdatereUnderholdsperioderEtterEndretOpphørsdato(
        b: Behandling,
        opphørSlettet: Boolean = false,
        forrigeOpphørsdato: LocalDate? = null,
    ) {
        b.underholdskostnader.forEach {
            it.justerPerioderForOpphørsdato(opphørSlettet, forrigeOpphørsdato)
        }
    }

    private fun tilpasseIkkeaktiveBarnetilsynsgrunnlagEtterVirkningsdato(behandling: Behandling) {
        val grunnlagsdatatype = Grunnlagsdatatype.BARNETILSYN
        val sisteAktiveGrunnlag =
            behandling.henteNyesteIkkeAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
            ) ?: run {
                log.warn { "Fant ingen aktive barnetilsynsgrunnlag som må tilpasses nytt virkingstidspunkt." }
                return
            }
        sisteAktiveGrunnlag.justerePerioderForBearbeidaBarnetilsynEtterVirkningstidspunkt(false)
    }

    private fun tilpasseAktiveBarnetilsynsgrunnlagEtterVirkningsdato(behandling: Behandling) {
        val grunnlagsdatatype = Grunnlagsdatatype.BARNETILSYN
        val sisteAktiveGrunnlag =
            behandling.henteNyesteAktiveGrunnlag(
                Grunnlagstype(grunnlagsdatatype, false),
                grunnlagsdatatype.innhentesForRolle(behandling)!!,
            ) ?: run {
                log.warn { "Fant ingen aktive barnetilsynsgrunnlag som må tilpasses nytt virkingstidspunkt." }
                return
            }
        sisteAktiveGrunnlag.justerePerioderForBearbeidaBarnetilsynEtterVirkningstidspunkt(true)
    }
}
