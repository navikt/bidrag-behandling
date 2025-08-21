package no.nav.bidrag.behandling.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply

class BidragDebugLogFilter : Filter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent): FilterReply =
        if (event.level == Level.DEBUG) {
            if (UnleashFeatures.DEBUG_LOGGING.isEnabled) {
                FilterReply.NEUTRAL
            } else {
                FilterReply.DENY
            }
        } else {
            FilterReply.NEUTRAL
        }
}
