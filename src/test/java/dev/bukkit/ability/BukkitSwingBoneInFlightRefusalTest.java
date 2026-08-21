package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.RPGEntity;

/**
 * Regression test for the bonemerang's in-flight cast refusal. Every cast
 * builds a FRESH {@link Effect} instance (the manager calls
 * {@link AbilityRegistry#createEffect} per cast), so instance state can never
 * block a second throw on its own. What refuses the re-cast is the manager's
 * per-EFFECT-KEY single-instance slot: for ITEM-scoped abilities the key is
 * the held item's UUID, so while the bone of a given bonemerang is in the air
 * that same item cannot start a second flight, while DIFFERENT instances
 * (different keys) are unaffected.
 *
 * These tests drive the REAL {@link BukkitEffectManager} headlessly: the
 * caster is a {@link BukkitPlayerEntity} backed by a mocked (non-server)
 * {@link Player}, and the registered BONE_SWING factory returns a no-op
 * effect subclass so the Bukkit-only machinery (armor stand spawn, sounds,
 * item morph) is never touched. A counter records how many times the effect's
 * cast() actually ran, i.e. how many flights were actually started.
 */
class BukkitSwingBoneInFlightRefusalTest {

    private static int castAttempts;

    @BeforeEach
    void setUp() {
        castAttempts = 0;
        AbilityRegistry.clear();
        AbilityRegistry.register(configured("BONE_SWING", AbilityAction.RIGHT_CLICK, CooldownScope.ITEM, 3000),
                NoopSwingBoneEffect::new);
    }

    @Test
    void secondCastOfTheSameBonemerangIsRefusedWhileTheBoneIsInFlight() {
        EffectManagerInterface manager = BukkitEffectManager.getInstance();
        RPGEntity entity = new TestPlayerEntity(player());
        Ability ability = AbilityRegistry.get("BONE_SWING").orElseThrow();

        Effect first = manager.cast(entity, ability);
        assertNotNull(first);
        assertEquals(1, castAttempts, "first cast must start the flight");

        // A fresh instance is handed back, but its cast() never runs: the
        // manager's already-active check refuses it.
        Effect second = manager.cast(entity, ability);
        assertNotNull(second);
        assertNotSame(first, second, "blocked casts return a fresh instance, not the active one");
        assertEquals(1, castAttempts, "a second, concurrent flight must not start");
    }

    @Test
    void twoDifferentBonemerangInstancesAreNotBlockedByEachOther() {
        EffectManagerInterface manager = BukkitEffectManager.getInstance();
        Ability ability = AbilityRegistry.get("BONE_SWING").orElseThrow();

        // Two different casters, each with their own item instance key: both
        // flights are allowed at the same time.
        Effect first = manager.cast(new TestPlayerEntity(player()), ability);
        Effect second = manager.cast(new TestPlayerEntity(player()), ability);
        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
        assertEquals(2, castAttempts, "different bonemerang instances must be able to fly at once");
    }

    private static Player player() {
        Player player = Mockito.mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Test");
        return player;
    }

    /**
     * BukkitPlayerEntity.getPlayer() resolves through the server registry,
     * which does not exist headless; hand back the mock player instead.
     */
    private static final class TestPlayerEntity extends BukkitPlayerEntity {

        private final Player player;

        TestPlayerEntity(Player player) {
            super(player);
            this.player = player;
        }

        @Override
        public Optional<Player> getPlayer() {
            return Optional.of(player);
        }
    }

    /**
     * A count-only stand-in for the real swing-bone effect, so the test never
     * touches a spawned armor stand, sounds, or the item morph.
     */
    private static final class NoopSwingBoneEffect extends BukkitSwingBoneEffect {

        NoopSwingBoneEffect(String cooldownKey) {
            super(cooldownKey);
        }

        @Override
        public void cast(RPGEntity caster, CooldownSink cooldownSink) {
            castAttempts++;
        }

        @Override
        public void cancel() {
        }
    }

    private static Ability configured(String id, AbilityAction action, CooldownScope scope, long cooldown) {
        Ability ability = new Ability(id) {
        };
        ability.setTriggerType(AbilityTriggerType.MANUAL);
        ability.setAction(action);
        ability.setScope(scope);
        ability.setCooldown(cooldown);
        return ability;
    }
}