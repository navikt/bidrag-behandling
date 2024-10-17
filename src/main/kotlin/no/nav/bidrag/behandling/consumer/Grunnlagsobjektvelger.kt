package no.nav.bidrag.behandling.consumer

import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagRequestType
import no.nav.bidrag.domene.enums.rolle.Rolletype

enum class Grunnlagsobjektvelger(
    val behandlinstypeMotRolletyper: Map<TypeBehandling, Set<Rolletype>>,
) {
    AINNTEKT(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    ARBEIDSFORHOLD(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    BARNETILLEGG(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
        ),
    ),

    // TODO: Verifsere for bidrag
    BARNETILSYN(mapOf(TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG))),
    KONTANTSTØTTE(
        mapOf(
            // TODO: Verifsere for bidrag
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    HUSSTANDSMEDLEMMER_OG_EGNE_BARN(
        mapOf(
            // TODO: Verifsere for bidrag
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSPLIKTIG),
        ),
    ),
    SIVILSTAND(
        mapOf(
            // TODO: Verifsere for bidrag
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    SKATTEGRUNNLAG(
        mapOf(
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG, Rolletype.BARN),
            TypeBehandling.SÆRBIDRAG to
                setOf(
                    Rolletype.BIDRAGSMOTTAKER,
                    Rolletype.BIDRAGSPLIKTIG,
                    Rolletype.BARN,
                ),
        ),
    ),
    UTVIDET_BARNETRYGD_OG_SMÅBARNSTILLEGG(
        mapOf(
            // TODO: Verifsere for bidrag
            TypeBehandling.BIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER, Rolletype.BIDRAGSPLIKTIG),
            TypeBehandling.FORSKUDD to setOf(Rolletype.BIDRAGSMOTTAKER),
            TypeBehandling.SÆRBIDRAG to setOf(Rolletype.BIDRAGSMOTTAKER),
        ),
    ),
    ;

    companion object {
        fun requestobjekter(
            behandlingstype: TypeBehandling,
            rolletype: Rolletype,
        ): Set<GrunnlagRequestType> =
            entries
                .filter { it.behandlinstypeMotRolletyper.keys.contains(behandlingstype) }
                .filter { it.behandlinstypeMotRolletyper[behandlingstype]?.contains(rolletype) ?: false }
                .map { GrunnlagRequestType.valueOf(it.name) }
                .toSet()
    }
}
