package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktivInntektDto
import no.nav.bidrag.behandling.transformers.grunnlag.grunnlagsdataTyperYtelser
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import org.junit.jupiter.api.BeforeEach
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

abstract class AktivGrunnlagTestFelles {
    @BeforeEach
    fun initMocks() {
        stubPersonConsumer()
        stubKodeverkProvider()
    }

    fun opprettHusstandsmedlemmer(behandling: Behandling) =
        setOf(
            behandling.oppretteHusstandsmedlem(
                null,
                testdataBarn1.ident,
                testdataBarn1.navn,
                testdataBarn1.fødselsdato,
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(5),
            ),
            behandling.oppretteHusstandsmedlem(
                null,
                testdataBarn2.ident,
                testdataBarn2.navn,
                LocalDate.now().plusMonths(5),
                behandling.virkningstidspunkt,
                behandling.virkningstidspunkt!!.plusMonths(5),
            ),
        )

    fun byggBehandling(): Behandling {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak()
        val grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse_endringsdiff.json",
            )

        behandling.grunnlag = grunnlag
        return behandling
    }

    fun beregnYtelser(
        behandling: Behandling,
        inntekter: Set<Inntekt>,
    ): Set<IkkeAktivInntektDto> =
        grunnlagsdataTyperYtelser
            .flatMap {
                behandling.grunnlag.toList().hentEndringerInntekter(
                    behandling.bidragsmottaker!!,
                    inntekter,
                    it,
                )
            }.toSet()

    fun opprettInntekt(
        datoFom: YearMonth,
        datoTom: YearMonth?,
        type: Inntektsrapportering = Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT,
        inntektstyper: List<Pair<Inntektstype, BigDecimal>> = emptyList(),
        inntektstyperKode: List<Pair<String, BigDecimal>> = emptyList(),
        ident: String = "",
        gjelderBarn: String? = null,
        taMed: Boolean = true,
        beløp: BigDecimal = BigDecimal.ONE,
    ) = Inntekt(
        datoTom = null,
        datoFom = null,
        opprinneligFom = datoFom.atDay(1),
        opprinneligTom = datoTom?.atEndOfMonth(),
        belop = beløp,
        ident = ident,
        gjelderBarn = gjelderBarn,
        id = Random.nextLong(1000),
        kilde = Kilde.OFFENTLIG,
        taMed = taMed,
        type = type,
        inntektsposter =
            (
                inntektstyper.map {
                    Inntektspost(
                        beløp = it.second,
                        inntektstype = it.first,
                        kode = "",
                    )
                } +
                    inntektstyperKode.map {
                        Inntektspost(
                            beløp = it.second,
                            inntektstype = null,
                            kode = it.first,
                        )
                    }
            ).toMutableSet(),
    )
}
