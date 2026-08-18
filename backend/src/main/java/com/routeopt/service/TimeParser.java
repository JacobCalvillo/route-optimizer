package com.routeopt.service;

import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lenient HH:mm parsing for the time strings the model returns.
 *
 * <p>The schema asks for HH:mm, but a bare "9" or "9:00" occasionally slips through. Accepting
 * those is cheaper than failing the whole batch, and an unparseable value degrades to "no
 * constraint" rather than to an exception.
 */
final class TimeParser {

    private static final Logger log = LoggerFactory.getLogger(TimeParser.class);

    private TimeParser() {}

    static LocalTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.matches("\\d{1,2}")) {
                return LocalTime.of(Integer.parseInt(trimmed), 0);
            }
            if (trimmed.matches("\\d{1}:\\d{2}")) {
                return LocalTime.parse("0" + trimmed);
            }
            return LocalTime.parse(trimmed);
        } catch (java.time.DateTimeException ex) {
            log.warn("Ignoring unparseable time value [{}]", value);
            return null;
        }
    }
}
