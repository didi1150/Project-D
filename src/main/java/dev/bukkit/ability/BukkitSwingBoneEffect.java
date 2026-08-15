package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.BackstabUtils;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Bonemerang: a bone armor stand thrown along the caster's look vector for a
 * fixed outward flight of {@code 18} ticks (~10.8 blocks) before homing back
 * to the caster's CURRENT position for up to another 18 ticks. Outbound hits
 * deal 1.0x damage; returning hits deal 2.0x; the bone pierces up to
 * {@link #MAX_PIERCE} distinct mobs. Hitting a solid block or exceeding the
 * pierce limit shatters the bone: the shatter penalty is a 3-second cooldown,
 * shortened by the caster's ABILITY_HASTE stat (see BONE_SWING in
 * abilities.yml), after which the original item is restored. The restore task
 * keeps polling the inventory (endlessly, if need be) until the lock item is
 * found again, so a thrown weapon is never left as a ghast tear; a clean catch
 * restores the item immediately.
 * Only ONE bone may be in flight PER ITEM INSTANCE: while a given bonemerang
 * item is away, it cannot be thrown again, but different bonemerang instances
 * (each keyed by its own item UUID) can all be airborne at the same time.
 * Per-item state (lock item, cooldown, restore) is tracked per item UUID;
 * mobs (no item UUID) keep one bone in flight per caster.
 */
public class BukkitSwingBoneEffect extends Effect {

    /**
     * Flights currently in progress, keyed by {@code casterUuid:cooldownKey}.
     * Because the cooldown key is the item instance's UUID for ITEM-scoped
     * abilities, this locks per (caster, bonemerang) pair: a second throw of
     * the SAME instance is refused until it returns or shatters, while two
     * DIFFERENT bonemerang instances can fly simultaneously. Mobs fall back to
     * the ability id as the key, so they keep one bone in flight per caster.
     * Entries are removed when the flight ends (cleanup / cancel).
     */
    private static final Set<String> FLIGHTS_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private final List<UUID> forwardHits = new ArrayList<>();
    private final List<UUID> backwardHits = new ArrayList<>();

    private boolean inAnimation;
    private int ticks = 0;
    private float speed;
    private boolean hitOutward;
    private boolean hitReturn;
    private ArmorStand armorStand;
    private Vector directionOutward;
    private RPGEntity caster;
    private Player player;
    private CooldownSink cooldownSink;
    private String uuid;
    private ItemStack newItemStack;
    private BukkitTask resetItemTask;
    private boolean cleanedUp;
    private boolean shatterRequested;
    private String flightKey;
    /** Inventory slot of the weapon that was morphed into the lock item. */
    private int heldSlot = -1;
    /** The weapon stack as held at cast time, used to verify the lock item. */
    private ItemStack originalWeapon;

    private static final int BONE_DAMAGE = 0;
    private static final double RETURN_DISTANCE_THRESHOLD = 0.5; // Distance to caster to auto-complete return
    private static final int MAX_PIERCE = 10; // Distinct mob collisions allowed per cycle
    private static final int MAX_FLIGHT_TICKS = 130; // Emergency despawn cap (36-tick flight + margin)

    public BukkitSwingBoneEffect(String cooldownKey) {
        super(null, MAX_FLIGHT_TICKS * 50L + 100, true, cooldownKey);
    }

    public String getUuid() {
        return uuid;
    }

    /**
     * A bone that finished its flight (caught, shattered or interrupted) is
     * considered expired at once, so the manager removes it from the active
     * effect list immediately and the caster is free to throw again - the
     * configured duration only caps a flight that never ends (emergency
     * despawn via the manager).
     */
    @Override
    public boolean hasExpired(long now) {
        return cleanedUp || super.hasExpired(now);
    }

    /**
     * Resolves the Bukkit living entity backing a caster, so the bone can be
     * thrown by players AND mobs. Mobs keep their vanilla AI but cast abilities
     * through an {@code RPGMobEntity} wrapper that shares the vanilla entity's
     * uuid, so {@link Bukkit#getEntity(uuid)} resolves the living mob.
     */
    private static LivingEntity resolveEntity(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayer().orElse(null);
        }
        // Off-server (tests/headless): there is no entity registry, so no-op.
        if (Bukkit.getServer() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(caster.getUuid());
        return entity instanceof LivingEntity living ? living : null;
    }

