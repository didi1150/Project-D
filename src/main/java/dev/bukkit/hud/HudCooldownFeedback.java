package dev.bukkit.hud;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.bukkit.utils.BukkitMessageSender;
import dev.core.ability.Ability;
import dev.core.utils.MessageComponent;

/**
 * Sends "ability is on cooldown" feedback as a single transient HUD line that
 * fades out after a configurable duration (hud.yml {@code messages} section).
 * Falls back to chat when the HUD is disabled. Debounced per player+ability so
 * right-click spam does not flicker the line.
 */
public final class HudCooldownFeedback {

    private static final long DEBOUNCE_MS = 800L;
    private static final String HUD_KEY = "hud:cooldown";
    private static final int HUD_PRIORITY = 100;

    /** key: playerId + abilityId */
    private static final Map<String, Long> lastWarn = new HashMap<>();

    private HudCooldownFeedback() {}

    public static void send(Player player, Ability ability, long remainingMs) {
        if (player == null || ability == null) return;
        if (!player.isOnline() || remainingMs <= 0) return;

        String abilityId = ability.getId() != null ? ability.getId() : "unknown";
        String debounceKey = player.getUniqueId() + ":" + abilityId;
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(debounceKey);
        if (last != null && now - last < DEBOUNCE_MS) return;
        lastWarn.put(debounceKey, now);

        HudConfig cfg = HudOverlayService.getInstance().getConfig();
        if (cfg == null) cfg = HudConfig.defaults();

        String text = format(cfg, displayName(ability), remainingMs);

        // If hud is disabled, fallback to chat (mirrors HudResourceFeedback)
        if (!cfg.enabled()) {
            BukkitMessageSender.getInstance().sendMessage(player, MessageComponent.of(text));
            playWarnSound(player);
            return;
        }

        HudOverlayService.getInstance().show(player, HUD_KEY, text,
                cfg.messages().onCooldownDurationMs(), HUD_PRIORITY, cfg.messages().onCooldownFadeMs());
        playWarnSound(player);
    }

    /**
     * Renders the {@code messages.on-cooldown} template: {@code <ability>} and
     * {@code <seconds>} (alias {@code %cds}) placeholders. Full color support
     * via {@code &} codes, hex and rgb().
     */
    static String format(HudConfig cfg, String abilityName, long remainingMs) {
        String template = cfg != null && cfg.messages() != null && cfg.messages().onCooldown() != null
                ? cfg.messages().onCooldown()
                : HudConfig.defaults().messages().onCooldown();
        double seconds = Math.max(0, remainingMs) / 1000.0;
        String sec = String.format(Locale.ROOT, "%.1f", seconds);
        return dev.core.utils.ColorCodes.translate(template
                .replace("<ability>", abilityName)
                .replace("<seconds>", sec)
                .replace("%cds", sec));
    }

    /** Display name of an ability; falls back to a prettified id. */
    static String displayName(Ability ability) {
        String name = ability.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String id = ability.getId() != null ? ability.getId() : "";
        String[] words = id.toLowerCase(Locale.ROOT).split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : id;
    }

    private static void playWarnSound(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        } catch (Exception ignored) {}
    }

    public static void clearDebounce(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        lastWarn.keySet().removeIf(k -> k.startsWith(uuid.toString()));
    }
}
