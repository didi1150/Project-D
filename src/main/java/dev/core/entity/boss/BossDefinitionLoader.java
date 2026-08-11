package dev.core.entity.boss;

import java.util.ArrayList;
import java.util.List;

import dev.core.stat.StatManager;
import dev.core.stat.loader.StatLoader;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Loads {@link BossDefinition}s from a config file shaped like:
 *
 * <pre>
 * bosses:
 *   1:
 *     wither-king:
 *       display-name: "&cWither King"
 *       entity-type: WITHER
 *       defeat-stage: death
 *       stats: { HEALTH_MAX: 15000 }
 *       stages:
 *         - type: THRESHOLD
 *           id: phase1
 *           health-threshold: 0.75
 *           next-stage: phase2
 *           attack: WITHER_SKULL
 *         - type: MONOLOGUE
 *           id: death
 *           lines: ["..."]
 * </pre>
 */
public class BossDefinitionLoader {

    public static List<BossDefinition> loadAll(ConfigProvider provider, BossStageTypeRegistry stageTypeRegistry,
            BossStrategyRegistry strategyRegistry) {
        List<BossDefinition> definitions = new ArrayList<>();
        ConfigSection root = provider.getRoot().getSection("bosses");
        if (root == null) {
            return definitions;
        }
        for (String floorKey : root.getKeys()) {
            int floor;
            try {
                floor = Integer.parseInt(floorKey);
            } catch (NumberFormatException ignored) {
                continue;
            }
            ConfigSection floorSection = root.getSection(floorKey);
            for (String bossId : floorSection.getKeys()) {
                definitions.add(load(floor, bossId, floorSection.getSection(bossId), stageTypeRegistry,
                        strategyRegistry));
            }
        }
        return definitions;
    }

    private static BossDefinition load(int floor, String id, ConfigSection section,
            BossStageTypeRegistry stageTypeRegistry, BossStrategyRegistry strategyRegistry) {
        String displayName = section.getString("display-name", id);
        String entityType = section.getString("entity-type", "");
        String defeatStageId = section.getString("defeat-stage", null);
        StatManager statManager = new StatManager(StatLoader.loadStats(section.getSection("stats")));
        List<BossStage> stages = loadStages(section.getSectionList("stages"), stageTypeRegistry, strategyRegistry);
        return new BossDefinition(id, displayName, entityType, floor, defeatStageId, statManager, stages);
    }

    private static List<BossStage> loadStages(List<ConfigSection> stageSections, BossStageTypeRegistry stageTypeRegistry,
            BossStrategyRegistry strategyRegistry) {
        List<BossStage> stages = new ArrayList<>();
        for (ConfigSection stageSection : stageSections) {
            String typeKey = stageSection.getString("type", "");
            if (typeKey.isBlank()) {
                throw new IllegalArgumentException("Boss stage is missing a 'type' key");
            }
            BossStageType type = stageTypeRegistry.resolve(typeKey)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown boss stage type: " + typeKey));
            stages.add(type.build(stageSection, strategyRegistry));
        }
        return stages;
    }
}
