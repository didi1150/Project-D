package dev.core.entity.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.core.ability.EffectManagerInterface;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.BossEncounterEndedEvent;
import dev.core.game.TaskScheduler;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.loader.StatLoader;
import dev.core.status.StatusEffectManagerInterface;

class RPGBossEntityEncounterTest {

    @Test
    void endEncounterPublishesOnlyOnce() {
        EventBusInterface eventBus = mock(EventBusInterface.class);
        RPGBossEntity boss = new RPGBossEntity(UUID.randomUUID(), "Test Boss",
                mock(EffectManagerInterface.class), eventBus, mock(TaskScheduler.class),
                mock(StatusEffectManagerInterface.class));

        boss.endEncounter(BossEncounterEndedEvent.Outcome.WIPE);
        boss.endEncounter(BossEncounterEndedEvent.Outcome.VICTORY);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(1)).sendEvent(captor.capture());
        assertTrue(captor.getValue() instanceof BossEncounterEndedEvent);
    }

    @Test
    void eventCarriesTheSelectedOutcome() {
        EventBusInterface eventBus = mock(EventBusInterface.class);
        RPGBossEntity boss = new RPGBossEntity(UUID.randomUUID(), "Test Boss",
                mock(EffectManagerInterface.class), eventBus, mock(TaskScheduler.class),
                mock(StatusEffectManagerInterface.class));

        boss.endEncounter(BossEncounterEndedEvent.Outcome.VICTORY);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).sendEvent(captor.capture());
        BossEncounterEndedEvent event = (BossEncounterEndedEvent) captor.getValue();
        assertTrue(event.getOutcome() == BossEncounterEndedEvent.Outcome.VICTORY);
        assertTrue(event.getTarget() == boss);
    }

    @Test
    void configureCopiesDefinitionStatsPerEncounter() {
        EventBusInterface eventBus = mock(EventBusInterface.class);
        RPGBossEntity boss = new RPGBossEntity(UUID.randomUUID(), "Test Boss",
                mock(EffectManagerInterface.class), eventBus, mock(TaskScheduler.class),
                mock(StatusEffectManagerInterface.class));

        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 15000));
        StatManager definitionStats = new StatManager(StatLoader.mergeStats(stats, Map.of()));
        BossDefinition definition = new BossDefinition("test", "Test Boss", "WITHER", 1, null,
                definitionStats, List.of());

        boss.configure(definition, mock(BossEntityContext.class), null);

        assertEquals(15000, boss.getMaxHealth(), 0.001);
        assertNotSame(definitionStats.getStats().get(StatType.HEALTH_MAX),
                boss.getStatManager().getStats().get(StatType.HEALTH_MAX));

        boss.getStatManager().setCurrentValue(StatType.HEALTH_RESOURCE, 1);
        assertEquals(15000,
                definitionStats.getCurrentValue(StatType.HEALTH_RESOURCE, System.currentTimeMillis()),
                0.001);
    }

    @Test
    void definitionWithoutStatManagerGetsAnEmptyOne() {
        BossDefinition definition = new BossDefinition("test", "Test Boss", "WITHER", 1, null, null, null);

        assertNotNull(definition.getBaseStatManager());
        assertTrue(definition.getStages().isEmpty());
    }
}
