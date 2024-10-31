package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
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
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdResponse
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.behandling.transformers.underhold.tilFaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDto
import no.nav.bidrag.behandling.transformers.underhold.tilTilleggsstønadDto
import no.nav.bidrag.behandling.transformers.underhold.tilUnderholdDto
import no.nav.bidrag.behandling.transformers.underhold.validere
import no.nav.bidrag.behandling.transformers.underhold.validerePerioder
import no.nav.bidrag.domene.enums.barnetilsyn.Skolealder
import no.nav.bidrag.domene.enums.diverse.Kilde
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val log = KotlinLogging.logger {}

@Service
class UnderholdService(
    private val barnetilsynRepository: BarnetilsynRepository,
    private val faktiskTilsynsutgiftRepository: FaktiskTilsynsutgiftRepository,
    private val tilleggsstønadRepository: TilleggsstønadRepository,
    private val underholdskostnadRepository: UnderholdskostnadRepository,
    private val personRepository: PersonRepository,
) {
    @Transactional
    fun angiTilsynsordning(
        underholdskostnad: Underholdskostnad,
        harTilsynsordning: Boolean = true,
    ): Boolean {
        underholdskostnad.harTilsynsordning = harTilsynsordning
        log.info {
            "Setter harTilsynsording til $harTilsynsordning for underholdskostnad med id ${underholdskostnad.id} i behandling ${underholdskostnad.behandling.id} "
        }
        return harTilsynsordning
    }

    @Transactional
    fun oppretteUnderholdskostnad(
        behandling: Behandling,
        gjelderBarn: BarnDto,
    ): Underholdskostnad {
        gjelderBarn.validere()

        gjelderBarn.personident?.let { personidentBarn ->
            val rolle = behandling.søknadsbarn.find { it.ident == personidentBarn.verdi }
            val eksisterendePerson = personRepository.findFirstByIdent(personidentBarn.verdi)
            if (eksisterendePerson == null) {

                val ident = if (rolle == null) personidentBarn.verdi else null
                val roller = rolle?.let { mutableSetOf(it) } ?: mutableSetOf()

                val person = personRepository.save(Person(ident = ident, rolle = roller))
                return lagreUnderholdskostnad(behandling, person)
            } else {
                return lagreUnderholdskostnad(behandling, eksisterendePerson)
            }
        } ?: run {
            return lagreUnderholdskostnad(behandling, personRepository.save(Person(navn = gjelderBarn.navn)))
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
            beregneUnderholdskostnad(
                underholdskostnad,
                Underholdselement.STØNAD_TIL_BARNETILSYN,
            ),
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
            faktiskTilsynsutgift = oppdatertFaktiskTilsynsutgift.tilFaktiskTilsynsutgiftDto(),
            underholdskostnad =
            beregneUnderholdskostnad(
                underholdskostnad,
                Underholdselement.FAKTISK_TILSYNSUGIFT,
            ),
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
            tilleggsstønad = oppdatertTilleggsstønad.tilTilleggsstønadDto(),
            underholdskostnad =
            beregneUnderholdskostnad(
                underholdskostnad,
                Underholdselement.TILLEGGSSTØNAD,
            ),
            valideringsfeil = underholdskostnad.barnetilsyn.validerePerioder(),
        )
    }

    @Transactional
    fun sletteFraUnderhold(
        behandling: Behandling,
        request: SletteUnderholdselement,
    ): UnderholdDto? {
        request.validere(behandling)

        val underholdskostnad = behandling.underholdskostnad.find { request.idUnderhold == it.id }!!

        when (request.type) {
            Underholdselement.BARN -> return sletteUnderholdskostnad(behandling, underholdskostnad)
            Underholdselement.FAKTISK_TILSYNSUGIFT -> return sletteFaktiskTilsynsutgift(
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

    fun sletteStønadTilBarnetilsyn(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ): UnderholdDto {
        val stønadTilBarnetilsyn = underholdskostnad.barnetilsyn.find { idElement == it.id }
        underholdskostnad.barnetilsyn.remove(stønadTilBarnetilsyn)
        return underholdskostnad.tilUnderholdDto(Underholdselement.STØNAD_TIL_BARNETILSYN)
    }

    fun sletteTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ): UnderholdDto {
        val tilleggsstønad = underholdskostnad.tilleggsstønad.find { idElement == it.id }
        underholdskostnad.tilleggsstønad.remove(tilleggsstønad)
        return underholdskostnad.tilUnderholdDto(Underholdselement.TILLEGGSSTØNAD)
    }

    fun sletteUnderholdskostnad(
        behandling: Behandling,
        underholdskostnad: Underholdskostnad,
    ): UnderholdDto? {
        behandling.underholdskostnad.remove(underholdskostnad)
        return null
    }

    fun sletteFaktiskTilsynsutgift(
        underholdskostnad: Underholdskostnad,
        idElement: Long,
    ): UnderholdDto {
        val faktiskTilsynsutgift = underholdskostnad.faktiskeTilsynsutgifter.find { idElement == it.id }
        underholdskostnad.faktiskeTilsynsutgifter.remove(faktiskTilsynsutgift)
        return underholdskostnad.tilUnderholdDto(Underholdselement.FAKTISK_TILSYNSUGIFT)
    }

    private fun lagreUnderholdskostnad(
        behandling: Behandling,
        person: Person,
    ): Underholdskostnad {
        val eksisterendeUnderholdskostnad = behandling.underholdskostnad.find { it.person.id == person.id }

        return if (eksisterendeUnderholdskostnad != null) {
            eksisterendeUnderholdskostnad
        } else {
            val u = underholdskostnadRepository.save(Underholdskostnad(behandling = behandling, person = person))
            behandling.underholdskostnad.add(u)
            u
        }
    }

    // TODO: Erstatte med ny bidragsberegningsmodul
    companion object {
        @Deprecated("Erstatte med ekstern modul")
        fun beregneUnderholdskostnad(
            underholdskostnad: Underholdskostnad,
            underholdselement: Underholdselement,
        ): Set<UnderholdskostnadDto> {
            val perioder =
                when (underholdselement) {
                    Underholdselement.TILLEGGSSTØNAD ->
                        underholdskostnad.tilleggsstønad
                            .sortedBy { it.fom }
                            .map { DatoperiodeDto(it.fom, it.tom) }

                    Underholdselement.STØNAD_TIL_BARNETILSYN ->
                        underholdskostnad.barnetilsyn
                            .sortedBy { it.fom }
                            .map { DatoperiodeDto(it.fom, it.tom) }

                    Underholdselement.FAKTISK_TILSYNSUGIFT ->
                        underholdskostnad.faktiskeTilsynsutgifter
                            .sortedBy { it.fom }
                            .map { DatoperiodeDto(it.fom, it.tom) }

                    else -> throw Exception("Barn er ikke støttet - testkode, erstattes av beregningsmodul.")
                }

            return perioder
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
}
