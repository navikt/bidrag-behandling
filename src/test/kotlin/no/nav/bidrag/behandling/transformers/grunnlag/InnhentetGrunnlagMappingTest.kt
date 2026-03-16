package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.matchers.shouldBe
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import org.junit.jupiter.api.Test

class InnhentetGrunnlagMappingTest {
    @Test
    fun `skal korrigere referanse med negativ rolleId og beholde årssuffiks`() {
        val correctedReference =
            korrigerPersonreferanse(
                currentRef = "innhentet_skattegrunnlag_person_PERSON_BIDRAGSPLIKTIG_20000301_-1180923499_2024",
                personobjekter =
                    setOf(
                        opprettPersonGrunnlag(
                            referanse = "person_PERSON_BIDRAGSPLIKTIG_20000301_-42",
                            type = Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
                        ),
                    ),
            )

        correctedReference shouldBe "innhentet_skattegrunnlag_person_PERSON_BIDRAGSPLIKTIG_20000301_-42_2024"
    }

    @Test
    fun `skal korrigere referanse med ekstra felt og beholde suffix`() {
        val correctedReference =
            korrigerPersonreferanse(
                currentRef = "innhentet_barnetillegg_person_PERSON_BIDRAGSMOTTAKER_19780825_BIDRAG_1303789909_PENSJON",
                personobjekter =
                    setOf(
                        opprettPersonGrunnlag(
                            referanse = "person_PERSON_BIDRAGSMOTTAKER_19780825_BIDRAG_999",
                            type = Grunnlagstype.PERSON_BIDRAGSMOTTAKER,
                        ),
                    ),
            )

        correctedReference shouldBe
            "innhentet_barnetillegg_person_PERSON_BIDRAGSMOTTAKER_19780825_BIDRAG_999_PENSJON"
    }
}

/**
 * Oppretter et minimalt persongrunnlag for testing av referansekorrigering.
 */
private fun opprettPersonGrunnlag(
    referanse: String,
    type: Grunnlagstype,
): GrunnlagDto =
    GrunnlagDto(
        referanse = referanse,
        gjelderReferanse = referanse,
        grunnlagsreferanseListe = emptyList(),
        type = type,
        innhold = POJONode("test"),
    )

/**
 * Kaller den private mapper-funksjonen via refleksjon for å kunne teste konkrete referansevarianter.
 */
private fun korrigerPersonreferanse(
    currentRef: String,
    personobjekter: Set<GrunnlagDto>,
): String {
    val mappingClass = Class.forName("no.nav.bidrag.behandling.transformers.grunnlag.InnhentetGrunnlagMappingKt")
    val method =
        mappingClass.getDeclaredMethod(
            "korrigerPersonReferanseIGrunnlagsreferansen",
            String::class.java,
            Set::class.java,
        )
    method.isAccessible = true
    return method.invoke(null, currentRef, personobjekter) as String
}
