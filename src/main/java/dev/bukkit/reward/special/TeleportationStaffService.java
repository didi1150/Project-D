package dev.bukkit.reward.special;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.DMain;
import dev.core.entity.EntityManager;

/**
 * Hidden random timer for the Teleportation Staff. One pending teleport may
 * exist per caster and per target; completion pulls the target to the caster's
 * current location.
 */
public final class TeleportationStaffService {

    public static final int MIN_DELAY_SECONDS = 5;
    public static final int MAX_DELAY_SECONDS = 15;

    private static final TeleportationStaffService INSTANCE = new TeleportationStaffService();

    private final Random random = new Random();
    private final Map<UUID, PendingTeleport> byCaster = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> byTarget = new ConcurrentHashMap<>();

    private TeleportationStaffService() {
    }

    public static TeleportationStaffService getInstance() {
        return INSTANCE;
    }

    public boolean start(Player caster, Player target) {
        if (caster == null || target == null || !caster.isOnline() || !target.isOnline()) {
            return false;
        }
        if (byCaster.containsKey(caster.getUniqueId())) {
            caster.sendMessage("§eYour Teleportation Staff is already charging.");
            return false;
        }
        if (byTarget.containsKey(target.getUniqueId())) {
            caster.sendMessage("§e" + target.getName() + " is already marked by a Teleportation Staff.");
            return false;
        }
        DMain plugin = DMain.getInstance();
        if (plugin == null) {
            return false;
        }

        int delaySeconds = MIN_DELAY_SECONDS + random.nextInt(MAX_DELAY_SECONDS - MIN_DELAY_SECONDS + 1);
        PendingTeleport pending = new PendingTeleport(caster.getUniqueId(), target.getUniqueId());
        byCaster.put(caster.getUniqueId(), pending);
        byTarget.put(target.getUniqueId(), pending);
        pending.task = Bukkit.getScheduler().runTaskLater(plugin, () -> complete(pending), delaySeconds * 20L);

        caster.sendMessage("§dThe Teleportation Staff begins to hum. Its target is marked.");
        caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f);
        return true;
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        PendingTeleport asCaster = byCaster.get(player.getUniqueId());
        if (asCaster != null) {
            cancel(asCaster);
            Player target = Bukkit.getPlayer(asCaster.targetId);
            if (target != null && target.isOnline()) {
                target.sendMessage("§7The Teleportation Staff's link fades away.");
            }
        }
        PendingTeleport asTarget = byTarget.get(player.getUniqueId());
        if (asTarget != null) {
            cancel(asTarget);
            Player caster = Bukkit.getPlayer(asTarget.casterId);
            if (caster != null && caster.isOnline()) {
                caster.sendMessage("§7The Teleportation Staff loses its mark.");
            }
        }
    }

    public void shutdown() {
        for (PendingTeleport pending : byCaster.values()) {
            cancel(pending);
        }
        byCaster.clear();
        byTarget.clear();
    }

    private void complete(PendingTeleport pending) {
        if (pending.task != null && !pending.task.isCancelled()) {
            pending.task.cancel();
        }
        byCaster.remove(pending.casterId, pending);
        byTarget.remove(pending.targetId, pending);

        Player caster = Bukkit.getPlayer(pending.casterId);
        Player target = Bukkit.getPlayer(pending.targetId);
        if (caster == null || target == null || !caster.isOnline() || !target.isOnline()
                || caster.isDead() || target.isDead()
                || EntityManager.getInstance().isGhost(caster.getUniqueId())
                || EntityManager.getInstance().isGhost(target.getUniqueId())) {
            return;
        }

        target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.2);
        target.teleport(caster.getLocation());
        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, caster.getLocation().add(0, 1, 0), 40, 0.5, 1.0, 0.5,
                0.2);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);

        caster.sendMessage("§dThe Teleportation Staff pulls §f" + target.getName() + "§d to your side.");
        target.sendMessage("§dThe Teleportation Staff pulls you to §f" + caster.getName() + "§d.");
    }

    private void cancel(PendingTeleport pending) {
        if (pending.task != null && !pending.task.isCancelled()) {
            pending.task.cancel();
        }
        byCaster.remove(pending.casterId, pending);
        byTarget.remove(pending.targetId, pending);
    }

    private static final class PendingTeleport {
        private final UUID casterId;
        private final UUID targetId;
        private BukkitTask task;

        private PendingTeleport(UUID casterId, UUID targetId) {
            this.casterId = casterId;
            this.targetId = targetId;
        }
    }
}
