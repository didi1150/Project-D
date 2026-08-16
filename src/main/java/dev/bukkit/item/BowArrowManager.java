package dev.bukkit.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.core.entity.EntityManager;
import dev.core.item.ItemType;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.stat.StatType;

/**
 * Unlimited-arrows groundwork: while a player holds a bow that requires arrows
 * ({@code itemType: BOW} + {@code requiresArrows: true}), a plain arrow stack
 * is kept in one of the main-inventory slots (9-35) so vanilla shooting never
 * runs dry. Free slots are preferred; when the main inventory is full the
 * highest-index occupied slot is temporarily overridden and its item is
 * stored, to be restored as soon as the player stops holding the bow.
 *
 * The stored item is only ever touched on bow-mode exit ({@link #onSlotSwap},
 * {@link #onSwapHands}, {@link #onCleanup}) - shooting merely refreshes the
 * arrow stack. Bows that fire without arrows (e.g. the Bonemerang,
 * {@code requiresArrows: false}) never arm the mechanic.
 *
 * Fired projectiles are also scaled with the custom damage system: at release
 * time the shooter's {@link StatType#ATTACK_DAMAGE} (held bow's active stats
 * included) times their {@link StatType#PROJECTILE_DAMAGE} multiplier is
 * stamped onto the projectile; {@code CombatListener} uses it on hit instead
 * of the vanilla bow damage.
 */
public final class BowArrowManager {

    public static final int ARROW_COUNT = 64;

    /** PDC key carrying the RPG-scaled damage of a player-fired projectile. */
    public static final NamespacedKey ARROW_DAMAGE_KEY = new NamespacedKey("project_d", "arrow_damage");

    /**
     * Bounce backdoor: an arrow stamped with this key is exempt from the
     * no-pickup rule (and, once a bounce ability exists, may be deflected and
     * recovered). Nothing stamps it today.
     */
    public static final NamespacedKey BOUNCE_KEY = new NamespacedKey("project_d", "bounce_arrow");

    /** Main-inventory slot range; hotbar (0-8), armor (36-39) and offhand (40) are excluded. */
    private static final int SLOT_START = 9;
    private static final int SLOT_END = 35;

    private static final ItemStack ARROWS = new ItemStack(Material.ARROW, ARROW_COUNT);

    private static final Map<UUID, BowState> BOWING = new HashMap<>();

    private BowArrowManager() {
    }

    private record BowState(int arrowSlot, ItemStack stored, boolean overrode) {
    }

    public static boolean isBowing(Player player) {
        return player != null && BOWING.containsKey(player.getUniqueId());
    }

    /**
     * Called when the player selects a hotbar slot: enter/exit bow mode based
     * on the item now held in hand.
     */
    public static void onSlotSwap(Player player, int newSlot) {
        ItemStack held = player.getInventory().getItem(newSlot);
        if (isBowing(player)) {
            if (!isBowRequiringArrows(held)) {
                exitBowMode(player);
            }
            return;
        }
        if (isBowRequiringArrows(held)) {
            enterBowMode(player);
        }
    }

    /**
     * Called from a deferred task when the player swaps hands: re-evaluates
     * bow mode against the (already swapped) main-hand item.
     */
    public static void onSwapHands(Player player) {
        if (isBowRequiringArrows(player.getInventory().getItemInMainHand())) {
            if (!isBowing(player)) {
                enterBowMode(player);
            }
        } else {
            exitBowMode(player);
        }
    }

    /**
     * True when {@code stack} is one of the temporary arrows of bow mode:
     * while the player holds a bow that requires arrows, the only arrow
     * material they may carry is the planted stack, so any arrow item is
     * treated as planted. Used to block dropping the planted stack.
     */
    public static boolean isPlantedArrows(Player player, ItemStack stack) {
        return isBowing(player) && stack != null && isArrowMaterial(stack.getType());
    }

    /**
     * True when {@code slot} is the main-inventory slot currently holding the
     * temporary arrow stack of bow mode. Used to block moving the planted
     * stack (clicks, drags, number-key swaps).
     */
    public static boolean isPlantedArrowSlot(Player player, int slot) {
        BowState state = BOWING.get(player.getUniqueId());
        return state != null && state.arrowSlot() == slot;
    }

