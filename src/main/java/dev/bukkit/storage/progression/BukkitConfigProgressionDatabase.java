package dev.bukkit.storage.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.progression.PlayerClassProgression;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;
import dev.core.storage.database.ProgressionDatabaseStrategy;

public class BukkitConfigProgressionDatabase implements ProgressionDatabaseStrategy {

    private final ConfigProvider provider;

    public BukkitConfigProgressionDatabase(ConfigProvider provider) {
        this.provider = provider;
    }

    @Override
    public Map<RPGClassType, PlayerClassProgression> loadAll(UUID playerId) {
        Map<RPGClassType, PlayerClassProgression> progressions = new HashMap<>();
        ConfigSection playerSec = provider.getSection(playerPath(playerId));
        ConfigSection classesSec = playerSec == null ? null : playerSec.getSection("classes");
        if (classesSec == null) {
            return progressions;
        }

        for (String key : classesSec.getKeys()) {
            RPGClassType type;
            try {
                type = RPGClassType.valueOf(key);
            } catch (IllegalArgumentException e) {
                System.out.println("[Progression] Ignoring unknown class key '" + key + "' for " + playerId);
                continue;
            }
            ConfigSection classSec = classesSec.getSection(key);
            if (classSec == null) {
                continue;
            }
            PlayerClassProgression prog = new PlayerClassProgression(type);
            int level = classSec.getInt("level", 0);
            int xp = classSec.getInt("xp", 0);
            prog.setLevel(level);
            if (xp > 0 || level > 0) {
                prog.setXp(xp);
            }
            prog.setUsableItems(classSec.getInt("usableItems", 5));
            progressions.put(type, prog);
        }
        return progressions;
    }

    @Override
    public void save(UUID playerId, PlayerClassProgression progression) {
        writeProgression(playerId, progression);
        provider.save();
    }

    @Override
    public void saveAll(UUID playerId, Map<RPGClassType, PlayerClassProgression> progressions) {
        if (progressions == null) {
            return;
        }
        for (PlayerClassProgression prog : progressions.values()) {
            writeProgression(playerId, prog);
        }
        provider.save();
    }

    @Override
    public RPGClassType getActiveClass(UUID playerId) {
        String value = provider.getRoot().getString(playerPath(playerId) + ".activeClass",
                RPGClassType.NONE.name());
        try {
            return RPGClassType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return RPGClassType.NONE;
        }
    }

    @Override
    public void setActiveClass(UUID playerId, RPGClassType rpgClassType) {
        RPGClassType type = rpgClassType == null ? RPGClassType.NONE : rpgClassType;
        provider.getRoot().set(playerPath(playerId) + ".activeClass", type.name());
        provider.save();
    }

    @Override
    public Set<String> loadUnlockedItems(UUID playerId) {
        ConfigSection playerSec = provider.getSection(playerPath(playerId));
        if (playerSec == null) {
            return Set.of();
        }
        return Set.copyOf(playerSec.getStringList("unlocked-items"));
    }

    @Override
    public void saveUnlockedItems(UUID playerId, Set<String> itemIds) {
        ArrayList<String> sorted = itemIds == null ? new ArrayList<>()
                : new ArrayList<>(itemIds);
        sorted.sort(Comparator.naturalOrder());
        provider.getRoot().set(playerPath(playerId) + ".unlocked-items", sorted);
        provider.save();
    }

    private void writeProgression(UUID playerId, PlayerClassProgression progression) {
        String base = playerPath(playerId) + ".classes." + progression.getClassType().name();
        provider.getRoot().set(base + ".level", progression.getLevel());
        provider.getRoot().set(base + ".xp", progression.getXp());
        provider.getRoot().set(base + ".usableItems", progression.getUsableItems());
    }

    private static String playerPath(UUID playerId) {
        return "players." + playerId;
    }
}
