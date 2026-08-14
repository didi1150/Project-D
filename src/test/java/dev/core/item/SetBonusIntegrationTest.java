package dev.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.item.display.BukkitLoreRenderer;
import dev.bukkit.utils.ManaDiscountUtils;
import dev.core.ability.Ability;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.SetBonus;
import dev.core.ability.impl.SpiritSceptreAbility;
import dev.core.ability.passive.SetPassive;
import dev.core.ability.passive.SetPassiveRegistry;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.display.RPGItemLoreRenderer;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemLoader;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Item sets: config loading, attachment to pieces and the full-set bonus
 * applying a stat through the StatEngine (Basic Archer Set -> +10% projectile
 * damage, i.e. the entity's projectile damage multiplier becomes 1.1).
 */
class SetBonusIntegrationTest {

    @AfterEach
    void clearEntityManager() {
        EntityManager.getInstance().clear();
        SetPassiveRegistry.clear();
    }

    @Test
    void loadSets_parsesFullSetProjectileDamageBonus() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot().getSection("item-sets.BASIC_ARCHER");
        root.set("name", "Basic Archer Set");
        root.set("pieces", List.of("BASIC_ARCHER_HELMET", "BASIC_ARCHER_CHESTPLATE",
                "BASIC_ARCHER_LEGGINGS", "BASIC_ARCHER_BOOTS"));
        root.set("bonuses.4", Map.of(
                "description", "Full Archer Set (4/4): +10% Projectile Damage",
                "stat-modifiers", List.of(Map.of(
                        "amount", 10.0, "statType", "PROJECTILE_DAMAGE", "modifierType", "FLAT")),
                "abilities", List.of()));

        Map<String, RPGItemSet> sets = RPGItemLoader.loadSets(provider);

        RPGItemSet set = sets.get("BASIC_ARCHER");
        assertTrue(set != null, "BASIC_ARCHER set must load");
        assertEquals(4, set.getPieceIds().size());
        SetBonus bonus4 = set.getBonusForPieces(4).orElse(null);
        assertTrue(bonus4 != null, "4-piece bonus must load");
        assertEquals(1, bonus4.getStatModifiers().size());
        assertEquals(StatType.PROJECTILE_DAMAGE, bonus4.getStatModifiers().get(0).statType);
        assertEquals(StatModifierType.FLAT, bonus4.getStatModifiers().get(0).statModifierType);
        assertEquals(10.0, bonus4.getStatModifiers().get(0).amount, 0.001);
    }

    @Test
    void loadAll_attachesItemsToTheirSet() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot().getSection("item-sets.BASIC_ARCHER");
        root.set("name", "Basic Archer Set");
        root.set("pieces", List.of("BASIC_ARCHER_HELMET", "BASIC_ARCHER_CHESTPLATE",
                "BASIC_ARCHER_LEGGINGS", "BASIC_ARCHER_BOOTS"));

        for (String id : List.of("BASIC_ARCHER_HELMET", "BASIC_ARCHER_CHESTPLATE",
                "BASIC_ARCHER_LEGGINGS", "BASIC_ARCHER_BOOTS")) {
            ConfigSection itemSection = provider.getRoot().getSection("items." + id);
            itemSection.set("name", id);
            itemSection.set("material", "LEATHER_HELMET");
            itemSection.set("slot", "HEAD");
        }

        Map<String, RPGItem> items = RPGItemLoader.loadAll(provider);

        RPGItemSet set = items.get("BASIC_ARCHER_HELMET").getItemSet().orElse(null);
        assertTrue(set != null, "archer pieces must be attached to the archer set");
        assertSame(set, items.get("BASIC_ARCHER_BOOTS").getItemSet().get(),
                "all archer pieces share the same set instance");
    }

    @Test
    void fullArcherSetAmplifiesProjectileDamageAndPartialSetDoesNot() {
        RPGEntity entity = new TestEntity(statsManager());

        RPGItemSet archerSet = RPGItemSet.builder("BASIC_ARCHER", "Basic Archer Set")
                .withPieceIds(List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"))
                .addBonus(4, new SetBonus("Full Archer Set (4/4): +10% Projectile Damage",
                        List.of(StatModifier.builder(10.0, StatModifierType.FLAT,
                                StatType.PROJECTILE_DAMAGE, "set:BASIC_ARCHER").build()),
                        List.of(), List.of()))
                .build();

        RPGItem helmet = piece("HELMET", EquipmentSlot.HEAD, archerSet);
        RPGItem chestplate = piece("CHESTPLATE", EquipmentSlot.CHEST, archerSet);
        RPGItem leggings = piece("LEGGINGS", EquipmentSlot.LEGS, archerSet);
        RPGItem boots = piece("BOOTS", EquipmentSlot.FEET, archerSet);

        assertEquals(1.0, entity.getProjectileDamageMultiplier(), 0.001, "no pieces: no bonus");

        entity.getEquipmentManager().equipItem(EquipmentSlot.HEAD, helmet);
        entity.getEquipmentManager().equipItem(EquipmentSlot.CHEST, chestplate);
        entity.getEquipmentManager().equipItem(EquipmentSlot.LEGS, leggings);
        assertEquals(1.0, entity.getProjectileDamageMultiplier(), 0.001, "3 pieces: no bonus");

        entity.getEquipmentManager().equipItem(EquipmentSlot.FEET, boots);
        assertEquals(1.1, entity.getProjectileDamageMultiplier(), 0.001,
                "full 4-piece set must amplify projectile damage by 10%");

        entity.getEquipmentManager().unequipItem(EquipmentSlot.HEAD);
        assertEquals(1.0, entity.getProjectileDamageMultiplier(), 0.001,
                "breaking the set must remove the bonus");
    }

    @Test
    void loadSets_parsesSetPassivesFromConfig() {
        SetPassiveRegistry.register(new TestPassive("THREAT"));

        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot().getSection("item-sets.BASIC_TANK");
        root.set("name", "Basic Tank Set");
        root.set("pieces", List.of("BASIC_TANK_HELMET", "BASIC_TANK_CHESTPLATE",
                "BASIC_TANK_LEGGINGS", "BASIC_TANK_BOOTS"));
        root.set("bonuses.4", Map.of(
                "description", "Full Tank Set (4/4): draws monster aggression",
                "stat-modifiers", List.of(),
                "passives", List.of("THREAT"),
                "abilities", List.of()));

        Map<String, RPGItemSet> sets = RPGItemLoader.loadSets(provider);

        RPGItemSet set = sets.get("BASIC_TANK");
        SetBonus bonus4 = set.getBonusForPieces(4).orElse(null);
        assertTrue(bonus4 != null, "4-piece bonus must load");
        assertEquals(1, bonus4.getPassives().size());
        assertEquals("THREAT", bonus4.getPassives().get(0).getId());
    }

    @Test
    void unknownSetPassiveIsSkippedWithWarning() {
        TestConfigProvider provider = new TestConfigProvider();
        ConfigSection root = provider.getRoot().getSection("item-sets.BASIC_TANK");
        root.set("name", "Basic Tank Set");
        root.set("pieces", List.of("A", "B", "C", "D"));
        root.set("bonuses.4", Map.of(
                "description", "Full Tank Set (4/4)",
                "stat-modifiers", List.of(),
                "passives", List.of("NOT_A_PASSIVE"),
                "abilities", List.of()));

        Map<String, RPGItemSet> sets = RPGItemLoader.loadSets(provider);

        SetBonus bonus4 = sets.get("BASIC_TANK").getBonusForPieces(4).orElse(null);
        assertTrue(bonus4.getPassives().isEmpty(), "unknown passives must be dropped");
    }

    @Test
    void equipmentManagerReportsActiveSetPassiveOnlyWhileSetWorn() {
        RPGEntity entity = new TestEntity(statsManager());

        RPGItemSet tankSet = RPGItemSet.builder("BASIC_TANK", "Basic Tank Set")
                .withPieceIds(List.of("HEAD", "CHEST", "LEGS", "FEET"))
                .addBonus(4, new SetBonus("Full Tank Set (4/4)", List.of(), List.of(),
                        List.of(new TestPassive("THREAT"))))
                .build();

        RPGItem helmet = piece("HEAD", EquipmentSlot.HEAD, tankSet);
        RPGItem chestplate = piece("CHEST", EquipmentSlot.CHEST, tankSet);
        RPGItem leggings = piece("LEGS", EquipmentSlot.LEGS, tankSet);
        RPGItem boots = piece("FEET", EquipmentSlot.FEET, tankSet);

        assertFalse(entity.getEquipmentManager().hasSetPassive("THREAT"), "no pieces: passive inactive");

        entity.getEquipmentManager().equipItem(EquipmentSlot.HEAD, helmet);
        entity.getEquipmentManager().equipItem(EquipmentSlot.CHEST, chestplate);
        entity.getEquipmentManager().equipItem(EquipmentSlot.LEGS, leggings);
        entity.getEquipmentManager().equipItem(EquipmentSlot.FEET, boots);
        assertTrue(entity.getEquipmentManager().hasSetPassive("THREAT"), "full 4-piece set must activate the passive");

        entity.getEquipmentManager().unequipItem(EquipmentSlot.HEAD);
        assertFalse(entity.getEquipmentManager().hasSetPassive("THREAT"), "breaking the set must deactivate the passive");
    }

    @Test
    void manaDiscountPassiveCutsManaCostsOnlyWhileFullSetWorn() {
        RPGEntity entity = new TestEntity(statsManager());

        RPGItemSet mageSet = RPGItemSet.builder("BASIC_MAGE", "Basic Mage Set")
                .withPieceIds(List.of("HEAD", "CHEST", "LEGS", "FEET"))
                .addBonus(4, new SetBonus("Full Mage Set (4/4): 10% cheaper mana costs",
                        List.of(), List.of(), List.of(new TestPassive("MANA_DISCOUNT"))))
                .build();

        RPGItem helmet = piece("HEAD", EquipmentSlot.HEAD, mageSet);
        RPGItem chestplate = piece("CHEST", EquipmentSlot.CHEST, mageSet);
        RPGItem leggings = piece("LEGS", EquipmentSlot.LEGS, mageSet);
        RPGItem boots = piece("FEET", EquipmentSlot.FEET, mageSet);

        assertEquals(100.0, ManaDiscountUtils.discountedCost(entity, "MANA_RESOURCE", 100.0), 0.001,
                "no pieces: full mana price");

        entity.getEquipmentManager().equipItem(EquipmentSlot.HEAD, helmet);
        entity.getEquipmentManager().equipItem(EquipmentSlot.CHEST, chestplate);
        entity.getEquipmentManager().equipItem(EquipmentSlot.LEGS, leggings);
        entity.getEquipmentManager().equipItem(EquipmentSlot.FEET, boots);
        assertEquals(90.0, ManaDiscountUtils.discountedCost(entity, "MANA_RESOURCE", 100.0), 0.001,
                "full mage set must reduce mana costs by 10%");
        assertEquals(100.0, ManaDiscountUtils.discountedCost(entity, "HEALTH_RESOURCE", 100.0), 0.001,
                "non-mana costs are never discounted");

        entity.getEquipmentManager().unequipItem(EquipmentSlot.HEAD);
        assertEquals(100.0, ManaDiscountUtils.discountedCost(entity, "MANA_RESOURCE", 100.0), 0.001,
                "breaking the set must restore full mana price");
    }

    @Test
    void itemLoreReflectsDiscountedManaCostForMageSetHolder() {
        RPGEntity entity = new TestEntity(statsManager());

        RPGItemSet mageSet = RPGItemSet.builder("BASIC_MAGE", "Basic Mage Set")
                .withPieceIds(List.of("HEAD", "CHEST", "LEGS", "FEET"))
                .addBonus(4, new SetBonus("Full Mage Set (4/4): 10% cheaper mana costs",
                        List.of(), List.of(), List.of(new TestPassive("MANA_DISCOUNT"))))
                .build();

        RPGItem sceptre = RPGItem.builder("SPIRIT_SCEPTRE", "Spirit Sceptre", EquipmentSlot.MAIN_HAND)
                .withAbilities(List.of(new SpiritSceptreAbility()))
                .build();

        RPGItemLoreRenderer renderer = new BukkitLoreRenderer();

        List<String> baseLore = renderer.render(sceptre, entity);
        assertTrue(baseLore.stream().anyMatch(line -> line.contains("+25")),
                "no set worn: lore shows the full mana cost");
        assertFalse(baseLore.stream().anyMatch(line -> line.contains("reduced by 10%")),
                "no set worn: lore must not claim a discount");

        RPGItem helmet = piece("HEAD", EquipmentSlot.HEAD, mageSet);
        RPGItem chestplate = piece("CHEST", EquipmentSlot.CHEST, mageSet);
        RPGItem leggings = piece("LEGS", EquipmentSlot.LEGS, mageSet);
        RPGItem boots = piece("FEET", EquipmentSlot.FEET, mageSet);
        entity.getEquipmentManager().equipItem(EquipmentSlot.HEAD, helmet);
        entity.getEquipmentManager().equipItem(EquipmentSlot.CHEST, chestplate);
        entity.getEquipmentManager().equipItem(EquipmentSlot.LEGS, leggings);
        entity.getEquipmentManager().equipItem(EquipmentSlot.FEET, boots);

        List<String> discountedLore = renderer.render(sceptre, entity);
        assertTrue(discountedLore.stream().anyMatch(line -> line.contains("+22.5")),
                "full mage set: lore shows the 10% discounted mana cost");
        assertTrue(discountedLore.stream().anyMatch(line -> line.contains("reduced by 10%")),
                "full mage set: lore explains the discount");

        entity.getEquipmentManager().unequipItem(EquipmentSlot.HEAD);
        List<String> restoredLore = renderer.render(sceptre, entity);
        assertTrue(restoredLore.stream().anyMatch(line -> line.contains("+25")),
                "breaking the set restores the full cost in the lore");
    }

    private static RPGItem piece(String id, EquipmentSlot slot, RPGItemSet set) {
        return RPGItem.builder(id, id, slot).withItemSet(set).build();
    }

    // ---------------------------------------------------------------- stubs

    private static StatManager statsManager() {
        Map<StatType, Stat> stats = new HashMap<>();
        long now = System.currentTimeMillis();
        stats.put(StatType.HEALTH_RESOURCE, new ResourceStat("health", t -> 100.0, t -> 0.0, now));
        stats.put(StatType.PROJECTILE_DAMAGE, new CombatStat("PROJECTILE_DAMAGE", 0));
        return new StatManager(stats);
    }

    private static final class TestEntity extends RPGEntity {
        TestEntity(StatManager statManager) {
            super(statManager, UUID.randomUUID(), "archer", EntityType.PLAYER, new NoopEffectManager(),
                    BukkitEventBus.getInstance(), RPGClassType.ARCHER);
        }
    }

    private static final class TestPassive implements SetPassive {
        private final String id;

        TestPassive(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private static final class NoopEffectManager implements EffectManagerInterface {
        @Override
        public dev.core.ability.Effect cast(RPGEntity entity, dev.core.ability.Ability ability) {
            return null;
        }

        @Override
        public boolean canActivate(RPGEntity entity, dev.core.ability.Ability ability) {
            return false;
        }

        @Override
        public long remainingCooldown(RPGEntity entity, dev.core.ability.Ability ability) {
            return 0;
        }

        @Override
        public void tick(long now) {
        }

        @Override
        public void cancelAll() {
        }
    }

    // ---------------------------------------------------------------- test config

    private static final class TestConfigProvider implements ConfigProvider {
        private final TestConfigSection root = new TestConfigSection(new HashMap<>());

        @Override
        public ConfigSection getRoot() {
            return root;
        }

        @Override
        public ConfigSection getSection(String path) {
            return root.getSection(path);
        }

        @Override
        public void save() {
        }
    }

    private static final class TestConfigSection implements ConfigSection {
        private final Map<String, Object> values;

        private TestConfigSection(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public String getString(String path, String def) {
            Object value = resolve(path);
            return value == null ? def : value.toString();
        }

        @Override
        public int getInt(String path, int def) {
            Object value = resolve(path);
            return value == null ? def : Integer.parseInt(value.toString());
        }

        @Override
        public double getDouble(String path, double def) {
            Object value = resolve(path);
            return value == null ? def : Double.parseDouble(value.toString());
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            Object value = resolve(path);
            return value == null ? def : Boolean.parseBoolean(value.toString());
        }

        @Override
        public List<String> getStringList(String path) {
            Object value = resolve(path);
            if (value instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    result.add(String.valueOf(item));
                }
                return result;
            }
            return new ArrayList<>();
        }

        @Override
        public ConfigSection getSection(String path) {
            Object value = resolve(path);
            if (value instanceof TestConfigSection section) {
                return section;
            }
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return new TestConfigSection(typed);
            }
            TestConfigSection section = new TestConfigSection(new HashMap<>());
            set(path, section);
            return section;
        }

        @Override
        public List<ConfigSection> getSectionList(String path) {
            Object value = resolve(path);
            if (value instanceof List<?> list) {
                List<ConfigSection> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.add(new TestConfigSection(new HashMap<>(toStringMap(map))));
                    }
                }
                return result;
            }
            return new ArrayList<>();
        }

        @Override
        public void set(String path, Object value) {
            if (path == null || path.isBlank()) {
                return;
            }
            String[] parts = path.split("\\.");
            Map<String, Object> target = values;
            for (int i = 0; i < parts.length - 1; i++) {
                String key = parts[i];
                Object nested = target.get(key);
                if (!(nested instanceof Map<?, ?> map)) {
                    nested = new HashMap<String, Object>();
                    target.put(key, nested);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) nested;
                target = typed;
            }
            target.put(parts[parts.length - 1], value);
        }

        @Override
        public Set<String> getKeys() {
            return values.keySet();
        }

        private Object resolve(String path) {
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] parts = path.split("\\.");
            Object value = values;
            for (String part : parts) {
                if (value instanceof Map<?, ?> map) {
                    value = map.get(part);
                } else {
                    return null;
                }
            }
            return value;
        }

        private static Map<String, Object> toStringMap(Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
    }
}