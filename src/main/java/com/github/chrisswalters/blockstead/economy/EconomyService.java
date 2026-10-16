package com.github.chrisswalters.blockstead.economy;

import org.bukkit.OfflinePlayer;

import java.util.logging.Logger;

/**
 * Zugriff auf die Server-Währung über Vault – die eigentliche Kontoführung macht ein Economy-Plugin
 * wie EssentialsX. Blockstead führt seit 2.0.0 keine eigenen Kontostände mehr.
 *
 * <p>Vault ist optional. Ohne Vault oder ohne aktives Economy-Plugin ist {@link #isAvailable()} {@code false}:
 * Lesezugriffe liefern dann 0, Buchungen schlagen mit einer Fehlermeldung fehl. Diese Klasse selbst enthält
 * keine Vault-Typen, die stecken nur in {@link VaultEconomy}.
 *
 * <p>Beträge sind bei Blockstead ganze Zahlen ({@code long}); Vault rechnet mit {@code double}.
 */
public class EconomyService {

    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final boolean vaultPresent;

    public EconomyService() {
        this.vaultPresent = isVaultPresent();
    }

    private static boolean isVaultPresent() {
        try {
            Class.forName(VAULT_ECONOMY_CLASS);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Ob Vault installiert ist und ein aktives Economy-Plugin angemeldet hat. */
    public boolean isAvailable() {
        return vaultPresent && VaultEconomy.isAvailable();
    }

    /** Kontostand; 0, wenn kein Economy-Plugin erreichbar ist. */
    public double getBalance(OfflinePlayer player) {
        if (!isAvailable()) {
            return 0;
        }
        try {
            return VaultEconomy.getBalance(player);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Ob die Person mindestens {@code amount} hat; {@code false} ohne Economy-Plugin. */
    public boolean has(OfflinePlayer player, long amount) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return VaultEconomy.has(player, amount);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Bucht {@code amount} ab. */
    public Transaction withdraw(OfflinePlayer player, long amount) {
        return isAvailable() ? VaultEconomy.withdraw(player, amount) : unavailable();
    }

    /** Bucht {@code amount} ein; legt bei Bedarf ein Konto an. */
    public Transaction deposit(OfflinePlayer player, long amount) {
        return isAvailable() ? VaultEconomy.deposit(player, amount) : unavailable();
    }

    /** Setzt den Kontostand (nur für die einmalige Übertragung alter Kontostände, siehe {@link BalanceImport}). */
    Transaction setBalance(OfflinePlayer player, long amount) {
        return isAvailable() ? VaultEconomy.setBalance(player, amount) : unavailable();
    }

    /** Betrag im Format des Economy-Plugins (z.B. "$1,000"); ohne Economy-Plugin nur die Zahl. */
    public String format(double amount) {
        if (isAvailable()) {
            try {
                return VaultEconomy.format(amount);
            } catch (RuntimeException e) {
                // weiter unten ohne Format
            }
        }
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    /** Eine Log-Zeile, über welches Economy-Plugin die Währung läuft – oder eine Warnung, wenn keins da ist. */
    public void logStatus(Logger log) {
        if (!vaultPresent) {
            log.warning("Vault is not installed – plots cannot be bought (staff can still assign them).");
        } else if (!VaultEconomy.isAvailable()) {
            log.warning("Vault is installed, but no economy plugin (e.g. EssentialsX) is active – "
                    + "plots cannot be bought (staff can still assign them).");
        } else {
            log.info("Server currency through Vault: " + VaultEconomy.providerName());
        }
    }

    private static Transaction unavailable() {
        return Transaction.failed("no economy plugin available through Vault");
    }
}
