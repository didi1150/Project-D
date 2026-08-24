package dev.bukkit.ability.behavior;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import dev.bukkit.DMain;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;

/**
 * Per-holder behavior for Blade Dance (IRON_SWORD).
 * Passive charge: 1 blade / 5s, up to 5, floating ItemDisplay swords orbit chest.
 * Active (right-click cone) is handled by BukkitBladeDanceEffect — this behavior
 * owns the stack/orbit state and exposes consume helpers.
 * ItemDisplays 0.6 scale, FIXED billboard, point straight up while orbiting,
 * persist across hotbar swaps until quit or consumed.
 */
public class BladeDanceBehavior implements AbilityBehavior {

    public static final String ITEM_ID = "BLADE_DANCE";
    public static final int MAX_BLADES = 5;
    public static final long GEN_INTERVAL_MS = 5000L;
    public static final double RADIUS = 1.4;
    public static final double Y_OFFSET = 1.0; // chest
    public static final float SPIN_DEG_PER_TICK = 10f;
    public static final float SCALE = 0.6f;
    public static final double DAMAGE_BASE = 20.0;
    public static final double DAMAGE_AD_RATIO = 0.3;
    public static final double DAMAGE_LETH_RATIO = 0.2;

    // global orbit task that keeps ItemDisplays spinning even while blade dance is not held (persist)
    private static org.bukkit.scheduler.BukkitTask GLOBAL_ORBIT_TASK = null;

    static final class HolderState {
        int blades = 0;
        long lastGenMs = 0;
        final List<ItemDisplay> displays = new ArrayList<>();
        float spinYaw = 0f;
    }

    private static final Map<UUID, HolderState> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REFCNT = new ConcurrentHashMap<>();

    private ActiveAbility ctx;

