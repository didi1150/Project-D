package dev.bukkit.storage.progression;

import java.util.HashMap;
import java.util.Map;
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
        ConfigSection playerSec = provider.getSection("players." + playerId.toString() + ".classes");
        if (playerSec == null) {
            return progressions;
        }

        for (String key : playerSec.getKeys()) {
            RPGClassType type = RPGClassType.valueOf(key);
            ConfigSection classSec = playerSec.getSection(key);
            int level = classSec.getInt("level", 1);
            int xp = classSec.getInt("xp", 0);
            int usable = classSec.getInt("usableItems", 0);

            PlayerClassProgression prog = new PlayerClassProgression(type);
            prog.setLevel(level);
            prog.setXp(xp);
            prog.setUsableItems(usable);
            progressions.put(type, prog);
        }
        return progressions;
    }

    @Override
    public void save(UUID playerId, PlayerClassProgression progression) {
        String base = "players." + playerId.toString() + ".classes." + progression.getClassType().name();
        provider.getRoot().set(base + ".level", progression.getLevel());
        provider.getRoot().set(base + ".xp", progression.getXp());
        provider.getRoot().set(base + ".usableItems", progression.getUsableItems());
        provider.save();
    }

    @Override
    public void saveAll(UUID playerId, Map<RPGClassType, PlayerClassProgression> progressions) {
        for (PlayerClassProgression prog : progressions.values()) {
            save(playerId, prog);
        }
    }

    @Override
    public RPGClassType getActiveClass(UUID playerId) {
        String base = "players." + playerId.toString() + ".activeClass";
        return RPGClassType.valueOf(provider.getRoot().getString(base, RPGClassType.NONE.name()));
    }

    @Override
    public void setActiveClass(UUID playerId, RPGClassType rpgClassType) {
        String base = "players." + playerId.toString() + ".activeClass";
        provider.getRoot().set(base, rpgClassType.name());
        provider.save();
    }

}
