package no.nav.bidrag.behandling.transformers.grunnlag

import no.nav.bidrag.behandling.manglerRolle
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.felles.commonObjectmapper

val GrunnlagDto.personObjekt get() = commonObjectmapper.treeToValue(innhold, Person::class.java)!!
val GrunnlagDto.personIdent get() = personObjekt.ident!!.verdi
val Set<GrunnlagDto>.bidragsmottaker
    get() =
        find { it.type == Grunnlagstype.PERSON_BIDRAGSMOTTAKER }
            ?: manglerRolle(Rolletype.BIDRAGSMOTTAKER, -1)
val Set<GrunnlagDto>.barn
    get() =
        filter {
            it.type == Grunnlagstype.PERSON_SØKNADSBARN || it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM
        }.toSet()

val Set<GrunnlagDto>.søknadsbarn
    get() =
        filter {
            it.type == Grunnlagstype.PERSON_SØKNADSBARN
        }.toSet()
