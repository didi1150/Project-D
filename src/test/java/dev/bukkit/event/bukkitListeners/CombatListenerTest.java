package dev.bukkit.event.bukkitListeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import dev.bukkit.item.BowArrowManager;

/**
 * Projectile damage scaling at hit time: a projectile stamped by
 * {@link BowArrowManager} at release keeps its RPG-scaled damage instead of
 * the vanilla bow damage.
 */
class CombatListenerTest {

    @Test
    void storedRpgDamageOverridesVanillaProjectileDamage() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        Projectile projectile = mock(Projectile.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE)).thenReturn(165.0);
        LivingEntity shooter = mock(LivingEntity.class);
        when(shooter.getUniqueId()).thenReturn(UUID.randomUUID());

        CombatListener.applyProjectileDamageScaling(event, projectile, shooter);

        verify(event).setDamage(165.0);
    }

    @Test
    void unstampedProjectileKeepsVanillaDamage() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        Projectile projectile = mock(Projectile.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE)).thenReturn(null);
        LivingEntity shooter = mock(LivingEntity.class);
        when(shooter.getUniqueId()).thenReturn(UUID.randomUUID());

        CombatListener.applyProjectileDamageScaling(event, projectile, shooter);

        // shooter is not a registered RPG entity: no scaling is applied
        verify(event, never()).setDamage(anyDouble());
    }

    @Test
    void stampedKeyIsTheBowArrowDamageKey() {
        assertEquals("arrow_damage", BowArrowManager.ARROW_DAMAGE_KEY.getKey());
        assertEquals(new NamespacedKey("project_d", "arrow_damage"), BowArrowManager.ARROW_DAMAGE_KEY);
    }
}