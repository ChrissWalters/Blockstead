package com.github.chrisswalters.blockstead.players;

import java.util.HashSet;
import java.util.Set;

/**
 * Laufzeitdaten des Plugins zu einer Person, die online ist (nicht persistent).
 * Verwaltet über {@link PlayerUtility}.
 */
public class BSPlayer {

    /** IDs der WorldGuard-Regionen, in denen sich die Person zuletzt befand. Nie {@code null}. */
    private Set<String> lastKnownRegions = new HashSet<>();

    /** @return veränderbares Set der zuletzt bekannten Region-IDs */
    public Set<String> getLastKnownRegions() {
        return lastKnownRegions;
    }

    /** Ersetzt die zuletzt bekannten Regionen; {@code null} wird zu einem leeren Set. */
    public void setLastKnownRegions(Set<String> lastKnownRegions) {
        this.lastKnownRegions = lastKnownRegions != null ? lastKnownRegions : new HashSet<>();
    }
}
