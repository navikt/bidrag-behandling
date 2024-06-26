package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.rolle.Rolletype

enum class Grunnlagsobjektvelger(
    val behandlinstypeMotRolletyper: Map<TypeBehandling, Set<Rolletype>>,
) {
    AINNTEKT(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRLIGE_UTGIFTER to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    ARBEIDSFORHOLD(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRLIGE_UTGIFTER to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    BARNETILLEGG(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.SÆRLIGE_UTGIFTER to setOf(Rolletype.BIDRAGSPLIKTIG),
        ),
    ),
    BARNETILSYN(emptyMap()),
    KONTANTSTØTTE(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRLIGE_UTGIFTER to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    HUSSTANDSMEDLEMMER_OG_EGNE_BARN(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRLIGE_UTGIFTER to setOf(Rolletype.BIDRAGSPLIKTIG),
        ),
    ),
    SIVILSTAND(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    SKATTEGRUNNLAG(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRLIGE_UTGIFTER to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG(
        mapOf(
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRLIGE_UTGIFTER to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    ;

    companion object {
        fun requestobjekter(
            behandlingstype: TypeBehandling,
            rolletype: Rolletype,
        ): Set<GrunnlagRequestType> {
            return entries.filter { it.behandlinstypeMotRolletyper.keys.contains(behandlingstype) }
                .filter { it.behandlinstypeMotRolletyper.values.first().contains(rolletype) }
                .map { GrunnlagRequestType.valueOf(it.name) }.toSet()
        }
    }
}
