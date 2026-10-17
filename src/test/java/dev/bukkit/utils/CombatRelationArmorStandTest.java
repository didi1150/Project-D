package dev.bukkit.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.ArmorStand;
import org.junit.jupiter.api.Test;

import dev.core.entity.RPGEntity;

class CombatRelationArmorStandTest {

    @Test
    void armorStandsAreNeverEnemies() {
        RPGEntity attacker = mock(RPGEntity.class);
        when(attacker.getUuid()).thenReturn(UUID.randomUUID());

        ArmorStand stand = mock(ArmorStand.class);
        when(stand.getUniqueId()).thenReturn(UUID.randomUUID());

        assertFalse(CombatRelation.isEnemy(attacker, stand));
    }
}
