package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdRequest
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdResponse
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.underhold.tilFaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDto
import no.nav.bidrag.behandling.transformers.underhold.tilTilleggsstønadDto
import no.nav.bidrag.behandling.transformers.underhold.validere
import no.nav.bidrag.behandling.transformers.underhold.validerePerioder
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.diverse.Kilde
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

@Service
class UnderholdService(
    private val underholdskostnadRepository: UnderholdskostnadRepository,
    private val personRepository: PersonRepository,
    private val notatService: NotatService,
    private val dtomapper: Dtomapper,
) {
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

        return gjelderBarn.personident?.let { personidentBarn ->
            val rolle = behandling.søknadsbarn.find { it.ident == personidentBarn.verdi }
            personRepository.findFirstByIdent(personidentBarn.verdi)?.let {
                lagreUnderholdskostnad(behandling, it)
            } ?: run {
                val roller = rolle?.let { mutableSetOf(it) } ?: mutableSetOf()
                val person = personRepository.save(Person(ident = personidentBarn.verdi, rolle = roller))
                person.rolle.forEach { it.person = person }

                lagreUnderholdskostnad(behandling, person)
            }
        } ?: run {
            lagreUnderholdskostnad(
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
                    )
                underholdskostnad.barnetilsyn.add(barnetilsyn)
                underholdskostnadRepository.save(underholdskostnad).barnetilsyn.last()
            }

        return OppdatereUnderholdResponse(
            stønadTilBarnetilsyn = oppdatertBarnetilsyn.tilStønadTilBarnetilsynDto(),
            underholdskostnad = beregneUnderholdskostnad(underholdskostnad),
            valideringsfeil = underholdskostnad.barnetilsyn.validerePerioder(),
        )
    }

    @Transactional
    fun oppdatereFaktiskeTilsynsutgifter(
        underholdskostnad: Underholdskostnad,
        request: FaktiskTilsynsutgiftDto,
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
                    FaktiskTilsynsutgift(
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        kostpenger = request.kostpenger,
                        tilsynsutgift = request.utgift,
                        kommentar = request.kommentar,
                        underholdskostnad = underholdskostnad,
                    )
                underholdskostnad.faktiskeTilsynsutgifter.add(faktiskTilsynsutgift)
                underholdskostnadRepository.save(underholdskostnad).faktiskeTilsynsutgifter.last()
            }

        return OppdatereUnderholdResponse(
            faktiskTilsynsutgift = oppdatertFaktiskTilsynsutgift.tilFaktiskTilsynsutgiftDto(),
            underholdskostnad = beregneUnderholdskostnad(underholdskostnad),
            valideringsfeil = underholdskostnad.barnetilsyn.validerePerioder(),
        )
    }

    @Transactional
    fun oppdatereTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        request: TilleggsstønadDto,
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
                    Tilleggsstønad(
                        fom = request.periode.fom,
                        tom = request.periode.tom,
                        dagsats = request.dagsats,
                        underholdskostnad = underholdskostnad,
                    )
                underholdskostnad.tilleggsstønad.add(tilleggsstønad)
                underholdskostnadRepository.save(underholdskostnad).tilleggsstønad.last()
            }

        return OppdatereUnderholdResponse(
            tilleggsstønad = oppdatertTilleggsstønad.tilTilleggsstønadDto(),
            underholdskostnad = beregneUnderholdskostnad(underholdskostnad),
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
        personRepository.deleteById(underholdskostnad.person.id!!)
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

    // TODO: Erstatte med ny bidragsberegningsmodul
    companion object {
        @Deprecated("Erstatte med ekstern modul")
        fun beregneUnderholdskostnad(underholdskostnad: Underholdskostnad): Set<UnderholdskostnadDto> =
            when {
                underholdskostnad.tilleggsstønad.isNotEmpty() -> {
                    underholdskostnad.tilleggsstønad
                        .sortedBy { it.fom }
                        .map { DatoperiodeDto(it.fom, it.tom) }
                        .tilUnderholdskostnadDto()
                }

                underholdskostnad.barnetilsyn.isNotEmpty() -> {
                    underholdskostnad.barnetilsyn
                        .sortedBy { it.fom }
                        .map { DatoperiodeDto(it.fom, it.tom) }
                        .tilUnderholdskostnadDto()
                }

                underholdskostnad.faktiskeTilsynsutgifter.isNotEmpty() -> {
                    underholdskostnad.faktiskeTilsynsutgifter
                        .sortedBy { it.fom }
                        .map { DatoperiodeDto(it.fom, it.tom) }
                        .tilUnderholdskostnadDto()
                }

                else -> emptySet()
            }

        fun List<DatoperiodeDto>.tilUnderholdskostnadDto() =
            this
                .map {
                    UnderholdskostnadDto(
                        periode = it,
                        forbruk = BigDecimal(5000),
                        boutgifter = BigDecimal(15450),
                        stønadTilBarnetilsyn = BigDecimal(3000),
                        tilsynsutgifter = BigDecimal(6000),
                        barnetrygd = BigDecimal(4000),
                    )
                }.toSet()
    }
}
