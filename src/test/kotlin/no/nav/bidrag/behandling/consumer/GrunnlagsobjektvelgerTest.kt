package no.nav.bidrag.behandling.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.junit.jupiter.api.Test

class GrunnlagsobjektvelgerTest {
    @Test
    open fun `teste grunnlagsobjektvelger for forskudd`() {
        val behandlingstype = TypeBehandling.FORSKUDD

        assertSoftly(Grunnlagsobjektvelger.requestobjekter(behandlingstype, Rolletype.BIDRAGSPLIKTIG)) { objekterBp ->
            objekterBp shouldHaveSize 4
            objekterBp.find { GrunnlagRequestType.AINNTEKT == it } != null
            objekterBp.find { GrunnlagRequestType.ARBEIDSFORHOLD == it } != null
            objekterBp.find { GrunnlagRequestType.BARNETILLEGG == it } != null
            objekterBp.find { GrunnlagRequestType.SKATTEGRUNNLAG == it } != null
        }

        assertSoftly(Grunnlagsobjektvelger.requestobjekter(behandlingstype, Rolletype.BIDRAGSMOTTAKER)) { objekterBm ->
            objekterBm shouldHaveSize 8
            objekterBm.find { GrunnlagRequestType.AINNTEKT == it } != null
            objekterBm.find { GrunnlagRequestType.ARBEIDSFORHOLD == it } != null
            objekterBm.find { GrunnlagRequestType.BARNETILLEGG == it } != null
            objekterBm.find { GrunnlagRequestType.HUSSTANDSMEDLEMMER_OG_EGNE_BARN == it } != null
            objekterBm.find { GrunnlagRequestType.KONTANTSTØTTE == it } != null
            objekterBm.find { GrunnlagRequestType.SIVILSTAND == it } != null
            objekterBm.find { GrunnlagRequestType.SKATTEGRUNNLAG == it } != null
            objekterBm.find { GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG == it } != null
        }

        assertSoftly(Grunnlagsobjektvelger.requestobjekter(behandlingstype, Rolletype.BARN)) { objekterBa ->
            objekterBa shouldHaveSize 3
            objekterBa.find { GrunnlagRequestType.AINNTEKT == it } != null
            objekterBa.find { GrunnlagRequestType.ARBEIDSFORHOLD == it } != null
            objekterBa.find { GrunnlagRequestType.SKATTEGRUNNLAG == it } != null
        }
    }

    @Test
    open fun `teste grunnlagsobjektvelger for særbidrag`() {
        val behandlingstype = TypeBehandling.SÆRBIDRAG

        assertSoftly(Grunnlagsobjektvelger.requestobjekter(behandlingstype, Rolletype.BIDRAGSPLIKTIG)) { objekterBp ->
            objekterBp shouldHaveSize 5
            objekterBp.find { GrunnlagRequestType.AINNTEKT == it } != null
            objekterBp.find { GrunnlagRequestType.ARBEIDSFORHOLD == it } != null
            objekterBp.find { GrunnlagRequestType.BARNETILLEGG == it } != null
            objekterBp.find { GrunnlagRequestType.HUSSTANDSMEDLEMMER_OG_EGNE_BARN == it } != null
            objekterBp.find { GrunnlagRequestType.SKATTEGRUNNLAG == it } != null
        }

        assertSoftly(Grunnlagsobjektvelger.requestobjekter(behandlingstype, Rolletype.BIDRAGSMOTTAKER)) { objekterBm ->
            objekterBm shouldHaveSize 7
            objekterBm.find { GrunnlagRequestType.AINNTEKT == it } != null
            objekterBm.find { GrunnlagRequestType.ARBEIDSFORHOLD == it } != null
            objekterBm.find { GrunnlagRequestType.BARNETILLEGG == it } != null
            objekterBm.find { GrunnlagRequestType.KONTANTSTØTTE == it } != null
            objekterBm.find { GrunnlagRequestType.SKATTEGRUNNLAG == it } != null
            objekterBm.find { GrunnlagRequestType.UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG == it } != null
        }

        assertSoftly(Grunnlagsobjektvelger.requestobjekter(behandlingstype, Rolletype.BARN)) { objekterBa ->
            objekterBa shouldHaveSize 3
            objekterBa.find { GrunnlagRequestType.AINNTEKT == it } != null
            objekterBa.find { GrunnlagRequestType.ARBEIDSFORHOLD == it } != null
            objekterBa.find { GrunnlagRequestType.SKATTEGRUNNLAG == it } != null
        }
    }
}