    /** True for the arrow item materials that ground pickups may carry. */
    public static boolean isArrowMaterial(Material material) {
        return material == Material.ARROW
                || material == Material.TIPPED_ARROW
                || material == Material.SPECTRAL_ARROW;
    }

    /**
     * True when the projectile is marked as a bounce arrow ({@link #BOUNCE_KEY}
     * set on its PDC). Bounce arrows stay recoverable from the ground; any
     * other arrow shot from a bow is consumed by the unlimited-arrow mechanic
     * and must never re-enter an inventory.
     */
    public static boolean isBounceArrow(Projectile projectile) {
        if (projectile == null) {
            return false;
        }
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        return pdc != null && Boolean.TRUE.equals(pdc.get(BOUNCE_KEY, PersistentDataType.BOOLEAN));
    }

    /** Item-stack variant of {@link #isBounceArrow(Projectile)} (ground item entities). */
    public static boolean isBounceArrow(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return Boolean.TRUE.equals(
                stack.getItemMeta().getPersistentDataContainer().get(BOUNCE_KEY, PersistentDataType.BOOLEAN));
    }

    /**
     * Called after the player fires a bow: tops the arrow stack back up,
     * makes the fired arrow unrecoverable (unless it is a bounce arrow) and
     * stamps the projectile with the RPG-scaled damage of the shot. Never
     * touches the stored item.
     */
    public static void onShoot(Player player, Projectile projectile) {
        BowState state = BOWING.get(player.getUniqueId());
        if (state != null) {
            player.getInventory().setItem(state.arrowSlot(), ARROWS.clone());
        }
        // The planted stack is the only arrow source: a fired arrow must never
        // be picked back up (it would merge into the stack or clutter the
        // inventory). Bounce arrows (future bounce ability, BOUNCE_KEY) are
        // exempt so a deflected arrow can be recovered.
        if (projectile instanceof AbstractArrow arrow && !isBounceArrow(projectile)) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        // Capture the shot's damage at release time so it stays fixed even if
        // the player swaps items before the projectile lands. Non-RPG players
        // (no EntityManager entry) keep vanilla arrow damage.
        EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(attacker -> {
            double damage = attacker.getStatEngineAdapter()
                    .getCurrentValue(StatType.ATTACK_DAMAGE, System.currentTimeMillis())
                    * attacker.getProjectileDamageMultiplier();
            projectile.getPersistentDataContainer().set(ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, damage);
        });
    }

    /**
     * Restores the player's inventory to its pre-bow state (quit / death) so
     * the server never persists the planted arrow stack.
     */
    public static void onCleanup(Player player) {
        exitBowMode(player);
    }

    private static void enterBowMode(Player player) {
        int arrowSlot = firstFreeMainSlot(player);
        ItemStack stored = null;
        boolean overrode = false;
        if (arrowSlot < 0) {
            arrowSlot = lastOccupiedMainSlot(player);
            stored = player.getInventory().getItem(arrowSlot);
            overrode = true;
        }
        player.getInventory().setItem(arrowSlot, ARROWS.clone());
        BOWING.put(player.getUniqueId(), new BowState(arrowSlot, stored, overrode));
    }

    private static void exitBowMode(Player player) {
        BowState state = BOWING.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.overrode() && state.stored() != null) {
            player.getInventory().setItem(state.arrowSlot(), state.stored());
        } else {
            player.getInventory().setItem(state.arrowSlot(), null);
        }
    }

    private static boolean isBowRequiringArrows(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        if (id == null) {
            return false;
        }
        Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(id);
        return item.isPresent() && item.get().getItemType() == ItemType.BOW && item.get().requiresArrows();
    }

    private static int firstFreeMainSlot(Player player) {
        Inventory inv = player.getInventory();
        for (int slot = SLOT_START; slot <= SLOT_END; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private static int lastOccupiedMainSlot(Player player) {
        Inventory inv = player.getInventory();
        for (int slot = SLOT_END; slot >= SLOT_START; slot--) {
            ItemStack stack = inv.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                return slot;
            }
        }
        return -1;
    }
}