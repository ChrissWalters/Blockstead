package com.github.chrisswalters.blockstead.economy;

/**
 * Ergebnis einer Buchung über das Economy-Plugin.
 *
 * @param success ob die Buchung durchgeführt wurde
 * @param error   Grund für das Scheitern (vom Economy-Plugin oder von Blockstead); {@code null} bei Erfolg
 */
public record Transaction(boolean success, String error) {

    static Transaction ok() {
        return new Transaction(true, null);
    }

    static Transaction failed(String error) {
        return new Transaction(false, error == null || error.isBlank() ? "unknown error" : error);
    }
}
