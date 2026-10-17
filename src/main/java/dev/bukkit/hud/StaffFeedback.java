package dev.bukkit.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import dev.bukkit.utils.BukkitMessageSender;
import dev.core.utils.MessageComponent;

/**
 * Transient Utility Staff feedback. Uses the HUD overlay (a TextDisplay) so it
 * is not overwritten by the per-tick health/mana action bar. Falls back to chat
 * when the HUD is disabled.
 */
public final class StaffFeedback {

    private static final String KEY = "staff:feedback";
    private static final long DURATION_MS = 1600L;
    private static final int PRIORITY = 50;
    private static final long DEBOUNCE_MS = 400L;

    private static final Map<UUID, Long> last = new HashMap<>();

    private StaffFeedback() {
    }

    public static void show(Player player, String message) {
        if (player == null || message == null || !player.isOnline()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = last.get(player.getUniqueId());
        if (previous != null && now - previous < DEBOUNCE_MS) {
            return;
        }
        last.put(player.getUniqueId(), now);

        HudOverlayService hud = HudOverlayService.getInstance();
        HudConfig cfg = hud.getConfig();
        if (cfg != null && cfg.enabled()) {
            hud.show(player, KEY, message, DURATION_MS, PRIORITY);
        } else {
            BukkitMessageSender.getInstance().sendMessage(player, MessageComponent.of(message));
        }
    }
}
