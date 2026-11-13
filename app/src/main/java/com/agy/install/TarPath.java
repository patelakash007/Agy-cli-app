package com.agy.install;

/** Normalizes a tar entry name into a safe relative path (null = reject). */
public final class TarPath {

    private TarPath() {
    }

    public static String normalize(String rawName) {
        return TarPolicy.normalizeName(rawName);
    }
}
