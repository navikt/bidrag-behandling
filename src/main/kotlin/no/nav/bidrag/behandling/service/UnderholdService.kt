package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.FaktiskTilsynsutgiftDto
import no.nav.bidrag.behandling.dto.v2.underhold.OppdatereUnderholdReponse
import no.nav.bidrag.behandling.dto.v2.underhold.SletteUnderholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.StønadTilBarnetilsynDto
import no.nav.bidrag.behandling.dto.v2.underhold.TilleggsstønadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdDto
import no.nav.bidrag.behandling.dto.v2.underhold.Underholdselement
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.behandling.transformers.person.tilPersoninfoDto
import no.nav.bidrag.behandling.transformers.underhold.validere
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate

@Service
class UnderholdService(
    private val beregneBidragService: BeregneBidragService,
) {

    @Transactional
    fun oppdatereStønadTilBarnetilsyn(
        underholdskostnad: Underholdskostnad,
        request: StønadTilBarnetilsynDto,
    ): OppdatereUnderholdReponse {
        request.validere(underholdskostnad)
        // TODO: implementere

        request.id?.let { id ->
            underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id }
        }

        return OppdatereUnderholdReponse(underholdskostnad = UnderholdskostnadDto(DatoperiodeDto(LocalDate.now())))
    }

    @Transactional
    fun oppdatereFaktiskTilsynsutgift(
        underholdskostnad: Underholdskostnad,
        request: FaktiskTilsynsutgiftDto,
    ): OppdatereUnderholdReponse {
        request.validere(underholdskostnad)
        // TODO: implementere

        request.id?.let { id ->
            underholdskostnad.faktiskeTilsynsutgifter.find { id == it.id }
        }
        return OppdatereUnderholdReponse(underholdskostnad = UnderholdskostnadDto(DatoperiodeDto(LocalDate.now())))
    }

    @Transactional
    fun oppdatereTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        request: TilleggsstønadDto,
    ): OppdatereUnderholdReponse {
        request.validere(underholdskostnad)
        // TODO: implementere

        request.id?.let { id ->
            underholdskostnad.tilleggsstønad.find { id == it.id }
        }

        return OppdatereUnderholdReponse(underholdskostnad = UnderholdskostnadDto(DatoperiodeDto(LocalDate.now())))
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
            Underholdselement.FAKTISK_TILSYNSUGIFT -> sletteFaktiskTilsynsutgift(underholdskostnad, request.idElement)
            Underholdselement.TILLEGGSSTØNAD -> sletteTilleggsstønad(underholdskostnad, request.idElement)
            Underholdselement.STØNAD_TIL_BARNETILSYN -> sletteStønadTilBarnetilsyn(underholdskostnad, request.idElement)
        }

        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Underholdselement ${request.type} finnes ikke. Behandling ${behandling.id} ble ikke oppdatert",
        )
    }

    fun sletteStønadTilBarnetilsyn(
        underholdskostnad: Underholdskostnad,
        idElement: Long
    ): UnderholdDto {
        val stønadTilBarnetilsyn = underholdskostnad.barnetilsyn.find { idElement == it.id }
        underholdskostnad.barnetilsyn.remove(stønadTilBarnetilsyn)
        return underholdskostnad.tilUnderholdDto()
    }

    fun sletteTilleggsstønad(
        underholdskostnad: Underholdskostnad,
        idElement: Long
    ): UnderholdDto {
        val tilleggsstønad = underholdskostnad.tilleggsstønad.find { idElement == it.id }
        underholdskostnad.tilleggsstønad.remove(tilleggsstønad)
        return underholdskostnad.tilUnderholdDto()
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
        return underholdskostnad.tilUnderholdDto()
    }

    fun Underholdskostnad.tilUnderholdDto() =
        UnderholdDto(
            id = this.id!!,
            gjelderBarn = this.person.tilPersoninfoDto(this.behandling),
            underholdskostnad = beregneBidragService.beregneUnderholdskostnad().tilUnderholdskostnad(),
            faktiskeTilsynsutgifter = this.faktiskeTilsynsutgifter.tilFaktiskeTilsynsutgiftDto(),
        )

    fun Set<FaktiskTilsynsutgift>.tilFaktiskeTilsynsutgiftDto() =
        this
            .map {
                FaktiskTilsynsutgiftDto(
                    id = it.id!!,
                    periode = DatoperiodeDto(it.fom, it.tom),
                    utgift = it.tilsynsutgift,
                    kostpenger = it.kostpenger ?: BigDecimal.ZERO,
                )
            }.toSet()
}

fun Set<UnderholdskostnadPeriodisert>.tilUnderholdskostnad() =
    this
        .map {
            // TODO: Implement me
            UnderholdskostnadDto(
                periode = DatoperiodeDto(it.fom, it.tom),
            )
        }.toSet()

fun oppretteUnderholdDtoMock() =
    UnderholdDto(
        id = 1L,
        faktiskeTilsynsutgifter = emptySet(),
        gjelderBarn = PersoninfoDto(),
        underholdskostnad = emptySet()
    )

// TODO: Erstatte med ny bidragsberegningsmodul
@Deprecated("Erstattes av ny modul for beregning av underholdskostnad i bidrag-beregning-felles")
@Component
class BeregneBidragService {
    fun beregneUnderholdskostnad(): Set<UnderholdskostnadPeriodisert> = emptySet()
}

// TODO: Erstatte med objekt fra beregningsmodulen
data class UnderholdskostnadPeriodisert(
    val fom: LocalDate,
    val tom: LocalDate? = null,
    val forbruk: BigDecimal = BigDecimal.ZERO,
    val boutgifter: BigDecimal = BigDecimal.ZERO,
    val stønadTilBarnetilsyn: BigDecimal = BigDecimal.ZERO,
    val beregnetTilsynsutgift: BigDecimal = BigDecimal.ZERO,
    val barnetrygd: BigDecimal = BigDecimal.ZERO,
    val underholdskostnad: BigDecimal = BigDecimal.ZERO,
)
