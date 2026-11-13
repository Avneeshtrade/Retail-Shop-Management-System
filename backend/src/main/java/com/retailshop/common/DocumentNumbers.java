package com.retailshop.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** Human-friendly document numbers like SO-20261113-7F3K9Q. */
public final class DocumentNumbers {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private DocumentNumbers() {}

    public static String next(String prefix) {
        return next(prefix, LocalDate.now());
    }

    public static String next(String prefix, LocalDate date) {
        StringBuilder sb = new StringBuilder(prefix).append('-').append(date.format(FMT)).append('-');
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
