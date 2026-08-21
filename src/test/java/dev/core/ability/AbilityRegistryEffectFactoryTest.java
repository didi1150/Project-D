package dev.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.entity.RPGEntity;
import dev.core.event.Event;

/**
 * Covers the single registration point of Feature 4: one
 * {@code AbilityRegistry.register(ability, effectFactory)} call wires both the
 * ability metadata and the backing effect, and {@code createEffect} is the
 * only lookup the effect manager needs.
 */
class AbilityRegistryEffectFactoryTest {

    /** Minimal effect that records its cooldown key. */
    private static final class TestEffect extends Effect {
        private final String key;

        private TestEffect(String cooldownKey) {
            super(null, 1000L, true, cooldownKey);
            this.key = cooldownKey;
        }

        @Override
        public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        }

        @Override
        public void cancel() {
        }

        String getKey() {
            return key;
        }
    }

    private static final class TestAbility extends Ability {
        private TestAbility(String id) {
            super(id);
        }
    }

    @BeforeEach
    void setUp() {
        AbilityRegistry.clear();
    }

    @Test
    void registerWithFactoryWiresBothHalves() {
        Ability ability = new TestAbility("TEST");
        AbilityRegistry.register(ability, TestEffect::new);

        assertEquals(ability, AbilityRegistry.get("TEST").orElse(null));
        Effect effect = AbilityRegistry.createEffect("TEST", "cooldown-key-1");
        assertNotNull(effect, "registered effect factory must create effects");
        assertEquals("cooldown-key-1", ((TestEffect) effect).getKey());
    }

    @Test
    void createEffectWithoutFactoryReturnsNull() {
        AbilityRegistry.register(new TestAbility("NO_EFFECT"));

        assertNull(AbilityRegistry.createEffect("NO_EFFECT", "key"));
    }

    @Test
    void createEffectForUnknownAbilityReturnsNull() {
        assertNull(AbilityRegistry.createEffect("UNKNOWN", "key"));
    }

    @Test
    void registerWithoutFactoryKeepsExistingEffectBinding() {
        AbilityRegistry.register(new TestAbility("TEST"), TestEffect::new);
        AbilityRegistry.register(new TestAbility("TEST"));

        assertNotNull(AbilityRegistry.createEffect("TEST", "key"),
                "a metadata-only re-register must not unbind the effect factory");
    }

    @Test
    void clearResetsBothStores() {
        AbilityRegistry.register(new TestAbility("TEST"), TestEffect::new);
        AbilityRegistry.clear();

        assertNull(AbilityRegistry.get("TEST").orElse(null));
        assertNull(AbilityRegistry.createEffect("TEST", "key"));
    }
}