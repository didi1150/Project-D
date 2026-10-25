package dev.bukkit.entity.boss.stage;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.bukkit.entity.BukkitDisplayEntityRegistry;
import dev.bukkit.entity.boss.BukkitBossEntity;
import dev.bukkit.entity.boss.NoTargetStrategy;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.game.boss.stalactite.StalactiteConfig;
import dev.core.entity.boss.BossStage;
import dev.core.entity.boss.BossStageContext;
import dev.core.entity.boss.BossStageType;
import dev.core.entity.boss.BossStrategyRegistry;
import dev.core.entity.boss.RPGBossEntity;
import dev.core.entity.strategy.NoopMovementStrategy;
import dev.core.event.EventAction;
import dev.core.event.impl.BossEncounterEndedEvent;
import dev.core.storage.config.ConfigSection;
import dev.core.utils.AdventureText;
import dev.core.utils.ColorCodes;
import io.papermc.paper.event.player.AsyncChatEvent;

public class BossGrowStageType implements BossStageType {

    @Override
    public String getType() {
        return "GROW";
    }

    @Override
    public BossStage build(ConfigSection params, BossStrategyRegistry strategies) {
        String id = params.getString("id", "grow");
        int introTicks = Math.max(0, params.getInt("intro-ticks", 80));
        double buriedDepth = Math.max(0, params.getDouble("buried-depth", 2.0));
        double halfHeight = Math.max(0, params.getDouble("half-height", 8.0));
        double fullHeight = Math.max(halfHeight, params.getDouble("full-height", 16.0));
        double baseRate = Math.max(0, params.getDouble("ascent-base-rate", 0.15));
        double maxRate = Math.max(baseRate, params.getDouble("ascent-max-rate", 1.5));
        int pillarCount = Math.max(1, params.getInt("pillar-count", 4));
        double pillarMaxHp = Math.max(1, params.getDouble("pillar-max-hp", 100.0));
        String wipeStageId = params.getString("wipe-stage", "wipe");
        Material groundMaterial = Material.matchMaterial(params.getString("ground-material", "BLACK_CONCRETE"));
        if (groundMaterial == null) {
            groundMaterial = Material.BLACK_CONCRETE;
        }
        double stalactitePushPercent = clampPercent(
                params.getDouble("stalactite-push-percent", StalactiteConfig.PUSH_AMOUNT_PERCENT));
        double stalactiteExplosionPercent = clampPercent(
                params.getDouble("stalactite-explosion-percent", StalactiteConfig.EXPLOSION_PERCENT));
        return new BossGrowStage(id, introTicks, buriedDepth, halfHeight, fullHeight, baseRate, maxRate,
                pillarCount, pillarMaxHp, wipeStageId, groundMaterial, stalactitePushPercent,
                stalactiteExplosionPercent);
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    public static boolean pushDown(RPGBossEntity boss, double fractionDelta) {
        if (boss.getStageState() instanceof GrowState state) {
            state.pushDown(fractionDelta);
            return true;
        }
        return false;
    }

    /**
     * Pushes a GROW boss down using the percentage configured on its stage. Only
     * applies once the intro is complete and the ascent is still active.
     */
    public static boolean stalactitePushDown(RPGBossEntity boss) {
        GrowState state = growState(boss);
        if (state == null || !state.introComplete || state.ended || state.wipeTransition) {
            return false;
        }
        state.pushDown(state.stalactitePushFraction);
        return true;
    }

    public static double stalactitePushPercent(RPGBossEntity boss) {
        GrowState state = growState(boss);
        return state != null ? state.getStalactitePushPercent() : StalactiteConfig.PUSH_AMOUNT_PERCENT;
    }

    public static double stalactiteExplosionPercent(RPGBossEntity boss) {
        GrowState state = growState(boss);
        return state != null ? state.getStalactiteExplosionPercent() : StalactiteConfig.EXPLOSION_PERCENT;
    }

    public static boolean damagePillars(RPGBossEntity boss, double amount) {
        if (boss.getStageState() instanceof GrowState state) {
            state.damagePillars(amount);
            return true;
        }
        return false;
    }

    public static GrowState growState(RPGBossEntity boss) {
        return boss.getStageState() instanceof GrowState state ? state : null;
    }

    public static final class GrowState {

        Location base;
        double startY;
        double currentY;
        double heightFraction;
        boolean introComplete;
        boolean ended;
        boolean wipeTransition;
        long lastTickMs;
        double pillarCurrentHp;
        double pillarMaxHp;
        double stalactitePushFraction = StalactiteConfig.PUSH_AMOUNT_PERCENT / 100.0;
        double stalactiteExplosionFraction = StalactiteConfig.EXPLOSION_PERCENT / 100.0;
        UUID crackDisplayId;
        EventAction<AsyncChatEvent> chatAction;

        public double getHeightFraction() {
            return heightFraction;
        }

        public double getStalactitePushPercent() {
            return stalactitePushFraction * 100.0;
        }

        public double getStalactiteExplosionPercent() {
            return stalactiteExplosionFraction * 100.0;
        }

        public double getPillarHealthFraction() {
            return pillarMaxHp <= 0 ? 0 : pillarCurrentHp / pillarMaxHp;
        }

        public double getPillarCurrentHp() {
            return pillarCurrentHp;
        }

        public double getPillarMaxHp() {
            return pillarMaxHp;
        }

        public void pushDown(double fractionDelta) {
            if (fractionDelta <= 0) {
                return;
            }
            heightFraction = Math.max(0, heightFraction - fractionDelta);
        }

        public void damagePillars(double amount) {
            if (amount <= 0) {
                return;
            }
            pillarCurrentHp = Math.max(0, pillarCurrentHp - amount);
        }

        public void setPillarHp(double hp) {
            pillarCurrentHp = Math.max(0, Math.min(pillarMaxHp, hp));
        }

        public void setPillarHealthFraction(double fraction) {
            pillarCurrentHp = pillarMaxHp * SealedGrowMath.clamp01(fraction);
        }
    }
}

final class SealedGrowMath {

