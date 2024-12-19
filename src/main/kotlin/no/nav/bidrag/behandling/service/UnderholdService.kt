package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.henteNyesteAktiveGrunnlag
import no.nav.bidrag.behandling.database.datamodell.henteNyesteIkkeAktiveGrunnlag
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilPerson
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.behandling.hentAlleBearbeidaBarnetilsyn
import no.nav.bidrag.behandling.transformers.underhold.aktivereBarnetilsynHvisIngenEndringerMåAksepteres
import no.nav.bidrag.behandling.transformers.underhold.erstatteOffentligePerioderIBarnetilsynstabellMedOppdatertGrunnlag
import no.nav.bidrag.behandling.transformers.underhold.harAndreBarnIUnderhold
import no.nav.bidrag.behandling.transformers.underhold.henteOgValidereUnderholdskostnad
import no.nav.bidrag.behandling.transformers.underhold.justerePerioder
import no.nav.bidrag.behandling.transformers.underhold.justerePerioderForBearbeidaBarnetilsynEtterVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.underhold.tilBarnetilsyn
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDto
import no.nav.bidrag.behandling.transformers.underhold.validere
import no.nav.bidrag.behandling.transformers.underhold.validerePerioderStønadTilBarnetilsyn
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

@Service
class UnderholdService(
    private val underholdskostnadRepository: UnderholdskostnadRepository,
    private val personRepository: PersonRepository,
    private val notatService: NotatService,
    private val dtomapper: Dtomapper,
    private val personService: PersonService,
) {
    fun oppdatereBegrunnelse(
        behandling: Behandling,
        request: OppdatereBegrunnelseRequest,
    ) {
        val rolleSøknadsbarn =
            request.underholdsid?.let {
                henteOgValidereUnderholdskostnad(behandling, it).barnetsRolleIBehandlingen
            }

        if (request.underholdsid == null) {
            val underholdHarAndreBarn =
                behandling.underholdskostnader.find { it.barnetsRolleIBehandlingen == null } != null
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
        if (!harTilsynsordning &&
            (
                underholdskostnad.barnetilsyn.isNotEmpty() ||
                    underholdskostnad.tilleggsstønad.isNotEmpty() ||
                    underholdskostnad.faktiskeTilsynsutgifter.isNotEmpty()
            )
        ) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke sette harTilsynsordning til usann så lenge barnet er registrert med stønad til barnetilstyn, tilleggsstønad, eller faktiske tilsynsutgift",
            )
        }

        underholdskostnad.harTilsynsordning = harTilsynsordning
    }

    @Transactional
    fun oppretteUnderholdskostnad(
        behandling: Behandling,
        gjelderBarn: BarnDto,
    ): Underholdskostnad {
        gjelderBarn.validere(behandling, personService)

        return gjelderBarn.personident?.let { personidentBarn ->
            val rolleSøknadsbarn = behandling.søknadsbarn.find { it.ident == personidentBarn.verdi }
            personRepository.findFirstByIdent(personidentBarn.verdi)?.let { eksisterendePerson ->
                rolleSøknadsbarn?.let { eksisterendePerson.rolle.add(it) }
                rolleSøknadsbarn?.person = eksisterendePerson
                lagreUnderholdskostnad(behandling, eksisterendePerson)
            } ?: run {
                val person =
                    Person(
                        ident = personidentBarn.verdi,
                        fødselsdato =
                            personService.hentPersonFødselsdato(personidentBarn.verdi)
                                ?: fantIkkeFødselsdatoTilPerson(behandling.id!!),
                        rolle = rolleSøknadsbarn?.let { mutableSetOf(it) } ?: mutableSetOf(),
                    )
                person.rolle.forEach { it.person = person }

                lagreUnderholdskostnad(behandling, person)
            }
        } ?: run {
            lagreUnderholdskostnad(
                behandling,
                Person(navn = gjelderBarn.navn, fødselsdato = gjelderBarn.fødselsdato!!),
            )
        }
    }

    @Transactional
    fun oppdatereStønadTilBarnetilsynManuelt(
        underholdskostnad: Underholdskostnad,
        request: StønadTilBarnetilsynDto,
    ): Barnetilsyn {
        request.validerePerioderStønadTilBarnetilsyn(underholdskostnad)

        val oppdatertBarnetilsyn: Barnetilsyn =
            request.id?.let { id ->
                val barnetilsyn = underholdskostnad.barnetilsyn.find { id == it.id }!!

                // dersom periode endres skal kilde alltid være manuell
                if (barnetilsyn.fom != request.periode.fom || barnetilsyn.tom != request.periode.tom) {
                    barnetilsyn.kilde = Kilde.MANUELL
                }

                barnetilsyn.fom = request.periode.fom
                barnetilsyn.tom = request.periode.tom
                barnetilsyn.under_skolealder =
                    when (request.skolealder) {
                        Skolealder.UNDER -> true
                        Skolealder.OVER -> false
                        else -> null
                    }
                barnetilsyn.omfang = request.tilsynstype ?: Tilsynstype.IKKE_ANGITT

                barnetilsyn
            } ?: run {
                val barnetilsyn =
                    Barnetilsyn(
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        under_skolealder =
                            when (request.skolealder) {
                                Skolealder.UNDER -> true
                                Skolealder.OVER -> false
                                else -> null
                            },
                        omfang = request.tilsynstype ?: Tilsynstype.IKKE_ANGITT,
                        kilde = Kilde.MANUELL,
                        underholdskostnad = underholdskostnad,
                    )
                underholdskostnad.barnetilsyn.add(barnetilsyn)
                underholdskostnad.harTilsynsordning = true
                underholdskostnadRepository
                    .save(underholdskostnad)
                    .barnetilsyn
                    .sortedBy { it.id }
                    .last()
            }

        return oppdatertBarnetilsyn
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
    fun tilpasseUnderholdEtterVirkningsdato(behandling: Behandling) {
        tilpasseAktiveBarnetilsynsgrunnlagEtterVirkningsdato(behandling)
        tilpasseIkkeaktiveBarnetilsynsgrunnlagEtterVirkningsdato(behandling)
        oppdatereUnderholdsperioderEtterEndretVirkningsdato(behandling)
        behandling.aktivereBarnetilsynHvisIngenEndringerMåAksepteres()
    }

    @Transactional
    fun oppdatereFaktiskeTilsynsutgifter(
        underholdskostnad: Underholdskostnad,
        request: OppdatereFaktiskTilsynsutgiftRequest,
    ): FaktiskTilsynsutgift {
        request.validere(underholdskostnad)

        val oppdatertFaktiskTilsynsutgift =
            request.id?.let { id ->
                underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id }
                val faktiskTilsynsutgift = underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id }!!
                faktiskTilsynsutgift.fom = request.periode.fom
                faktiskTilsynsutgift.tom = request.periode.tom
                faktiskTilsynsutgift.kostpenger = request.kostpenger
                faktiskTilsynsutgift.tilsynsutgift = request.utgift
                faktiskTilsynsutgift.kommentar = request.kommentar
                faktiskTilsynsutgift
            } ?: run {
                val faktiskTilsynsutgift =
                    FaktiskTilsynsutgift(
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        kostpenger = request.kostpenger,
                        tilsynsutgift = request.utgift,
                        kommentar = request.kommentar,
                        underholdskostnad = underholdskostnad,
                    )
                underholdskostnad.faktiskeTilsynsutgifter.add(faktiskTilsynsutgift)
                underholdskostnad.harTilsynsordning = true
                underholdskostnadRepository
                    .save(underholdskostnad)
                    .faktiskeTilsynsutgifter
                    .sortedBy { it.id }
                    .last()
            }
        return oppdatertFaktiskTilsynsutgift
    }

    @Transactional
    fun oppdatereTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        request: OppdatereTilleggsstønadRequest,
    ): Tilleggsstønad {
        request.validere(underholdskostnad)

        val oppdatertTilleggsstønad =
            request.id?.let { id ->
                val tilleggsstønad = underholdskostnad.tilleggsstønad.find { id == it.id }!!
                tilleggsstønad.fom = request.periode.fom
                tilleggsstønad.tom = request.periode.tom
                tilleggsstønad.dagsats = request.dagsats
                tilleggsstønad.underholdskostnad = underholdskostnad
                tilleggsstønad
            } ?: run {
                val tilleggsstønad =
                    Tilleggsstønad(
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        dagsats = request.dagsats,
                        underholdskostnad = underholdskostnad,
                    )
                underholdskostnad.tilleggsstønad.add(tilleggsstønad)
                underholdskostnad.harTilsynsordning = true
                underholdskostnadRepository
                    .save(underholdskostnad)
                    .tilleggsstønad
                    .sortedBy { it.id }
                    .last()
            }

        return oppdatertTilleggsstønad
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
        if (underholdskostnad.person.underholdskostnad.isEmpty() && underholdskostnad.barnetsRolleIBehandlingen == null) {
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
    ): Underholdskostnad {
        val u = underholdskostnadRepository.save(Underholdskostnad(behandling = behandling, person = person))
        behandling.underholdskostnader.add(u)
        return u
    }

    private fun oppdatereUnderholdsperioderEtterEndretVirkningsdato(b: Behandling) {
        b.underholdskostnader.forEach {
            it.erstatteOffentligePerioderIBarnetilsynstabellMedOppdatertGrunnlag()
            it.justerePerioder()
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
