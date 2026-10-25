package dev.bukkit.game.boss.stalactite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.bukkit.DMain;
import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.entity.BukkitDisplayEntityRegistry;
import dev.bukkit.entity.BukkitEntityMetadata;
import dev.bukkit.entity.boss.BukkitBossEntity;
import dev.bukkit.entity.boss.stage.BossGrowStageType;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.item.BowArrowManager;
import dev.bukkit.status.BukkitStatusEffectManager;
import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.entity.boss.RPGBossEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.impl.RPGEntityDamageEvent;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;
import dev.core.utils.AdventureText;

/**
 * Bukkit stalactite entity — owns growing / active / falling lifecycle, hitbox
 * (Interaction + Slime collider), display, healthbar, highlights and damage
 * handling. Manager only spawns/despawns instances via factory.
 */
public class BukkitStalactiteEntity extends RPGEntity {

    public static final NamespacedKey STALACTITE_INDEX_KEY = new NamespacedKey("project_d", "stalactite_idx");
    public static final String COLLIDER_METADATA = "STALACTITE_COLLIDER";

    private final Location ceiling;
    private final Location ground;
    private final World world;
    /**
     * Pivot from which the stalactite visually grows — top face stays fixed. By
     * default equals ceiling.
     */
    private final Location pivot;
    private final boolean showHighlights;

    public BlockDisplay display;
    public Interaction hitbox;
    public TextDisplay healthbar;
    public Slime collider;

    // highlights
    public BlockDisplay hitboxHighlight;
    public BlockDisplay colliderHighlight;
    public final List<BlockDisplay> taperedHighlights = new ArrayList<>();

    private Lifecycle lifecycle = Lifecycle.GROWING;
    public long stateSinceTick = 0;
    public long readyAtTick = 0;
    public double fallVelocity = 0.0;
    public double currentY;
    public long tickCounter = 0;
    public boolean pendingDespawnAfterImpact = false;
    public int spawnIndex = 0;
    public int fallTicks = 0;
    private boolean impactResolved = false;
    private ImpactReason impactReason = ImpactReason.NONE;
    private Location lastImpactLocation;
    private double lastPushPercent = 0.0;
    private double lastExplosionPercent = 0.0;

    private EventAction<RPGEntityDamageEvent> damageAction;
    private EventAction<RPGEntityDeathEvent> deathAction;
    private final Random random = new Random();

    private BukkitStalactiteEntity(UUID uuid, Location ceiling, Location ground, World world) {
        super(createStatManager(), uuid, "Stalactite", EntityType.MOB, BukkitEffectManager.getInstance(),
                BukkitEventBus.getInstance(), RPGClassType.NONE, BukkitStatusEffectManager.getInstance());
        this.ceiling = ceiling.clone();
        this.ground = ground != null ? ground.clone() : null;
        this.world = world;
        this.pivot = ceiling.clone();
        this.showHighlights = StalactiteConfig.HITBOX_HIGHLIGHT_ENABLED;
        this.currentY = ceiling.getY();
        setHealth(getMaxHealth());
    }

    /** Custom pivot constructor — stalactite grows downward from pivot. */
    private BukkitStalactiteEntity(UUID uuid, Location ceiling, Location ground, World world, Location pivot) {
        super(createStatManager(), uuid, "Stalactite", EntityType.MOB, BukkitEffectManager.getInstance(),
                BukkitEventBus.getInstance(), RPGClassType.NONE, BukkitStatusEffectManager.getInstance());
        this.ceiling = ceiling.clone();
        this.ground = ground != null ? ground.clone() : null;
        this.world = world;
        this.pivot = (pivot != null ? pivot.clone() : ceiling.clone());
        this.pivot.setWorld(world);
        this.showHighlights = StalactiteConfig.HITBOX_HIGHLIGHT_ENABLED;
        this.currentY = this.pivot.getY();
        setHealth(getMaxHealth());
    }

    private BukkitStalactiteEntity(UUID uuid, Location ceiling, Location ground, World world, Location pivot,
            boolean showHighlights) {
        super(createStatManager(), uuid, "Stalactite", EntityType.MOB, BukkitEffectManager.getInstance(),
                BukkitEventBus.getInstance(), RPGClassType.NONE, BukkitStatusEffectManager.getInstance());
        this.ceiling = ceiling.clone();
        this.ground = ground != null ? ground.clone() : null;
        this.world = world;
        this.pivot = (pivot != null ? pivot.clone() : ceiling.clone());
        this.pivot.setWorld(world);
        this.showHighlights = showHighlights;
        this.currentY = this.pivot.getY();
        setHealth(getMaxHealth());
    }

    private static StatManager createStatManager() {
        long now = System.currentTimeMillis();
        Map<StatType, Stat> stats = new HashMap<>();
        double maxHealth = StalactiteConfig.MAX_HEALTH;
        stats.put(StatType.HEALTH_MAX, new CombatStat(StatType.HEALTH_MAX.name(), maxHealth));
        stats.put(StatType.HEALTH_RESOURCE,
                new ResourceStat(StatType.HEALTH_RESOURCE.name(), _ -> maxHealth, _ -> 0.0, now));
        stats.put(StatType.HEALTH_REGEN, new CombatStat(StatType.HEALTH_REGEN.name(), 0));
        stats.put(StatType.ARMOR, new CombatStat(StatType.ARMOR.name(), 0));
        stats.put(StatType.MAGIC_RESIST, new CombatStat(StatType.MAGIC_RESIST.name(), 0));
        stats.put(StatType.ATTACK_DAMAGE, new CombatStat(StatType.ATTACK_DAMAGE.name(), 0));
        stats.put(StatType.CRIT_CHANCE, new CombatStat(StatType.CRIT_CHANCE.name(), 0));
        stats.put(StatType.LETHALITY, new CombatStat(StatType.LETHALITY.name(), 0));
        stats.put(StatType.ARMOR_PENETRATION, new CombatStat(StatType.ARMOR_PENETRATION.name(), 0));
        stats.put(StatType.MOVE_SPEED, new CombatStat(StatType.MOVE_SPEED.name(), 0));
        return new StatManager(stats);
    }

