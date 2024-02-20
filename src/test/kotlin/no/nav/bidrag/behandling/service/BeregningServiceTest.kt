package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.utils.ROLLE_BA_1
import no.nav.bidrag.behandling.utils.ROLLE_BA_2
import no.nav.bidrag.behandling.utils.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class BeregningServiceTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var beregnForskuddApi: BeregnForskuddApi
    lateinit var beregningService: BeregningService

    @BeforeEach
    fun initMocks() {
        beregningService =
            BeregningService(
                behandlingService,
                beregnForskuddApi,
            )
        every { beregnForskuddApi.beregn(any()) } returns BeregnetForskuddResultat()
    }

    @Test
    fun `skal bygge grunnlag for beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        every { behandlingService.hentBehandlingById(any(), any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        every { beregnForskuddApi.beregn(capture(beregnCapture)) } returns BeregnetForskuddResultat()

        beregningService.beregneForskudd(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 2) {
            beregnForskuddApi.beregn(any())
        }
        beregnGrunnlagList shouldHaveSize 2
        assertSoftly(beregnGrunnlagList[0]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.from(behandling.datoTom?.plusDays(1))
            it.grunnlagListe shouldHaveSize 16

            val personer =
                it.grunnlagListe.hentAllePersoner()
            personer shouldHaveSize 5
            personer.any {
                objectmapper.treeToValue(
                    it.innhold,
                    Person::class.java,
                ).ident == ROLLE_BA_1.fødselsnummer
            } shouldBe true

            val bostatuser =
                it.grunnlagListe.filter { gl -> gl.type == Grunnlagstype.BOSTATUS_PERIODE }
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filter { gl -> gl.type == Grunnlagstype.SIVILSTAND_PERIODE }
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filter { gl -> gl.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE }
            inntekter shouldHaveSize 3
        }
        assertSoftly(beregnGrunnlagList[1]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.from(behandling.datoTom?.plusDays(1))
            it.grunnlagListe shouldHaveSize 14

            val personer =
                it.grunnlagListe.hentAllePersoner()
            personer shouldHaveSize 4
            personer.any {
                objectmapper.treeToValue(
                    it.innhold,
                    Person::class.java,
                ).ident == ROLLE_BA_2.fødselsnummer
            } shouldBe true

            val bostatuser =
                it.grunnlagListe.filter { gl -> gl.type == Grunnlagstype.BOSTATUS_PERIODE }
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filter { gl -> gl.type == Grunnlagstype.SIVILSTAND_PERIODE }
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filter { gl -> gl.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE }
            inntekter shouldHaveSize 3
        }
    }
}
