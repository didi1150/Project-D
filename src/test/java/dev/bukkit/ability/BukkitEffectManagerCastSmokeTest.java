package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;

/**
 * Smoke test for the effect dispatch registry. Replicates the sequence
 * DMain.onEnable performs (register abilities + wire effect factories), then
 * drives the REAL {@link BukkitEffectManager} through {@code cast()} with a
 * fake (non-Bukkit) entity.
 *
 * The fake entity is intentionally NOT a BukkitPlayerEntity, so the swing-bone
 * single-instance branch is skipped; what this test verifies is the wiring: a
 * registered ability id resolves to a non-null Effect (never a silent null),
 * and an unregistered id returns null with the warning path intact.
 */
class BukkitEffectManagerCastSmokeTest {

    @BeforeEach
    void setUp() {
        AbilityRegistry.clear();
        AbilityRegistry.register(configured("BONE_SWING", AbilityAction.RIGHT_CLICK, CooldownScope.ITEM, 3000));
        AbilityRegistry
                .register(configured("PARTICLE_TEST_ABILITY", AbilityAction.RIGHT_CLICK, CooldownScope.PLAYER, 0));
        AbilityRegistry.register(configured("SMASH", AbilityAction.RIGHT_CLICK, CooldownScope.ITEM, 5000));
        BukkitEffectRegistry.register("BONE_SWING", BukkitSwingBoneEffect::new);
        BukkitEffectRegistry.register("PARTICLE_TEST_ABILITY", BukkitParticleTestEffect::new);
        BukkitEffectRegistry.register("SMASH", BukkitSmashEffect::new);
    }

    @Test
    void castReturnsEffectForEveryRegisteredAbility() {
        EffectManagerInterface manager = BukkitEffectManager.getInstance();
        RPGEntity entity = new TestRPGEntity();

        for (String id : new String[] { "BONE_SWING", "PARTICLE_TEST_ABILITY", "SMASH" }) {
            Ability ability = AbilityRegistry.get(id).orElseThrow();
            Effect effect = manager.cast(entity, ability);
            assertNotNull(effect, "cast() silently returned null for registered ability " + id);
        }
    }

    @Test
    void castSwingBoneDispatchProducesSwingBoneEffect() {
        EffectManagerInterface manager = BukkitEffectManager.getInstance();
        RPGEntity entity = new TestRPGEntity();
        Ability ability = AbilityRegistry.get("BONE_SWING").orElseThrow();

        assertTrue(manager.cast(entity, ability) instanceof BukkitSwingBoneEffect);
    }

    @Test
    void castSmashDispatchProducesSmashEffect() {
        EffectManagerInterface manager = BukkitEffectManager.getInstance();
        RPGEntity entity = new TestRPGEntity();
        Ability ability = AbilityRegistry.get("SMASH").orElseThrow();

        assertTrue(manager.cast(entity, ability) instanceof BukkitSmashEffect);
    }

    @Test
    void castUnknownAbilityReturnsNull() {
        EffectManagerInterface manager = BukkitEffectManager.getInstance();
        RPGEntity entity = new TestRPGEntity();

        Ability unknown = configured("NO_SUCH_ABILITY", AbilityAction.RIGHT_CLICK, CooldownScope.PLAYER, 0);
        assertNull(manager.cast(entity, unknown));
    }

    private static Ability configured(String id, AbilityAction action, CooldownScope scope, long cooldown) {
        Ability ability = new StubAbility(id);
        ability.setTriggerType(AbilityTriggerType.MANUAL);
        ability.setAction(action);
        ability.setScope(scope);
        ability.setCooldown(cooldown);
        return ability;
    }

    private static final class StubAbility extends Ability {
        StubAbility(String id) {
            super(id);
        }
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity() {
            super(new StatManager(DefaultStats.getDefaultStats()), UUID.randomUUID(), "test", EntityType.MOB,
                    BukkitEffectManager.getInstance(), null, RPGClassType.NONE);
        }
    }
}