    private SealedGrowMath() {
    }

    static double smoothstep(double progress) {
        double t = clamp01(progress);
        return t * t * (3 - 2 * t);
    }

    static double introY(double startY, double targetY, double progress) {
        return startY + (targetY - startY) * smoothstep(progress);
    }

    static double ascentSpeed(double baseRate, double maxRate, double currentHp, double maxHp) {
        if (maxHp <= 0) {
            return maxRate;
        }
        double ratio = clamp01(currentHp / maxHp);
        return baseRate + (maxRate - baseRate) * (1 - ratio);
    }

    static double nextFraction(double fraction, double speed, double deltaSeconds, double fullHeight) {
        if (fullHeight <= 0) {
            return 1;
        }
        return clamp01(fraction + speed * deltaSeconds / fullHeight);
    }

    static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}

final class BossGrowStage implements BossStage {

    private final String id;
    private final int introTicks;
    private final double buriedDepth;
    private final double halfHeight;
    private final double fullHeight;
    private final double baseRate;
    private final double maxRate;
    private final int pillarCount;
    private final double pillarMaxHp;
    private final String wipeStageId;
    private final Material groundMaterial;
    private final double stalactitePushPercent;
    private final double stalactiteExplosionPercent;

    BossGrowStage(String id, int introTicks, double buriedDepth, double halfHeight, double fullHeight,
            double baseRate, double maxRate, int pillarCount, double pillarMaxHp, String wipeStageId,
            Material groundMaterial, double stalactitePushPercent, double stalactiteExplosionPercent) {
        this.id = id;
        this.introTicks = introTicks;
        this.buriedDepth = buriedDepth;
        this.halfHeight = halfHeight;
        this.fullHeight = fullHeight;
        this.baseRate = baseRate;
        this.maxRate = maxRate;
        this.pillarCount = pillarCount;
        this.pillarMaxHp = pillarMaxHp;
        this.wipeStageId = wipeStageId;
        this.groundMaterial = groundMaterial;
        this.stalactitePushPercent = stalactitePushPercent;
        this.stalactiteExplosionPercent = stalactiteExplosionPercent;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void onEnter(BossStageContext context) {
        if (!(context.getBoss() instanceof BukkitBossEntity boss)
                || boss.getLivingEntity().isEmpty()) {
            context.broadcast("§c[Grow] Boss entity is not available.");
            return;
        }

        LivingEntity living = boss.getLivingEntity().get();
        Location base = living.getLocation().clone();
        World world = base.getWorld();
        if (world == null) {
            context.broadcast("§c[Grow] Boss has no world.");
            return;
        }

        BossGrowStageType.GrowState state = new BossGrowStageType.GrowState();
        state.base = base;
        state.startY = base.getY() - buriedDepth;
        state.currentY = state.startY;
        state.heightFraction = 0;
        state.lastTickMs = System.currentTimeMillis();
        state.pillarMaxHp = pillarCount * pillarMaxHp;
        state.pillarCurrentHp = state.pillarMaxHp;
        state.stalactitePushFraction = stalactitePushPercent / 100.0;
        state.stalactiteExplosionFraction = stalactiteExplosionPercent / 100.0;
        state.crackDisplayId = spawnCrackDisplay(world, base);
        context.setState(state);

        boss.setMovementStrategy(new NoopMovementStrategy());
        boss.setTargetingStrategy(new NoTargetStrategy());
        boss.setAttackPattern((mob, target, now) -> {
        });
        boss.setDamageImmune(true);
        living.setInvulnerable(true);
        living.setAI(false);
        living.setGravity(false);

        Location start = base.clone();
        start.setY(state.startY);
        living.teleport(start);

        boss.setDisplayOverride(0f, label(boss, 0));
        context.broadcast("§4The sealing chamber trembles... something is waking below.");
        world.playSound(base, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 3.0f, 0.4f);
        world.playSound(base, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.6f);

        state.chatAction = new EventAction<>(event -> handleDebugChat(context, state, event), AsyncChatEvent.class);
        BukkitEventBus.getInstance().subscribe(state.chatAction);
    }

    @Override
    public void tick(BossStageContext context, long now) {
        BossGrowStageType.GrowState state = (BossGrowStageType.GrowState) context.getState();
        if (state == null || state.ended) {
            return;
        }
        if (!(context.getBoss() instanceof BukkitBossEntity boss)
                || boss.getLivingEntity().isEmpty()) {
            return;
        }
        LivingEntity living = boss.getLivingEntity().get();
        World world = living.getWorld();

        long deltaMs = Math.max(0, now - state.lastTickMs);
        state.lastTickMs = now;
        double deltaSeconds = Math.min(0.25, deltaMs / 1000.0);
        double elapsedTicks = Math.max(0, now - context.getStageStartTime()) / 50.0;

        if (!state.introComplete) {
            tickIntro(context, boss, living, world, state, elapsedTicks);
            return;
        }

        boolean allDestroyed = state.pillarMaxHp > 0 && state.pillarCurrentHp <= 0;
        if (allDestroyed) {
            triggerWipe(context, state);
            return;
        }

        if (state.heightFraction <= 0.0) {
            state.ended = true;
            teleportY(living, state.base, state.base.getY());
            world.spawnParticle(Particle.EXPLOSION_EMITTER, state.base.clone().add(0, 1, 0), 3, 1.5, 1.0, 1.5, 0);
            world.playSound(state.base, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0f, 0.6f);
            context.broadcast("§aThe Sealed Entity is forced back beneath the earth.");
            context.endEncounter(BossEncounterEndedEvent.Outcome.VICTORY);
            return;
        }

        double speed = SealedGrowMath.ascentSpeed(baseRate, maxRate, state.pillarCurrentHp, state.pillarMaxHp);
        state.heightFraction = SealedGrowMath.nextFraction(state.heightFraction, speed, deltaSeconds, fullHeight);
        double y = state.base.getY() + state.heightFraction * fullHeight;
        state.currentY = y;
        teleportY(living, state.base, y);
        boss.setDisplayOverride((float) state.heightFraction, label(boss, state.heightFraction));

        if (elapsedTicks % 4 == 0) {
            world.spawnParticle(Particle.BLOCK_CRUMBLE, state.base.clone().add(0, 0.2, 0), 6, 1.2, 0.1, 1.2, 0.02,
                    groundMaterial.createBlockData());
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, new Location(world, state.base.getX(), y,
                    state.base.getZ()), 3, 0.8, 0.4, 0.8, 0.01);
        }

        if (state.heightFraction >= 1.0) {
            triggerWipe(context, state);
        }
    }