    /**
     * Factory: spawns hitbox Interaction first to bind RPG uuid, then creates
     * entity and initializes remainder at GROWING state (scale 0 -> max).
     */
    public static BukkitStalactiteEntity spawnNew(World world, Location ceiling, Location pivot, int idx, long nowTick,
            boolean showHighlights) {
        Location ce = ceiling.clone();
        ce.setWorld(world);
        Location piv = (pivot != null ? pivot.clone() : ce.clone());
        piv.setWorld(world);
        Location ground = findGroundBelow(world, piv);
        Location hitboxLoc = StalactiteHitbox.computeHitboxLocation(ce);
        Interaction hitbox = world.spawn(hitboxLoc, Interaction.class, inter -> {
            inter.setInteractionWidth(StalactiteConfig.HITBOX_WIDTH);
            inter.setInteractionHeight(StalactiteConfig.HITBOX_HEIGHT);
            inter.setResponsive(true);
            inter.setInvulnerable(false);
            inter.getPersistentDataContainer().set(STALACTITE_INDEX_KEY, PersistentDataType.INTEGER, idx);
            inter.setCustomNameVisible(false);
        });
        BukkitStalactiteEntity entity = new BukkitStalactiteEntity(hitbox.getUniqueId(), ce, ground, world, piv,
                showHighlights);
        entity.hitbox = hitbox;
        entity.spawnIndex = idx;
        entity.initialize(idx, nowTick);
        return entity;
    }

