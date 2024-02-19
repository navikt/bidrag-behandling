package no.nav.bidrag.behandling.transformers.vedtak

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.felles.toCompactString

private val LOGGER = KotlinLogging.logger {}

fun OpprettVedtakRequestDto.toMermaidOld(): List<String> {
    val printList = mutableListOf<String>()
    printList.add("\nflowchart LR\n")
    printList.addAll(
        toTreeMapOld().flatMap { it.entries.map { "${it.key} --> ${it.value}" } }
            .toSet()
            .map { "\t\t\t$it\n" },
    )
    return printList
}

fun OpprettVedtakRequestDto.toTreeMapOld(): List<Map<String, Any?>> {
    val printList = mutableListOf<String>()
    val mapList = mutableListOf<Map<String, Any?>>()
    printList.add("\nflowchart LR\n")
    val vedtakId = "Vedtak[Vedtak $type]"
    stønadsendringListe.forEach { st ->
        val stønadsendringId =
            "Stønadsendring_${st.type}_${st.kravhaver.verdi}"
        printList.add("\t$vedtakId --> $stønadsendringId\n")
        mapList.add(mapOf(vedtakId to stønadsendringId))
        printList.add("\tsubgraph Stønadsendring - ${st.type} - ${st.kravhaver.verdi}\n")
        st.grunnlagReferanseListe.forEach {
            printList.add(
                "\t\t$stønadsendringId --> $it\n",
            )
            mapList.add(mapOf(stønadsendringId to it))
        }
        val periodeMap =
            st.periodeListe.flatMap {
                val periodeId = "Periode${it.periode.fom.toCompactString()}${st.kravhaver.verdi}"
                listOf(mapOf(stønadsendringId to periodeId)) +
                    it.grunnlagReferanseListe.toTree(
                        grunnlagListe,
                        periodeId,
                    )
            }

        mapList.addAll(periodeMap)
    }
    return mapList
}

fun Grunnlagsreferanse.toTreeMapOld(grunnlagsListe: List<BaseGrunnlag>): Map<out Grunnlagsreferanse, Any> {
    val grunnlag = grunnlagsListe.filtrerBasertPåEgenReferanse(referanse = this)
    if (grunnlag.isEmpty()) {
        return emptyMap()
    }
    val treeMap =
        grunnlag.flatMap {
            it.grunnlagsreferanseListe.map { it.toTreeMapOld(grunnlagsListe) }
//                it.gjelderReferanse?.let {
//                    mapOf(it to "")
//                }
        }

    return mapOf(this to treeMap.filterNotNull())
}

fun List<Grunnlagsreferanse>.toTree(
    grunnlagsListe: List<BaseGrunnlag>,
    referertAv: Grunnlagsreferanse,
): List<Map<String, Any?>> {
    val printList = mutableListOf<Map<String, Any?>>()

    val trestruktur =
        map {
            it.toTreeMapOld(grunnlagsListe)
        }

    trestruktur.forEach {
        it.entries.forEach {
            printList.add(mapOf(referertAv to it.key))
            printList.addAll(it.value.toTreeMapOld(it.key))
        }
    }

    return printList
}

fun Any.toTreeMapOld(referertAv: Grunnlagsreferanse?): List<Map<String, Any?>> {
    fun referertAvKey(referertTil: String): Map<String, String> {
        if (referertAv == referertTil) {
            LOGGER.warn { "Sirkulær referanse: $referertAv -> $referertTil" }
            return mapOf()
        }
        return mapOf(referertAv.toString() to referertTil)
    }
    if (this is Map.Entry<*, *>) {
        val value =
            if (value == null || value is ArrayList<*> && (value as ArrayList<*>).isEmpty()) null else value
        if (value is ArrayList<*> && value.size > 1) {
            return value.flatMap {
                it.toTreeMapOld(key.toString())
            }.toSet().toList()
        }
        if (value is ArrayList<*> && value.size == 1) {
            val firstValue = value.first()
            if (firstValue is Map<*, *>) {
                val firstMapValue = firstValue.entries.first()
                return listOf(
                    mapOf(key.toString() to firstMapValue.key),
                )
            }
            return listOfNotNull(referertAvKey(key.toString()), mapOf(key.toString() to firstValue))
        }
        if (value == null || value is String && value.isEmpty()) {
            return listOf()
        }
        return listOfNotNull(referertAvKey(key.toString()), mapOf(key.toString() to value))
    }
    if (this is Map<*, *>) {
        return entries.flatMap {
            it.toTreeMapOld(it.key.toString()) + referertAvKey(it.key.toString())
        }
    }
    if (this is ArrayList<*>) {
        return this.flatMap {
            val nøkkel = (it as Map<*, *>).keys.first()
            it.toTreeMapOld(referertAv) + referertAvKey(nøkkel.toString())
        }
    }
    return listOf(mapOf())
}
