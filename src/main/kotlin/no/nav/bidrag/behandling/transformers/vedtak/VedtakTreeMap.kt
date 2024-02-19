package no.nav.bidrag.behandling.transformers.vedtak

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.node.POJONode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetAinntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnhentetSkattegrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningForskudd
import no.nav.bidrag.transport.behandling.felles.grunnlag.erPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.felles.toCompactString
import java.math.BigDecimal

private val LOGGER = KotlinLogging.logger {}

fun MutableMap<String, MutableList<String>>.merge(with: Map<String, MutableList<String>>) {
    with.forEach { (key, value) ->
        val list = getOrDefault(key, mutableListOf())
        list.addAll(value)
        put(key, list)
    }
}

fun MutableMap<String, MutableList<String>>.add(
    subgraph: String,
    value: String,
) {
    val list = getOrDefault(subgraph, mutableListOf())
    list.add(value)
    put(subgraph, list)
}

enum class MermaidSubgraph {
    STONADSENDRING,
    PERIODE,
    NOTAT,
    SJABLON,
    PERSON,
    INGEN,
}

enum class TreeChildType {
    VEDTAK,
    STØNADSENDRING,
    PERIODE,
    GRUNNLAG,
}

data class TreeChild(
    override val name: String,
    override val id: String,
    override val type: TreeChildType,
    override val children: MutableList<TreeChild> = mutableListOf(),
    override val grunnlag: BaseGrunnlag? = null,
    override val grunnlagstype: Grunnlagstype? = null,
    @JsonIgnore
    override val parent: TreeChild?,
    override val periode: TreePeriode? = null,
) : ITreeChild

data class TreePeriode(
    val beløp: BigDecimal?,
    @Schema(description = "Valutakoden tilhørende stønadsbeløpet")
    val valutakode: String?,
    @Schema(description = "Resultatkoden tilhørende stønadsbeløpet")
    val resultatkode: String,
    @Schema(description = "Referanse - delytelseId/beslutningslinjeId -> bidrag-regnskap. Skal fjernes senere")
    val delytelseId: String?,
)

interface ITreeChild {
    val name: String
    val id: String
    val parent: TreeChild?
    val grunnlag: BaseGrunnlag?
    val periode: TreePeriode?
    val grunnlagstype: Grunnlagstype?
    val type: TreeChildType
    val children: MutableList<TreeChild>
}

fun OpprettVedtakRequestDto.toMermaid(): List<String> {
    val printList = mutableListOf<String>()
    printList.add("\nflowchart LR\n")
    printList.addAll(
        toTree().toMermaidSubgraphMap().toMermaid().removeDuplicates(),
    )
    return printList
}

fun Map<String, List<String>>.toMermaid(): List<String> {
    val printList = mutableListOf<String>()
    entries.forEach {
        if (it.key != MermaidSubgraph.INGEN.name) {
            printList.add("\tsubgraph ${it.key}\n")
            if (it.key == MermaidSubgraph.SJABLON.name || it.key == MermaidSubgraph.NOTAT.name) {
                printList.add("\tdirection TB\n")
            }
            printList.addAll(it.value.map { "\t\t$it\n" })
            printList.add("\tend\n")
        } else {
            printList.addAll(it.value.map { "\t$it\n" })
        }
    }
    return printList
}

fun VedtakDto.toMermaid(): List<String> {
    val printList = mutableListOf<String>()
    printList.add("\nflowchart LR\n")
    printList.addAll(
        toTree().toMermaidSubgraphMap().toMermaid().removeDuplicates(),
    )
    return printList
}

fun TreeChild.tilSubgraph(): String? =
    when (type) {
        TreeChildType.STØNADSENDRING -> "Stønadsendring - $name"
        TreeChildType.PERIODE -> parent?.tilSubgraph()
        TreeChildType.GRUNNLAG ->
            when (grunnlagstype) {
                Grunnlagstype.SJABLON -> MermaidSubgraph.SJABLON.name
                Grunnlagstype.NOTAT -> MermaidSubgraph.NOTAT.name
                else -> if (this.name.startsWith("PERSON_")) MermaidSubgraph.PERSON.name else parent?.tilSubgraph()
            }

        else -> MermaidSubgraph.INGEN.name
    }