    private void triggerWipe(BossStageContext context, BossGrowStageType.GrowState state) {
        if (wipeStageId != null && !wipeStageId.isBlank() && context.getStageManager().hasStage(wipeStageId)) {
            state.wipeTransition = true;
        } else {
            state.ended = true;
            context.endEncounter(BossEncounterEndedEvent.Outcome.WIPE);
        }
    }

    private void tickIntro(BossStageContext context, BukkitBossEntity boss, LivingEntity living, World world,
            BossGrowStageType.GrowState state, double elapsedTicks) {
        double progress = introTicks <= 0 ? 1.0 : Math.min(1.0, elapsedTicks / introTicks);
        double targetY = state.base.getY() + halfHeight;
        double y = SealedGrowMath.introY(state.startY, targetY, progress);
        state.currentY = y;
        state.heightFraction = SealedGrowMath.clamp01((y - state.base.getY()) / fullHeight);
        teleportY(living, state.base, y);
        boss.setDisplayOverride((float) state.heightFraction, label(boss, state.heightFraction));

        if (elapsedTicks % 2 == 0) {
            world.spawnParticle(Particle.BLOCK_CRUMBLE, state.base.clone().add(0, 0.2, 0), 18, 2.0, 0.2, 2.0,
                    0.04, groundMaterial.createBlockData());
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, state.base.clone().add(0, 0.6, 0), 6, 1.6, 0.3, 1.6,
                    0.02);
        }
        if (elapsedTicks % 20 == 0) {
            world.playSound(state.base, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2.0f, 0.3f);
        }