    public BladeDanceBehavior(ActiveAbility ctx) {
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
        if (GLOBAL_ORBIT_TASK != null) return;
        Plugin plugin = resolvePlugin();
        if (plugin == null) return;
        GLOBAL_ORBIT_TASK = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // tick all holders' orbits regardless of equipped state (persist)
            for (Map.Entry<UUID, HolderState> en : STATES.entrySet()) {
                UUID uuid = en.getKey();
                HolderState st = en.getValue();
                if (st.displays.isEmpty()) continue;
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || p.isDead()) continue;
                // orbit tick
                st.spinYaw = (st.spinYaw + SPIN_DEG_PER_TICK) % 360f;
                Location center = p.getLocation().clone().add(0, Y_OFFSET, 0);
                World world = p.getWorld();
                if (world == null) continue;
                int count = st.displays.size();
                for (int i = 0; i < count; i++) {
                    ItemDisplay d = st.displays.get(i);
                    if (d == null || !d.isValid()) continue;
                    double ang = Math.toRadians(st.spinYaw + i * 360.0 / count);
                    Location loc = new Location(world,
                            center.getX() + Math.cos(ang) * RADIUS,
                            center.getY(),
                            center.getZ() + Math.sin(ang) * RADIUS);
                    // blades point straight upwards
                    loc.setYaw(0f);
                    loc.setPitch(0f);
                    boolean snap = false;
                    try { double distSq = d.getLocation().distanceSquared(loc); snap = distSq > 6.25; } catch (Exception ignored) {}
                    if (snap) d.setTeleportDuration(0);
                    d.teleport(loc);
                    if (snap) d.setTeleportDuration(1);
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
        // ensure lastGenMs initialized
        if (hs.lastGenMs == 0) hs.lastGenMs = System.currentTimeMillis();
        ensureGlobalOrbitTask();
        boolean isFirst = REFCNT.getOrDefault(uuid, 0) == 1;
        if (isFirst) {
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onTick, TickEvent.class));
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onQuit, PlayerQuitEvent.class));
        }
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        UUID uuid = ctx.getHolder().getUuid();
        int cnt = REFCNT.getOrDefault(uuid, 1) - 1;
        if (cnt <= 0) {
            REFCNT.remove(uuid);
            // persist: keep STATES and displays alive, like HunterBow HolderState
            // do NOT despawn; HUD remains until quit/consumed
        } else {
            REFCNT.put(uuid, cnt);
        }
    }

    // ============================================================== TICK

    private void onTick(TickEvent e) {
        UUID uuid = ctx.getHolder().getUuid();
        HolderState state = STATES.get(uuid);
        if (state == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline() || player.isDead()) return;
        // generate charge (only while blade dance is held — pause otherwise)
        long now = System.currentTimeMillis();
        if (state.blades < MAX_BLADES && now - state.lastGenMs >= GEN_INTERVAL_MS) {
            if (isBladeDanceEquipped(player)) {
                state.blades++;
                state.lastGenMs = now;
                spawnOrbital(player, state);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f + state.blades * 0.12f);
                player.spawnParticle(Particle.ENCHANTED_HIT, player.getLocation().clone().add(0, 1.0, 0), 5, 0.3, 0.3, 0.3, 0.02);
            }
        }
        // orbit is now handled by global task; no need to orbit here (kept for generation-only tick)
    }

    private void spawnOrbital(Player player, HolderState state) {
        World world = player.getWorld();
        if (world == null) return;
        Location center = player.getLocation().clone().add(0, Y_OFFSET, 0);
        double angle = Math.toRadians(state.spinYaw + (state.displays.size() * 360.0 / MAX_BLADES));
        Location loc = new Location(world,
                center.getX() + Math.cos(angle) * RADIUS,
                center.getY(),
                center.getZ() + Math.sin(angle) * RADIUS);
        // blades point straight upwards
        loc.setYaw(0f);
        loc.setPitch(0f);
        ItemDisplay disp = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.IRON_SWORD));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setViewRange(32);
            d.setShadowStrength(0f);
            // point straight upwards, scale 0.6
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(SCALE, SCALE, SCALE),
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            ));
        });
        state.displays.add(disp);
    }

    private void orbitTick(Player player, HolderState state) {
        // legacy single-holder orbit (now superseded by global task, kept for fallback)
        if (state.displays.isEmpty()) return;
        World world = player.getWorld();
        if (world == null) return;
        state.spinYaw = (state.spinYaw + SPIN_DEG_PER_TICK) % 360f;
        Location center = player.getLocation().clone().add(0, Y_OFFSET, 0);
        int count = state.displays.size();
        for (int i = 0; i < count; i++) {
            ItemDisplay d = state.displays.get(i);
            if (d == null || !d.isValid()) continue;
            double ang = Math.toRadians(state.spinYaw + i * 360.0 / count);
            Location loc = new Location(world,
                    center.getX() + Math.cos(ang) * RADIUS,
                    center.getY(),
                    center.getZ() + Math.sin(ang) * RADIUS);
            loc.setYaw(0f);
            loc.setPitch(0f);
            boolean snap = false;
            try {
                double distSq = d.getLocation().distanceSquared(loc);
                snap = distSq > 6.25;
            } catch (Exception ignored) {}
            if (snap) d.setTeleportDuration(0);
            d.teleport(loc);
            if (snap) d.setTeleportDuration(1);
        }
    }

    // ---- helpers exposed for active cone effect ----
    /**
     * Snapshot and consume all stacked blades for a cone launch.
     * Resets stack count and generation timer.
     * @return list of orbiting ItemDisplays to be repurposed as projectiles, or empty if none.
     */
    public static List<ItemDisplay> consumeBlades(UUID holderUuid) {
        HolderState st = STATES.get(holderUuid);
        if (st == null || st.blades <= 0 || st.displays.isEmpty()) return List.of();
        List<ItemDisplay> list = new ArrayList<>(st.displays);
        st.displays.clear();
        st.blades = 0;
        st.lastGenMs = System.currentTimeMillis();
        return list;
    }

    public static int getBladeCount(UUID holderUuid) {
        HolderState st = STATES.get(holderUuid);
        return st == null ? 0 : st.blades;
    }

    private void onQuit(PlayerQuitEvent e) {
        if (!e.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid())) return;
        HolderState state = STATES.remove(ctx.getHolder().getUuid());
        if (state != null) {
            for (ItemDisplay d : state.displays) {
                try { if (d != null && d.isValid()) d.remove(); } catch (Exception ignored) {}
            }
            state.displays.clear();
        }
        REFCNT.remove(ctx.getHolder().getUuid());
    }

    public static boolean isBladeDance(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        return ITEM_ID.equals(id);
    }

    private boolean isBladeDanceEquipped(Player player) {
        // persist check: actual equipped main hand must be blade dance to trigger release/generation gate
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isBladeDance(main)) return true;
        // also allow offhand? spec says iron_sword main hand; keep main only
        return false;
    }

    private static Plugin resolvePlugin() {
        try { return DMain.getInstance(); } catch (Exception e) { return null; }
    }

    // ---- pure logic for tests ----
    public static double calculateBladeDamage(double attackDamage, double lethality) {
        return DAMAGE_BASE + attackDamage * DAMAGE_AD_RATIO + lethality * DAMAGE_LETH_RATIO;
    }

    // For testing / external inspect
    public static int getBlades(UUID uuid) {
        HolderState s = STATES.get(uuid);
        return s == null ? 0 : s.blades;
    }

    public static void setBladesForTest(UUID uuid, int blades) {
        HolderState s = STATES.computeIfAbsent(uuid, k -> new HolderState());
        s.blades = Math.max(0, Math.min(MAX_BLADES, blades));
    }

    public static void clearState(UUID uuid) {
        HolderState s = STATES.remove(uuid);
        if (s != null) for (ItemDisplay d : s.displays) try { if (d != null && d.isValid()) d.remove(); } catch (Exception ignored) {}
    }
}
