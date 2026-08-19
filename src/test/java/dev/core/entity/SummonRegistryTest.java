package dev.core.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SummonRegistryTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SUMMON_ONE = UUID.randomUUID();
    private static final UUID SUMMON_TWO = UUID.randomUUID();

    @AfterEach
    void clearRegistry() {
        SummonRegistry.getInstance().clearAll();
    }

    @Test
    void registerOwnsAndUnregisters() {
        SummonRegistry registry = SummonRegistry.getInstance();
        registry.register(OWNER, SUMMON_ONE);

        assertTrue(registry.isSummon(SUMMON_ONE));
        assertEquals(OWNER, registry.getOwner(SUMMON_ONE));
        assertEquals(List.of(SUMMON_ONE), registry.getSummons(OWNER));

        registry.unregister(SUMMON_ONE);
        assertFalse(registry.isSummon(SUMMON_ONE));
        assertNull(registry.getOwner(SUMMON_ONE));
    }

    @Test
    void allSummonIdsSnapshotsEveryOwner() {
        SummonRegistry registry = SummonRegistry.getInstance();
        registry.register(OWNER, SUMMON_ONE);
        registry.register(UUID.randomUUID(), SUMMON_TWO);

        assertEquals(2, registry.allSummonIds().size());
        assertTrue(registry.allSummonIds().contains(SUMMON_ONE));
        assertTrue(registry.allSummonIds().contains(SUMMON_TWO));
    }

    @Test
    void clearAllRemovesEverySummon() {
        SummonRegistry registry = SummonRegistry.getInstance();
        registry.register(OWNER, SUMMON_ONE);
        registry.register(UUID.randomUUID(), SUMMON_TWO);

        registry.clearAll();

        assertTrue(registry.allSummonIds().isEmpty());
        assertFalse(registry.isSummon(SUMMON_ONE));
    }

    @Test
    void getSummonsPreservesSpawnOrderOldestFirst() {
        SummonRegistry registry = SummonRegistry.getInstance();
        UUID summonThree = UUID.randomUUID();
        registry.register(OWNER, SUMMON_ONE);
        registry.register(OWNER, SUMMON_TWO);
        registry.register(OWNER, summonThree);

        assertEquals(List.of(SUMMON_ONE, SUMMON_TWO, summonThree), registry.getSummons(OWNER));

        registry.unregister(SUMMON_TWO);
        assertEquals(List.of(SUMMON_ONE, summonThree), registry.getSummons(OWNER));
    }
}