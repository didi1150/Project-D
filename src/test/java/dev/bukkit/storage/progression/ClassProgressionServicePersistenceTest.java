package dev.bukkit.storage.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.core.entity.EntityManager;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.PlayerClassXpChangedEvent;
import dev.core.progression.PlayerClassProgression;
import dev.core.progression.PlayerProgression;
import dev.core.stat.StatManager;
import dev.core.storage.database.ProgressionCacheStrategy;
import dev.core.storage.database.ProgressionDatabaseStrategy;

class ClassProgressionServicePersistenceTest {

    private ProgressionCacheStrategy cache;
    private ProgressionDatabaseStrategy database;
    private EventBusInterface eventBus;
    private ClassProgressionService service;

    @BeforeEach
    void setUp() {
        EntityManager.getInstance().clear();
        cache = mock(ProgressionCacheStrategy.class);
        database = mock(ProgressionDatabaseStrategy.class);
        eventBus = mock(EventBusInterface.class);
        service = new ClassProgressionService(cache, database, eventBus);
    }

    @Test
    void saveAllUsesLiveLocalProgressionsAndUnlocks() {
        UUID playerId = UUID.randomUUID();
        PlayerProgression local = new PlayerProgression(playerId);
        PlayerClassProgression mage = local.getProgression(RPGClassType.MAGE);
        mage.addXp(350);
        local.setActiveClass(RPGClassType.MAGE, new StatManager(new HashMap<>()));
        local.unlockItem("SEALBREAKER");

        service.saveAll(local);

        verify(database).saveAll(eq(playerId), argThat(map -> {
            PlayerClassProgression saved = map.get(RPGClassType.MAGE);
            return saved != null && saved.getXp() == mage.getXp() && saved.getLevel() == mage.getLevel();
        }));
        verify(database).setActiveClass(playerId, RPGClassType.MAGE);
        verify(database).saveUnlockedItems(eq(playerId),
                argThat(set -> set.contains("SEALBREAKER")));
        verify(cache, atLeastOnce()).put(eq(playerId), any());
    }

    @Test
    void unlockItemPersistsWhenNoLiveEntityExists() {
        UUID playerId = UUID.randomUUID();
        Set<String> stored = new HashSet<>(Set.of("EXISTING"));
        when(database.loadUnlockedItems(playerId)).thenAnswer(invocation -> Set.copyOf(stored));
        doAnswer(invocation -> {
            stored.clear();
            stored.addAll(invocation.getArgument(1));
            return null;
        }).when(database).saveUnlockedItems(eq(playerId), any());

        assertTrue(service.unlockItem(playerId, "NEW_ITEM"));

        verify(database).saveUnlockedItems(eq(playerId),
                argThat(set -> set.contains("EXISTING") && set.contains("NEW_ITEM")));
        assertEquals(Set.of("EXISTING", "NEW_ITEM"), service.getUnlockedItems(playerId));
    }

    @Test
    void addExperienceFiresXpChangedEvent() {
        UUID playerId = UUID.randomUUID();
        PlayerClassProgression mage = new PlayerClassProgression(RPGClassType.MAGE);
        when(cache.get(playerId, RPGClassType.MAGE)).thenReturn(Optional.of(mage));

        service.addExperience(playerId, RPGClassType.MAGE, 250);

        ArgumentCaptor<PlayerClassXpChangedEvent> captor = ArgumentCaptor
                .forClass(PlayerClassXpChangedEvent.class);
        verify(eventBus).sendEvent(captor.capture());
        PlayerClassXpChangedEvent event = captor.getValue();
        assertEquals(playerId, event.getPlayerId());
        assertEquals(RPGClassType.MAGE, event.getClassType());
        assertEquals(0, event.getOldXp());
        assertEquals(250, event.getNewXp());
        assertEquals(mage.getLevel(), event.getNewLevel());
    }

    @Test
    void setExperienceFiresXpChangedEvent() {
        UUID playerId = UUID.randomUUID();
        PlayerClassProgression mage = new PlayerClassProgression(RPGClassType.MAGE);
        mage.addXp(350);
        when(cache.get(playerId, RPGClassType.MAGE)).thenReturn(Optional.of(mage));

        service.setExperience(playerId, RPGClassType.MAGE, 10);

        ArgumentCaptor<PlayerClassXpChangedEvent> captor = ArgumentCaptor
                .forClass(PlayerClassXpChangedEvent.class);
        verify(eventBus).sendEvent(captor.capture());
        PlayerClassXpChangedEvent event = captor.getValue();
        assertEquals(350, event.getOldXp());
        assertEquals(10, event.getNewXp());
        assertEquals(10, mage.getXp());
    }

    @Test
    void localProgressionStartsWithNoUnlockedItems() {
        PlayerProgression local = new PlayerProgression(UUID.randomUUID());
        assertTrue(local.getUnlockedItems().isEmpty());
        assertTrue(local.unlockItem("A"));
        assertTrue(!local.unlockItem("A"));
        assertEquals(Set.of("A"), local.getUnlockedItems());
    }

}
