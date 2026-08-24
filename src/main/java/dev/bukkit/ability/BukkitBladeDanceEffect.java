package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.ability.behavior.BladeDanceBehavior;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Active cone for Blade Dance — right-click fires all stacked blades forward in a pyramid.
 * Keeps ItemDisplays, makes them point towards enemy (forward) and hit all enemies they pass through.
 * Inspired by LloydFirstAbility pyramid: mid 7 blocks ahead, left 2/1 and right 1/2 offsets.
 */
public class BukkitBladeDanceEffect extends Effect {

    public BukkitBladeDanceEffect(String cooldownKey) {
        super(null, 600L, true, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        Player player = resolvePlayer(caster);
        if (player == null) return;
        UUID uuid = caster.getUuid();
        int blades = BladeDanceBehavior.getBladeCount(uuid);
        if (blades <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.7f);
            player.sendMessage("§7No blades stacked — wait for charge.");
            return;
        }
        // consume orbiting displays
        List<ItemDisplay> displays = BladeDanceBehavior.consumeBlades(uuid);
        if (displays == null || displays.isEmpty()) return;
        cooldownSink.startCooldown();

        double ad = caster.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, System.currentTimeMillis());
        double leth = caster.getStatEngineAdapter().getCurrentValue(StatType.LETHALITY, System.currentTimeMillis());
        double perBlade = BladeDanceBehavior.calculateBladeDamage(ad, leth);

