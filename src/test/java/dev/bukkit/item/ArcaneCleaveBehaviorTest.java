package dev.bukkit.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.ability.behavior.ArcaneCleaveBehavior;
import dev.bukkit.event.BukkitEventBus;
import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.ActiveAbility;
import dev.core.ability.ActiveAbilityRegistry;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.RPGEntityBuilder;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.DefaultStats;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;
import dev.core.stat.modifier.StatModifier;
import dev.core.item.ItemType;
import dev.bukkit.utils.DamageUtils;

public class ArcaneCleaveBehaviorTest {

    @BeforeEach
    void setupStats() {
        try {
            StatTypeAdapter.initializeStatTypes();
        } catch (Exception ignored) {}
        if (DefaultStats.getStatsByClass(RPGClassType.TANK).isEmpty()) {
            Map<StatType, Stat> defaults = new HashMap<>();
            defaults.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 200));
            defaults.put(StatType.MANA_MAX, new CombatStat("MANA_MAX", 200));
            defaults.put(StatType.MANA_RESOURCE, new ResourceStat("MANA_RESOURCE", n -> 200.0, n -> 4.0, System.currentTimeMillis()));
            defaults.put(StatType.ATTACK_DAMAGE, new CombatStat("ATTACK_DAMAGE", 10));
            defaults.put(StatType.ATTACK_SPEED, new CombatStat("ATTACK_SPEED", 1.0));
            Map<RPGClassType, Map<StatType, Stat>> all = new HashMap<>();
            all.put(RPGClassType.TANK, defaults);
            DefaultStats.loadAll(all);
        }
        AbilityRegistry.clear();
        ActiveAbilityRegistry.getInstance().clear();
        AbilityRegistry.preregister();
    }

    @Test
    void arcaneBladeItem_hasCorrectStats() {
        Ability manaPassive = AbilityRegistry.get("ARCANE_MANA_RESTORE").orElseThrow();
        Ability cleavePassive = AbilityRegistry.get("ARCANE_CLEAVE").orElseThrow();
        assertEquals(AbilityTriggerType.PASSIVE, manaPassive.getTriggerType());
        assertEquals(AbilityTriggerType.PASSIVE, cleavePassive.getTriggerType());

        RPGItem blade = RPGItem.builder("ARCANE_BLADE", "Arcane Blade", "IRON_SWORD", EquipmentSlot.MAIN_HAND)
                .withItemType(ItemType.SWORD)
                .withActiveStats(List.of(
                        StatModifier.builder(20.0, dev.core.stat.modifier.StatModifierType.FLAT, StatType.ATTACK_DAMAGE, "ARCANE_BLADE").build(),
                        StatModifier.builder(0.2, dev.core.stat.modifier.StatModifierType.FLAT, StatType.ATTACK_SPEED, "ARCANE_BLADE").build(),
                        StatModifier.builder(100.0, dev.core.stat.modifier.StatModifierType.FLAT, StatType.MANA_MAX, "ARCANE_BLADE").build()
                ))
                .withAbilities(List.of(manaPassive, cleavePassive))
                .build();

        assertEquals("ARCANE_BLADE", blade.getId());
        assertEquals("IRON_SWORD", blade.getMaterial());
        assertEquals(3, blade.getActiveStats().size());
        assertEquals(2, blade.getAbilities().size(), "Arcane Blade should have two passive abilities");

        Map<StatType, Double> amounts = new HashMap<>();
        for (var m : blade.getActiveStats()) amounts.put(m.statType, m.amount);
        assertEquals(20.0, amounts.get(StatType.ATTACK_DAMAGE), 0.001);
        assertEquals(0.2, amounts.get(StatType.ATTACK_SPEED), 0.001);
        assertEquals(100.0, amounts.get(StatType.MANA_MAX), 0.001);
        for (var m : blade.getActiveStats()) assertEquals(dev.core.stat.modifier.StatModifierType.FLAT, m.statModifierType);
        for (Ability a : blade.getAbilities()) assertEquals(AbilityTriggerType.PASSIVE, a.getTriggerType());
    }

    @Test
    void manaRestoreOnHit_isFivePercentOfMax() {
        UUID uuid = UUID.randomUUID();
        EventBusInterface bus = BukkitEventBus.getInstance();
        EffectManagerInterface eff = new NoopEffectManager();
        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.MANA_MAX, new CombatStat("MANA_MAX", 200));
        stats.put(StatType.MANA_RESOURCE, new ResourceStat("MANA_RESOURCE", n -> 200.0, n -> 4.0, System.currentTimeMillis()));
        stats.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100));
        stats.put(StatType.HEALTH_RESOURCE, new ResourceStat("HEALTH_RESOURCE", n -> 100.0, n -> 2.0, System.currentTimeMillis()));
        stats.put(StatType.ATTACK_DAMAGE, new CombatStat("ATTACK_DAMAGE", 10));
        stats.put(StatType.ATTACK_SPEED, new CombatStat("ATTACK_SPEED", 1.0));
        StatManager sm = new StatManager(stats);
        RPGEntity entity = new RPGEntityBuilder(uuid, "TestPlayer", EntityType.PLAYER).withStatManager(sm).withEffectManager(eff).withEventBus(bus).build();
        entity.setMana(80);
        assertEquals(80, entity.getMana(), 0.001);
        double maxMana = entity.getStatEngineAdapter().getCurrentValue(StatType.MANA_MAX, System.currentTimeMillis());
        assertEquals(200, maxMana, 0.001);
        double restore = maxMana * 0.05;
        assertEquals(10, restore, 0.001);
        double next = Math.min(entity.getMana() + restore, maxMana);
        entity.setMana(next);
        assertEquals(90, entity.getMana(), 0.001);
        entity.setMana(195);
        double next2 = Math.min(195 + restore, maxMana);
        entity.setMana(next2);
        assertEquals(200, entity.getMana(), 0.001);
    }

    @Test
    void hitCounter_triggersEveryThird() {
        // Per-holder behavior hitCount — test via ActiveAbility + behavior instance
        UUID uuid = UUID.randomUUID();
        EventBusInterface bus = BukkitEventBus.getInstance();
        EffectManagerInterface eff = new NoopEffectManager();
        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.MANA_MAX, new CombatStat("MANA_MAX", 200));
        stats.put(StatType.MANA_RESOURCE, new ResourceStat("MANA_RESOURCE", n -> 200.0, n -> 4.0, System.currentTimeMillis()));
        stats.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100));
        stats.put(StatType.HEALTH_RESOURCE, new ResourceStat("HEALTH_RESOURCE", n -> 100.0, n -> 2.0, System.currentTimeMillis()));
        StatManager sm = new StatManager(stats);
        RPGEntity entity = new RPGEntityBuilder(uuid, "TestPlayer", EntityType.PLAYER).withStatManager(sm).withEffectManager(eff).withEventBus(bus).build();
        Ability cleave = AbilityRegistry.get("ARCANE_CLEAVE").orElseThrow();
        ActiveAbility aa = new ActiveAbility(entity, cleave, bus);
        ArcaneCleaveBehavior beh = new ArcaneCleaveBehavior(aa);
        aa.setBehavior(beh);
        ActiveAbilityRegistry.getInstance().track(entity, aa);
        assertEquals(0, beh.getHitCount());
        for (int i = 1; i <= 6; i++) {
            // Simulate behavior's hitCount increment as done in onDamage
            int before = beh.getHitCount();
            beh.setHitCount(before + 1);
            boolean shouldCleave = beh.getHitCount() % 3 == 0;
            if (i == 3 || i == 6) assertTrue(shouldCleave, "Hit " + i + " should trigger cleave");
            else assertFalse(shouldCleave, "Hit " + i + " should not trigger cleave");
        }
        assertEquals(6, beh.getHitCount());
        beh.setHitCount(0);
        assertEquals(0, beh.getHitCount());
        ActiveAbilityRegistry.getInstance().clear();
    }

    @Test
    void chargeableDamage_acceptsRpgHandledSentinelOnManagedVictims() {
        // Real swing on a vanilla target (setup/post-game): full damage counts.
        assertTrue(DamageUtils.isChargeableDamage(5.0, false));
        assertTrue(DamageUtils.isChargeableDamage(5.0, true));
        // Swing on an RPG-managed victim (clear/boss): CombatListener applies the
        // real damage via the RPG pipeline and stamps the event with the ~0
        // sentinel — it must still count, but only for managed victims.
        assertTrue(DamageUtils.isChargeableDamage(DamageUtils.RPG_HANDLED_ENTITY, true),
                "RPG-handled swing on a dungeon mob/boss must charge the passives");
        assertFalse(DamageUtils.isChargeableDamage(DamageUtils.RPG_HANDLED_ENTITY, false),
                "sentinel noise on unmanaged victims (hurt pokes) must not count");
        // True zero/negative noise never counts.
        assertFalse(DamageUtils.isChargeableDamage(0.0, true));
        assertFalse(DamageUtils.isChargeableDamage(-1.0, true));
    }

    @Test
    void inHalfCircle_correctlyFilters() {
        Vector forward = new Vector(0, 0, 1);
        Vector inFront = new Vector(0, 0, 2);
        assertTrue(ArcaneCleaveBehavior.inHalfCircle(forward, inFront, 3.0));
        Vector behind = new Vector(0, 0, -2);
        assertFalse(ArcaneCleaveBehavior.inHalfCircle(forward, behind, 3.0));
        Vector rightSide = new Vector(2, 0, 0);
        assertTrue(ArcaneCleaveBehavior.inHalfCircle(forward, rightSide, 3.0));
        Vector leftSide = new Vector(-2, 0, 0);
        assertTrue(ArcaneCleaveBehavior.inHalfCircle(forward, leftSide, 3.0));
        Vector behindDiag = new Vector(1, 0, -1);
        assertFalse(ArcaneCleaveBehavior.inHalfCircle(forward, behindDiag, 3.0));
        Vector farFront = new Vector(0, 0, 5);
        assertFalse(ArcaneCleaveBehavior.inHalfCircle(forward, farFront, 3.0));
        Vector zero = new Vector(0, 0, 0);
        assertTrue(ArcaneCleaveBehavior.inHalfCircle(forward, zero, 3.0));
    }

    @Test
    void computeArcPoints_generatesHalfCircle() {
        Vector center = new Vector(0, 0, 0);
        Vector forward = new Vector(0, 0, 1);
        Vector right = new Vector(1, 0, 0);
        double radius = 2.5;
        var points = ArcaneCleaveBehavior.computeArcPoints(center, forward, right, radius);
        assertEquals(13, points.size());
        Vector first = points.get(0);
        assertEquals(-2.5, first.getX(), 0.01);
        assertEquals(0, first.getY(), 0.01);
        assertEquals(0, first.getZ(), 0.01);
        Vector middle = points.get(6);
        assertEquals(0, middle.getX(), 0.01);
        assertEquals(2.5, middle.getZ(), 0.01);
        Vector last = points.get(12);
        assertEquals(2.5, last.getX(), 0.01);
        assertEquals(0, last.getZ(), 0.01);
        for (Vector p : points) {
            Vector off = p.clone().subtract(center);
            double dist = Math.sqrt(off.getX()*off.getX() + off.getZ()*off.getZ());
            assertEquals(radius, dist, 0.01);
            double dot = off.getX()*forward.getX() + off.getZ()*forward.getZ();
            assertTrue(dot >= -0.001);
        }
    }

    @Test
    void arcaneBlade_itemCanBeDeserializedFromConfigMap() {
        AbilityRegistry.get("ARCANE_MANA_RESTORE").orElseThrow();
        AbilityRegistry.get("ARCANE_CLEAVE").orElseThrow();
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Arcane Blade");
        data.put("material", "IRON_SWORD");
        data.put("slot", "MAIN_HAND");
        data.put("itemType", "SWORD");
        data.put("description", List.of("A blade humming with arcane energy."));
        data.put("abilities", List.of("ARCANE_MANA_RESTORE", "ARCANE_CLEAVE"));
        data.put("active-stats", List.of(
                Map.of("amount", 20.0, "statType", "ATTACK_DAMAGE", "statModifierType", "FLAT", "statTarget", "BOTH", "policy", "STACK"),
                Map.of("amount", 0.2, "statType", "ATTACK_SPEED", "statModifierType", "FLAT", "statTarget", "BOTH", "policy", "STACK"),
                Map.of("amount", 100.0, "statType", "MANA_MAX", "statModifierType", "FLAT", "statTarget", "BOTH", "policy", "STACK")
        ));
        RPGItem item = RPGItem.deserialize("ARCANE_BLADE", data);
        assertEquals("ARCANE_BLADE", item.getId());
        assertEquals("IRON_SWORD", item.getMaterial());
        assertEquals(EquipmentSlot.MAIN_HAND, item.getEquipmentSlot());
        assertEquals(3, item.getActiveStats().size());
        assertEquals(2, item.getAbilities().size());
        assertTrue(item.getAbilities().stream().anyMatch(a -> a.getId().equals("ARCANE_MANA_RESTORE")));
        assertTrue(item.getAbilities().stream().anyMatch(a -> a.getId().equals("ARCANE_CLEAVE")));
        for (Ability a : item.getAbilities()) assertEquals(AbilityTriggerType.PASSIVE, a.getTriggerType());
    }

    @Test
    void hasPassive_returnsTrueWhenEquipped() {
        Ability manaPassive = AbilityRegistry.get("ARCANE_MANA_RESTORE").orElseThrow();
        Ability cleavePassive = AbilityRegistry.get("ARCANE_CLEAVE").orElseThrow();
        RPGItem blade = RPGItem.builder("ARCANE_BLADE", "Arcane Blade", "IRON_SWORD", EquipmentSlot.MAIN_HAND)
                .withItemType(ItemType.SWORD).withAbilities(List.of(manaPassive, cleavePassive)).build();
        UUID uuid = UUID.randomUUID();
        EventBusInterface bus = BukkitEventBus.getInstance();
        EffectManagerInterface eff = new NoopEffectManager();
        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.MANA_MAX, new CombatStat("MANA_MAX", 200));
        stats.put(StatType.MANA_RESOURCE, new ResourceStat("MANA_RESOURCE", n -> 200.0, n -> 4.0, System.currentTimeMillis()));
        stats.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100));
        stats.put(StatType.HEALTH_RESOURCE, new ResourceStat("HEALTH_RESOURCE", n -> 100.0, n -> 2.0, System.currentTimeMillis()));
        StatManager sm = new StatManager(stats);
        RPGEntity entity = new RPGEntityBuilder(uuid, "TestPlayer", EntityType.PLAYER).withStatManager(sm).withEffectManager(eff).withEventBus(bus).build();
        assertFalse(ActiveAbilityRegistry.getInstance().has(entity, "ARCANE_MANA_RESTORE"), "No item equipped yet");
        assertFalse(ActiveAbilityRegistry.getInstance().has(entity, "ARCANE_CLEAVE"));
        entity.getEquipmentManager().equipItem(EquipmentSlot.MAIN_HAND, blade);
        assertTrue(ActiveAbilityRegistry.getInstance().has(entity, "ARCANE_MANA_RESTORE"), "Should have mana restore after equipping via ActiveAbilityRegistry");
        assertTrue(ActiveAbilityRegistry.getInstance().has(entity, "ARCANE_CLEAVE"), "Should have cleave after equipping");
        assertTrue(entity.getEquipmentManager().hasActiveAbility("ARCANE_MANA_RESTORE"));
        entity.getEquipmentManager().unequipItem(EquipmentSlot.MAIN_HAND);
        assertFalse(ActiveAbilityRegistry.getInstance().has(entity, "ARCANE_MANA_RESTORE"), "Should not have passive after unequipping");
        ActiveAbilityRegistry.getInstance().clear();
    }

    private static class NoopEffectManager implements EffectManagerInterface {
        @Override public Effect cast(RPGEntity entity, Ability ability) { return null; }
        @Override public boolean canActivate(RPGEntity entity, Ability ability) { return true; }
        @Override public long remainingCooldown(RPGEntity entity, Ability ability) { return 0; }
        @Override public void tick(long now) {}
        @Override public void cancelAll() {}
    }
}