        if (progress >= 1.0) {
            state.introComplete = true;
            state.heightFraction = SealedGrowMath.clamp01(halfHeight / fullHeight);
            state.currentY = targetY;
            boss.setDisplayOverride((float) state.heightFraction, label(boss, state.heightFraction));
            context.broadcast("§4The Sealed Entity bursts through the floor!");
            world.playSound(state.base, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.6f);
            if (state.crackDisplayId != null) {
                BukkitDisplayEntityRegistry.getInstance().removeDisplayEntity(state.crackDisplayId);
                state.crackDisplayId = null;
            }
        }
    }

    @Override
    public boolean shouldTransition(BossStageContext context, long now) {
        BossGrowStageType.GrowState state = (BossGrowStageType.GrowState) context.getState();
        return state != null && state.wipeTransition;
    }

    @Override
    public String getNextStageId(BossStageContext context) {
        return wipeStageId;
    }

    @Override
    public void onExit(BossStageContext context) {
        BossGrowStageType.GrowState state = (BossGrowStageType.GrowState) context.getState();
        if (state != null && state.chatAction != null) {
            BukkitEventBus.getInstance().unsubscribe(state.chatAction);
            state.chatAction = null;
        }
        if (state != null && state.crackDisplayId != null) {
            BukkitDisplayEntityRegistry.getInstance().removeDisplayEntity(state.crackDisplayId);
            state.crackDisplayId = null;
        }
        if (context.getBoss() instanceof BukkitBossEntity boss) {
            boss.clearDisplayOverride();
        }
    }

    private static final String DEBUG_PREFIX = "!pillars";

