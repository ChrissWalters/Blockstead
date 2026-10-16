package com.github.chrisswalters.blockstead.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Die einzige Klasse mit Vault-Typen.
 *
 * <p>Vault ist optional: Fehlt es, bricht Java beim Laden dieser Klasse mit {@code NoClassDefFoundError} ab.
 * {@link EconomyService} prüft deshalb vorher, ob Vault da ist, und ruft diese Klasse nur dann auf.
 *
 * <p>Der Provider wird bei jedem Zugriff neu geholt statt beim Start zwischengespeichert: Economy-Plugins wie
 * EssentialsX melden sich teils früh an, sind aber erst später benutzbar, und können ausgetauscht werden.
 * Ausnahmen aus dem Economy-Plugin werden in {@link Transaction}-Fehler übersetzt.
 */
final class VaultEconomy {

    private VaultEconomy() {
    }

    /** Aktueller Economy-Provider; {@code null}, wenn keiner angemeldet ist. */
    private static Economy provider() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    /** Ob ein Economy-Plugin angemeldet <em>und</em> aktiv ist. */
    static boolean isAvailable() {
        try {
            Economy economy = provider();
            return economy != null && economy.isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Name des Economy-Plugins, z.B. "EssentialsX Economy"; {@code null} ohne Provider. */
    static String providerName() {
        Economy economy = provider();
        return economy == null ? null : economy.getName();
    }

    static double getBalance(OfflinePlayer player) {
        return provider().getBalance(player);
    }

    static boolean has(OfflinePlayer player, double amount) {
        return provider().has(player, amount);
    }

    static Transaction withdraw(OfflinePlayer player, double amount) {
        try {
            return toTransaction(provider().withdrawPlayer(player, amount));
        } catch (RuntimeException e) {
            return Transaction.failed(e.toString());
        }
    }

    /** Bucht ein; legt vorher ein Konto an, falls die Person noch keins hat (z.B. offline, nie gekauft). */
    static Transaction deposit(OfflinePlayer player, double amount) {
        try {
            Economy economy = provider();
            if (!economy.hasAccount(player)) {
                economy.createPlayerAccount(player);
            }
            return toTransaction(economy.depositPlayer(player, amount));
        } catch (RuntimeException e) {
            return Transaction.failed(e.toString());
        }
    }

    /**
     * Setzt den Kontostand auf {@code amount}. Vault kennt kein "setzen", also wird die Differenz
     * ein- bzw. abgebucht. Legt vorher ein Konto an, falls nötig.
     */
    static Transaction setBalance(OfflinePlayer player, double amount) {
        try {
            Economy economy = provider();
            if (!economy.hasAccount(player)) {
                economy.createPlayerAccount(player);
            }
            double difference = amount - economy.getBalance(player);
            if (difference > 0) {
                return toTransaction(economy.depositPlayer(player, difference));
            }
            if (difference < 0) {
                return toTransaction(economy.withdrawPlayer(player, -difference));
            }
            return Transaction.ok();
        } catch (RuntimeException e) {
            return Transaction.failed(e.toString());
        }
    }

    static String format(double amount) {
        return provider().format(amount);
    }

    private static Transaction toTransaction(EconomyResponse response) {
        return response.transactionSuccess() ? Transaction.ok() : Transaction.failed(response.errorMessage);
    }
}
