package dev.bukkit.reward.special;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.reward.special.SpecialItemBuff;
import dev.core.reward.special.SpecialItemBuffConfig;
import dev.core.reward.special.SpecialItemBuffConfigLoader;
import dev.core.reward.special.SpecialItemBuffStat;
import dev.core.stat.modifier.StatModifier;
import dev.core.storage.config.ConfigProvider;

/**
 * Applies the configurable random buffs used by Gambler's Note and Tic Tac Toe
 * wins. Buffs are temporary {@link StatModifier}s, so they affect the RPG stat
 * pipeline (unlike vanilla Strength/Regeneration, which the custom combat
 * system does not read).
 */
public final class SpecialItemBuffService {

    private static final SpecialItemBuffService INSTANCE = new SpecialItemBuffService();
    private static final String SOURCE_PREFIX = "special-item-buff:";

    private final Random random = new Random();
    private SpecialItemBuffConfig config = new SpecialItemBuffConfig(SpecialItemBuffConfigLoader.defaults());

    private SpecialItemBuffService() {
    }

    public static SpecialItemBuffService getInstance() {
        return INSTANCE;
    }

    public void init(ConfigProvider provider) {
        this.config = SpecialItemBuffConfigLoader.load(provider);
    }

    public Optional<SpecialItemBuff> applyRandom(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        Optional<SpecialItemBuff> buff = config.random(random);
        buff.ifPresent(value -> apply(player, value));
        return buff;
    }

    /**
     * Applies one randomly selected buff to every supplied player and returns
     * the chosen buff so callers can announce it once.
     */
    public Optional<SpecialItemBuff> applyRandomToAll(Collection<? extends Player> players) {
        if (players == null || players.isEmpty()) {
            return Optional.empty();
        }
        Optional<SpecialItemBuff> buff = config.random(random);
        if (buff.isEmpty()) {
            return Optional.empty();
        }
        for (Player player : List.copyOf(players)) {
            apply(player, buff.get());
        }
        return buff;
    }

    private void apply(Player player, SpecialItemBuff buff) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        RPGEntity holder = EntityManager.getInstance().getEntity(player.getUniqueId()).orElse(null);
        if (holder == null) {
            return;
        }
        String sourceId = SOURCE_PREFIX + buff.id();
        holder.getStatManager().removeModifiersBySource(sourceId);
        for (SpecialItemBuffStat stat : buff.stats()) {
            StatModifier modifier = StatModifier.builder(stat.amount(), stat.modifierType(), stat.statType(), sourceId)
                    .duration(buff.durationSeconds())
                    .stackPolicy(stat.stackPolicy())
                    .statTarget(stat.statTarget())
                    .build();
            holder.addStatModifier(modifier);
        }
        player.sendMessage("§d✦ " + buff.displayName() + " §7for §f" + formatDuration(buff.durationSeconds())
                + "§7.");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
    }

    private static String formatDuration(double seconds) {
        if (seconds == Math.rint(seconds)) {
            return String.format("%.0fs", seconds);
        }
        return String.format("%.1fs", seconds);
    }
}
