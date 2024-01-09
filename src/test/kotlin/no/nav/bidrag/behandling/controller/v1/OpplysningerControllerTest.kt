package no.nav.bidrag.behandling.controller.v1

import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.deprecated.dto.AddOpplysningerRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataDto
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

class OpplysningerControllerTest : KontrollerTestRunner() {
    @Autowired
    private lateinit var grunnlagRepository: GrunnlagRepository

    @Test
    fun `skal opprette og oppdatere opplysninger`() {
        val roller =
            setOf(
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(568),
                ),
            )
        val testBehandlingMedNull =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp\"}", false, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp1\"}", true, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET)

        val oppAktivResult1 =
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                behandlingId,
                Grunnlagsdatatype.BOFORHOLD_BEARBEIDET,
            )

        Assertions.assertEquals("{\"test\": \"opp1\"}", oppAktivResult1!!.data)
    }

    @Test
    fun `skal ikke være mulig å opprette flere aktive opplysninger`() {
        val roller =
            setOf(
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(429),
                ),
            )
        val testBehandlingMedNull =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp\"}", true, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp2\"}", true, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET)

        // 3. Assert that opp1 is active
        val oppAktivResult1 =
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                behandlingId,
                Grunnlagsdatatype.BOFORHOLD_BEARBEIDET,
            )

        Assertions.assertEquals("{\"test\": \"opp2\"}", oppAktivResult1!!.data)
    }

    @Test
    fun `skal opprette og oppdatere opplysninger1`() {
        val roller =
            setOf(
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(409),
                ),
            )
        val testBehandlingMedNull =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp\"}", false, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp1\"}", false, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"inn1\"}", false, Grunnlagsdatatype.INNTEKT_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"inn2\"}", false, Grunnlagsdatatype.INNTEKT_BEARBEIDET)

        // 3. Assert that opp1 is active
        val oppAktivResult1 =
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                behandlingId,
                Grunnlagsdatatype.BOFORHOLD_BEARBEIDET,
            )
        Assertions.assertEquals("{\"test\": \"opp1\"}", oppAktivResult1?.data)

        // 4. Assert that inn1 is active
        val oppAktivResult2 =
            grunnlagRepository.findTopByBehandlingIdAndTypeOrderByInnhentetDescIdDesc(
                behandlingId,
                Grunnlagsdatatype.INNTEKT_BEARBEIDET,
            )
        Assertions.assertEquals("{\"test\": \"inn2\"}", oppAktivResult2!!.data)
    }

    private fun skalOppretteOpplysninger(
        behandlingId: Long,
        data: String,
        aktiv: Boolean,
        opplysningerType: Grunnlagsdatatype,
    ): GrunnlagsdataDto {
        val opplysninger = createOpplysninger(behandlingId, data, aktiv, opplysningerType)

        val opp =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger",
                HttpMethod.POST,
                HttpEntity(opplysninger),
                GrunnlagsdataDto::class.java,
            )

        Assertions.assertEquals(HttpStatus.OK, opp.statusCode)
        val body = opp.body!!
        Assertions.assertEquals(behandlingId, body.behandlingsid)

        return body
    }

    private fun createOpplysninger(
        behandlingId: Long,
        data: String,
        aktiv: Boolean,
        opplysningerType: Grunnlagsdatatype,
    ): AddOpplysningerRequest {
        return AddOpplysningerRequest(behandlingId, aktiv, opplysningerType, data, LocalDate.parse("2025-02-01"))
    }
}
