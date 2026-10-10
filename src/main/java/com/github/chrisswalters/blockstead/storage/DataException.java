package com.github.chrisswalters.blockstead.storage;

/**
 * Fehler beim Zugriff auf die Datenbank. Verpackt die {@link java.sql.SQLException},
 * damit nicht jede Methode sie deklarieren muss; Befehle fangen sie ab und melden einen
 * Datenbankfehler (siehe {@code CommandSupport}).
 */
public class DataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
