package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.repository.BarnetilsynRepository
import no.nav.bidrag.behandling.database.repository.FaktiskTilsynsutgiftRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.TilleggsstønadRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereFaktiskTilsynsutgiftRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereTilleggsstønadRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdResponse
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.underhold.henteOgValidereUnderholdskostnad
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDto
import no.nav.bidrag.behandling.transformers.underhold.validere
import no.nav.bidrag.behandling.transformers.underhold.validerePerioder
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.diverse.Kilde
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

@Service
class UnderholdService(
    private val barnetilsynRepository: BarnetilsynRepository,
    private val faktiskTilsynsutgiftRepository: FaktiskTilsynsutgiftRepository,
    private val tilleggsstønadRepository: TilleggsstønadRepository,
    private val underholdskostnadRepository: UnderholdskostnadRepository,
    private val personRepository: PersonRepository,
    private val notatService: NotatService,
    private val dtomapper: Dtomapper,
) {
    fun oppdatereBegrunnelse(
        behandling: Behandling,
        request: OppdatereBegrunnelseRequest,
    ) {
        val rolleSøknadsbarn =
            request.underholdsid?.let {
                henteOgValidereUnderholdskostnad(behandling, it).person.rolle.firstOrNull()
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

    @Deprecated("Erstattes av oppdatereBegrunnelse og oppdatereTilsynsordning")
    @Transactional
    fun oppdatereUnderhold(
        underholdskostnad: Underholdskostnad,
        request: OppdatereUnderholdRequest,
    ): UnderholdDto {
        request.validere()
        request.harTilsynsordning?.let { underholdskostnad.harTilsynsordning = it }
        val rolleSøknadsbarn = underholdskostnad.person.rolle.firstOrNull()
        request.begrunnelse?.let {
            notatService.oppdatereNotat(
                underholdskostnad.behandling,
                Notattype.UNDERHOLDSKOSTNAD,
                it,
                rolleSøknadsbarn ?: underholdskostnad.behandling.bidragsmottaker!!,
            )
        }
        return dtomapper.tilUnderholdDto(underholdskostnad)
    }

    @Transactional
    fun oppretteUnderholdskostnad(
        behandling: Behandling,
        gjelderBarn: BarnDto,
    ): Underholdskostnad {
        gjelderBarn.validere()
        gjelderBarn.personident?.let { personidentBarn ->
            val rolleSøknadsbarn = behandling.søknadsbarn.find { it.ident == personidentBarn.verdi }
            val eksisterendePerson = personRepository.findFirstByIdent(personidentBarn.verdi)
            if (eksisterendePerson == null) {
                val person =
                    personRepository.save(
                        Person(
                            ident = personidentBarn.verdi,
                            rolle = rolleSøknadsbarn?.let { mutableSetOf(it) } ?: mutableSetOf(),
                        ),
                    )
                person.rolle.forEach { it.person = person }
                return lagreUnderholdskostnad(behandling, person)
            } else {
                rolleSøknadsbarn?.let { eksisterendePerson.rolle.add(it) }
                rolleSøknadsbarn?.person = eksisterendePerson
                return lagreUnderholdskostnad(behandling, eksisterendePerson)
            }
        } ?: run {
            return lagreUnderholdskostnad(
                behandling,
                personRepository.save(Person(navn = gjelderBarn.navn, fødselsdato = gjelderBarn.fødselsdato)),
            )
        }
    }

    @Transactional
    fun oppdatereStønadTilBarnetilsynManuelt(
        underholdskostnad: Underholdskostnad,
        request: StønadTilBarnetilsynDto,
    ): OppdatereUnderholdResponse {
        request.validere(underholdskostnad)

        val oppdatertBarnetilsyn: Barnetilsyn =
            request.id?.let { id ->
                val barnetilsyn = underholdskostnad.barnetilsyn.find { id == it.id }!!
                barnetilsyn.fom = request.periode.fom
                barnetilsyn.tom = request.periode.tom
                barnetilsyn.under_skolealder =
                    when (request.skolealder) {
                        Skolealder.UNDER -> true
                        Skolealder.OVER -> false
                        else -> null
                    }
                barnetilsyn.omfang = request.tilsynstype
                barnetilsyn.kilde = Kilde.MANUELL
                barnetilsyn
            } ?: run {
                val barnetilsyn =
                    barnetilsynRepository.save(
                        Barnetilsyn(
                            fom = request.periode.fom,
                            tom = request.periode.tom,
                            under_skolealder =
                                when (request.skolealder) {
                                    Skolealder.UNDER -> true
                                    Skolealder.OVER -> false
                                    else -> null
                                },
                            omfang = request.tilsynstype,
                            kilde = Kilde.MANUELL,
                            underholdskostnad = underholdskostnad,
                        ),
                    )
                underholdskostnad.barnetilsyn.add(barnetilsyn)
                barnetilsyn
            }

        return OppdatereUnderholdResponse(
            stønadTilBarnetilsyn = oppdatertBarnetilsyn.tilStønadTilBarnetilsynDto(),
            underholdskostnad =
                dtomapper.tilUnderholdskostnadsperioderForBehandlingMedKunEttSøknadsbarn(underholdskostnad.behandling),
            valideringsfeil = underholdskostnad.barnetilsyn.validerePerioder(),
        )
    }

    @Transactional
    fun oppdatereFaktiskeTilsynsutgifter(
        underholdskostnad: Underholdskostnad,
        request: OppdatereFaktiskTilsynsutgiftRequest,
    ): OppdatereUnderholdResponse {
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
                    faktiskTilsynsutgiftRepository.save(
                        FaktiskTilsynsutgift(
                            fom = request.periode.fom,
                            tom = request.periode.tom,
                            kostpenger = request.kostpenger,
                            tilsynsutgift = request.utgift,
                            kommentar = request.kommentar,
                            underholdskostnad = underholdskostnad,
                        ),
                    )
                underholdskostnad.faktiskeTilsynsutgifter.add(faktiskTilsynsutgift)
                faktiskTilsynsutgift
            }
        return OppdatereUnderholdResponse(
            faktiskTilsynsutgift = dtomapper.tilFaktiskTilsynsutgiftDto(oppdatertFaktiskTilsynsutgift),
            underholdskostnad =
                dtomapper.tilUnderholdskostnadsperioderForBehandlingMedKunEttSøknadsbarn(
                    underholdskostnad.behandling,
                ),
            valideringsfeil = underholdskostnad.barnetilsyn.validerePerioder(),
        )
    }

    @Transactional
    fun oppdatereTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        request: OppdatereTilleggsstønadRequest,
    ): OppdatereUnderholdResponse {
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
                    tilleggsstønadRepository.save(
                        Tilleggsstønad(
                            fom = request.periode.fom,
                            tom = request.periode.tom,
                            dagsats = request.dagsats,
                            underholdskostnad = underholdskostnad,
                        ),
                    )
                underholdskostnad.tilleggsstønad.add(tilleggsstønad)
                tilleggsstønad
            }

        return OppdatereUnderholdResponse(
            tilleggsstønad = dtomapper.tilTilleggsstønadDto(oppdatertTilleggsstønad),
            underholdskostnad =
                dtomapper.tilUnderholdskostnadsperioderForBehandlingMedKunEttSøknadsbarn(underholdskostnad.behandling),
            valideringsfeil = underholdskostnad.barnetilsyn.validerePerioder(),
        )
    }

    @Transactional
    fun sletteFraUnderhold(
        behandling: Behandling,
        request: SletteUnderholdselement,
    ): UnderholdDto? {
        request.validere(behandling)

        val underholdskostnad = behandling.underholdskostnader.find { request.idUnderhold == it.id }!!

        when (request.type) {
            Underholdselement.BARN -> return sletteUnderholdskostnad(behandling, underholdskostnad)
            Underholdselement.FAKTISK_TILSYNSUTGIFT -> return sletteFaktiskTilsynsutgift(
                underholdskostnad,
                request.idElement,
            )

            Underholdselement.TILLEGGSSTØNAD -> return sletteTilleggsstønad(underholdskostnad, request.idElement)
            Underholdselement.STØNAD_TIL_BARNETILSYN -> return sletteStønadTilBarnetilsyn(
                underholdskostnad,
                request.idElement,
            )
        }
    }

    private fun sletteStønadTilBarnetilsyn(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ): UnderholdDto {
        val stønadTilBarnetilsyn = underholdskostnad.barnetilsyn.find { idElement == it.id }
        underholdskostnad.barnetilsyn.remove(stønadTilBarnetilsyn)
        return dtomapper.tilUnderholdDto(underholdskostnad)
    }

    private fun sletteTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ): UnderholdDto {
        val tilleggsstønad = underholdskostnad.tilleggsstønad.find { idElement == it.id }
        underholdskostnad.tilleggsstønad.remove(tilleggsstønad)
        return dtomapper.tilUnderholdDto(underholdskostnad)
    }

    private fun sletteUnderholdskostnad(
        behandling: Behandling,
        underholdskostnad: Underholdskostnad,
    ): UnderholdDto? {
        behandling.underholdskostnader.remove(underholdskostnad)
        underholdskostnad.person.underholdskostnad.remove(underholdskostnad)
        if (underholdskostnad.person.underholdskostnad.isEmpty() && underholdskostnad.person.rolle.isEmpty()) {
            personRepository.deleteById(underholdskostnad.person.id!!)
            notatService.sletteNotat(behandling, Notattype.UNDERHOLDSKOSTNAD, behandling.bidragsmottaker!!)
        }
        underholdskostnadRepository.deleteById(underholdskostnad.id!!)
        return null
    }

    private fun sletteFaktiskTilsynsutgift(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ): UnderholdDto {
        val faktiskTilsynsutgift = underholdskostnad.faktiskeTilsynsutgifter.find { idElement == it.id }
        underholdskostnad.faktiskeTilsynsutgifter.remove(faktiskTilsynsutgift)
        return dtomapper.tilUnderholdDto(underholdskostnad)
    }

    private fun lagreUnderholdskostnad(
        behandling: Behandling,
        person: Person,
    ): Underholdskostnad {
        val eksisterendeUnderholdskostnad = behandling.underholdskostnader.find { it.person.id == person.id }

        return if (eksisterendeUnderholdskostnad != null) {
            eksisterendeUnderholdskostnad
        } else {
            val u = underholdskostnadRepository.save(Underholdskostnad(behandling = behandling, person = person))
            behandling.underholdskostnader.add(u)
            u
        }
    }
}
