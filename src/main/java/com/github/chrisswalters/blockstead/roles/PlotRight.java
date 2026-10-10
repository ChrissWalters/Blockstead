package com.github.chrisswalters.blockstead.roles;

/**
 * Einzelne Rechte auf einem Grundstück, die einer {@link PlotRole} zugeordnet werden.
 * Die Namen in der config.yml sind die kleingeschriebenen Konstanten (z.B. "build").
 * Durchgesetzt werden sie vom {@code PlotProtectionListener}.
 */
public enum PlotRight {
    /** Bauen und Abbauen, Eimer, Werkzeuge mit Wirkung auf Blöcke, sonstige Block-Interaktionen. */
    BUILD,
    /** Behälter mit Inventar: Truhen, Fässer, Shulkerkisten, Trichter, Spender, Lesepulte usw. */
    CONTAINERS,
    /** Türen, Falltüren, Zauntore, Knöpfe, Hebel, Druckplatten, Glocken. */
    DOORS,
    /** Arbeitsblöcke: Werkbank, Öfen, Ambosse, Verzauberungstisch, Braustand, Betten, Endertruhe usw. */
    WORKSTATIONS,
    /** Nicht-feindliche Entities: Tiere, Dorfbewohnende, Rüstungsständer, Rahmen. */
    ENTITIES,
    /** Home auf dem Grundstück setzen ({@code /plot home set}). */
    HOME
}
