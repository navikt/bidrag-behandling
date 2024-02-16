package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.grunnlag.personIdent
import no.nav.bidrag.behandling.transformers.grunnlag.personObjekt
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto

fun GrunnlagDto.tilRolle(behandling: Behandling) =
    Rolle(
        behandling,
        rolletype =
            when (type) {
                Grunnlagstype.PERSON_SØKNADSBARN -> Rolletype.BARN
                Grunnlagstype.PERSON_BIDRAGSMOTTAKER -> Rolletype.BIDRAGSMOTTAKER
                Grunnlagstype.PERSON_REELL_MOTTAKER -> Rolletype.REELMOTTAKER
                Grunnlagstype.PERSON_BIDRAGSPLIKTIG -> Rolletype.BIDRAGSPLIKTIG
                else -> throw RuntimeException("")
            },
        ident = personIdent,
        foedselsdato = personObjekt.fødselsdato,
    )