fun TreeChild.toMermaidSubgraphMap(parent: TreeChild? = null): Map<String, MutableList<String>> {
    val mermaidSubgraphMap = mutableMapOf<String, MutableList<String>>()

    if (parent != null) {
        if (parent.type == TreeChildType.PERIODE) {
            mermaidSubgraphMap.add(
                parent.tilSubgraph()!!,
                "${parent.id}{${parent.name}} --> $id[$name]",
            )
        } else if (type == TreeChildType.GRUNNLAG) {
            if (grunnlagstype == Grunnlagstype.SJABLON || grunnlag?.erPerson() == true || grunnlagstype == Grunnlagstype.NOTAT) {
                val subgraph = tilSubgraph()
                mermaidSubgraphMap.add(
                    subgraph!!,
                    "$id[$name] ~~~ END",
                )
//                mermaidSubgraphMap.add(
//                    parent.tilSubgraph()!!,
//                    "${parent.id}[${parent.name}] ~~~ $subgraph",
//                )
            } else {
                mermaidSubgraphMap.add(
                    parent.tilSubgraph()!!,
                    "${parent.id}[${parent.name}] --> $id[$name]",
                )
            }
        } else {
            mermaidSubgraphMap.add(
                parent.tilSubgraph()!!,
                "${parent.id}[${parent.name}] --> $id[$name]",
            )
        }
    }

    children.forEach { mermaidSubgraphMap.merge(it.toMermaidSubgraphMap(this)) }

    return mermaidSubgraphMap
}

fun List<String>.removeDuplicates(): List<String> {
    val distinctList = mutableListOf<String>()
    val ignoreList = listOf("subgraph", "\tend", "flowchart")
    this.forEach {
        if (ignoreList.any { ignore -> it.contains(ignore) }) {
            distinctList.add(it)
        } else if (!distinctList.contains(it)) {
            distinctList.add(it)
        }
    }
    return distinctList
}

fun VedtakDto.toTree(): TreeChild {
    val vedtakParent =
        TreeChild(
            id = "Vedtak",
            name = "Vedtak",
            type = TreeChildType.VEDTAK,
            parent = null,
        )
    stønadsendringListe.forEach { st ->
        val stønadsendringId =
            "Stønadsendring_${st.type}_${st.kravhaver.verdi}"
        val stønadsendringTree =
            TreeChild(
                id = stønadsendringId,
                name = "Stønadsendring ${st.type} ${st.kravhaver.verdi}",
                type = TreeChildType.STØNADSENDRING,
                parent = vedtakParent,
            )
        vedtakParent.children.add(stønadsendringTree)
        stønadsendringTree.children.addAll(
            st.grunnlagReferanseListe.toTree(
                grunnlagListe,
                stønadsendringTree,
            ),
        )
        st.periodeListe.forEach {
            val periodeId = "Periode${it.periode.fom.toCompactString()}${st.kravhaver.verdi}"
            val periodeTree =
                TreeChild(
                    id = periodeId,
                    name = "Periode ${it.periode.fom.toCompactString()}",
                    type = TreeChildType.PERIODE,
                    parent = stønadsendringTree,
                    periode =
                        TreePeriode(
                            beløp = it.beløp,
                            valutakode = it.valutakode,
                            resultatkode = it.resultatkode,
                            delytelseId = it.delytelseId,
                        ),
                )

            periodeTree.children.addAll(
                it.grunnlagReferanseListe.toTree(
                    grunnlagListe,
                    periodeTree,
                ).toMutableList(),
            )
            stønadsendringTree.children.add(periodeTree)
        }
    }
    return vedtakParent
}

fun OpprettVedtakRequestDto.toTree(): TreeChild {
    val vedtakParent =
        TreeChild(
            id = "Vedtak",
            name = "Vedtak",
            type = TreeChildType.VEDTAK,
            parent = null,
        )
    stønadsendringListe.forEach { st ->
        val stønadsendringId =
            "Stønadsendring_${st.type}_${st.kravhaver.verdi}"
        val stønadsendringTree =
            TreeChild(
                id = stønadsendringId,
                name = "Stønadsendring ${st.type} ${st.kravhaver.verdi}",
                type = TreeChildType.STØNADSENDRING,
                parent = vedtakParent,
            )
        vedtakParent.children.add(stønadsendringTree)
        stønadsendringTree.children.addAll(
            st.grunnlagReferanseListe.toTree(
                grunnlagListe,
                stønadsendringTree,
            ),
        )
        st.periodeListe.forEach {
            val periodeId = "Periode${it.periode.fom.toCompactString()}${st.kravhaver.verdi}"
            val periodeTree =
                TreeChild(
                    id = periodeId,
                    name = "Periode ${it.periode.fom.toCompactString()}",
                    type = TreeChildType.PERIODE,
                    parent = stønadsendringTree,
                    periode =
                        TreePeriode(
                            beløp = it.beløp,
                            valutakode = it.valutakode,
                            resultatkode = it.resultatkode,
                            delytelseId = it.delytelseId,
                        ),
                )

            periodeTree.children.addAll(
                it.grunnlagReferanseListe.toTree(
                    grunnlagListe,
                    periodeTree,
                ).toMutableList(),
            )
            stønadsendringTree.children.add(periodeTree)
        }
    }
    return vedtakParent
}

