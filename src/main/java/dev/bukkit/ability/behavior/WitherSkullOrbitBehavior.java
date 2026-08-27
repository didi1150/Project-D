package dev.bukkit.ability.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.bukkit.DMain;
import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;

/**
 * Per-holder behavior for the Wither Guard set's orbiting skulls. Manages
 * spawning, orbiting, generation, and consumption of wither skeleton skull
 * ItemDisplay entities. Modeled on {@link BladeDanceBehavior}.
 */
public class WitherSkullOrbitBehavior implements AbilityBehavior {

    public static final String ABILITY_ID = "WITHER_SKULL_ORBIT";
    public static final int MAX_SKULLS = 5;
    public static final long GEN_INTERVAL_MS = 5000L;
    public static final double RADIUS = 1.6;
    public static final double Y_OFFSET = 1.0;
    public static final float SPIN_DEG_PER_TICK = 8f;
    public static final float SCALE = 0.8f;

    private static BukkitTask GLOBAL_ORBIT_TASK = null;

    static final class HolderState {
        int skulls = 0;
        long lastGenMs = 0;
        final List<ItemDisplay> displays = new ArrayList<>();
        float spinYaw = 0f;
    }

    private static final Map<UUID, HolderState> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REFCNT = new ConcurrentHashMap<>();

    private ActiveAbility ctx;

