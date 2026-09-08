package com.framework.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateUtils {

    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private DateUtils() {
    }

    public static String todayIso() {
        return LocalDate.now().format(ISO_DATE);
    }

    public static String nowIso() {
        return LocalDateTime.now().format(ISO_DATETIME);
    }

    public static String futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow).format(ISO_DATE);
    }

    public static String pastDate(int daysAgo) {
        return LocalDate.now().minusDays(daysAgo).format(ISO_DATE);
    }

    /** Unique-ish suffix for test data (order refs, emails) — epoch millis, not for security use. */
    public static long uniqueSuffix() {
        return LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
