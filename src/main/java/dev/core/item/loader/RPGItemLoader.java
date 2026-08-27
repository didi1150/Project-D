package dev.core.item.loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.SetBonus;
import dev.core.ability.passive.SetPassive;
import dev.core.ability.passive.SetPassiveRegistry;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.ItemType;
import dev.core.item.ItemUsage;
import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatTarget;
import dev.core.stat.StatType;
import dev.core.stat.modifier.ModifierStackPolicy;
import dev.core.stat.modifier.StatModifierType;
import dev.core.stat.modifier.StatModifier;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public class RPGItemLoader {

    public static Map<String, RPGItem> loadAll(ConfigProvider provider) {
        Map<String, RPGItemSet> sets = loadSets(provider);
        Map<String, RPGItem> items = new HashMap<>();
        ConfigSection root = provider.getRoot().getSection("items");

        for (String id : root.getKeys()) {
            RPGItem item = load(id, root.getSection(id));
            sets.values().stream().filter(set -> set.containsPiece(id)).findFirst().ifPresent(item::setItemSet);
            items.put(id, item);
        }

        return items;
    }

    /**
     * Loads the {@code item-sets} section of items.yml. A set groups piece ids
     * and grants a {@code SetBonus} (stat modifiers and/or abilities) once a
     * given number of pieces are equipped at the same time.
     */
    public static Map<String, RPGItemSet> loadSets(ConfigProvider provider) {
        Map<String, RPGItemSet> sets = new HashMap<>();
        ConfigSection root = provider.getRoot().getSection("item-sets");
        if (root == null || root.getKeys().isEmpty()) {
            return sets;
        }

        for (String id : root.getKeys()) {
            ConfigSection section = root.getSection(id);
            String name = section.getString("name", id);
            List<String> pieces = section.getStringList("pieces");

            RPGItemSet.Builder builder = RPGItemSet.builder(id, name).withPieceIds(pieces);

            ConfigSection bonusesSection = section.getSection("bonuses");
            if (bonusesSection != null) {
                for (String pieceCountKey : bonusesSection.getKeys()) {
                    try {
                        int pieceCount = Integer.parseInt(pieceCountKey);
                        builder.addBonus(pieceCount, loadSetBonus(id, pieceCount, bonusesSection.getSection(pieceCountKey)));
                    } catch (NumberFormatException e) {
                        System.out.println("Item set " + id + ": bonus key '" + pieceCountKey + "' is not a piece count; ignored.");
                    }
                }
            }

            sets.put(id, builder.build());
        }

        return sets;
    }

    private static SetBonus loadSetBonus(String setId, int pieceCount, ConfigSection section) {
        String description = section.getString("description", "");
        List<StatModifier> statModifiers = new ArrayList<>();
        for (ConfigSection s : section.getSectionList("stat-modifiers")) {
            statModifiers.add(parseStatModifier(s, "set:" + setId + ":" + pieceCount));
        }

        List<Ability> abilities = new ArrayList<>();
        for (String abilityId : section.getStringList("abilities")) {
            AbilityRegistry.getOrWarn(abilityId, "item set " + setId).ifPresent(abilities::add);
        }

        List<SetPassive> passives = new ArrayList<>();
        for (String passiveId : section.getStringList("passives")) {
            SetPassiveRegistry.getOrWarn(passiveId, "item set " + setId).ifPresent(passives::add);
        }

        return new SetBonus(description, statModifiers, abilities, passives);
    }

    public static RPGItem load(String id, ConfigSection section) {
        String name = section.getString("name", id);
        String material = section.getString("material", "STONE");
        EquipmentSlot slot = EquipmentSlot.valueOf(section.getString("slot", "MAIN_HAND"));

        List<StatModifier> passive = new ArrayList<>();
        for (ConfigSection s : section.getSectionList("passive-stats")) {
            passive.add(parseStatModifier(s, id));
        }

        List<StatModifier> active = new ArrayList<>();
        for (ConfigSection s : section.getSectionList("active-stats")) {
            active.add(parseStatModifier(s, id));
        }

        List<Ability> abilities = new ArrayList<>();
        for (String abilityId : section.getStringList("abilities")) {
            AbilityRegistry.getOrWarn(abilityId, "item " + id).ifPresent(abilities::add);
        }

        RPGClassType classType = RPGClassType.valueOf(section.getString("classType", RPGClassType.NONE.name()));
        int unlockLevel = section.getInt("unlockLevel", 0);

        List<RPGClassType> allowedClasses = new ArrayList<>();
        for (String className : section.getStringList("allowed-classes")) {
            try {
                allowedClasses.add(RPGClassType.valueOf(className.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                System.out.println("Unknown allowed class '" + className + "' for item " + id + "; ignored.");
            }
        }
        boolean mobOnly = section.getBoolean("mob-only", false);
        ItemUsage usage;
        String usageRaw = section.getString("usage", null);
        if (usageRaw != null) {
            try {
                usage = ItemUsage.valueOf(usageRaw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                usage = mobOnly ? ItemUsage.MOB_ONLY : ItemUsage.BOTH;
            }
        } else {
            usage = mobOnly ? ItemUsage.MOB_ONLY : ItemUsage.BOTH;
        }

        ItemType itemType = parseItemType(id, section.getString("itemType", null));
        // A bow normally needs arrows; itemType is parsed first so the default
        // below can key off it. Special bows (e.g. the Bonemerang) opt out.
        boolean requiresArrows = itemType == ItemType.BOW;
        if (section.getString("requiresArrows", null) != null) {
            requiresArrows = section.getBoolean("requiresArrows", requiresArrows);
        }

        List<String> description = section.getStringList("description");

        return RPGItem.builder(id, name, slot).withMaterial(material).withPassiveStats(passive).withActiveStats(active)
                .withAbilities(abilities).withRpgClassType(classType).withUnlockLevel(unlockLevel)
                .withAllowedClasses(allowedClasses).usage(usage)
                .withItemType(itemType).requiresArrows(requiresArrows)
                .withLeatherColor(RPGItem.parseRgbColor(section.getString("leather-color", null)))
                .withSkullOwner(section.getString("skull-owner", null))
                .withSkullTexture(section.getString("skull-texture", null))
                .withDescription(description)
                .build();
    }

    /**
     * Parses the {@code itemType} config key; unknown values fall back to
     * {@link ItemType#MISC} with a warning.
     */
    private static ItemType parseItemType(String itemId, String raw) {
        if (raw == null) {
            return ItemType.MISC;
        }
        try {
            return ItemType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown item type '" + raw + "' for item " + itemId + "; defaulting to MISC.");
            return ItemType.MISC;
        }
    }

    /**
     * Parses a single stat modifier entry shared by item stats
     * ({@code passive-stats}/{@code active-stats}) and set bonus
     * ({@code stat-modifiers}) config blocks.
     */
    private static StatModifier parseStatModifier(ConfigSection s, String sourceId) {
        double amount = s.getDouble("amount", 0);
        ModifierStackPolicy policy = ModifierStackPolicy.valueOf(s.getString("policy", "STACK"));
        StatModifierType statModifierType = StatModifierType
                .valueOf(s.getString("statModifierType", s.getString("modifierType", "FLAT")));
        StatType statType = StatType.valueOf(s.getString("statType", StatType.ATTACK_DAMAGE.name()));
        StatTarget statTarget = StatTarget.valueOf(s.getString("statTarget", "BOTH"));
        int priority = s.getInt("priority", 0);

        return StatModifier.builder(amount, statModifierType, statType, sourceId)
            .stackPolicy(policy)
            .statTarget(statTarget)
            .priority(priority)
            .build();
    }

    public static void saveAll(ConfigProvider provider, Map<String, RPGItem> items) {
        ConfigSection root = provider.getRoot().getSection("items");

        for (RPGItem item : items.values()) {
            ConfigSection section = root.getSection(item.getId());
            section.set("name", item.getName());
            section.set("material", item.getMaterial());
            section.set("slot", item.getEquipmentSlot().name());

            item.getLeatherColor().ifPresent(color -> section.set("leather-color", color));
            item.getSkullOwner().ifPresent(owner -> section.set("skull-owner", owner));
            item.getSkullTexture().ifPresent(texture -> section.set("skull-texture", texture));

            List<Map<String, Object>> passive = new ArrayList<>();
            for (StatModifier sm : item.getPassiveStats()) {
                Map<String, Object> map = new HashMap<>();
                map.put("amount", sm.amount);
                map.put("policy", sm.stackPolicy.name());
                map.put("statModifierType", sm.statModifierType.name());
                map.put("statType", sm.statType.name());
                map.put("statTarget", sm.statTarget.name());
                passive.add(map);
            }
            section.set("passive-stats", passive);

            List<Map<String, Object>> active = new ArrayList<>();
            for (StatModifier sm : item.getActiveStats()) {
                Map<String, Object> map = new HashMap<>();
                map.put("amount", sm.amount);
                map.put("policy", sm.stackPolicy.name());
                map.put("statModifierType", sm.statModifierType.name());
                map.put("statType", sm.statType.name());
                map.put("statTarget", sm.statTarget.name());
                active.add(map);
            }
            section.set("active-stats", active);

            List<String> abilityIds = item.getAbilities().stream().map(Ability::getId).toList();
            section.set("abilities", abilityIds);

            section.set("classType", item.getRpgClassType().name());
            section.set("unlockLevel", item.getUnlockLevel());
            section.set("itemType", item.getItemType().name());
            section.set("requiresArrows", item.requiresArrows());
            if (!item.getAllowedClasses().isEmpty()) {
                section.set("allowed-classes", item.getAllowedClasses().stream().map(Enum::name).toList());
            }
            if (item.getUsage() == ItemUsage.MOB_ONLY) {
                section.set("usage", item.getUsage().name());
            }
        }

        provider.save();
    }

    public static void saveItem(ConfigProvider provider, RPGItem item) {
        ConfigSection root = provider.getRoot().getSection("items");

        ConfigSection section = root.getSection(item.getId());
        section.set("name", item.getName());
        section.set("material", item.getMaterial());
        section.set("slot", item.getEquipmentSlot().name());

        item.getLeatherColor().ifPresent(color -> section.set("leather-color", color));
        item.getSkullOwner().ifPresent(owner -> section.set("skull-owner", owner));
        item.getSkullTexture().ifPresent(texture -> section.set("skull-texture", texture));

        List<Map<String, Object>> passive = new ArrayList<>();
        for (StatModifier sm : item.getPassiveStats()) {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", sm.amount);
            map.put("policy", sm.stackPolicy.name());
            map.put("statModifierType", sm.statModifierType.name());
            map.put("statType", sm.statType.name());
            map.put("statTarget", sm.statTarget.name());
            passive.add(map);
        }
        section.set("passive-stats", passive);

        List<Map<String, Object>> active = new ArrayList<>();
        for (StatModifier sm : item.getActiveStats()) {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", sm.amount);
            map.put("policy", sm.stackPolicy.name());
            map.put("statModifierType", sm.statModifierType.name());
            map.put("statType", sm.statType.name());
            map.put("statTarget", sm.statTarget.name());
            active.add(map);
        }
        section.set("active-stats", active);

        List<String> abilityIds = item.getAbilities().stream().map(Ability::getId).toList();
        section.set("abilities", abilityIds);
        
        section.set("classType", item.getRpgClassType().name());
        section.set("unlockLevel", item.getUnlockLevel());
        section.set("itemType", item.getItemType().name());
        section.set("requiresArrows", item.requiresArrows());
        if (!item.getAllowedClasses().isEmpty()) {
            section.set("allowed-classes", item.getAllowedClasses().stream().map(Enum::name).toList());
        }
        if (item.getUsage() == ItemUsage.MOB_ONLY) {
            section.set("usage", item.getUsage().name());
        }
        provider.save();
    }
}