        launchCone(player, caster, displays, perBlade);
    }

    private void launchCone(Player player, RPGEntity holder, List<ItemDisplay> displays, double perBlade) {
        World world = player.getWorld();
        if (world == null) {
            for (ItemDisplay d : displays) if (d != null && d.isValid()) d.remove();
            return;
        }
        // direction
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().clone();
        // flatten Y slightly? keep as is for 3D cone; but for horizontal cone we flatten a bit
        Vector flatDir = dir.clone();
        // keep original dir for 3D, but for pyramid we use flat for Y? Use full dir
        // mid 7 blocks ahead, dropped 1 block toward ground for gravity arc (Lloyd style)
        Location mid = eye.clone().add(dir.clone().multiply(7)).subtract(0, 1, 0);
        // compute right vector (final for inner class)
        Vector up = new Vector(0, 1, 0);
        Vector tmpRight = dir.clone().crossProduct(up);
        tmpRight.normalize();
        if (tmpRight.lengthSquared() < 0.001) tmpRight = new Vector(1, 0, 0);
        tmpRight.normalize();
        final Vector right = tmpRight;
        // normalize dir
        dir.normalize();
        Vector left = right.clone().multiply(-1);

        int n = displays.size();
        // offsets pyramid like Lloyd: for 5 -> -2,-1,0,1,2
        double[] offsets;
        switch (n) {
            case 1 -> offsets = new double[]{0};
            case 2 -> offsets = new double[]{-1, 1};
            case 3 -> offsets = new double[]{-1, 0, 1};
            case 4 -> offsets = new double[]{-2, -1, 1, 2};
            default -> offsets = new double[]{-2, -1, 0, 1, 2};
        }
        // if displays size != offsets length (e.g., 5 but we have 5) ok; if n=5 but offsets 5, fine. For n mismatch, truncate/pad
        // ensure offsets length == n, else regenerate
        if (offsets.length != n) {
            offsets = new double[n];
            for (int i = 0; i < n; i++) {
                offsets[i] = - (n - 1) / 2.0 + i;
                // scale to 1 block per offset step
            }
        }

        Location[] targets = new Location[n];
        for (int i = 0; i < n; i++) {
            double off = offsets[i];
            // use right * off (negative = left)
            Location t = mid.clone().add(right.clone().multiply(off));
            targets[i] = t;
        }

        // prepare vectors and hit tracking
        List<Vector> vectors = new ArrayList<>(n);
        Map<Integer, List<UUID>> hitEntities = new HashMap<>();
        // re-use the existing ItemDisplays: reposition them to near eye and point forward
        for (int i = 0; i < n; i++) {
            ItemDisplay disp = displays.get(i);
            if (disp == null || !disp.isValid()) {
                vectors.add(new Vector(0,0,0));
                hitEntities.put(i, new ArrayList<>());
                continue;
            }
            // spawn location slightly in front of eye to avoid inside player
            Location spawn = eye.clone().add(dir.clone().multiply(0.5));
            // add small right offset like Lloyd's 0.1 to avoid self-hit
            spawn.add(right.clone().multiply(0.1));
            // point blade towards target (forward) with initial ground bias
            Vector vec = targets[i].clone().subtract(spawn).toVector().normalize();
            vec.setY(vec.getY() - 0.12);
            vec.normalize();
            vectors.add(vec);
            hitEntities.put(i, new ArrayList<>());

            // repurpose display: teleport to spawn, set forward-pointing transform
            disp.teleport(spawn);
            // make blade point towards enemy: blade tip forward along vec
            // For ItemDisplay FIXED, blade up default; rotate 90° around X to point forward, then yaw/pitch to vec
            disp.setTeleportDuration(0);
            disp.setInterpolationDuration(0);
            // set yaw/pitch to vec direction
            Location yawPitch = spawn.clone();
            yawPitch.setDirection(vec);
            // we will teleport with yaw/pitch each tick as well
            // set transformation to make blade point forward (90° X)
            disp.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0f,0f,0f),
                    new org.joml.AxisAngle4f((float)Math.toRadians(90), 1f,0f,0f),
                    new org.joml.Vector3f(BladeDanceBehavior.SCALE, BladeDanceBehavior.SCALE, BladeDanceBehavior.SCALE),
                    new org.joml.AxisAngle4f(0f,0f,0f,1f)
            ));
            disp.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            disp.setBrightness(new org.bukkit.entity.Display.Brightness(15,15));
            disp.setTeleportDuration(1);
            // set initial yaw/pitch
            Location withDir = spawn.clone();
            withDir.setDirection(vec);
            disp.teleport(withDir);
            disp.setTeleportDuration(1);
        }

        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.1f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.7f, 1.3f);

        // task to move each blade — 7 blocks with gravity arc
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (displays.stream().noneMatch(d -> d != null && d.isValid())) {
                    cancel();
                    return;
                }
                if (ticks > 7) { // max travel 7 blocks
                    for (ItemDisplay d : displays) if (d != null && d.isValid()) d.remove();
                    cancel();
                    return;
                }
                for (int i = 0; i < displays.size(); i++) {
                    ItemDisplay disp = displays.get(i);
                    if (disp == null || !disp.isValid()) continue;
                    Vector vec = vectors.get(i);
                    if (vec == null || vec.lengthSquared() < 0.001) continue;

                    // gravity arc — dip toward ground each tick
                    vec.setY(vec.getY() - 0.035);
                    vec.normalize();

                    // block collision check like Lloyd: block at display +0.2 up +0.3 right (lower for ground)
                    Location check = disp.getLocation().clone().add(0,0.2,0).add(right.clone().multiply(0.3));
                    if (world.getBlockAt(check).getType() != Material.AIR) {
                        disp.remove();
                        world.spawnParticle(Particle.SMOKE, check, 4, 0.1,0.1,0.1,0.02);
                        continue;
                    }

                    // move
                    Location next = disp.getLocation().clone().add(vec.clone());
                    // keep blade pointing along vec
                    next.setDirection(vec);
                    disp.teleport(next);
                    // trail
                    world.spawnParticle(Particle.CRIT, next, 1, 0.05,0.05,0.05,0.02);
                    if (ticks % 2 == 0) world.spawnParticle(Particle.ENCHANTED_HIT, next, 1, 0.02,0.02,0.02,0.02);

                    // damage check nearby 1.5,3,1.5
                    for (Entity ent : disp.getNearbyEntities(1.5, 3, 1.5)) {
                        if (!(ent instanceof LivingEntity le)) continue;
                        if (le.getUniqueId().equals(holder.getUuid())) continue;
                        if (EntityManager.getInstance().isGhost(le.getUniqueId())) continue;
                        if (!CombatRelation.isEnemy(holder, ent)) continue;
                        List<UUID> hit = hitEntities.get(i);
                        if (hit.contains(ent.getUniqueId())) continue;
                        hit.add(ent.getUniqueId());
                        DamageUtils.damageEntity(le, perBlade, holder, DamageType.PHYSICAL);
                        world.spawnParticle(Particle.CRIT, le.getLocation().clone().add(0, le.getHeight()*0.5, 0), 6, 0.2,0.2,0.2,0.1);
                        world.playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.2f);
                    }
                }
                ticks++;
                if (ticks > 7) {
                    for (ItemDisplay d : displays) if (d != null && d.isValid()) d.remove();
                    cancel();
                }
            }
        }.runTaskTimer(DMain.getInstance(), 0L, 1L);
    }

    private Player resolvePlayer(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity pe) return pe.getPlayer().orElse(null);
        if (Bukkit.getServer() == null) return null;
        var e = Bukkit.getEntity(caster.getUuid());
        return e instanceof Player p ? p : null;
    }

    @Override public void cancel() {}
}
