package no.nav.bidrag.behandling.transformers.vedtak

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse

private val LOGGER = KotlinLogging.logger {}

typealias TreeMap = Map<String, List<Map<String, String>>>

fun List<Grunnlagsreferanse>.lagTre(grunnlagsListe: List<GrunnlagDto>): List<Any> {
    val map =
        this.map {
            it.lagTre(grunnlagsListe)
        }

    map.forEach {
        it.entries.forEach {
            LOGGER.info { "\n${it.key}: \n\n\t${it.value.logTree()}" }
        }
    }

    return map
}

fun Any.logTree(step: Int = 1): String {
    if (this is Map.Entry<*, *>) {
        val value =
            if (this.value == null || this.value is ArrayList<*> && (this.value as ArrayList<*>).isEmpty()) null else this.value
        if (value is ArrayList<*> && value.size > 1) {
            return "${this.key}: \n${"\t".repeat(step + 1)}${value.logTree(step + 1)}"
        }
        return "${this.key} ${value?.logTree(step)?.let { "-> $it" } ?: ""}"
    }
    if (this is Map<*, *>) {
        return this.entries.map { it.logTree(step) }.joinToString("()")
    }
    if (this is ArrayList<*>) {
        return this.map { it.logTree() }.joinToString("\n".repeat(1) + "\t".repeat(step))
    }
    return this as String
}

fun Grunnlagsreferanse.lagTre(grunnlagsListe: List<GrunnlagDto>): Map<out Grunnlagsreferanse, Any> {
    val grunnlag = grunnlagsListe.filtrerBasertPåEgenReferanse(referanse = this)
    if (grunnlag.isEmpty()) {
        return emptyMap()
    }
    val treeMap =
        grunnlag[0].grunnlagsreferanseListe.map { it.lagTre(grunnlagsListe) } +
            grunnlag[0].gjelderReferanse?.let {
                mapOf(grunnlag[0].gjelderReferanse to null)
            }
    return mapOf(this to treeMap.filterNotNull())
}

fun List<Grunnlagsreferanse>.validerInneholderListe(grunnlagsListe: List<GrunnlagDto>) {
    val feilListe = mutableListOf<String>()
    this.forEach {
        it.valider(grunnlagsListe, feilListe)
    }

    if (feilListe.isNotEmpty()) {
        throw RuntimeException("Feil i grunnlagsreferanser: ${feilListe.joinToString()}")
    }
}

fun Grunnlagsreferanse.valider(
    grunnlagsListe: List<GrunnlagDto>,
    feilListe: MutableList<String>,
) {
    val grunnlag = grunnlagsListe.filtrerBasertPåEgenReferanse(referanse = this)
    if (grunnlag.isEmpty()) {
        feilListe.add("Grunnlaget med referanse $this finnes ikke i grunnlagslisten")
    }
    if (grunnlag.size > 1) {
        feilListe.add("Grunnlaget med referanse $this finnes flere ganger i grunnlagslisten")
    }
    if (grunnlag.isNotEmpty() && grunnlag.size == 1) {
        val grunnlagsreferanser =
            grunnlag[0].grunnlagsreferanseListe + listOf(grunnlag[0].gjelderReferanse)
        grunnlagsreferanser.filterNotNull().forEach { it.valider(grunnlagsListe, feilListe) }
    }
}
