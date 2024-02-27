package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.verify
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class BeregningServiceTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    lateinit var beregningService: BeregningService

    @BeforeEach
    fun initMocks() {
        beregningService =
            BeregningService(
                behandlingService,
            )
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
    }

    @Test
    fun `skal bygge grunnlag for beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        mockkConstructor(BeregnForskuddApi::class)
        every { BeregnForskuddApi().beregn(capture(beregnCapture)) } answers { callOriginal() }
        val resultat = beregningService.beregneForskudd(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 2) {
            BeregnForskuddApi().beregn(any())
        }
        resultat shouldHaveSize 2
        resultat[0].resultat.grunnlagListe shouldHaveSize 35
        beregnGrunnlagList shouldHaveSize 2
        assertSoftly(beregnGrunnlagList[0]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.from(behandling.datoTom?.plusDays(1))
            it.grunnlagListe shouldHaveSize 16

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 4
            personer.hentPerson(testdataBarn1.ident) shouldNotBe null

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 5
        }
        assertSoftly(beregnGrunnlagList[1]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.from(behandling.datoTom?.plusDays(1))
            it.grunnlagListe shouldHaveSize 14

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 4
            personer.hentPerson(testdataBarn2.ident) shouldNotBe null

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 3
        }
    }
}
