package dev.bukkit.summon;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import dev.bukkit.item.BukkitItemStackAdapter;

/**
 * Persistence helpers for the Soul Tome: a Support-only main-hand item that
 * stores captured souls in its own PDC as an ordered list of
 * {@link SoulFragment}s. The most recently captured soul is the last element
 * (LIFO), so summoning pops the newest soul first.
 *
 * <p>
 * Souls live on the item: when the run ends and the player's inventory is
 * cleared for a new run, a freshly bought tome starts empty again.
 */
public final class SoulTome {

    public static final String ITEM_ID = "SOUL_TOME";
    private static final String SEPARATOR = ";";
    private static final String FRAGMENT_SEPARATOR = "|";

    private SoulTome() {
    }

    public static NamespacedKey soulsKey() {
        return new NamespacedKey("project_d", "souls");
    }

    public static String encode(SoulFragment fragment) {
        String base = fragment.mobType().name() + FRAGMENT_SEPARATOR + fragment.tier().name();
        if (fragment.definitionId() == null || fragment.definitionId().isBlank()) {
            return base;
        }
        return base + FRAGMENT_SEPARATOR + fragment.definitionId();
    }

    public static SoulFragment decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 2 && parts.length != 3) {
            return null;
        }
        try {
            if (parts.length == 3 && parts[2].isBlank()) {
                return null; // trailing separator: malformed
            }
            String definitionId = parts.length == 3 ? parts[2] : null;
            return new SoulFragment(org.bukkit.entity.EntityType.valueOf(parts[0]),
                    dev.core.game.dungeon.proceduralDungeon.util.SpawnTier.valueOf(parts[1]), definitionId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The tome stack in the player's inventory, or null if none is held. */
    public static ItemStack findTome(Player player) {
        if (player == null) {
            return null;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isTome(stack)) {
                return stack;
            }
        }
        return null;
    }

    public static boolean isTome(ItemStack stack) {
        return stack != null && ITEM_ID.equals(BukkitItemStackAdapter.getRpgItemId(stack));
    }

    public static List<SoulFragment> getSouls(ItemStack stack) {
        List<SoulFragment> souls = new ArrayList<>();
        if (!isTome(stack) || !stack.hasItemMeta()) {
            return souls;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String joined = pdc.get(soulsKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (joined == null || joined.isBlank()) {
            return souls;
        }
        for (String part : joined.split(SEPARATOR, -1)) {
            SoulFragment fragment = decode(part);
            if (fragment != null) {
                souls.add(fragment);
            }
        }
        return souls;
    }

    public static int countSouls(ItemStack stack) {
        return getSouls(stack).size();
    }

    /**
     * Appends a soul to the tome (LIFO: newest last). Returns {@code true} when
     * the soul was stored; {@code false} when the tome is absent, full or the
     * fragment could not be encoded.
     */
    public static boolean addSoul(ItemStack stack, SoulFragment fragment, int capacity) {
        if (!isTome(stack) || fragment == null) {
            return false;
        }
        List<SoulFragment> souls = getSouls(stack);
        if (souls.size() >= capacity) {
            return false;
        }
        souls.add(fragment);
        writeSouls(stack, souls);
        return true;
    }

    /**
     * Pops the most recently captured soul (LIFO). Returns null when the tome
     * holds no souls.
     */
    public static SoulFragment popSoul(ItemStack stack) {
        if (!isTome(stack) || countSouls(stack) == 0) {
            return null;
        }
        List<SoulFragment> souls = getSouls(stack);
        SoulFragment popped = souls.remove(souls.size() - 1);
        writeSouls(stack, souls);
        return popped;
    }

    private static void writeSouls(ItemStack stack, List<SoulFragment> souls) {
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        StringBuilder sb = new StringBuilder();
        for (SoulFragment fragment : souls) {
            if (sb.length() > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(encode(fragment));
        }
        pdc.set(soulsKey(), org.bukkit.persistence.PersistentDataType.STRING, sb.length() == 0 ? "" : sb.toString());
        stack.setItemMeta(meta);
    }
}