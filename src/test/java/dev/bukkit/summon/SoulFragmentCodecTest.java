package dev.bukkit.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;

/**
 * Exercises the codec that moves {@link SoulFragment}s in and out of string
 * form (tome PDC / dropped soul item). Pure string handling, no Bukkit runtime
 * required.
 */
class SoulFragmentCodecTest {

    @Test
    void encodeDecodeRoundTrip() {
        SoulFragment fragment = new SoulFragment(EntityType.ZOMBIE, SpawnTier.ADVANCED, "ZOMBIE_SWARMER");
        assertEquals(fragment, SoulTome.decode(SoulTome.encode(fragment)));
    }

    @Test
    void encodeDecodeLegacyTwoPartSoul() {
        // Souls captured before the definition id was recorded stay readable.
        SoulFragment fragment = new SoulFragment(EntityType.ZOMBIE, SpawnTier.ADVANCED, null);
        assertEquals(fragment, SoulTome.decode(SoulTome.encode(fragment)));
        assertEquals(fragment, SoulTome.decode("ZOMBIE|ADVANCED"));
    }

    @Test
    void decodeRejectsGarbage() {
        assertNull(SoulTome.decode(null));
        assertNull(SoulTome.decode(""));
        assertNull(SoulTome.decode("ZOMBIE"));
        assertNull(SoulTome.decode("NOT_A_TYPE|BASIC"));
        assertNull(SoulTome.decode("ZOMBIE|NOT_A_TIER"));
        assertNull(SoulTome.decode("ZOMBIE|BASIC|"));
        assertNull(SoulTome.decode("ZOMBIE|BASIC|A|B"));
    }

    @Test
    void encodeIsStableAcrossFragments() {
        SoulFragment one = new SoulFragment(EntityType.SPIDER, SpawnTier.BASIC, null);
        SoulFragment two = new SoulFragment(EntityType.WITHER_SKELETON, SpawnTier.ELITE, "HALLOWED_KNIGHT");
        assertEquals("SPIDER|BASIC", SoulTome.encode(one));
        assertEquals("WITHER_SKELETON|ELITE|HALLOWED_KNIGHT", SoulTome.encode(two));
    }
}