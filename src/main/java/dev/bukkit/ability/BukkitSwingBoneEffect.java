package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

public class BukkitSwingBoneEffect extends Effect {

    private boolean hitOutward = false;
    private boolean hitReturn = false;

    private final List<UUID> forwardHits = new ArrayList<>();
    private final List<UUID> backwardHits = new ArrayList<>();

    private boolean inAnimation;
    private int ticks = 0;
    private float speed;
    private ArmorStand armorStand;
    private Runnable startCooldown;
    private Vector directionOutward;
    private Runnable resetCooldown;
    private String uuid;
    private ItemStack newItemStack;
    private BukkitTask resetItemTask;

    private static final int BONE_DAMAGE = 0;
    private static final double RETURN_DISTANCE_THRESHOLD = 0.5; // Distance to caster to auto-complete return

    public BukkitSwingBoneEffect(String cooldownKey) {
        super(null, 1850, true, cooldownKey);
    }

    public String getUuid() {
        return uuid;
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
    public void cast(RPGEntity caster, Runnable startCooldown, Runnable resetCooldown) {
        this.resetCooldown = resetCooldown;
        if (inAnimation) {
            return;
        }
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            return;
        }

        // Players swap the weapon to a ghast tear while it is in flight and then
        // restore it on return; mobs keep their vanilla equipment untouched.
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            Player player = playerEntity.getPlayer().get();
            UUID uuid2 = BukkitItemStackAdapter.getUUID(player.getInventory().getItemInMainHand());
            if (uuid2 != null) {
                uuid = uuid2.toString();
            }
            newItemStack = copyItemStackWithMaterial(player.getInventory().getItemInMainHand(), Material.GHAST_TEAR);
            player.getInventory().setItemInMainHand(newItemStack);
            this.startCooldown = startCooldown;
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
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null || !inAnimation || armorStand == null || !armorStand.isValid()) {
            return;
        }

        Location teleportLoc = armorStand.getLocation();

        // Collision with solid block ends immediately
        if (teleportLoc.clone().add(0, 1.485, 0).getBlock().getType().isSolid()) {
            cleanup(caster, casterEntity, teleportLoc, true);
            return;
        }

        if (ticks < 18) { // Outward phase
            teleportLoc.add(directionOutward.clone().multiply(speed));
            dealDamage(caster, casterEntity, now, forwardHits);

            if (!hitOutward) {
                hitOutward = true;
            }
            dealDamage(caster, casterEntity, now, forwardHits);

        } else if (ticks < 36) { // Return phase
            Vector directionToCaster = casterEntity.getLocation().toVector().subtract(teleportLoc.toVector())
                    .normalize();
            teleportLoc.add(directionToCaster.multiply(speed));
            casterEntity.getWorld().spawnParticle(Particle.ENCHANT, teleportLoc, 1);

            if (!hitReturn) {
                hitReturn = true;
            }
            dealDamage(caster, casterEntity, now, backwardHits);

            if (teleportLoc.distanceSquared(casterEntity.getLocation()) <= RETURN_DISTANCE_THRESHOLD) {
                cleanup(caster, casterEntity, teleportLoc, false);
                resetCooldown.run();
                return;
            }
        } else { // End of animation
            cleanup(caster, casterEntity, teleportLoc, false);
            return;
        }

        teleportLoc.setYaw((teleportLoc.getYaw() + 24.0f) % 360f);
        armorStand.teleport(teleportLoc);

        ticks++;
    }

    private void cleanup(RPGEntity caster, LivingEntity casterEntity, Location loc, boolean cooldown) {
        cancel();

        if (startCooldown != null && cooldown) {
            casterEntity.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 50);
            startCooldown.run();
            long baseCooldownTime = 3000;
            EntityManager.getInstance().getEntity(caster.getUuid()).ifPresent(entity -> {
				long reducedCooldownTime = (long) (baseCooldownTime * 100 / (100
						+ entity.getStatEngineAdapter().getCurrentValue(StatType.ABILITY_HASTE,
								System.currentTimeMillis())));
                if (caster instanceof BukkitPlayerEntity) {
                    scheduleItemRestore((Player) casterEntity, loc, reducedCooldownTime / 1000 * 20);
                }
            });
            casterEntity.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, new Random().nextFloat(0.3f, 0.6f),
                    new Random().nextFloat(0.8f, 1.2f));
        } else {
            if (caster instanceof BukkitPlayerEntity) {
                scheduleItemRestore((Player) casterEntity, loc, 0);
            } else {
                resetCooldown.run();
            }
        }
    }

    private void scheduleItemRestore(Player player, Location loc, long startDelayTicks) {
        final boolean restoreCooldown = startDelayTicks <= 0;
        resetItemTask = Bukkit.getScheduler().runTaskTimer(DMain.getInstance(), () -> {
            if (resetItem(player)) {
                player.playSound(loc, Sound.BLOCK_WOOD_PLACE, new Random().nextFloat(0.3f, 0.6f),
                        new Random().nextFloat(1.25f, 1.5f));
                if (resetItemTask != null) {
                    resetItemTask.cancel();
                }
                if (restoreCooldown) {
                    resetCooldown.run();
                }
            }
        }, startDelayTicks, 5);
    }

    private boolean resetItem(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            UUID uuid2 = BukkitItemStackAdapter.getUUID(item);
            if (item != null && uuid2 != null && uuid.equals(uuid2.toString())) {
                newItemStack = copyItemStackWithMaterial(item, Material.BONE);
                inv.setItem(i, newItemStack);
                return true;
            }
        }
        return false;
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
                hitList.add(le.getUniqueId());

                double multiplier = hitReturn ? 2 : 1;
                // Damage comes from the caster's ATTACK_DAMAGE stat (players: their
                // weapon's stat included; mobs: base stats + equipped weapon), then
                // scaled by the caster's (config-driven) ability-damage-multiplier
                // and their projectile damage bonus (e.g. the Basic Archer Set).
                double attackDamage = (caster.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now)
                        + BONE_DAMAGE) * multiplier * caster.getAbilityDamageMultiplier()
                        * caster.getProjectileDamageMultiplier()
                        * BackstabUtils.backstabMultiplier(caster, le);
                EntityManager.getInstance().getEntity(entity.getUniqueId()).ifPresentOrElse(target -> {
                    RPGDamageResult rpgDamage = target.dealRPGDamage(caster, target, attackDamage,
                            DamageType.PHYSICAL);
                    // Hurt animation/sound are handled centrally by dealRPGDamage (only
                    // when the target is not immune). Here we only knock back + render
                    // the floating damage numbers, and skip both on a denied hit.
                    if (rpgDamage.getResult() != DamageResult.DENY) {
                        knockback(le);
                        showDamageIndicator(le, rpgDamage.getDamage(), rpgDamage.getResult());
                        playHitSound(le, rpgDamage.getResult());
                    }
                }, () -> {
                    // Vanilla mob: damageMob fires an EntityDamageByEntityEvent that
                    // CombatListener already renders an indicator for, so we must NOT
                    // render again here or the indicator doubles.
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