    private static Location findGroundBelow(World world, Location from) {
        int x = from.getBlockX();
        int z = from.getBlockZ();
        int minY = world.getMinHeight();
        for (int y = from.getBlockY() - 1; y >= minY; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(world, from.getX(), y + 1.0, from.getZ(), from.getYaw(), from.getPitch());
            }
        }
        return null;
    }

    private void initialize(int idx, long nowTick) {
        BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();

        // collider
        spawnCollider(idx, StalactiteHitbox.computeColliderLocation(ceiling));

        // BlockDisplay — start hidden (GROWING), pivot-anchored so top stays at pivot
        BlockData tmpData = Material.POINTED_DRIPSTONE.createBlockData();
        try {
            tmpData = StalactiteConfig.DISPLAY_MATERIAL.createBlockData();
        } catch (Exception ignored) {
        }
        final BlockData blockData = tmpData;
        BlockDisplay disp = registry.spawnDisplayEntity(pivot.clone(), BlockDisplay.class, d -> {
            d.setBlock(blockData);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setInterpolationDuration(0);
            Vector3f hidden = StalactiteConfig.HIDDEN_SCALE_DOWNWARD;
            Vector3f trans = displayTranslationForScale(hidden.x, hidden.z);
            d.setTransformation(new Transformation(trans, new Quaternionf(), new Vector3f(hidden), new Quaternionf()));
        });
        this.display = disp;

        TextDisplay text = registry.spawnDisplayEntity(pivot.clone().add(0, 1.5, 0), TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setSeeThrough(false);
            t.setInvulnerable(true);
            t.setGravity(false);
            t.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            t.text(AdventureText.legacy(healthbarText()));
            t.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1f, 1f, 1f),
                    new Quaternionf()));
        });
        this.healthbar = text;

        this.currentY = pivot.getY();
        this.lifecycle = Lifecycle.GROWING;
        this.stateSinceTick = nowTick;
        this.readyAtTick = nowTick + StalactiteConfig.REGROW_TICKS;
        this.tickCounter = nowTick;
        this.fallVelocity = 0;
        this.fallTicks = 0;
        this.pendingDespawnAfterImpact = false;
        this.impactResolved = false;
        this.impactReason = ImpactReason.NONE;
        this.lastImpactLocation = null;
        this.lastPushPercent = 0.0;
        this.lastExplosionPercent = 0.0;
        setDamageImmune(true);
        setAlive(true);
        setHealth(getMaxHealth());

        // highlights — spawn then hide to 0 for growing animation
        spawnHitboxHighlights(registry);
        hideHitboxHighlights();

        // events + registry
        damageAction = new EventAction<>(this::onRPGDamage, RPGEntityDamageEvent.class, EventAction.HIGH_PRIORITY);
        deathAction = new EventAction<>(this::onRPGDeathEvent, RPGEntityDeathEvent.class, EventAction.HIGH_PRIORITY);
        BukkitEventBus.getInstance().subscribe(damageAction);
        BukkitEventBus.getInstance().subscribe(deathAction);
        EntityManager.getInstance().registerEntity(this);
    }

    // ---- lifecycle helpers ----

    public boolean isDamageable() {
        return lifecycle == Lifecycle.ACTIVE;
    }

    public boolean isPendingDespawn() {
        return pendingDespawnAfterImpact;
    }

    public Location getCeiling() {
        return ceiling.clone();
    }

    public Location getGround() {
        return ground != null ? ground.clone() : null;
    }

    public boolean hasGround() {
        return ground != null;
    }

    public Location getPivot() {
        return pivot.clone();
    }

    public World getWorld() {
        return world;
    }

    public ImpactReason getImpactReason() {
        return impactReason;
    }

    public Location getLastImpactLocation() {
        return lastImpactLocation != null ? lastImpactLocation.clone() : null;
    }

    public double getLastPushPercent() {
        return lastPushPercent;
    }

    public double getLastExplosionPercent() {
        return lastExplosionPercent;
    }

    public boolean wasBossImpact() {
        return impactReason == ImpactReason.BOSS;
    }

    // ---- pivot-anchored helpers ----
    /**
     * Translation to keep top face at pivot while scaling down. Entity stays at
     * pivot (center).
     */
    private static Vector3f displayTranslationForScale(float scaleX, float scaleZ) {
        // pivot is center: translation = -scale/2 keeps center at pivot
        float tx = -scaleX / 2.0f;
        float tz = -scaleZ / 2.0f;
        return new Vector3f(tx, 0.0f, tz);
    }

    private static Vector3f displayTranslationForScale(Vector3f scale) {
        return displayTranslationForScale(scale.x, scale.z);
    }

    private static Vector3f hitboxHighlightTranslation(float hw, float hh) {
        float tx = -hw / 2.0f;
        float tz = -hw / 2.0f;
        // top at pivot (0), bottom at -hh -> translationY = -hh
        return new Vector3f(tx, -hh, tz);
    }

    private static Vector3f taperedSegmentTranslation(float segW, float segH, int index) {
        float tx = -segW / 2.0f;
        float tz = -segW / 2.0f;
        float ty = -(index + 1) * segH;
        return new Vector3f(tx, ty, tz);
    }

    private static Vector3f colliderHighlightTranslation(float slimeSize, float progress) {
        float tx = -slimeSize / 2.0f;
        float tz = -slimeSize / 2.0f;
        float centerY = StalactiteConfig.MAX_DISPLAY_SCALE_Y / 2.0f * progress;
        float ty = -centerY - slimeSize / 2.0f;
        return new Vector3f(tx, ty, tz);
    }

    public String healthbarText() {
        if (lifecycle == Lifecycle.GROWING) {
            long remaining = Math.max(0, readyAtTick - tickCounter);
            double secs = remaining / 20.0;
            return "§7Regrowing §e" + String.format("%.1fs", secs);
        }
        if (lifecycle == Lifecycle.FALLING) {
            return "§c§lFalling!";
        }
        double curHealth = getHealth();
        double curMax = getMaxHealth();
        int cur = (int) Math.max(0, Math.round(curHealth));
        int max = (int) Math.round(curMax);
        double healthForColor = curMax > 0 ? curHealth / curMax : 0;
        String color = healthForColor > 0.5 ? "§a" : healthForColor > 0.25 ? "§e" : "§c";
        return color + "❤ " + cur + "§7/" + max;
    }

    private void becomeActive() {
        lifecycle = Lifecycle.ACTIVE;
        stateSinceTick = tickCounter;
        setDamageImmune(false);
        if (display != null && display.isValid()) {
            // pivot-anchored: keep entity at pivot, only scale+translation
            Vector3f downward = StalactiteConfig.maxDisplayScaleDownward();
            Vector3f trans = displayTranslationForScale(downward);
            display.setInterpolationDuration(6);
            display.setTransformation(new Transformation(trans, new Quaternionf(), downward, new Quaternionf()));
        }
        if (healthbar != null && healthbar.isValid()) {
            healthbar.teleport(ceiling.clone().add(0, 1.5, 0));
            healthbar.text(AdventureText.legacy(healthbarText()));
        }
        if (collider != null && collider.isValid()) {
            collider.teleport(StalactiteHitbox.computeColliderLocation(ceiling));
        } else {
            // recreate if missing
            spawnCollider(0, StalactiteHitbox.computeColliderLocation(ceiling));
        }
        if (hitbox != null && hitbox.isValid()) {
            hitbox.teleport(StalactiteHitbox.computeHitboxLocation(ceiling));
        }
        // respawn highlights to full
        BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();
        spawnHitboxHighlights(registry);
        World w = ceiling.getWorld();
        if (w != null) {
            w.playSound(ceiling, Sound.BLOCK_POINTED_DRIPSTONE_PLACE, 1.0f, 1.0f);
            w.spawnParticle(Particle.HAPPY_VILLAGER, ceiling.clone().add(0, -0.5, 0), 6, 0.2, 0.3, 0.2, 0.05);
        }
    }

    @Override
    public void onDeath() {
        if (!isAlive())
            return;
        setAlive(false);
        if (getHealth() > 0)
            setHealth(0);
        RPGEntityDeathEvent event = new RPGEntityDeathEvent(this, getLastAttacker());
        BukkitEventBus.getInstance().sendEvent(event);
        EntityManager.getInstance().markDead(getUuid());
        startFalling();
    }

    private void onRPGDeathEvent(RPGEntityDeathEvent event) {
        if (!event.getTarget().getUuid().equals(getUuid()))
            return;
        if (lifecycle == Lifecycle.FALLING)
            return;
        startFalling();
    }

    private void onRPGDamage(RPGEntityDamageEvent event) {
        if (!event.getTarget().getUuid().equals(getUuid()))
            return;
        if (!isDamageable())
            return;
        Location loc = hitbox != null ? hitbox.getLocation() : pivot;
        World w = loc.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
            w.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2.0f, 1.7f + random.nextFloat() * 0.3f);
        }
        // Health is reduced *after* this event in RPGEntity.dealRPGDamage, so immediate
        // getHealth() is stale.
        // Defer bar update to next tick so it reads post-damage health. This fixes
        // melee where only 2nd hit updated.
        try {
            Bukkit.getScheduler().runTask(DMain.getInstance(), () -> {
                if (healthbar != null && healthbar.isValid())
                    healthbar.text(AdventureText.legacy(healthbarText()));
                if (getHealth() <= 0 && lifecycle == Lifecycle.ACTIVE)
                    startFalling();
            });
        } catch (Exception ignored) {
            // Fallback for tests/headless: try immediate (stale) update
            if (healthbar != null && healthbar.isValid())
                healthbar.text(AdventureText.legacy(healthbarText()));
        }
    }

    @Override
    protected void playHitReaction(RPGEntity attacker) {
        // handled via RPGEntityDamageEvent
    }

    private void startFalling() {
        if (lifecycle == Lifecycle.FALLING)
            return;
        lifecycle = Lifecycle.FALLING;
        stateSinceTick = tickCounter;
        fallVelocity = StalactiteConfig.FALL_INITIAL_VELOCITY;
        fallTicks = 0;
        impactResolved = false;
        impactReason = ImpactReason.NONE;
        lastImpactLocation = null;
        lastPushPercent = 0.0;
        lastExplosionPercent = 0.0;
        currentY = pivot.getY();
        setDamageImmune(true);
        if (healthbar != null && healthbar.isValid())
            healthbar.text(AdventureText.legacy(healthbarText()));
        Location loc = hitbox != null ? hitbox.getLocation() : pivot;
        World w = loc.getWorld();
        if (w != null) {
            w.playSound(loc, Sound.BLOCK_POINTED_DRIPSTONE_BREAK, 1.0f, 0.8f);
            w.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 12, 0.3, 0.4, 0.3, 0.02,
                    Material.POINTED_DRIPSTONE.createBlockData());
        }
    }

    private boolean tryBossImpact(Location displayLoc) {
        RPGBossEntity boss = findGrowBoss();
        if (!(boss instanceof BukkitBossEntity bukkitBoss)) {
            return false;
        }
        LivingEntity living = bukkitBoss.getLivingEntity().orElse(null);
        if (living == null || living.isDead() || !world.equals(living.getWorld())) {
            return false;
        }
        BoundingBox bossBox = living.getBoundingBox();
        if (!StalactiteHitbox.intersectsTaperedStalactite(bossBox, displayLoc,
                StalactiteConfig.BOSS_COLLISION_MARGIN)) {
            return false;
        }
        if (!BossGrowStageType.stalactitePushDown(boss)) {
            return false;
        }
        impactResolved = true;
        impactReason = ImpactReason.BOSS;
        lastImpactLocation = displayLoc.clone();
        lastPushPercent = BossGrowStageType.stalactitePushPercent(boss);
        playBossImpact(displayLoc);
        pendingDespawnAfterImpact = true;
        return true;
    }

    private RPGBossEntity findGrowBoss() {
        for (RPGEntity entity : EntityManager.getInstance().getAliveEntities()) {
            if (entity.getEntityType() != EntityType.BOSS || !(entity instanceof RPGBossEntity boss)) {
                continue;
            }
            if (BossGrowStageType.growState(boss) == null || !(boss instanceof BukkitBossEntity bukkitBoss)) {
                continue;
            }
            LivingEntity living = bukkitBoss.getLivingEntity().orElse(null);
            if (living != null && !living.isDead() && world.equals(living.getWorld())) {
                return boss;
            }
        }
        return null;
    }

    private void playBossImpact(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return;
        }
        w.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 22, 0.5, 0.6, 0.5, 0.08,
                Material.POINTED_DRIPSTONE.createBlockData());
        w.spawnParticle(Particle.CRIT, loc, 12, 0.4, 0.5, 0.4, 0.1);
        w.playSound(loc, Sound.BLOCK_POINTED_DRIPSTONE_BREAK, 1.2f, 0.7f);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
    }

    private void onImpact() {
        if (impactResolved) {
            return;
        }
        impactResolved = true;
        impactReason = ImpactReason.GROUND;
        Location impactLoc = ground != null ? ground.clone()
                : new Location(world, pivot.getX(), currentY, pivot.getZ(), pivot.getYaw(), pivot.getPitch());
        lastImpactLocation = impactLoc.clone();
        World w = impactLoc.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, impactLoc.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.EXPLOSION, impactLoc.clone().add(0, 0.2, 0), 3, 1.5, 0.4, 1.5, 0);
            w.spawnParticle(Particle.CLOUD, impactLoc.clone().add(0, 0.6, 0), 24, 2.5, 1.0, 2.5, 0.08);
            w.spawnParticle(Particle.BLOCK_CRUMBLE, impactLoc, 24, 0.8, 0.3, 0.8, 0.1,
                    Material.POINTED_DRIPSTONE.createBlockData());
            w.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
            w.playSound(impactLoc, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.4f);
        }
        explodeOnGround(impactLoc);
        pendingDespawnAfterImpact = true;
    }

    private void explodeOnGround(Location impactLoc) {
        RPGBossEntity boss = findGrowBoss();
        double percent = boss != null ? BossGrowStageType.stalactiteExplosionPercent(boss)
                : StalactiteConfig.EXPLOSION_PERCENT;
        lastExplosionPercent = percent;
        if (percent <= 0 || world == null) {
            return;
        }
        double radius = StalactiteConfig.EXPLOSION_RADIUS;
        double radiusSquared = radius * radius;
        for (Entity entity : world.getNearbyEntities(impactLoc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!(living instanceof Player) && !(living instanceof Mob)) {
                continue;
            }
            if (living.isDead()) {
                continue;
            }
            if (collider != null && living.getUniqueId().equals(collider.getUniqueId())) {
                continue;
            }
            if (BukkitEntityMetadata.has(living, COLLIDER_METADATA)) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
                continue;
            }
            if (living.getLocation().distanceSquared(impactLoc) > radiusSquared) {
                continue;
            }
            double maxHealth = maxHealthOf(living);
            double damage = maxHealth * (percent / 100.0);
            if (damage > 0) {
                DamageUtils.damageEntity(living, damage, null, DamageType.MAGIC);
            }
        }
    }

    private static double maxHealthOf(LivingEntity living) {
        RPGEntity rpg = EntityManager.getInstance().getEntity(living.getUniqueId()).orElse(null);
        if (rpg != null) {
            return rpg.getMaxHealth();
        }
        var attribute = living.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }

    private void onExpire() {
        if (impactResolved) {
            return;
        }
        impactResolved = true;
        impactReason = ImpactReason.EXPIRED;
        Location loc = new Location(world, pivot.getX(), currentY, pivot.getZ(), pivot.getYaw(), pivot.getPitch());
        lastImpactLocation = loc.clone();
        World w = loc.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.POOF, loc, 12, 0.4, 0.4, 0.4, 0.02);
            w.playSound(loc, Sound.BLOCK_POINTED_DRIPSTONE_BREAK, 1.0f, 0.7f);
        }
        pendingDespawnAfterImpact = true;
    }

    @Override
    public void tick(long now) {
        super.tick(now);
        // EntityManager-driven stat tick; lifecycle is driven by tickStalactite via
        // Manager
    }

    public void tickStalactite(long globalTick) {
        this.tickCounter = globalTick;
        switch (lifecycle) {
        case GROWING -> {
            long remaining = readyAtTick - globalTick;
            double progress = 1.0 - (remaining / (double) StalactiteConfig.REGROW_TICKS);
            progress = Math.max(0, Math.min(1.0, progress));
            if (progress >= 1.0 || globalTick >= readyAtTick) {
                becomeActive();
            } else {
                float clamped = (float) progress;
                if (display != null && display.isValid()) {
                    Vector3f ns = new Vector3f(clamped * StalactiteConfig.MAX_DISPLAY_SCALE_X,
                            -clamped * StalactiteConfig.MAX_DISPLAY_SCALE_Y,
                            clamped * StalactiteConfig.MAX_DISPLAY_SCALE_Z);
                    Vector3f trans = displayTranslationForScale(ns);
                    display.setInterpolationDuration(1);
                    display.setTransformation(new Transformation(trans, new Quaternionf(), ns, new Quaternionf()));
                }
                if (healthbar != null && healthbar.isValid()) {
                    healthbar.text(AdventureText.legacy(healthbarText()));
                    healthbar.teleport(pivot.clone().add(0, 1.5, 0));
                }
                updateHighlightsForRegrow(clamped);
            }
        }
        case ACTIVE -> {
            if (healthbar != null && healthbar.isValid()) {
                Location want = pivot.clone().add(0, 1.5, 0);
                if (healthbar.getLocation().distanceSquared(want) > 0.01)
                    healthbar.teleport(want);
            }
            if (collider != null && collider.isValid()) {
                Location want = StalactiteHitbox.computeColliderLocation(ceiling);
                if (collider.getLocation().distanceSquared(want) > 0.01)
                    collider.teleport(want);
            }
            if (hitbox != null && hitbox.isValid()) {
                Location want = StalactiteHitbox.computeHitboxLocation(ceiling);
                if (hitbox.getLocation().distanceSquared(want) > 0.01)
                    hitbox.teleport(want);
            }
            teleportHitboxHighlights(pivot);
        }
        case FALLING -> {
            fallTicks++;
            fallVelocity += StalactiteConfig.FALL_GRAVITY_PER_TICK;
            currentY -= fallVelocity;
            Location displayLoc = new Location(world, pivot.getX(), currentY, pivot.getZ(), pivot.getYaw(),
                    pivot.getPitch());
            Location hitboxLoc = new Location(world, displayLoc.getX(),
                    displayLoc.getY() - StalactiteConfig.MAX_DISPLAY_SCALE_Y, displayLoc.getZ(), displayLoc.getYaw(),
                    displayLoc.getPitch());
            Location colliderLoc = new Location(world, displayLoc.getX(),
                    displayLoc.getY() - StalactiteConfig.MAX_DISPLAY_SCALE_Y / 2.0, displayLoc.getZ(),
                    displayLoc.getYaw(), displayLoc.getPitch());
            if (display != null && display.isValid())
                display.teleport(displayLoc);
            if (hitbox != null && hitbox.isValid())
                hitbox.teleport(hitboxLoc);
            if (healthbar != null && healthbar.isValid())
                healthbar.teleport(displayLoc.clone().add(0, 1.5, 0));
            teleportCollider(colliderLoc);
            teleportHitboxHighlights(displayLoc);
            if (!impactResolved && tryBossImpact(displayLoc)) {
                return;
            }
            if (!impactResolved && ground != null && currentY <= ground.getY()) {
                currentY = ground.getY();
                onImpact();
                return;
            }
            if (!impactResolved && fallTicks >= StalactiteConfig.MAX_FALL_TICKS) {
                onExpire();
            }
        }
        }
    }

    // ---- projectile handling ----
    public void dealRPGDamageFromProjectile(LivingEntity bukkitAttacker, Projectile projectile) {
        if (!isDamageable())
            return;
        RPGEntity attackerRPG = null;
        UUID attackerId = null;
        if (projectile != null && projectile.getShooter() instanceof LivingEntity shooter) {
            attackerId = shooter.getUniqueId();
            bukkitAttacker = shooter;
        } else if (bukkitAttacker != null) {
            attackerId = bukkitAttacker.getUniqueId();
        }
        if (attackerId != null)
            attackerRPG = EntityManager.getInstance().getEntity(attackerId).orElse(null);
        double baseDamage;
        if (projectile != null) {
            Double stamped = projectile.getPersistentDataContainer().get(BowArrowManager.ARROW_DAMAGE_KEY,
                    PersistentDataType.DOUBLE);
            if (stamped != null && stamped > 0)
                baseDamage = stamped;
            else if (attackerRPG != null)
                baseDamage = attackerRPG.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                        System.currentTimeMillis()) * attackerRPG.getProjectileDamageMultiplier();
            else
                baseDamage = 10.0;
        } else {
            if (attackerRPG != null)
                baseDamage = attackerRPG.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                        System.currentTimeMillis());
            else
                baseDamage = 10.0;
        }
        RPGDamageResult result = this.dealRPGDamage(attackerRPG, this, baseDamage, DamageType.PHYSICAL);
        // Show damage indicator at hitbox location (not pivot) per clarification
        if (result != null && result.getResult() != DamageResult.DENY && result.getDamage() > 0) {
            try {
                DMain plugin = DMain.getInstance();
                CombatListener cl = plugin != null ? plugin.getCombatListener() : null;
                if (cl != null) {
                    Location loc = hitbox != null && hitbox.isValid() ? hitbox.getLocation().clone().add(0, 1, 0)
                            : pivot.clone().add(0, 1, 0);
                    // Projectile stalactite damage is always PHYSICAL
                    cl.showPhysicalDamage(loc, result.getDamage(), result.getResult());
                    // play hit sound at hitbox entity if available, else at location
                    if (hitbox != null && hitbox.isValid()) {
                        cl.playProjectileHitSound(hitbox, result.getResult());
                    } else {
                        loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.79f);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    // ---- collider ----
    private void spawnCollider(int idx, Location colliderLoc) {
        World w = colliderLoc.getWorld();
        if (w == null)
            return;
        if (collider != null) {
            try {
                if (collider.isValid())
                    collider.remove();
            } catch (Exception ignored) {
            }
            collider = null;
        }
        try {
            Slime slime = w.spawn(colliderLoc.clone(), Slime.class, s -> {
                try {
                    s.setSize(StalactiteConfig.COLLIDER_SLIME_SIZE);
                } catch (Exception ignored) {
                }
                s.setInvisible(true);
                s.setAI(false);
                s.setInvulnerable(true);
                s.setSilent(true);
                s.setGravity(false);
                s.setCollidable(false);
                s.setCustomNameVisible(false);
                s.setPersistent(false);
                s.setRemoveWhenFarAway(false);
                s.setCanPickupItems(false);
                s.setAware(false);
                try {
                    s.addPotionEffect(
                            new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
                } catch (Exception ignored) {
                }
                s.getPersistentDataContainer().set(STALACTITE_INDEX_KEY, PersistentDataType.INTEGER, idx);
                try {
                    BukkitEntityMetadata.set(s, COLLIDER_METADATA, true);
                } catch (Exception ignored) {
                }
            });
            this.collider = slime;
        } catch (Exception e) {
            Bukkit.getLogger()
                    .warning("[Stalactite] failed to spawn slime collider at " + colliderLoc + ": " + e.getMessage());
        }
    }

    private void teleportCollider(Location loc) {
        if (collider != null && collider.isValid())
            collider.teleport(loc);
    }

    private void removeCollider() {
        if (collider != null) {
            try {
                if (collider.isValid())
                    collider.remove();
            } catch (Exception ignored) {
            }
            collider = null;
        }
    }

    // ---- highlights ----
    private void spawnHitboxHighlights(BukkitDisplayEntityRegistry registry) {
        if (!showHighlights)
            return;
        removeHitboxHighlights();
        World w = pivot.getWorld();
        if (w == null)
            return;
        // All highlights are pivot-anchored: entity stays at pivot, top face locked via
        // translation
        try {
            float hw = StalactiteConfig.HITBOX_WIDTH;
            float hh = StalactiteConfig.HITBOX_HEIGHT;
            Vector3f trans = hitboxHighlightTranslation(hw, hh);
            BlockData bd = StalactiteConfig.HITBOX_HIGHLIGHT_MATERIAL.createBlockData();
            BlockDisplay disp = registry.spawnDisplayEntity(pivot.clone(), BlockDisplay.class, d -> {
                d.setBlock(bd);
                d.setInvulnerable(true);
                d.setGravity(false);
                d.setBrightness(new Display.Brightness(StalactiteConfig.HITBOX_HIGHLIGHT_BRIGHTNESS,
                        StalactiteConfig.HITBOX_HIGHLIGHT_BRIGHTNESS));
                d.setViewRange(32f);
                d.setInterpolationDuration(0);
                d.setTransformation(
                        new Transformation(trans, new Quaternionf(), new Vector3f(hw, hh, hw), new Quaternionf()));
                try {
                    d.setGlowing(true);
                } catch (Exception ignored) {
                }
                try {
                    d.setGlowColorOverride(Color.LIME);
                } catch (Exception ignored) {
                }
            });
            hitboxHighlight = disp;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Stalactite] failed to spawn hitbox highlight: " + e.getMessage());
        }
        try {
            int segments = StalactiteConfig.HITBOX_HIGHLIGHT_TAPER_SEGMENTS;
            float totalH = StalactiteConfig.MAX_DISPLAY_SCALE_Y;
            float segH = totalH / segments;
            double baseW = StalactiteConfig.MAX_DISPLAY_SCALE_X;
            double tipW = StalactiteConfig.HITBOX_TIP_WIDTH;
            double height = totalH;
            double tipY = pivot.getY() - height;
            for (int i = 0; i < segments; i++) {
                double topY = pivot.getY() - i * segH;
                double bottomY = topY - segH;
                double centerY = (topY + bottomY) / 2.0;
                double t = (centerY - tipY) / height;
                t = Math.max(0, Math.min(1, t));
                float segWidth = (float) StalactiteHitbox.lerp(tipW, baseW, t);
                Vector3f trans = taperedSegmentTranslation(segWidth, segH, i);
                BlockData bd = StalactiteConfig.HITBOX_HIGHLIGHT_TAPER_MATERIAL.createBlockData();
                final float fw = segWidth;
                final float fSegH = segH;
                final Vector3f fTrans = new Vector3f(trans);
                BlockDisplay seg = registry.spawnDisplayEntity(pivot.clone(), BlockDisplay.class, d -> {
                    d.setBlock(bd);
                    d.setInvulnerable(true);
                    d.setGravity(false);
                    d.setBrightness(new Display.Brightness(StalactiteConfig.HITBOX_HIGHLIGHT_BRIGHTNESS,
                            StalactiteConfig.HITBOX_HIGHLIGHT_BRIGHTNESS));
                    d.setViewRange(32f);
                    d.setInterpolationDuration(0);
                    d.setTransformation(new Transformation(fTrans, new Quaternionf(), new Vector3f(fw, fSegH, fw),
                            new Quaternionf()));
                    try {
                        d.setGlowing(true);
                    } catch (Exception ignored) {
                    }
                    try {
                        d.setGlowColorOverride(Color.GREEN);
                    } catch (Exception ignored) {
                    }
                });
                taperedHighlights.add(seg);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Stalactite] failed to spawn tapered highlights: " + e.getMessage());
        }
        try {
            float slimeSize = StalactiteConfig.COLLIDER_SLIME_SIZE * 1.02f;
            Vector3f trans = colliderHighlightTranslation(slimeSize, 1.0f);
            BlockData bd = Material.LIME_STAINED_GLASS.createBlockData();
            BlockDisplay col = registry.spawnDisplayEntity(pivot.clone(), BlockDisplay.class, d -> {
                d.setBlock(bd);
                d.setInvulnerable(true);
                d.setGravity(false);
                d.setBrightness(new Display.Brightness(StalactiteConfig.HITBOX_HIGHLIGHT_BRIGHTNESS,
                        StalactiteConfig.HITBOX_HIGHLIGHT_BRIGHTNESS));
                d.setViewRange(32f);
                d.setInterpolationDuration(0);
                d.setTransformation(new Transformation(trans, new Quaternionf(),
                        new Vector3f(slimeSize, slimeSize, slimeSize), new Quaternionf()));
                try {
                    d.setGlowing(true);
                } catch (Exception ignored) {
                }
                try {
                    d.setGlowColorOverride(Color.YELLOW);
                } catch (Exception ignored) {
                }
            });
            colliderHighlight = col;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Stalactite] failed to spawn collider highlight: " + e.getMessage());
        }
    }

    private void removeHitboxHighlights() {
        BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();
        if (hitboxHighlight != null) {
            try {
                UUID id = hitboxHighlight.getUniqueId();
                if (Bukkit.getEntity(id) != null)
                    registry.removeDisplayEntity(id);
                else if (hitboxHighlight.isValid())
                    hitboxHighlight.remove();
            } catch (Exception ignored) {
            }
            hitboxHighlight = null;
        }
        if (colliderHighlight != null) {
            try {
                UUID id = colliderHighlight.getUniqueId();
                if (Bukkit.getEntity(id) != null)
                    registry.removeDisplayEntity(id);
                else if (colliderHighlight.isValid())
                    colliderHighlight.remove();
            } catch (Exception ignored) {
            }
            colliderHighlight = null;
        }
        for (BlockDisplay seg : new ArrayList<>(taperedHighlights)) {
            try {
                UUID id = seg.getUniqueId();
                if (Bukkit.getEntity(id) != null)
                    registry.removeDisplayEntity(id);
                else if (seg.isValid())
                    seg.remove();
            } catch (Exception ignored) {
            }
        }
        taperedHighlights.clear();
    }

    private void teleportHitboxHighlights(Location currentPivot) {
        if (!showHighlights)
            return;
        World w = currentPivot.getWorld();
        if (w == null)
            return;
        // Pivot-anchored: entity stays at pivot, block offset via translation. During
        // FALLING, pivot moves (displayLoc).
        if (hitboxHighlight != null && hitboxHighlight.isValid()) {
            // ensure highlight entity follows pivot, keep full scale translation
            if (hitboxHighlight.getLocation().distanceSquared(currentPivot) > 0.01)
                hitboxHighlight.teleport(currentPivot.clone());
            // ensure transformation is full if it was scaled during growth
            Transformation tr = hitboxHighlight.getTransformation();
            float hw = StalactiteConfig.HITBOX_WIDTH;
            float hh = StalactiteConfig.HITBOX_HEIGHT;
            Vector3f expectedScale = new Vector3f(hw, hh, hw);
            Vector3f expectedTrans = hitboxHighlightTranslation(hw, hh);
            if (!tr.getScale().equals(expectedScale) || !tr.getTranslation().equals(expectedTrans)) {
                hitboxHighlight.setInterpolationDuration(0);
                hitboxHighlight.setTransformation(
                        new Transformation(expectedTrans, new Quaternionf(), expectedScale, new Quaternionf()));
            }
        }
        if (!taperedHighlights.isEmpty()) {
            int segments = taperedHighlights.size();
            float totalH = StalactiteConfig.MAX_DISPLAY_SCALE_Y;
            float segH = totalH / segments;
            double baseW = StalactiteConfig.MAX_DISPLAY_SCALE_X;
            double tipW = StalactiteConfig.HITBOX_TIP_WIDTH;
            double height = totalH;
            double tipY = pivot.getY() - height; // pivot-based, but for teleport we use currentPivot for entity
                                                 // location
            for (int i = 0; i < taperedHighlights.size(); i++) {
                BlockDisplay seg = taperedHighlights.get(i);
                if (seg == null || !seg.isValid())
                    continue;
                if (seg.getLocation().distanceSquared(currentPivot) > 0.01)
                    seg.teleport(currentPivot.clone());
                // ensure full scale for ACTIVE/FALLING (if coming from GROWING scaled)
                double topY = pivot.getY() - i * segH;
                double bottomY = topY - segH;
                double centerY = (topY + bottomY) / 2.0;
                double t = (centerY - tipY) / height;
                t = Math.max(0, Math.min(1, t));
                float segWidth = (float) StalactiteHitbox.lerp(tipW, baseW, t);
                Vector3f expectedScale = new Vector3f(segWidth, segH, segWidth);
                Vector3f expectedTrans = taperedSegmentTranslation(segWidth, segH, i);
                Transformation tr = seg.getTransformation();
                if (!tr.getScale().equals(expectedScale) || !tr.getTranslation().equals(expectedTrans)) {
                    seg.setInterpolationDuration(0);
                    seg.setTransformation(
                            new Transformation(expectedTrans, new Quaternionf(), expectedScale, new Quaternionf()));
                }
            }
        }
        if (colliderHighlight != null && colliderHighlight.isValid()) {
            if (colliderHighlight.getLocation().distanceSquared(currentPivot) > 0.01)
                colliderHighlight.teleport(currentPivot.clone());
            float slimeSize = StalactiteConfig.COLLIDER_SLIME_SIZE * 1.02f;
            Vector3f expectedScale = new Vector3f(slimeSize, slimeSize, slimeSize);
            Vector3f expectedTrans = colliderHighlightTranslation(slimeSize, 1.0f);
            Transformation tr = colliderHighlight.getTransformation();
            if (!tr.getScale().equals(expectedScale) || !tr.getTranslation().equals(expectedTrans)) {
                colliderHighlight.setInterpolationDuration(0);
                colliderHighlight.setTransformation(
                        new Transformation(expectedTrans, new Quaternionf(), expectedScale, new Quaternionf()));
            }
        }
    }

    private void hideHitboxHighlights() {
        if (!showHighlights)
            return;
        if (hitboxHighlight != null && hitboxHighlight.isValid()) {
            hitboxHighlight.setInterpolationDuration(2);
            Vector3f trans = hitboxHighlightTranslation(0, 0);
            hitboxHighlight.setTransformation(
                    new Transformation(trans, new Quaternionf(), new Vector3f(0, 0, 0), new Quaternionf()));
        }
        for (int i = 0; i < taperedHighlights.size(); i++) {
            BlockDisplay seg = taperedHighlights.get(i);
            if (seg != null && seg.isValid()) {
                seg.setInterpolationDuration(2);
                Vector3f trans = taperedSegmentTranslation(0, 0, i);
                seg.setTransformation(
                        new Transformation(trans, new Quaternionf(), new Vector3f(0, 0, 0), new Quaternionf()));
            }
        }
        if (colliderHighlight != null && colliderHighlight.isValid()) {
            colliderHighlight.setInterpolationDuration(2);
            Vector3f trans = colliderHighlightTranslation(0, 0);
            colliderHighlight.setTransformation(
                    new Transformation(trans, new Quaternionf(), new Vector3f(0, 0, 0), new Quaternionf()));
        }
    }

    private void updateHighlightsForRegrow(float progress) {
        if (!showHighlights)
            return;
        float clamped = progress;
        if (hitboxHighlight != null && hitboxHighlight.isValid()) {
            hitboxHighlight.setInterpolationDuration(1);
            float hw = StalactiteConfig.HITBOX_WIDTH * clamped;
            float hh = StalactiteConfig.HITBOX_HEIGHT * clamped;
            Vector3f scale = new Vector3f(hw, hh, hw);
            Vector3f trans = hitboxHighlightTranslation(hw, hh);
            hitboxHighlight.setTransformation(new Transformation(trans, new Quaternionf(), scale, new Quaternionf()));
            // entity stays at pivot — no teleport
        }
        int idx = 0;
        for (BlockDisplay seg : taperedHighlights) {
            if (seg == null || !seg.isValid()) {
                idx++;
                continue;
            }
            int segments = taperedHighlights.size();
            float totalH = StalactiteConfig.MAX_DISPLAY_SCALE_Y;
            double tipW = StalactiteConfig.HITBOX_TIP_WIDTH;
            double baseW = StalactiteConfig.MAX_DISPLAY_SCALE_X;
            double height = totalH;
            double tipY = pivot.getY() - height;
            float segH = totalH / segments;
            double topY = pivot.getY() - idx * segH;
            double bottomY = topY - segH;
            double centerY = (topY + bottomY) / 2.0;
            double t = (centerY - tipY) / height;
            t = Math.max(0, Math.min(1, t));
            float origW = (float) StalactiteHitbox.lerp(tipW, baseW, t);
            float segW = origW * clamped;
            float segHp = segH * clamped;
            Vector3f scale = new Vector3f(segW, segHp, segW);
            Vector3f trans = taperedSegmentTranslation(segW, segHp, idx);
            seg.setInterpolationDuration(1);
            seg.setTransformation(new Transformation(trans, new Quaternionf(), scale, new Quaternionf()));
            idx++;
        }
        if (colliderHighlight != null && colliderHighlight.isValid()) {
            float slimeSize = StalactiteConfig.COLLIDER_SLIME_SIZE * 1.02f * clamped;
            Vector3f scale = new Vector3f(slimeSize, slimeSize, slimeSize);
            Vector3f trans = colliderHighlightTranslation(slimeSize, clamped);
            colliderHighlight.setInterpolationDuration(1);
            colliderHighlight.setTransformation(new Transformation(trans, new Quaternionf(), scale, new Quaternionf()));
        }
    }

    // ---- cleanup ----
    public void despawn() {
        try {
            if (damageAction != null)
                BukkitEventBus.getInstance().unsubscribe(damageAction);
        } catch (Exception ignored) {
        }
        try {
            if (deathAction != null)
                BukkitEventBus.getInstance().unsubscribe(deathAction);
        } catch (Exception ignored) {
        }
        damageAction = null;
        deathAction = null;
        try {
            EntityManager.getInstance().removeEntity(getUuid());
        } catch (Exception ignored) {
        }
        // keep mark dead? already removed
        BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();
        if (display != null) {
            try {
                UUID id = display.getUniqueId();
                if (Bukkit.getEntity(id) != null)
                    registry.removeDisplayEntity(id);
                else if (display.isValid())
                    display.remove();
            } catch (Exception ignored) {
            }
            display = null;
        }
        if (healthbar != null) {
            try {
                UUID id = healthbar.getUniqueId();
                if (Bukkit.getEntity(id) != null)
                    registry.removeDisplayEntity(id);
                else if (healthbar.isValid())
                    healthbar.remove();
            } catch (Exception ignored) {
            }
            healthbar = null;
        }
        if (hitbox != null) {
            try {
                if (hitbox.isValid())
                    hitbox.remove();
            } catch (Exception ignored) {
            }
            hitbox = null;
        }
        removeCollider();
        removeHitboxHighlights();
    }

    public enum ImpactReason {
        NONE, BOSS, GROUND, EXPIRED
    }

    private enum Lifecycle {
        GROWING, ACTIVE, FALLING
    }
}
