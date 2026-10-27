package dev.core.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

class RewardConfigLoaderTest {

    @Test
    void loadReadsSettingsAndBothRewardTypes() {
        ConfigProvider provider = mock(ConfigProvider.class);
        ConfigSection root = mock(ConfigSection.class);
        ConfigSection settings = mock(ConfigSection.class);
        ConfigSection rewards = mock(ConfigSection.class);
        ConfigSection dye = mock(ConfigSection.class);
        ConfigSection item = mock(ConfigSection.class);

        when(provider.getRoot()).thenReturn(root);
        when(root.getSection("settings")).thenReturn(settings);
        when(root.getSection("rewards")).thenReturn(rewards);
        when(settings.getDouble("roll-min-seconds", RewardConfigLoader.DEFAULT_ROLL_MIN_SECONDS))
                .thenReturn(1.25);
        when(settings.getDouble("roll-max-seconds", RewardConfigLoader.DEFAULT_ROLL_MAX_SECONDS))
                .thenReturn(2.75);
        when(settings.getDouble("roll-top-speed", RewardConfigLoader.DEFAULT_ROLL_TOP_SPEED))
                .thenReturn(9.5);

        when(rewards.getKeys()).thenReturn(Set.of("DYE", "ITEM"));
        when(rewards.getSection("DYE")).thenReturn(dye);
        when(rewards.getSection("ITEM")).thenReturn(item);

        when(dye.getString("type", "CLASS_XP_DYE")).thenReturn("CLASS_XP_DYE");
        when(dye.getInt("weight", 1)).thenReturn(50);
        when(dye.getString("display-name", "DYE")).thenReturn("Blue Dye");
        when(dye.getStringList("description")).thenReturn(List.of("line"));
        when(dye.getInt("xp-min", 50)).thenReturn(40);
        when(dye.getInt("xp-max", 40)).thenReturn(80);

        when(item.getString("type", "CLASS_XP_DYE")).thenReturn("ITEM_UNLOCK");
        when(item.getInt("weight", 1)).thenReturn(10);
        when(item.getString("display-name", "ITEM")).thenReturn("Unlock");
        when(item.getStringList("description")).thenReturn(List.of());
        when(item.getString("item-id", null)).thenReturn("SEALBREAKER");
        when(item.getBoolean("give-item", true)).thenReturn(false);

        RewardConfig config = RewardConfigLoader.load(provider);

        assertEquals(1.25, config.getRollMinSeconds(), 0.0001);
        assertEquals(2.75, config.getRollMaxSeconds(), 0.0001);
        assertEquals(9.5, config.getRollTopSpeed(), 0.0001);
        assertEquals(2, config.getRewards().size());

        RewardDefinition dyeReward = config.getRewards().stream().filter(r -> r.id().equals("DYE")).findFirst()
                .orElseThrow();
        assertEquals(RewardType.CLASS_XP_DYE, dyeReward.type());
        assertEquals(40, dyeReward.xpMin());
        assertEquals(80, dyeReward.xpMax());

        RewardDefinition itemReward = config.getRewards().stream().filter(r -> r.id().equals("ITEM")).findFirst()
                .orElseThrow();
        assertEquals(RewardType.ITEM_UNLOCK, itemReward.type());
        assertEquals("SEALBREAKER", itemReward.itemId());
        assertTrue(!itemReward.giveItem());
    }

    @Test
    void randomRollTicksStayInsideConfiguredInterval() {
        RewardConfig config = new RewardConfig(RewardConfigLoader.DEFAULT_ROLL_MIN_SECONDS,
                RewardConfigLoader.DEFAULT_ROLL_MAX_SECONDS, RewardConfigLoader.DEFAULT_ROLL_TOP_SPEED, List.of());
        int minTicks = (int) Math.round(RewardConfigLoader.DEFAULT_ROLL_MIN_SECONDS * 20.0);
        int maxTicks = (int) Math.round(RewardConfigLoader.DEFAULT_ROLL_MAX_SECONDS * 20.0);
        for (int i = 0; i < 500; i++) {
            int ticks = config.randomRollTicks(new java.util.Random(i));
            assertTrue(ticks >= minTicks && ticks <= maxTicks, "ticks out of range: " + ticks);
        }
    }

    @Test
    void loadUsesLongDefaultsWhenSettingsAreMissing() {
        ConfigProvider provider = mock(ConfigProvider.class);
        ConfigSection root = mock(ConfigSection.class);

        when(provider.getRoot()).thenReturn(root);
        when(root.getSection("settings")).thenReturn(null);
        when(root.getSection("rewards")).thenReturn(null);

        RewardConfig config = RewardConfigLoader.load(provider);

        assertEquals(RewardConfigLoader.DEFAULT_ROLL_MIN_SECONDS, config.getRollMinSeconds(), 0.0001);
        assertEquals(RewardConfigLoader.DEFAULT_ROLL_MAX_SECONDS, config.getRollMaxSeconds(), 0.0001);
        assertEquals(RewardConfigLoader.DEFAULT_ROLL_TOP_SPEED, config.getRollTopSpeed(), 0.0001);
        assertTrue(config.getRewards().isEmpty());
    }
}