fun List<Grunnlagsreferanse>.toTree(
    grunnlagsListe: List<BaseGrunnlag>,
    parent: TreeChild?,
): List<TreeChild> {
    return map {
        it.toTree(grunnlagsListe, parent)
    }.filterNotNull()
}

fun Grunnlagsreferanse.toTree(
    grunnlagsListe: List<BaseGrunnlag>,
    parent: TreeChild?,
): TreeChild? {
    val grunnlagListe = grunnlagsListe.filtrerBasertPåEgenReferanse(referanse = this)
    if (grunnlagListe.isEmpty()) {
        return null
    }

    val grunnlag = grunnlagListe.first()
    val treeMap =
        grunnlagListe.flatMap {
            it.grunnlagsreferanseListe.map { it.toTree(grunnlagsListe, parent) } +
                it.gjelderReferanse?.toTree(grunnlagsListe, parent)
        }

    return TreeChild(
        name =
            when (grunnlag.type) {
                Grunnlagstype.SLUTTBEREGNING_FORSKUDD ->
                    "Sluttberegning" +
                        "(${grunnlag.innholdTilObjekt<SluttberegningForskudd>().periode.fom.toCompactString()})"

                Grunnlagstype.SJABLON ->
                    "Sjablon" +
                        "(${((grunnlag.innhold as POJONode).pojo as LinkedHashMap<*, *>).get("sjablonNavn")})"

                Grunnlagstype.DELBEREGNING_INNTEKT ->
                    "Delberegning inntekt(" +
                        "${grunnlag.innholdTilObjekt<DelberegningInntekt>().periode.fom.toCompactString()})"

                Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND ->
                    "Delberegning barn i husstand(" +
                        "${grunnlag.innholdTilObjekt<DelberegningBarnIHusstand>().periode.fom.toCompactString()})"

                Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE ->
                    "Inntektsrapportering" +
                        "(${grunnlag.innholdTilObjekt<InntektsrapporteringPeriode>().inntektsrapportering}))"

                Grunnlagstype.SIVILSTAND_PERIODE -> "Sivilstand(${grunnlag.innholdTilObjekt<SivilstandPeriode>().sivilstand}))"
                Grunnlagstype.BOSTATUS_PERIODE -> "Bosstatus(${grunnlag.innholdTilObjekt<BostatusPeriode>().bostatus}))"
                Grunnlagstype.NOTAT -> "Notat(${grunnlag.innholdTilObjekt<NotatGrunnlag>().type})"
                Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM -> "Innhentet husstandsmedlem"
                Grunnlagstype.INNHENTET_SIVILSTAND -> "Innhentet sivilstand"
                Grunnlagstype.INNHENTET_ARBEIDSFORHOLD -> "Innhentet arbeidsforhold"
                Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE ->
                    "Innhentet skattegrunnlag" +
                        "(${grunnlag.innholdTilObjekt<InnhentetSkattegrunnlag>().periode.fom.toCompactString()})"

                Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT_PERIODE ->
                    "Innhentet ainntekt" +
                        "(${grunnlag.innholdTilObjekt<InnhentetAinntekt>().periode.fom.toCompactString()})"

                else ->
                    if (grunnlag.erPerson()) {
                        "${grunnlag.type}(${grunnlag.innholdTilObjekt<Person>().fødselsdato.toCompactString()})"
                    } else {
                        this
                    }
            },
        id = this,
        grunnlag = grunnlag,
        type = TreeChildType.GRUNNLAG,
        grunnlagstype = grunnlag.type,
        parent = parent,
        children = treeMap.filterNotNull().toMutableList(),
    )
}