    private void handleDebugChat(BossStageContext context, BossGrowStageType.GrowState state, AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("projectd.boss.debug")) {
            return;
        }
        if (state.base == null || state.base.getWorld() == null
                || !state.base.getWorld().equals(player.getWorld())) {
            return;
        }
        String raw = AdventureText.plain(event.message());
        if (raw == null) {
            return;
        }
        String message = raw.trim();
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(DEBUG_PREFIX)) {
            return;
        }
        if (message.length() > DEBUG_PREFIX.length()
                && !Character.isWhitespace(message.charAt(DEBUG_PREFIX.length()))) {
            return;
        }
        String args = message.substring(DEBUG_PREFIX.length()).trim();
        event.setCancelled(true);
        context.getStageManager().getScheduler()
                .runTaskLater(() -> applyDebugChat(context, state, player, args), 0L);
    }

    private void applyDebugChat(BossStageContext context, BossGrowStageType.GrowState state, Player player,
            String args) {
        if (!player.isOnline() || state.ended || BossGrowStageType.growState(context.getBoss()) != state) {
            return;
        }
        if (!(context.getBoss() instanceof BukkitBossEntity boss) || boss.getLivingEntity().isEmpty()
                || !boss.getLivingEntity().get().getWorld().equals(player.getWorld())) {
            return;
        }
        if (args.isBlank()) {
            reportPillars(player, state);
            return;
        }

        String[] parts = args.split("\\s+");
        String action = parts[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
            case "reset" -> {
                state.setPillarHealthFraction(1.0);
                reportPillars(player, state);
            }
            case "damage" -> {
                if (parts.length < 2) {
                    pillarsUsage(player);
                    return;
                }
                state.damagePillars(Double.parseDouble(parts[1]));
                reportPillars(player, state);
            }
            case "hp", "set" -> {
                if (parts.length < 2) {
                    pillarsUsage(player);
                    return;
                }
                state.setPillarHp(Double.parseDouble(parts[1]));
                reportPillars(player, state);
            }
            default -> {
                state.setPillarHealthFraction(Double.parseDouble(action) / 100.0);
                reportPillars(player, state);
            }
            }
        } catch (NumberFormatException e) {
            pillarsUsage(player);
        }
    }

    private static void reportPillars(Player player, BossGrowStageType.GrowState state) {
        double current = state.getPillarCurrentHp();
        double max = state.getPillarMaxHp();
        int percent = (int) Math.round(state.getPillarHealthFraction() * 100.0);
        player.sendMessage("§a[Pillars] §e" + formatPillarValue(current) + "§7/§e" + formatPillarValue(max)
                + " §7(" + percent + "%)");
    }

    private static void pillarsUsage(Player player) {
        player.sendMessage("§e[Pillars] Usage: §f!pillars §7| §f!pillars <0-100> §7| §f!pillars hp <amount> §7| "
                + "§f!pillars damage <amount> §7| §f!pillars reset");
    }

    private static String formatPillarValue(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private UUID spawnCrackDisplay(World world, Location base) {
        BlockData blockData = groundMaterial.createBlockData();
        BlockDisplay display = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(
                base.clone().add(0, 0.02, 0), BlockDisplay.class, d -> {
                    d.setBlock(blockData);
                    d.setPersistent(false);
                    d.setInvulnerable(true);
                    d.setGravity(false);
                    d.setBrightness(new Display.Brightness(0, 0));
                    d.setInterpolationDuration(0);
                    d.setTransformation(new Transformation(new Vector3f(-2.5f, 0, -2.5f), new Quaternionf(),
                            new Vector3f(5f, 0.03f, 5f), new Quaternionf()));
                });
        return display.getUniqueId();
    }

    private static void teleportY(LivingEntity living, Location base, double y) {
        Location target = base.clone();
        target.setY(y);
        living.teleport(target);
    }

    private static String label(BukkitBossEntity boss, double fraction) {
        int percent = (int) Math.round(SealedGrowMath.clamp01(fraction) * 100);
        return ColorCodes.translate(boss.getName()) + " §7— §e" + percent + "%";
    }

}
