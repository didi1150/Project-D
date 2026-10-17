package dev.bukkit.utils;

import org.bukkit.Bukkit;

import dev.core.utils.AdventureText;

/**
 * Small helpers for sending legacy strings through Paper's Adventure-based
 * Bukkit APIs without repeating the conversion at every call site.
 */
public final class BukkitText {

    private BukkitText() {
    }

    public static void broadcast(String legacyMessage) {
        Bukkit.broadcast(AdventureText.legacy(legacyMessage));
    }
}
