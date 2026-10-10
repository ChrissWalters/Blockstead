package com.github.chrisswalters.blockstead.util;

/**
 * Vergleich von Versionsangaben wie "5.5.87".
 */
public final class Versions {

    private Versions() {
    }

    /**
     * Vergleicht zwei Versionsangaben Teil für Teil als Zahlen.
     * Nicht-numerische Anhänge (z.B. "-SNAPSHOT") werden ignoriert.
     *
     * @return negativ, wenn {@code a} älter ist als {@code b}; 0 bei Gleichheit; sonst positiv
     */
    public static int compare(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int na = i < pa.length ? parseLeadingInt(pa[i]) : 0;
            int nb = i < pb.length ? parseLeadingInt(pb[i]) : 0;
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }

    /** Liest die führenden Ziffern eines Strings ("87-beta" → 87); ohne Ziffern 0. */
    private static int parseLeadingInt(String s) {
        String digits = s.replaceAll("^(\\d*).*$", "$1");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
