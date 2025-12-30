package no.nav.bidrag.behandling.database.datamodell.extensions

import io.hypersistence.utils.hibernate.type.ImmutableType
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.tilÅrsakstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.engine.spi.SharedSessionContractImplementor
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime

fun LasterGrunnlagAsyncStatus?.lasterGrunnlagAsync(): Boolean =
    UnleashFeatures.HENT_GRUNNLAG_ASYNC.isEnabled && this != null &&
        listOf(LasterGrunnlagAsyncStatus.BESTILT, LasterGrunnlagAsyncStatus.LASTER).contains(this)

enum class LasterGrunnlagAsyncStatus {
    BESTILT,
    LASTER,
    FERDIG,
    FEILET,
}

class BehandlingMetadataDo : MutableMap<String, String> by hashMapOf() {
    companion object {
        fun from(initValue: Map<String, String> = hashMapOf()): BehandlingMetadataDo {
            val dokmap = BehandlingMetadataDo()
            dokmap.putAll(initValue)
            return dokmap
        }
    }

    private val følgerAutomatiskVedtak = "følger_automatisk_vedtak"
    private val klagePåBisysVedtak = "klage_på_bisys_vedtak"
    private val lasterGrunnlagAsync = "laster_grunnlag_async_tidspunkt"
    private val lasterGrunnlagAsyncStatus = "laster_grunnlag_async_status"

    fun bestillLastGrunnlagAsync() {
        update(lasterGrunnlagAsync, LocalDateTime.now().toString())
        update(lasterGrunnlagAsyncStatus, LasterGrunnlagAsyncStatus.BESTILT.name)
    }

    fun startLastGrunnlagAsync() {
        update(lasterGrunnlagAsync, LocalDateTime.now().toString())
        update(lasterGrunnlagAsyncStatus, LasterGrunnlagAsyncStatus.LASTER.name)
    }

    fun avsluttLastGrunnlagAsync(feilet: Boolean = false) {
        update(lasterGrunnlagAsync, LocalDateTime.now().toString())
        if (feilet) {
            update(lasterGrunnlagAsyncStatus, LasterGrunnlagAsyncStatus.FEILET.name)
            return
        }
        update(lasterGrunnlagAsyncStatus, LasterGrunnlagAsyncStatus.FERDIG.name)
    }

    fun statusLasterGrunnlagAsync(): LasterGrunnlagAsyncStatus? =
        if (!UnleashFeatures.HENT_GRUNNLAG_ASYNC.isEnabled) {
            LasterGrunnlagAsyncStatus.FERDIG
        } else {
            get(lasterGrunnlagAsyncStatus)?.let {
                LasterGrunnlagAsyncStatus.valueOf(it)
            }
        }

    fun lasterGrunnlagAsync() = statusLasterGrunnlagAsync()?.lasterGrunnlagAsync()

    fun setKlagePåBisysVedtak() {
        update(klagePåBisysVedtak, "true")
    }

    fun erKlagePåBisysVedtak() = get(klagePåBisysVedtak)?.toBooleanStrictOrNull() == true

    fun setFølgerAutomatiskVedtak(vedtaksid: Int?) {
        vedtaksid?.let { update(følgerAutomatiskVedtak, it.toString()) }
    }

    fun getFølgerAutomatiskVedtak(): Int? = get(følgerAutomatiskVedtak)?.toIntOrNull()

    private fun update(
        key: String,
        value: String?,
    ) {
        remove(key)
        value?.let { put(key, value) }
    }

    fun copy(): BehandlingMetadataDo = from(this)
}

class BehandlingMetadataDoConverter : ImmutableType<BehandlingMetadataDo>(BehandlingMetadataDo::class.java) {
    override fun get(
        rs: ResultSet,
        p1: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): BehandlingMetadataDo? {
        val map = rs.getObject(p1) as Map<String, String>?
        return map?.let { BehandlingMetadataDo.from(it) }
    }

    override fun set(
        st: PreparedStatement,
        value: BehandlingMetadataDo?,
        index: Int,
        session: SharedSessionContractImplementor,
    ) {
        st.setObject(index, value?.toMap())
    }

    override fun getSqlType(): Int = Types.OTHER

    override fun compare(
        p0: Any?,
        p1: Any?,
        p2: SessionFactoryImplementor?,
    ): Int = 0

    override fun fromStringValue(sequence: CharSequence?): BehandlingMetadataDo? =
        try {
            sequence?.let { JacksonUtil.fromString(sequence as String, BehandlingMetadataDo::class.java) }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                String.format(
                    "Could not transform the [%s] value to a Map!",
                    sequence,
                ),
            )
        }
}

@Converter
open class ÅrsakConverter : AttributeConverter<VirkningstidspunktÅrsakstype?, String?> {
    override fun convertToDatabaseColumn(attribute: VirkningstidspunktÅrsakstype?): String? = attribute?.name

    override fun convertToEntityAttribute(dbData: String?): VirkningstidspunktÅrsakstype? = dbData?.tilÅrsakstype()
}

fun hentDefaultÅrsak(
    typeBehandling: TypeBehandling,
    vedtakstype: Vedtakstype,
): VirkningstidspunktÅrsakstype? =
    when (typeBehandling) {
        TypeBehandling.FORSKUDD, TypeBehandling.BIDRAG, TypeBehandling.BIDRAG_18_ÅR -> {
            if (vedtakstype == Vedtakstype.ALDERSJUSTERING) {
                VirkningstidspunktÅrsakstype.AUTOMATISK_JUSTERING
            } else if (vedtakstype != Vedtakstype.OPPHØR) {
                VirkningstidspunktÅrsakstype.FRA_SØKNADSTIDSPUNKT
            } else {
                null
            }
        }

        TypeBehandling.SÆRBIDRAG -> {
            null
        }
    }
