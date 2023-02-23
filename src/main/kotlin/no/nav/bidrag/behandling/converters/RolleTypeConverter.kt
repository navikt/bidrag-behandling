package no.nav.bidrag.behandling.converters

import no.nav.bidrag.behandling.database.datamodell.RolleType
import org.springframework.core.convert.converter.Converter

class RolleTypeConverter : Converter<String, RolleType> {
    override fun convert(source: String): RolleType {
        return RolleType.valueOf(source)
    }
}
