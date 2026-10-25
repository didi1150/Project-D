package dev.bukkit.entity.boss.stage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import dev.bukkit.DMain;
import dev.bukkit.entity.boss.BukkitBossEntity;
import dev.bukkit.entity.boss.NoTargetStrategy;
import dev.bukkit.game.boss.ritual.SealedWipeRitual;
import dev.bukkit.game.states.BossState;
import dev.core.entity.boss.BossStage;
import dev.core.entity.boss.BossStageContext;
import dev.core.entity.boss.BossStageType;
import dev.core.entity.boss.BossStrategyRegistry;
import dev.core.entity.strategy.NoopMovementStrategy;
import dev.core.event.impl.BossEncounterEndedEvent;
import dev.core.game.GameStateController;
import dev.core.storage.config.ConfigSection;
import dev.core.utils.ColorCodes;

public class WipeStageType implements BossStageType {

    @Override
    public String getType() {
        return "WIPE";
    }

    @Override
    public BossStage build(ConfigSection params, BossStrategyRegistry strategies) {
        String id = params.getString("id", "wipe");
        int durationTicks = Math.max(1, params.getInt("duration-ticks", 240));
        int killTick = clamp(params.getInt("kill-ticks", 200), 1, durationTicks);
        int pullTicks = Math.max(0, params.getInt("pull-ticks", 60));
        int drawTicks = Math.max(0, params.getInt("draw-ticks", 80));
        int riseStartTick = Math.max(0, params.getInt("rise-start-ticks", 40));
        int riseTicks = Math.max(1, params.getInt("rise-ticks", 70));
        int gripStartTick = clamp(params.getInt("grip-start-ticks", 140), 0, Math.max(0, killTick - 1));
        int gripTicks = Math.max(1, params.getInt("grip-ticks", 50));
        double radius = Math.max(2, params.getDouble("radius", 8));
        double hoverHeight = Math.max(1, params.getDouble("hover-height", 6.5));
        double handDepth = Math.max(1, params.getDouble("hand-depth", 3));
        double handHeight = Math.max(2, params.getDouble("hand-height", 6));
        boolean killPlayers = params.getBoolean("kill-players", true);
        String message = params.getString("message", "§4The seal shatters. The Sealed Hand claims the party.");
        Material handMaterial = Material.matchMaterial(params.getString("hand-material", "SCULK"));
        if (handMaterial == null) {
            handMaterial = Material.SCULK;
        }

        return new WipeStage(id, durationTicks, killTick, pullTicks, drawTicks, riseStartTick, riseTicks,
                gripStartTick, gripTicks, radius, hoverHeight, handDepth, handHeight, killPlayers, message,
                handMaterial);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

final class WipeStage implements BossStage {

    private final String id;
    private final int durationTicks;
    private final int killTick;
    private final int pullTicks;
    private final int drawTicks;
    private final int riseStartTick;
    private final int riseTicks;
    private final int gripStartTick;
    private final int gripTicks;
    private final double radius;
    private final double hoverHeight;
    private final double handDepth;
    private final double handHeight;
    private final boolean killPlayers;
    private final String message;
    private final Material handMaterial;

    WipeStage(String id, int durationTicks, int killTick, int pullTicks, int drawTicks, int riseStartTick,
            int riseTicks, int gripStartTick, int gripTicks, double radius, double hoverHeight, double handDepth,
            double handHeight, boolean killPlayers, String message, Material handMaterial) {
        this.id = id;
        this.durationTicks = durationTicks;
        this.killTick = killTick;
        this.pullTicks = pullTicks;
        this.drawTicks = drawTicks;
        this.riseStartTick = riseStartTick;
        this.riseTicks = riseTicks;
        this.gripStartTick = gripStartTick;
        this.gripTicks = gripTicks;
        this.radius = radius;
        this.hoverHeight = hoverHeight;
        this.handDepth = handDepth;
        this.handHeight = handHeight;
        this.killPlayers = killPlayers;
        this.message = message;
        this.handMaterial = handMaterial;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void onEnter(BossStageContext context) {
        if (!(context.getBoss() instanceof BukkitBossEntity boss) || boss.getLivingEntity().isEmpty()) {
            context.broadcast("§c[Wipe] Boss entity is not available.");
            return;
        }

        LivingEntity living = boss.getLivingEntity().get();
        World world = living.getWorld();
        Location center = boss.getSpawnLocation();
        if (center == null) {
            center = living.getLocation().clone();
        }
        if (world == null || center.getWorld() == null) {
            context.broadcast("§c[Wipe] Boss has no world.");
            return;
        }

        boss.setMovementStrategy(new NoopMovementStrategy());
        boss.setTargetingStrategy(new NoTargetStrategy());
        boss.setAttackPattern((mob, target, now) -> {
        });
        boss.setDamageImmune(true);
        living.setInvulnerable(true);
        living.setAI(false);
        living.setGravity(false);
        boss.setDisplayOverride(1f, ColorCodes.translate(boss.getName()) + " §7— §4SEAL BROKEN");

        boolean activeEncounter = killPlayers && isActiveEncounter(boss);
        SealedWipeRitual.Settings settings = new SealedWipeRitual.Settings(durationTicks, killTick, pullTicks,
                drawTicks, riseStartTick, riseTicks, gripStartTick, gripTicks, radius, hoverHeight, handDepth,
                handHeight, activeEncounter, handMaterial);
        SealedWipeRitual ritual = new SealedWipeRitual(world, center, settings);
        ritual.start();
        context.setState(new WipeState(ritual));
        context.broadcast(message);
    }

    @Override
    public void tick(BossStageContext context, long now) {
        WipeState state = (WipeState) context.getState();
        if (state == null || state.ended) {
            return;
        }
        long tick = Math.max(0, (now - context.getStageStartTime()) / 50);
        state.ritual.tick(tick);
        if (tick >= durationTicks) {
            state.ended = true;
            state.ritual.stop();
            context.endEncounter(BossEncounterEndedEvent.Outcome.WIPE);
        }
    }

    @Override
    public void onExit(BossStageContext context) {
        WipeState state = (WipeState) context.getState();
        if (state != null) {
            state.ritual.stop();
        }
        if (context.getBoss() instanceof BukkitBossEntity boss) {
            boss.clearDisplayOverride();
        }
    }

    private static boolean isActiveEncounter(BukkitBossEntity boss) {
        try {
            GameStateController controller = DMain.getInstance().getGameStateController();
            return controller != null && controller.getCurrentState() instanceof BossState state
                    && boss.getUuid().equals(state.getBossUuid());
        } catch (Exception ignored) {
            return false;
        }
    }

    static final class WipeState {

        private final SealedWipeRitual ritual;
        private boolean ended;

        WipeState(SealedWipeRitual ritual) {
            this.ritual = ritual;
        }
    }
}