    private void startBoneProjectile(LivingEntity casterEntity) {
        Location spawnLoc = casterEntity.getEyeLocation().subtract(0, 1.5, 0);
        armorStand = casterEntity.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setGravity(false);
            stand.setSmall(false);
            stand.setBasePlate(false);
            stand.setMarker(true);
            stand.getEquipment().setHelmet(new ItemStack(Material.BONE));
            stand.setMetadata("BONEMERANG", new FixedMetadataValue(DMain.getInstance(), true));
        });

        speed = 0.6f;
        directionOutward = casterEntity.getEyeLocation().getDirection().normalize();

        inAnimation = true;
        casterEntity.getWorld().playSound(spawnLoc, Sound.ENTITY_SKELETON_HURT, new Random().nextFloat(0.3f, 0.6f),
                new Random().nextFloat(1.75f, 2.0f));
    }

    private ItemStack copyItemStackWithMaterial(ItemStack original, Material newMaterial) {
        if (original == null || newMaterial == null)
            return null;

        ItemStack copy = original.clone();

        ItemStack newStack = new ItemStack(newMaterial, copy.getAmount());

        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            newStack.setItemMeta(meta);
        }

        return newStack;
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        // One bone in flight per (caster, item instance): while THIS bonemerang
        // is away it cannot be thrown again, but other instances of the same
        // ability (different cooldown key = different item UUID) are unaffected
        // and may fly at the same time. The lock is taken BEFORE any Bukkit
        // work, so a second throw is refused even while the first one is still
        // being started; if the cast then turns out impossible (no resolvable
        // caster) the slot is released again.
        flightKey = caster.getUuid() + ":" + getCooldownKey();
        if (!FLIGHTS_IN_PROGRESS.add(flightKey)) {
            return;
        }

        if (inAnimation) {
            return;
        }
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            releaseFlight();
            return;
        }

        this.caster = caster;
        this.cooldownSink = cooldownSink;
        this.cleanedUp = false;
        this.shatterRequested = false;
        this.hitOutward = false;
        this.hitReturn = false;

        // Players swap the weapon to a ghast tear (non-interactive lock item)
        // while it is in flight and then restore it on return; mobs keep their
        // vanilla equipment untouched. Each bonemerang instance (its UUID in
        // the item's PDC) keeps its own lock item + cooldown + restore.
        this.player = null;
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            Player playerPlayer = playerEntity.getPlayer().get();
            this.player = playerPlayer;
            UUID uuid2 = BukkitItemStackAdapter.getUUID(playerPlayer.getInventory().getItemInMainHand());
            if (uuid2 != null) {
                uuid = uuid2.toString();
            }
            heldSlot = playerPlayer.getInventory().getHeldItemSlot();
            originalWeapon = playerPlayer.getInventory().getItemInMainHand().clone();
            newItemStack = copyItemStackWithMaterial(originalWeapon, Material.GHAST_TEAR);
            playerPlayer.getInventory().setItemInMainHand(newItemStack);
        }

        startBoneProjectile(casterEntity);
    }

    @Override
    public void cancel() {
        ticks = 0;
        if (armorStand != null && armorStand.isValid()) {
            armorStand.remove();
        }
        inAnimation = false;

        // Interrupted mid-flight (armor stand removed, effect expired, plugin
        // shutdown): treat like a shatter, so the player is never left holding a
        // ghast tear while stuck with no cooldown (which allowed infinite recast).
        if (!cleanedUp) {
            cleanedUp = true;
            releaseFlight();
            if (cooldownSink != null) {
                cooldownSink.startCooldown();
            }
            if (player != null) {
                Location loc = player.getLocation();
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, new Random().nextFloat(0.3f, 0.6f),
                        new Random().nextFloat(0.8f, 1.2f));
                scheduleItemRestore(player, loc);
            }
        }
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (!inAnimation) {
            return;
        }

        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            // Caster unresolvable (despawn/logout): manager expiry still calls
            // cancel(), which restores the item and starts the cooldown.
            return;
        }

        // Interrupted: the armor stand was removed or died mid-flight. Treat as
        // a shatter so item restore + cooldown still happen.
        if (armorStand == null || !armorStand.isValid()) {
            cleanup(caster, casterEntity, player != null ? player.getLocation() : null, true);
            return;
        }

        // Safety cap: the bone should be caught long before this; a missed
        // return (teleport, huge distance) despawns it with the shatter penalty.
        if (++ticks > MAX_FLIGHT_TICKS) {
            cleanup(caster, casterEntity, armorStand.getLocation(), true);
            return;
        }

        Location teleportLoc = armorStand.getLocation();

        // Collision with a solid block ends the flight immediately (shatter).
        if (teleportLoc.clone().add(0, 1.485, 0).getBlock().getType().isSolid()) {
            cleanup(caster, casterEntity, teleportLoc, true);
            return;
        }

        if (ticks < 18) { // Outward phase: 18 ticks straight along the look vector (~10.8 blocks)
            teleportLoc.add(directionOutward.clone().multiply(speed));
            dealDamage(caster, casterEntity, now, forwardHits);

            if (!hitOutward) {
                hitOutward = true;
            }
            dealDamage(caster, casterEntity, now, forwardHits);

            if (shatterRequested) {
                cleanup(caster, casterEntity, teleportLoc, true); // exceeded pierced mobs
                return;
            }
        } else if (ticks < 36) { // Return phase: up to 18 ticks homing to the caster's CURRENT position
            Vector directionToCaster = casterEntity.getLocation().toVector().subtract(teleportLoc.toVector())
                    .normalize();
            teleportLoc.add(directionToCaster.multiply(speed));
            casterEntity.getWorld().spawnParticle(Particle.ENCHANT, teleportLoc, 1);

            if (!hitReturn) {
                hitReturn = true;
            }
            dealDamage(caster, casterEntity, now, backwardHits);
            if (shatterRequested) {
                cleanup(caster, casterEntity, teleportLoc, true);
                return;
            }

            if (teleportLoc.distanceSquared(casterEntity.getLocation()) <= RETURN_DISTANCE_THRESHOLD) {
                cleanup(caster, casterEntity, teleportLoc, false); // caught: clean return
                return;
            }
        } else { // End of animation: clean auto-catch
            cleanup(caster, casterEntity, teleportLoc, false);
            return;
        }

        teleportLoc.setYaw((teleportLoc.getYaw() + 24.0f) % 360f);
        armorStand.teleport(teleportLoc);
    }

    /**
     * Ends the flight. {@code cooldown} true means the bone shattered (solid
     * block, pierce limit, interruption): the haste-scaled cooldown applies
     * and the item comes back when it ends. False means a clean catch: the
     * cooldown (if any was started) is cleared and the item comes back
     * immediately.
     */
    private void cleanup(RPGEntity caster, LivingEntity casterEntity, Location loc, boolean cooldown) {
        cleanedUp = true;
        releaseFlight();
        cancel();

        Location effectLoc = loc != null ? loc : casterEntity.getLocation();

        if (cooldown) {
            if (cooldownSink != null) {
                cooldownSink.startCooldown();
            }
            effectLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, effectLoc, 50);
            effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_ITEM_BREAK, new Random().nextFloat(0.3f, 0.6f),
                    new Random().nextFloat(0.8f, 1.2f));
        } else if (cooldownSink != null) {
            cooldownSink.clearCooldown();
        }

        if (player != null) {
            scheduleItemRestore(player, effectLoc);
        }
    }

    /**
     * Frees this instance's in-flight slot so the same bonemerang can be
     * thrown again.
     */
    private void releaseFlight() {
        if (flightKey != null) {
            FLIGHTS_IN_PROGRESS.remove(flightKey);
            flightKey = null;
        }
    }

    /**
     * Restores the player's weapon once the flight is over. After a shatter the
     * item is only swapped back once the haste-scaled cooldown expires, so the
     * item never returns before the ability is usable again; after a clean
     * catch it is restored immediately. The task polls the inventory every 5
     * ticks with no attempt cap, so even a lock item dropped or moved around
     * mid-flight is eventually found and converted back.
     */
    private void scheduleItemRestore(Player player, Location loc) {
        if (resetItemTask != null) {
            resetItemTask.cancel();
        }
        resetItemTask = Bukkit.getScheduler().runTaskTimer(DMain.getInstance(), () -> {
            if (cooldownSink != null && cooldownSink.remainingCooldown() > 0) {
                return; // shattered: wait for the cooldown to end
            }
            if (resetItem(player)) {
                player.playSound(loc, Sound.BLOCK_WOOD_PLACE, new Random().nextFloat(0.3f, 0.6f),
                        new Random().nextFloat(1.25f, 1.5f));
                stopRestoreTask();
            }
        }, 0L, 5L);
    }

    private void stopRestoreTask() {
        if (resetItemTask != null) {
            resetItemTask.cancel();
            resetItemTask = null;
        }
    }

    /**
     * Swaps the morphed lock item back to the original weapon material. First
     * preference is a UUID match anywhere in the inventory (the player may
     * have moved the lock item mid-flight); second is the exact slot that was
     * morphed at cast time, which also covers weapons without an instance UUID
     * (where an all-UUID scan cannot find anything).
     */
    private boolean resetItem(Player player) {
        PlayerInventory inv = player.getInventory();
        if (uuid != null) {
            for (int i = 0; i <= 40; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null) {
                    continue;
                }
                UUID itemUuid = BukkitItemStackAdapter.getUUID(item);
                if (itemUuid != null && uuid.equals(itemUuid.toString())) {
                    newItemStack = copyItemStackWithMaterial(item, Material.BONE);
                    inv.setItem(i, newItemStack);
                    return true;
                }
            }
        }

        // Fallback: the lock item is still sitting in the slot that was morphed.
        if (heldSlot >= 0 && heldSlot <= 40 && isLockItem(inv.getItem(heldSlot))) {
            newItemStack = copyItemStackWithMaterial(inv.getItem(heldSlot), Material.BONE);
            inv.setItem(heldSlot, newItemStack);
            return true;
        }
        return false;
    }

    /**
     * True if the stack looks like the lock item this effect morphed: a ghast
     * tear carrying the same RPG item id as the weapon held at cast time.
     */
    private boolean isLockItem(ItemStack stack) {
        if (originalWeapon == null || stack == null || stack.getType() != Material.GHAST_TEAR) {
            return false;
        }
        String stackId = BukkitItemStackAdapter.getRpgItemId(stack);
        return stackId != null && stackId.equals(BukkitItemStackAdapter.getRpgItemId(originalWeapon));
    }

    private int totalHits() {
        return forwardHits.size() + backwardHits.size();
    }

    private void dealDamage(RPGEntity caster, LivingEntity casterEntity, long now, List<UUID> hitList) {
        List<Entity> nearbyEntities = (List<Entity>) casterEntity.getWorld()
                .getNearbyEntities(armorStand.getLocation().clone().add(0, 1.575, 0), 0.8, 0.8, 0.8);

        boolean casterIsPlayer = caster instanceof BukkitPlayerEntity;

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof LivingEntity le) || le.getUniqueId().equals(caster.getUuid())) {
                continue;
            }
            if (le.hasMetadata("BONEMERANG")) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(le.getUniqueId())) {
                continue; // ghosts take no damage; don't track them as a hit
            }

            // Players throw at mobs; mobs throw at players.
            if (casterIsPlayer && entity.getType() == EntityType.PLAYER) {
                continue;
            }
            if (!casterIsPlayer && entity.getType() != EntityType.PLAYER) {
                continue;
            }

            if (!hitList.contains(le.getUniqueId())) {
                // Penetration limit: the 11th distinct mob of the cycle shatters
                // the bone instead of taking damage.
                if (totalHits() >= MAX_PIERCE) {
                    shatterRequested = true;
                    return;
                }
                hitList.add(le.getUniqueId());

                // Outbound hits deal 1.0x, returning hits 2.0x damage. Damage
                // comes from the caster's ATTACK_DAMAGE stat (players: their
                // weapon's stat included; mobs: base stats + equipped weapon),
                // then scaled by the caster's (config-driven)
                // ability-damage-multiplier and their projectile damage bonus
                // (e.g. the Basic Archer Set).
                double multiplier = hitReturn ? 2 : 1;
                double attackDamage = (caster.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now)
                        + BONE_DAMAGE) * multiplier * caster.getAbilityDamageMultiplier()
                        * caster.getProjectileDamageMultiplier()
                        * BackstabUtils.backstabMultiplier(caster, le);
                EntityManager.getInstance().getEntity(entity.getUniqueId()).ifPresentOrElse(target -> {
                    RPGDamageResult rpgDamage = target.dealRPGDamage(caster, target, attackDamage,
                            DamageType.PHYSICAL);
                    // Hurt animation/sound are handled centrally by dealRPGDamage
                    // (only when the target is not immune). Here we only knock
                    // back + render the floating damage numbers, and skip both
                    // on a denied hit.
                    if (rpgDamage.getResult() != DamageResult.DENY) {
                        knockback(le);
                        showDamageIndicator(le, rpgDamage.getDamage(), rpgDamage.getResult());
                        playHitSound(le, rpgDamage.getResult());
                    }
                }, () -> {
                    // Vanilla mob: damageMob fires an EntityDamageByEntityEvent
                    // that CombatListener already renders an indicator for, so we
                    // must NOT render again here or the indicator doubles.
                    DamageUtils.damageMob(le, attackDamage, casterEntity);
                    knockback(le);
                    playHitSound(le, DamageResult.NORMAL);
                });
            }
        }
    }

    private void knockback(LivingEntity le) {
        Vector knockbackDirection = le.getLocation().toVector()
                .subtract(armorStand.getLocation().toVector()).normalize();
        le.setVelocity(knockbackDirection.multiply(0.1));
    }

    /**
     * Mirror of the melee damage numbers (CombatListener.showPhysicalDamage) so a
     * bone hit is as visible as a sword swing.
     */
    private void showDamageIndicator(LivingEntity le, double damage, DamageResult result) {
        if (damage <= 0) {
            return;
        }
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.showPhysicalDamage(le.getLocation(), damage, result);
    }

    /**
     * Impact sound for a landed bone hit; reused from the central projectile
     * hit-sound logic in {@link CombatListener}.
     */
    private void playHitSound(LivingEntity le, DamageResult result) {
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.playProjectileHitSound(le, result);
    }

}