package dev.core.stat.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.RPGItemSet;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.engine.StatEngine;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;
import dev.core.stat.StatType;
import dev.core.stat.StatManager;
import dev.core.event.EventBusInterface;
import dev.core.ability.EffectManagerInterface;
import dev.core.event.EventAction;
import dev.core.event.Event;

/**
 * Simple manual test runner for StatEngine behavior.
 */
public class StatEngineManualTest {

    public static void main(String[] args) {
        // Initialize descriptors for existing StatType enum
        StatTypeAdapter.initializeStatTypes();

        // Create dummy managers
        EffectManagerInterface dummyEffect = new DummyEffectManager();
        EventBusInterface dummyBus = new DummyEventBus();

        // Create an entity
        RPGEntity entity = new RPGEntity(UUID.randomUUID(), "tester", EntityType.PLAYER, dummyEffect, dummyBus) {
        };

        // Create a simple item that grants +10 attack damage when active
        StatModifier flat10 = StatModifier.builder(10.0, StatModifierType.FLAT, StatType.ATTACK_DAMAGE, "test").build();
        List<StatModifier> active = new ArrayList<>(); 
        active.add(flat10);
        RPGItem item = new RPGItem("test:sword", "Test Sword", EquipmentSlot.MAIN_HAND, null, new ArrayList<>(), active, new ArrayList<>());

        // Equip the item
        entity.getEquipmentManager().equipItem(EquipmentSlot.MAIN_HAND, item);

        // Ask the StatEngine for computed value for attack damage with base 0
        String statId = StatTypeAdapter.toId(StatType.ATTACK_DAMAGE);
        StatEngine engine = entity.getStatEngine();
        double computed = engine.computeValue(statId, 0.0);

        System.out.println("Computed attack damage (expected 10.0): " + computed);
    }

    // Simple dummy implementations for test harness
    private static class DummyEventBus implements EventBusInterface {
        @Override public java.util.List<EventAction<?>> getSubscribed() { return java.util.List.of(); }
        @Override public <E> java.util.List<EventAction<E>> getSubscribedOfType(Class<E> type) { return java.util.List.of(); }
        @Override public EventAction<?> getSubscribedWithId(String id) { return null; }
        @Override public void subscribe(EventAction<?> eventAction) {}
        @Override public <E> void subscribeOnce(EventAction<E> eventAction) {}
        @Override public <E> void subscribeOnCondition(EventAction<E> eventAction, java.util.function.Predicate<E> predicate) {}
        @Override public void unsubscribe(String id) {}
        @Override public void unsubscribe(EventAction<?> eventAction) {}
        @Override public <E> void sendEvent(E event) {}
        @Override public <E> void sendEvent(E event, java.util.function.Function<E, Boolean> condition) {}
    }

    private static class DummyEffectManager implements EffectManagerInterface {
        @Override public dev.core.ability.Effect cast(RPGEntity entity, dev.core.ability.Ability ability) { return null; }
        @Override public boolean canActivate(RPGEntity entity, dev.core.ability.Ability ability) { return false; }
        @Override public long remainingCooldown(RPGEntity entity, dev.core.ability.Ability ability) { return 0; }
        @Override public void tick(long now) {}
        @Override public void cancelAll() {}
    }
}
