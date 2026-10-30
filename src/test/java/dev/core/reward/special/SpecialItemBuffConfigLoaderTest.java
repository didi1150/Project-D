package dev.core.reward.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.core.stat.StatTarget;
import dev.core.stat.StatType;
import dev.core.stat.modifier.ModifierStackPolicy;
import dev.core.stat.modifier.StatModifierType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class SpecialItemBuffConfigLoaderTest {

    @Test
    void loadsConfiguredBuffStats() {
        ConfigProvider provider = mock(ConfigProvider.class);
        ConfigSection root = mock(ConfigSection.class);
        ConfigSection buffs = mock(ConfigSection.class);
        ConfigSection buff = mock(ConfigSection.class);
        ConfigSection stat = mock(ConfigSection.class);

        when(provider.getRoot()).thenReturn(root);
        when(root.getSection("buffs")).thenReturn(buffs);
        when(buffs.getKeys()).thenReturn(Set.of("SPEEDY"));
        when(buffs.getSection("SPEEDY")).thenReturn(buff);
        when(buff.getString("display-name", "SPEEDY")).thenReturn("Speedy");
        when(buff.getDouble("duration-seconds", 10.0)).thenReturn(12.0);
        when(buff.getSectionList("stat-modifiers")).thenReturn(List.of(stat));
        when(stat.getString("statType", StatType.MOVE_SPEED.name())).thenReturn("MOVE_SPEED");
        when(stat.getDouble("amount", 0)).thenReturn(30.0);
        when(stat.getString("modifierType", StatModifierType.FLAT.name())).thenReturn("FLAT");
        when(stat.getString("policy", ModifierStackPolicy.STACK.name())).thenReturn("STACK");
        when(stat.getString("statTarget", StatTarget.BOTH.name())).thenReturn("BOTH");

        SpecialItemBuffConfig config = SpecialItemBuffConfigLoader.load(provider);

        assertFalse(config.isEmpty());
        assertEquals(1, config.getBuffs().size());
        SpecialItemBuff loaded = config.getBuffs().get(0);
        assertEquals("SPEEDY", loaded.id());
        assertEquals("Speedy", loaded.displayName());
        assertEquals(12.0, loaded.durationSeconds(), 0.0001);
        assertEquals(1, loaded.stats().size());
        assertEquals(StatType.MOVE_SPEED, loaded.stats().get(0).statType());
        assertEquals(30.0, loaded.stats().get(0).amount(), 0.0001);
    }

    @Test
    void fallsBackToDefaultsWhenNoBuffsAreConfigured() {
        ConfigProvider provider = mock(ConfigProvider.class);
        ConfigSection root = mock(ConfigSection.class);

        when(provider.getRoot()).thenReturn(root);
        when(root.getSection("buffs")).thenReturn(null);

        SpecialItemBuffConfig config = SpecialItemBuffConfigLoader.load(provider);

        assertFalse(config.isEmpty());
        assertTrue(config.getBuffs().stream().allMatch(SpecialItemBuff::isUsable));
    }
}
