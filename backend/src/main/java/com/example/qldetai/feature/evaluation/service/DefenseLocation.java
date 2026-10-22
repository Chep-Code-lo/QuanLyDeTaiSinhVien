package com.example.qldetai.feature.evaluation.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Comparison key only: retain the original room label in storage and responses. */
public final class DefenseLocation {
    public static final String NORMALIZATION_PATTERN =
            "^[\\p{Z}\\s]*(phòng|phong)[\\p{Z}\\s]+|[\\p{Z}\\s]+";
    private static final Pattern NORMALIZER = Pattern.compile(NORMALIZATION_PATTERN);

    private DefenseLocation() {}

    public static String key(String location) {
        return NORMALIZER.matcher(location.trim().toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
