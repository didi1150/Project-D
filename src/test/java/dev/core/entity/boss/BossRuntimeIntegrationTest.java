package dev.core.entity.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.bukkit.event.BukkitEventBus;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;

class BossRuntimeIntegrationTest {

    @Test
    void bossDefinitionAndInstanceShouldIntegrateWithCoreEntityBossPackage() {
        BossStage firstStage = new BossStage() {
            @Override
            public String getId() {
                return "first";
            }
        };

        BossStage secondStage = new BossStage() {
            @Override
            public String getId() {
                return "second";
            }
        };

        BossDefinition definition = new BossDefinition("boss-1", "Test Boss", "ZOMBIE", 200L, List.of(firstStage, secondStage));
        BossEntityContext context = new BossEntityContext() {
            @Override
            public void broadcast(String message) {
            }

            @Override
            public void spawnAdd(String entityType, int count) {
            }
        };

        BossInstance instance = new BossInstance(definition, context);

        assertNotNull(instance);
        assertEquals("first", instance.getCurrentStageId());
        assertEquals(0, instance.getStageIndex());
        assertFalse(instance.isImmune());

        instance.stateSet("hits", 3);
        assertEquals(3, instance.getState("hits"));

        instance.handleEvent(new GameEvent("advance", Map.of()));
        assertEquals("second", instance.getCurrentStageId());
        assertEquals(1, instance.getStageIndex());
        assertTrue(instance.getDefinition().getStages().size() >= 2);

        instance.jumpToStage("first");
        assertEquals("first", instance.getCurrentStageId());
    }

    @Test
    void bossRuntimeShouldRegisterOnlyRelevantTriggers() {
        EventBusInterface eventBus = BukkitEventBus.getInstance();
        eventBus.getSubscribed().clear();

        BossRuntime runtime = new BossRuntime(eventBus) {
            @Override
            protected void registerBossTriggers() {
                registerTrigger(event -> {
                }, String.class);
                registerTrigger(event -> {
                }, Integer.class);
            }
        };

        runtime.register();
        assertEquals(2, eventBus.getSubscribed().size());

        runtime.unregister();
        assertEquals(0, eventBus.getSubscribed().size());

        eventBus.getSubscribed().clear();
    }
}
