package com.merchtyl.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Final safety boundary applied after SLF4J has formatted all message arguments. */
public final class RedactedMessageConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        String value = LogSanitizer.maskSensitiveText(event.getFormattedMessage());
        value = value.replaceAll("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "[REDACTED]");
        value = value.replaceAll("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b", "[REDACTED]");
        value = value.replaceAll("(?i)((?:phone|mobile|telephone|client_ip|request_ip)\\s*[=:]\\s*)[^\\s,}]+", "$1[REDACTED]");
        return value;
    }
}
