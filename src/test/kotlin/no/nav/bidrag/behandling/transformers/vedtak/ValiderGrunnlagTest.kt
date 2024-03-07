package no.nav.bidrag.behandling.transformers.vedtak

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettStønadsendringRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class ValiderGrunnlagTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initPersonMock() {
            stubPersonConsumer()
        }
    }

    val grunnlagBm =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBM.ident,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            foedselsdato = testdataBM.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val grunnlagBp =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBP.ident,
            rolletype = Rolletype.BIDRAGSPLIKTIG,
            foedselsdato = testdataBP.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag1 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn1.ident,
            rolletype = Rolletype.BARN,
            foedselsdato = testdataBarn1.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val søknadsbarnGrunnlag2 =
        Rolle(
            behandling = oppretteBehandling(),
            ident = testdataBarn2.ident,
            rolletype = Rolletype.BARN,
            foedselsdato = testdataBarn2.foedselsdato,
            id = 1L,
        ).tilGrunnlagPerson()
    val personobjekter = setOf(grunnlagBm, grunnlagBp, søknadsbarnGrunnlag1, søknadsbarnGrunnlag2)

    @Test
    fun `skal validere at det ikke finne duplikat referanser`() {
        val request =
            OpprettVedtakRequestDto(
                kilde = Vedtakskilde.MANUELT,
                enhetsnummer = Enhetsnummer("4806"),
                vedtakstidspunkt = LocalDateTime.now(),
                type = Vedtakstype.ENDRING,
                stønadsendringListe = emptyList(),
                engangsbeløpListe = emptyList(),
                behandlingsreferanseListe = emptyList(),
                grunnlagListe =
                    listOf(
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_1",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = listOf("ref_2", "ref_4"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_1",
                            gjelderReferanse = søknadsbarnGrunnlag1.referanse,
                            grunnlagsreferanseListe = listOf("ref_3"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_3",
                            gjelderReferanse = søknadsbarnGrunnlag1.referanse,
                            grunnlagsreferanseListe = listOf("ref_4"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_4",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = listOf("ref_5"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_5",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = emptyList(),
                            innhold = POJONode(""),
                        ),
                    ),
                fastsattILand = null,
                innkrevingUtsattTilDato = null,
                // Settes automatisk av bidrag-vedtak basert på token
                opprettetAv = null,
            )

        val value = shouldThrow<RuntimeException> { request.validerGrunnlagsreferanser() }
        value.message shouldContain "Feil i grunnlagsreferanser: Grunnlagslisten har duplikat grunnlagsreferanser for følgende referanse: ref_1"
    }

    @Test
    fun `skal validere grunnlagsreferanser i opprett vedtak request`() {
        val request =
            OpprettVedtakRequestDto(
                kilde = Vedtakskilde.MANUELT,
                enhetsnummer = Enhetsnummer("4806"),
                vedtakstidspunkt = LocalDateTime.now(),
                type = Vedtakstype.ENDRING,
                stønadsendringListe =
                    listOf(
                        OpprettStønadsendringRequestDto(
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            skyldner = Personident("NAV"),
                            kravhaver = Personident(testdataBM.ident),
                            mottaker = Personident(testdataBarn1.ident),
                            sak = Saksnummer("3123123"),
                            type = Stønadstype.FORSKUDD,
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = listOf("ref_2", "ref_3"),
                            periodeListe =
                                listOf(
                                    OpprettPeriodeRequestDto(
                                        periode = ÅrMånedsperiode(LocalDate.parse("2020-01-01"), null),
                                        beløp = BigDecimal.ZERO,
                                        resultatkode = Resultatkode.FORHØYET_FORSKUDD_100_PROSENT.name,
                                        valutakode = "NOK",
                                        grunnlagReferanseListe = listOf("ref_1"),
                                    ),
                                ),
                        ),
                    ),
                engangsbeløpListe = emptyList(),
                behandlingsreferanseListe = emptyList(),
                grunnlagListe =
                    listOf(
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_1",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = listOf("ref_2", "ref_4"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_2",
                            gjelderReferanse = søknadsbarnGrunnlag1.referanse,
                            grunnlagsreferanseListe = listOf("ref_3"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_3",
                            gjelderReferanse = søknadsbarnGrunnlag1.referanse,
                            grunnlagsreferanseListe = listOf("ref_4"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_4",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = listOf("ref_5"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_5",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = emptyList(),
                            innhold = POJONode(""),
                        ),
                    ) + personobjekter.toList().map { it.tilOpprettRequestDto() },
                fastsattILand = null,
                innkrevingUtsattTilDato = null,
                // Settes automatisk av bidrag-vedtak basert på token
                opprettetAv = null,
            )

        shouldNotThrowAny { request.validerGrunnlagsreferanser() }
    }

    @Test
    fun `skal feile validering grunnlagsreferanser i opprett vedtak request hvis referert grunnlag ikke finnes`() {
        val request =
            OpprettVedtakRequestDto(
                kilde = Vedtakskilde.MANUELT,
                enhetsnummer = Enhetsnummer("4806"),
                vedtakstidspunkt = LocalDateTime.now(),
                type = Vedtakstype.ENDRING,
                stønadsendringListe =
                    listOf(
                        OpprettStønadsendringRequestDto(
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            skyldner = Personident("NAV"),
                            kravhaver = Personident(testdataBM.ident),
                            mottaker = Personident(testdataBarn1.ident),
                            sak = Saksnummer("3123123"),
                            type = Stønadstype.FORSKUDD,
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = listOf("ref_2", "ref_3"),
                            periodeListe =
                                listOf(
                                    OpprettPeriodeRequestDto(
                                        periode = ÅrMånedsperiode(LocalDate.parse("2020-01-01"), null),
                                        beløp = BigDecimal.ZERO,
                                        resultatkode = Resultatkode.FORHØYET_FORSKUDD_100_PROSENT.name,
                                        valutakode = "NOK",
                                        grunnlagReferanseListe = listOf("ref_1"),
                                    ),
                                ),
                        ),
                    ),
                engangsbeløpListe = emptyList(),
                behandlingsreferanseListe = emptyList(),
                grunnlagListe =
                    listOf(
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_1",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = listOf("ref_2", "ref_4"),
                            innhold = POJONode(""),
                        ),
                    ) + personobjekter.toList().map { it.tilOpprettRequestDto() },
                fastsattILand = null,
                innkrevingUtsattTilDato = null,
                // Settes automatisk av bidrag-vedtak basert på token
                opprettetAv = null,
            )

        val value = shouldThrow<RuntimeException> { request.validerGrunnlagsreferanser() }
        value.message shouldBe "Feil i grunnlagsreferanser: Grunnlaget med referanse \"ref_2\" referert av \"ref_1\" finnes ikke i grunnlagslisten\n" +
            "Grunnlaget med referanse \"ref_4\" referert av \"ref_1\" finnes ikke i grunnlagslisten\n" +
            "Grunnlaget med referanse \"ref_2\" referert av \"Stønadsendring(FORSKUDD, *1*2*3*1*)\" finnes ikke i grunnlagslisten\n" +
            "Grunnlaget med referanse \"ref_3\" referert av \"Stønadsendring(FORSKUDD, *1*2*3*1*)\" finnes ikke i grunnlagslisten"
    }

    @Test
    fun `skal feile validering grunnlagsreferanser i opprett vedtak request hvis referert grunnlag er syklisk`() {
        val request =
            OpprettVedtakRequestDto(
                kilde = Vedtakskilde.MANUELT,
                enhetsnummer = Enhetsnummer("4806"),
                vedtakstidspunkt = LocalDateTime.now(),
                type = Vedtakstype.ENDRING,
                stønadsendringListe =
                    listOf(
                        OpprettStønadsendringRequestDto(
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            skyldner = Personident("NAV"),
                            kravhaver = Personident(testdataBM.ident),
                            mottaker = Personident(testdataBarn1.ident),
                            sak = Saksnummer("3123123"),
                            type = Stønadstype.FORSKUDD,
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = listOf("ref_2"),
                            periodeListe =
                                listOf(
                                    OpprettPeriodeRequestDto(
                                        periode = ÅrMånedsperiode(LocalDate.parse("2020-01-01"), null),
                                        beløp = BigDecimal.ZERO,
                                        resultatkode = Resultatkode.FORHØYET_FORSKUDD_100_PROSENT.name,
                                        valutakode = "NOK",
                                        grunnlagReferanseListe = listOf("ref_1"),
                                    ),
                                ),
                        ),
                    ),
                engangsbeløpListe = emptyList(),
                behandlingsreferanseListe = emptyList(),
                grunnlagListe =
                    listOf(
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_1",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = listOf("ref_2"),
                            innhold = POJONode(""),
                        ),
                        OpprettGrunnlagRequestDto(
                            type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                            referanse = "ref_2",
                            gjelderReferanse = grunnlagBm.referanse,
                            grunnlagsreferanseListe = listOf("ref_1"),
                            innhold = POJONode(""),
                        ),
                    ) + personobjekter.toList().map { it.tilOpprettRequestDto() },
                fastsattILand = null,
                innkrevingUtsattTilDato = null,
                // Settes automatisk av bidrag-vedtak basert på token
                opprettetAv = null,
            )

        val value = shouldThrow<RuntimeException> { request.validerGrunnlagsreferanser() }
        value.message shouldBe "Feil i grunnlagsreferanser: Grunnlaget med referanse \"ref_2\" referert av \"ref_1\" inneholder sirkulær avhengighet. Referanseliste [ref_1, person_PERSON_BIDRAGSMOTTAKER_19780825_1]\n" +
            "Grunnlaget med referanse \"ref_1\" referert av \"ref_2\" inneholder sirkulær avhengighet. Referanseliste [ref_2, person_PERSON_BIDRAGSMOTTAKER_19780825_1]"
    }
}
