package no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.opprettVedtakDto
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManueltOverstyrtGebyr
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningGebyr
import no.nav.bidrag.transport.behandling.vedtak.response.EngangsbeløpDto
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OppdaterRolleGebyrTest {
    private val bpPersonRef = "person_BIDRAGSPLIKTIG"
    private val bmPersonRef = "person_BIDRAGSMOTTAKER"
    private val bpSluttberegningRef = "SLUTTBEREGNING_GEBYR_BP"
    private val bmSluttberegningRef = "SLUTTBEREGNING_GEBYR_BM"
    private val bmManueltOverstyrtRef = "MANUELT_OVERSTYRT_GEBYR_BM"

    private fun byggPersonGrunnlag(
        referanse: String,
        type: Grunnlagstype,
        ident: String,
    ) = GrunnlagDto(
        referanse = referanse,
        type = type,
        innhold = POJONode(Person(ident = Personident(ident))),
        grunnlagsreferanseListe = emptyList(),
    )

    private fun byggSluttberegningGebyrGrunnlag(
        referanse: String,
        gjelderReferanse: String,
        ilagtGebyr: Boolean,
        grunnlagsreferanseListe: List<String> = emptyList(),
    ) = GrunnlagDto(
        referanse = referanse,
        type = Grunnlagstype.SLUTTBEREGNING_GEBYR,
        gjelderReferanse = gjelderReferanse,
        innhold = POJONode(SluttberegningGebyr(ilagtGebyr = ilagtGebyr)),
        grunnlagsreferanseListe = grunnlagsreferanseListe,
    )

    private fun byggManueltOverstyrtGebyrGrunnlag(
        referanse: String,
        gjelderReferanse: String,
        ilagtGebyr: Boolean,
        begrunnelse: String,
    ) = GrunnlagDto(
        referanse = referanse,
        type = Grunnlagstype.MANUELT_OVERSTYRT_GEBYR,
        gjelderReferanse = gjelderReferanse,
        innhold = POJONode(ManueltOverstyrtGebyr(ilagtGebyr = ilagtGebyr, begrunnelse = begrunnelse)),
        grunnlagsreferanseListe = emptyList(),
    )

    private fun byggEngangsbeløp(
        type: Engangsbeløptype,
        skyldnerIdent: String,
        referanse: String,
        grunnlagReferanseListe: List<String> = emptyList(),
        saksnummer: String = SAKSNUMMER,
    ) = EngangsbeløpDto(
        type = type,
        skyldner = Personident(skyldnerIdent),
        kravhaver = Personident("NAV"),
        mottaker = Personident("NAV"),
        sak = Saksnummer(saksnummer),
        innkreving = Innkrevingstype.MED_INNKREVING,
        beslutning = Beslutningstype.ENDRING,
        omgjørVedtakId = null,
        eksternReferanse = null,
        resultatkode = "GEBYR_ILAGT",
        beløp = BigDecimal(1270),
        valutakode = "NOK",
        referanse = referanse,
        delytelseId = null,
        grunnlagReferanseListe = grunnlagReferanseListe,
    )

    @Test
    fun `skal sette harGebyrsøknad og GebyrRolle for BP uten manuell overstyring`() {
        val behandling = oppretteBehandling()
        val bpRolle = testdataBP.tilRolle(behandling)
        behandling.roller.add(bpRolle)

        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bpPersonRef, Grunnlagstype.PERSON_BIDRAGSPLIKTIG, testdataBP.ident),
                byggSluttberegningGebyrGrunnlag(bpSluttberegningRef, bpPersonRef, ilagtGebyr = true),
            )

        val vedtakDto =
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_SKYLDNER,
                            testdataBP.ident,
                            "gebyr-bp-ref",
                            grunnlagReferanseListe = listOf(bpSluttberegningRef),
                        ),
                    ),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, vedtakDto)

        assertSoftly(bpRolle) {
            harGebyrsøknad shouldBe true
            gebyr.shouldNotBeNull()
            assertSoftly(gebyr!!) {
                overstyrGebyr shouldBe false
                ilagtGebyr shouldBe true
                begrunnelse.shouldBeNull()
                beregnetIlagtGebyr shouldBe true
            }
        }
    }

    @Test
    fun `skal sette harGebyrsøknad og GebyrRolle for BM med manuell overstyring`() {
        val behandling = oppretteBehandling()
        val bmRolle = testdataBM.tilRolle(behandling)
        behandling.roller.add(bmRolle)

        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bmPersonRef, Grunnlagstype.PERSON_BIDRAGSMOTTAKER, testdataBM.ident),
                byggManueltOverstyrtGebyrGrunnlag(bmManueltOverstyrtRef, bmPersonRef, ilagtGebyr = false, begrunnelse = "test"),
                byggSluttberegningGebyrGrunnlag(
                    bmSluttberegningRef,
                    bmPersonRef,
                    ilagtGebyr = false,
                    grunnlagsreferanseListe = listOf(bmManueltOverstyrtRef),
                ),
            )

        val vedtakDto =
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_MOTTAKER,
                            testdataBM.ident,
                            "gebyr-bm-ref",
                            grunnlagReferanseListe = listOf(bmSluttberegningRef),
                        ),
                    ),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, vedtakDto)

        assertSoftly(bmRolle) {
            harGebyrsøknad shouldBe true
            gebyr.shouldNotBeNull()
            assertSoftly(gebyr!!) {
                overstyrGebyr shouldBe true
                ilagtGebyr shouldBe false
                begrunnelse shouldBe "test"
                beregnetIlagtGebyr shouldBe true
            }
        }
    }

    @Test
    fun `skal ikke oppdatere rolle når ingen SLUTTBEREGNING_GEBYR finnes`() {
        val behandling = oppretteBehandling()
        val bpRolle = testdataBP.tilRolle(behandling)
        behandling.roller.add(bpRolle)

        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bpPersonRef, Grunnlagstype.PERSON_BIDRAGSPLIKTIG, testdataBP.ident),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, opprettVedtakDto())

        assertSoftly(bpRolle) {
            harGebyrsøknad shouldBe false
            gebyr.shouldBeNull()
        }
    }

    @Test
    fun `skal opprette GebyrRolleSøknad med riktige verdier ved treff på grunnlagReferanseListe`() {
        val behandling = oppretteBehandling()
        behandling.roller.add(testdataBP.tilRolle(behandling))

        val engangsbeløpRef = "gebyr-bp-engangsbelop-ref"
        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bpPersonRef, Grunnlagstype.PERSON_BIDRAGSPLIKTIG, testdataBP.ident),
                byggSluttberegningGebyrGrunnlag(bpSluttberegningRef, bpPersonRef, ilagtGebyr = true),
            )

        val vedtakDto =
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_SKYLDNER,
                            testdataBP.ident,
                            engangsbeløpRef,
                            grunnlagReferanseListe = listOf(bpSluttberegningRef),
                        ),
                    ),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, vedtakDto)

        val bpRolle = behandling.roller.first { it.ident == testdataBP.ident }
        val gebyrSøknader = bpRolle.gebyr!!.gebyrSøknader
        gebyrSøknader shouldHaveSize 1
        assertSoftly(gebyrSøknader.first()) {
            saksnummer shouldBe SAKSNUMMER
            søknadsid shouldBe -1L
            gjelder18ÅrSøknad shouldBe false
            referanse shouldBe engangsbeløpRef
            manueltOverstyrtGebyr.shouldBeNull()
        }
    }

    @Test
    fun `skal opprette GebyrRolleSøknad ved treff på skyldner som fallback`() {
        val behandling = oppretteBehandling()
        behandling.roller.add(testdataBP.tilRolle(behandling))

        val engangsbeløpRef = "gebyr-bp-skyldner-ref"
        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bpPersonRef, Grunnlagstype.PERSON_BIDRAGSPLIKTIG, testdataBP.ident),
                byggSluttberegningGebyrGrunnlag(bpSluttberegningRef, bpPersonRef, ilagtGebyr = true),
            )

        // engangsbeløp grunnlagReferanseListe er tom – skal matche på skyldner.verdi == person.personIdent
        val vedtakDto =
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_SKYLDNER,
                            testdataBP.ident,
                            engangsbeløpRef,
                            grunnlagReferanseListe = emptyList(),
                        ),
                    ),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, vedtakDto)

        val bpRolle = behandling.roller.first { it.ident == testdataBP.ident }
        bpRolle.gebyr!!.gebyrSøknader shouldHaveSize 1
        bpRolle.gebyr!!
            .gebyrSøknader
            .first()
            .referanse shouldBe engangsbeløpRef
    }

    @Test
    fun `skal ikke opprette GebyrRolleSøknad når ingen matchende engangsbeløp finnes`() {
        val behandling = oppretteBehandling()
        behandling.roller.add(testdataBP.tilRolle(behandling))

        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bpPersonRef, Grunnlagstype.PERSON_BIDRAGSPLIKTIG, testdataBP.ident),
                byggSluttberegningGebyrGrunnlag(bpSluttberegningRef, bpPersonRef, ilagtGebyr = true),
            )

        // Ingen engangsbeløp med matching referanse eller skyldner
        val vedtakDto =
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_SKYLDNER,
                            "annen-ident-12345",
                            "ukjent-ref",
                            grunnlagReferanseListe = listOf("ukjent-grunnlag-ref"),
                        ),
                    ),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, vedtakDto)

        val bpRolle = behandling.roller.first { it.ident == testdataBP.ident }
        assertSoftly(bpRolle) {
            harGebyrsøknad shouldBe true
            gebyr.shouldNotBeNull()
            gebyr!!.gebyrSøknader shouldHaveSize 0
        }
    }

    @Test
    fun `skal oppdatere begge roller i ett kall`() {
        val behandling = oppretteBehandling()
        val bpRolle = testdataBP.tilRolle(behandling)
        val bmRolle = testdataBM.tilRolle(behandling)
        behandling.roller.addAll(listOf(bpRolle, bmRolle))

        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bpPersonRef, Grunnlagstype.PERSON_BIDRAGSPLIKTIG, testdataBP.ident),
                byggPersonGrunnlag(bmPersonRef, Grunnlagstype.PERSON_BIDRAGSMOTTAKER, testdataBM.ident),
                byggSluttberegningGebyrGrunnlag(bpSluttberegningRef, bpPersonRef, ilagtGebyr = true),
                byggManueltOverstyrtGebyrGrunnlag(bmManueltOverstyrtRef, bmPersonRef, ilagtGebyr = false, begrunnelse = "test"),
                byggSluttberegningGebyrGrunnlag(
                    bmSluttberegningRef,
                    bmPersonRef,
                    ilagtGebyr = false,
                    grunnlagsreferanseListe = listOf(bmManueltOverstyrtRef),
                ),
            )

        val vedtakDto =
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_SKYLDNER,
                            testdataBP.ident,
                            "gebyr-bp-ref",
                            grunnlagReferanseListe = listOf(bpSluttberegningRef),
                        ),
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_MOTTAKER,
                            testdataBM.ident,
                            "gebyr-bm-ref",
                            grunnlagReferanseListe = listOf(bmSluttberegningRef),
                        ),
                    ),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, vedtakDto)

        assertSoftly(bpRolle) {
            harGebyrsøknad shouldBe true
            gebyr.shouldNotBeNull()
            gebyr!!.overstyrGebyr shouldBe false
            gebyr!!.ilagtGebyr shouldBe true
            gebyr!!.begrunnelse.shouldBeNull()
            gebyr!!.beregnetIlagtGebyr shouldBe true
            gebyr!!.gebyrSøknader shouldHaveSize 1
        }

        assertSoftly(bmRolle) {
            harGebyrsøknad shouldBe true
            gebyr.shouldNotBeNull()
            gebyr!!.overstyrGebyr shouldBe true
            gebyr!!.ilagtGebyr shouldBe false
            gebyr!!.begrunnelse shouldBe "test"
            gebyr!!.beregnetIlagtGebyr shouldBe true
            gebyr!!.gebyrSøknader shouldHaveSize 1
        }
    }

    @Test
    fun `GebyrRolleSøknad skal ha riktige verdier for manuelt overstyrt gebyr`() {
        val behandling = oppretteBehandling()
        behandling.roller.add(testdataBM.tilRolle(behandling))

        val grunnlagListe =
            listOf(
                byggPersonGrunnlag(bmPersonRef, Grunnlagstype.PERSON_BIDRAGSMOTTAKER, testdataBM.ident),
                byggManueltOverstyrtGebyrGrunnlag(bmManueltOverstyrtRef, bmPersonRef, ilagtGebyr = false, begrunnelse = "Fritatt"),
                byggSluttberegningGebyrGrunnlag(
                    bmSluttberegningRef,
                    bmPersonRef,
                    ilagtGebyr = true,
                    grunnlagsreferanseListe = listOf(bmManueltOverstyrtRef),
                ),
            )

        val vedtakDto =
            opprettVedtakDto().copy(
                engangsbeløpListe =
                    listOf(
                        byggEngangsbeløp(
                            Engangsbeløptype.GEBYR_MOTTAKER,
                            testdataBM.ident,
                            "gebyr-bm-ref-2",
                            grunnlagReferanseListe = listOf(bmSluttberegningRef),
                        ),
                    ),
            )

        grunnlagListe.oppdaterRolleGebyr(behandling, vedtakDto)

        val bmRolle = behandling.roller.first { it.ident == testdataBM.ident }
        val søknad = bmRolle.gebyr!!.gebyrSøknader.first()
        assertSoftly(søknad) {
            manueltOverstyrtGebyr.shouldNotBeNull()
            manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
            manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
            manueltOverstyrtGebyr!!.begrunnelse shouldBe "Fritatt"
            // beregnetIlagtGebyr = !sluttberegning.ilagtGebyr = !true = false
            manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
        }
    }
}
