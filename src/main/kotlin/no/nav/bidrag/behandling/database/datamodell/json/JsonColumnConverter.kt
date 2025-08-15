package no.nav.bidrag.behandling.database.datamodell.json

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.bidrag.transport.felles.commonObjectmapper
import kotlin.reflect.KClass

@Converter
abstract class JsonColumnConverter<T : Any>(
    private val clazz: KClass<T>,
) : AttributeConverter<T, String?> {
    override fun convertToDatabaseColumn(attribute: T?): String? = attribute?.let { commonObjectmapper.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): T? =
        dbData?.let {
            commonObjectmapper.readValue(it, clazz.java)
        }
}
