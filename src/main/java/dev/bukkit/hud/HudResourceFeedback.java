package dev.bukkit.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.bukkit.utils.BukkitMessageSender;
import dev.core.ability.CostMode;
import dev.core.stat.StatType;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.descriptor.StatDescriptor;
import dev.core.utils.MessageComponent;
import dev.bukkit.item.display.BukkitTextColorAdapter;

/**
 * Sends "Not enough <resource>" feedback. HUD transient when enabled,
 * chat fallback otherwise. Auto-colored from StatType/StatRegistry.
 */
public final class HudResourceFeedback {

    private static final long DEBOUNCE_MS = 800L;
    private static final long HUD_DURATION_MS = 1600L;
    private static final String HUD_KEY = "hud:resource";

    private static final Map<UUID, Long> lastWarn = new HashMap<>();

    private HudResourceFeedback() {}

    public static void send(Player player, CostMode mode) {
        if (player == null || mode == null) return;
        if (!player.isOnline()) return;

        long now = System.currentTimeMillis();
        Long last = lastWarn.get(player.getUniqueId());
        if (last != null && now - last < DEBOUNCE_MS) return;
        lastWarn.put(player.getUniqueId(), now);

        HudConfig cfg = HudOverlayService.getInstance().getConfig();
        if (cfg == null) cfg = HudConfig.defaults();
        String template = cfg.messages() != null && cfg.messages().notEnough() != null
                ? cfg.messages().notEnough() : "&cNot enough <resource>!";

        String coloredResource = coloredResourceName(mode);

        String replaced = template.replace("<resource>", coloredResource);
        String text = dev.core.utils.ColorCodes.translate(replaced);

        // If hud is disabled, fallback to chat (per requirement)
        if (!cfg.enabled()) {
            // use BukkitMessageSender for consistent styling, but direct chat is fine for resource warning
            BukkitMessageSender.getInstance().sendMessage(player, MessageComponent.of(text));
            // sound still
            try { player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f); } catch (Exception ignored) {}
            return;
        }

        HudOverlayService.getInstance().show(player, HUD_KEY, text, HUD_DURATION_MS, 100);
        try { player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 0.9f); } catch (Exception ignored) {}
    }

    /** Auto from StatType color via StatRegistry/adapter. */
    private static String coloredResourceName(CostMode mode) {
        try {
            StatType type = StatType.valueOf(mode.getResourceType());
            // Prefer registry descriptor (honors stats.yml color overrides)
            var opt = StatTypeAdapter.getDescriptor(type);
            if (opt.isPresent()) {
                StatDescriptor desc = opt.get();
                String chatCode = BukkitTextColorAdapter.toChatCode(desc.getColor());
                return chatCode + desc.getDisplayName();
            }
            // fallback to enum color
            return BukkitTextColorAdapter.toChatColor(type.getColor()) + type.getDisplayName();
        } catch (Exception e) {
            // fallback plain
            if (mode == CostMode.MANA) return ChatColor.AQUA + "Mana";
            if (mode == CostMode.HEALTH) return ChatColor.RED + "Health";
            return mode.name();
        }
    }

    public static void clearDebounce(Player player) {
        if (player != null) lastWarn.remove(player.getUniqueId());
    }
}