    public WitherSkullOrbitBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
        UUID uuid = ctx.getHolder().getUuid();
        STATES.computeIfAbsent(uuid, k -> {
            HolderState hs = new HolderState();
            hs.lastGenMs = System.currentTimeMillis();
            return hs;
        });
        REFCNT.merge(uuid, 1, Integer::sum);
        ensureGlobalOrbitTask();
    }

    private static synchronized void ensureGlobalOrbitTask() {
        if (GLOBAL_ORBIT_TASK != null)
            return;
        Plugin plugin = resolvePlugin();
        if (plugin == null)
            return;
        GLOBAL_ORBIT_TASK = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, HolderState> en : STATES.entrySet()) {
                UUID uuid = en.getKey();
                HolderState st = en.getValue();
                if (st.displays.isEmpty())
                    continue;
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || p.isDead())
                    continue;
                st.spinYaw = (st.spinYaw + SPIN_DEG_PER_TICK) % 360f;
                Location center = p.getLocation().clone().add(0, Y_OFFSET, 0);
                World world = p.getWorld();
                if (world == null)
                    continue;
                int count = st.displays.size();
                for (int i = 0; i < count; i++) {
                    ItemDisplay d = st.displays.get(i);
                    if (d == null || !d.isValid())
                        continue;
                    double ang = Math.toRadians(st.spinYaw + i * 360.0 / count);
                    Location loc = new Location(world, center.getX() + Math.cos(ang) * RADIUS, center.getY(),
                            center.getZ() + Math.sin(ang) * RADIUS);

                    Vector dir = loc.clone().subtract(p.getLocation()).toVector();
                    setDirectionToYaw(loc, dir);
                    loc.setPitch(0f);
                    boolean snap = false;
                    try {
                        double distSq = d.getLocation().distanceSquared(loc);
                        snap = distSq > 6.25;
                    } catch (Exception ignored) {
                    }
                    if (snap)
                        d.setTeleportDuration(0);
                    d.teleport(loc);
                    if (snap)
                        d.setTeleportDuration(1);
                }
            }
        }, 1L, 1L);
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        UUID uuid = ctx.getHolder().getUuid();
        HolderState hs = STATES.computeIfAbsent(uuid, k -> {
            HolderState nh = new HolderState();
            nh.lastGenMs = System.currentTimeMillis();
            return nh;
        });
        if (hs.lastGenMs == 0)
            hs.lastGenMs = System.currentTimeMillis();
        ensureGlobalOrbitTask();
        boolean isFirst = REFCNT.getOrDefault(uuid, 0) == 1;
        if (isFirst) {
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onTick, TickEvent.class));
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onQuit, PlayerQuitEvent.class));
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onDeath, PlayerDeathEvent.class));
        }
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        UUID uuid = ctx.getHolder().getUuid();
        int cnt = REFCNT.getOrDefault(uuid, 1) - 1;
        if (cnt <= 0) {
            REFCNT.remove(uuid);
            clearState(uuid);
        } else {
            REFCNT.put(uuid, cnt);
        }
    }

    private void onTick(TickEvent e) {
        UUID uuid = ctx.getHolder().getUuid();
        HolderState state = STATES.get(uuid);
        if (state == null)
            return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline() || player.isDead())
            return;

        // Generate additional skulls over time
        long now = System.currentTimeMillis();
        if (state.skulls < MAX_SKULLS && now - state.lastGenMs >= GEN_INTERVAL_MS) {
            spawnOrbital(player, state);
            state.skulls = state.displays.size();
            state.lastGenMs = now;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f + state.skulls * 0.12f);
            player.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().clone().add(0, 1.0, 0), 5, 0.3, 0.3,
                    0.3, 0.02);
        }
    }

    private void spawnOrbital(Player player, HolderState state) {
        World world = player.getWorld();
        if (world == null)
            return;
        Location center = player.getLocation().clone().add(0, Y_OFFSET, 0);
        double angle = Math.toRadians(state.spinYaw + (state.displays.size() * 360.0 / MAX_SKULLS));
        Location loc = new Location(world, center.getX() + Math.cos(angle) * RADIUS, center.getY(),
                center.getZ() + Math.sin(angle) * RADIUS);
        loc.setYaw(0f);
        loc.setPitch(0f);
        ItemDisplay disp = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.WITHER_SKELETON_SKULL));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setViewRange(32);
            d.setShadowStrength(0f);
            Transformation transformation = new Transformation(new Vector3f(0, 0, 0), new Quaternionf(),
                    new Vector3f(SCALE), new Quaternionf());
            d.setTransformation(transformation);
        });
        state.displays.add(disp);
    }

    /**
     * Returns a live snapshot of the holder's orbiting skulls.
     */
    public static List<ItemDisplay> getOrbitingSkulls(UUID holderUuid) {
        HolderState st = STATES.get(holderUuid);
        return st == null ? List.of() : List.copyOf(st.displays);
    }

    /**
     * Consumes a specific skull from the orbit (by reference).
     */
    public static void consumeSkull(UUID holderUuid, ItemDisplay skull) {
        HolderState st = STATES.get(holderUuid);
        if (st == null)
            return;
        st.displays.remove(skull);
        st.skulls = st.displays.size();
        st.lastGenMs = System.currentTimeMillis();
    }

    /**
     * Returns the current skull count for the holder.
     */
    public static int getSkullCount(UUID holderUuid) {
        HolderState st = STATES.get(holderUuid);
        return st == null ? 0 : st.skulls;
    }

    private void onQuit(PlayerQuitEvent e) {
        if (!e.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        cleanupHolder(ctx.getHolder().getUuid());
    }

    private void onDeath(PlayerDeathEvent e) {
        if (!e.getEntity().getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        cleanupHolder(ctx.getHolder().getUuid());
    }

    private void cleanupHolder(UUID uuid) {
        HolderState state = STATES.remove(uuid);
        if (state != null) {
            for (ItemDisplay d : state.displays) {
                try {
                    if (d != null && d.isValid())
                        d.remove();
                } catch (Exception ignored) {
                }
            }
            state.displays.clear();
        }
        REFCNT.remove(uuid);
    }

    /**
     * Clears all orbit state for a holder (used by death/quit/set-unequip).
     */
    public static void clearState(UUID uuid) {
        HolderState s = STATES.remove(uuid);
        if (s != null) {
            for (ItemDisplay d : s.displays) {
                try {
                    if (d != null && d.isValid())
                        d.remove();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void setDirectionToYaw(Location loc, Vector dir) {
        dir.normalize();
        double dx = dir.getX();
        double dz = dir.getZ();
        double yaw = Math.atan2(-dx, dz) * (180 / Math.PI);
        loc.setYaw((float) yaw);
    }

    private static Plugin resolvePlugin() {
        try {
            return DMain.getInstance();
        } catch (Exception e) {
            return null;
        }
    }

    // For testing
    public static int getSkulls(UUID uuid) {
        HolderState s = STATES.get(uuid);
        return s == null ? 0 : s.skulls;
    }

    public static void setSkullsForTest(UUID uuid, int count) {
        HolderState s = STATES.computeIfAbsent(uuid, k -> new HolderState());
        s.skulls = Math.max(0, Math.min(MAX_SKULLS, count));
    }
}
