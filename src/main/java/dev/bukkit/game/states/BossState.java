package dev.bukkit.game.states;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitEntityMetadata;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.entity.boss.BukkitBossEntity;
import dev.bukkit.entity.boss.BukkitBossFactory;
import dev.bukkit.game.scheduler.BukkitTaskScheduler;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.utils.BukkitText;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.BossDefinitionRegistry;
import dev.core.entity.boss.FloorData;
import dev.core.entity.boss.FloorDataRegistry;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.BossEncounterEndedEvent;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.game.GameState;
import dev.core.game.GameStateResult;
import dev.core.game.TaskScheduler;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.settings.GameSettings;
import dev.core.progression.PlayerClassProgression;

public class BossState extends GameState {
    private final static String NAME = "BOSSSTATE";
    // Single global boss arena — no party id required.

    private final ClassProgressionService classProgressionService;
    private final EventBusInterface eventBus;
    private final BukkitBossFactory bossFactory;
    private final TaskScheduler scheduler;
    private UUID bossUuid;
    private BukkitBossEntity activeBoss;
    private boolean encounterFinished;

    public BossState(EventBusInterface eventBus, ClassProgressionService classProgressionService) {
        super(NAME, -1, eventBus);
        this.eventBus = eventBus;
        this.classProgressionService = classProgressionService;
        this.scheduler = new BukkitTaskScheduler(DMain.getInstance());
        this.bossFactory = new BukkitBossFactory(eventBus, scheduler);
    }

    @Override
    protected void onStart() {
        encounterFinished = false;
        GameSettings settings = GameSettings.getCurrentSettings();
        String bossTemplate = settings.getBossWorld();
        if (bossTemplate == null || bossTemplate.isBlank()) {
            BukkitText.broadcast(
                    "§cBossState failed: no boss arena template configured for floor " + settings.getFloor());
            complete(GameStateResult.COMPLETE);
            return;
        }

        FloorData floorData = FloorDataRegistry.getInstance().getOrEmpty(settings.getFloor());
        prepareBossArena(bossTemplate, settings, floorData);
    }

    @Override
    protected void onStop() {
        if (activeBoss != null) {
            bossFactory.despawn(activeBoss);
            activeBoss = null;
        }
        bossUuid = null;
        DMain.getInstance().getBossArenaManager().destroyInstance().whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                Bukkit.getLogger().warning("Failed to destroy boss arena: " + throwable.getMessage());
            }
        });
    }

    public boolean hasActiveBoss() {
        return activeBoss != null && bossUuid != null;
    }

    public UUID getBossUuid() {
        return bossUuid;
    }

    public void injectDebugBoss(BukkitBossEntity boss) {
        if (boss == null) {
            return;
        }
        if (activeBoss != null) {
            bossFactory.despawn(activeBoss);
        }
        this.activeBoss = boss;
        this.bossUuid = boss.getUuid();
        boss.getBukkitEntity()
                .ifPresent(living -> BukkitEntityMetadata.set(living, "BOSS", true));
    }

    public void clearInjectedBoss(UUID uuid) {
        if (uuid != null && uuid.equals(this.bossUuid)) {
            this.activeBoss = null;
            this.bossUuid = null;
        }
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerQuitEvent> quitAction = new EventAction<>(this::handleQuit, PlayerQuitEvent.class);
        EventAction<PlayerJoinEvent> joinAction = new EventAction<>(this::handleJoin, PlayerJoinEvent.class);
        EventAction<RPGEntityDeathEvent> deathAction = new EventAction<>(this::handleBossDeath,
                RPGEntityDeathEvent.class);
        EventAction<BossEncounterEndedEvent> encounterAction = new EventAction<>(this::handleEncounterEnded,
                BossEncounterEndedEvent.class);

        addSubscriber(quitAction);
        addSubscriber(joinAction);
        addSubscriber(deathAction);
        addSubscriber(encounterAction);
    }

    private void handleQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (EntityManager.getInstance().isSpectator(player.getUniqueId())) {
            return;
        }
        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) EntityManager.getInstance()
                .getEntity(player.getUniqueId()).get();
        classProgressionService.saveAll(playerEntity.getUuid());
        playerEntity.onDeath();
    }

    private void handleJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
        if (EntityManager.getInstance().isSpectator(player.getUniqueId()) || optional.isEmpty()) {
            toGhost(player);
            return;
        }
        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
        playerEntity.syncState();
        BukkitInventorySync.syncInventory(playerEntity, player);
        for (RPGClassType rpgClassType : RPGClassType.values()) {
            PlayerClassProgression progression = playerEntity.getPlayerProgression().getProgression(rpgClassType);
            PlayerClassProgression cachedProgression = classProgressionService.getProgression(player.getUniqueId(),
                    rpgClassType);
            progression.setLevel(cachedProgression.getLevel());
            progression.setUsableItems(cachedProgression.getUsableItems());
            progression.setXp(cachedProgression.getXp());
        }
        playerEntity.getPlayerProgression().setActiveClass(classProgressionService.getActiveClass(player.getUniqueId()),
                playerEntity.getStatManager());
    }

    private void toGhost(Player player) {
        EntityManager.getInstance().registerSpectator(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(maxHealthAttr.getValue());
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, -1, 1, false, false));

        EntityManager.getInstance().getAliveEntities().forEach(entity -> {
            if (entity instanceof BukkitPlayerEntity playerEntity) {
                playerEntity.getPlayer().ifPresent(other -> {
                    if (other.canSee(player) && !EntityManager.getInstance().isSpectator(other.getUniqueId())) {
                        other.hidePlayer(DMain.getInstance(), player);
                    }
                });
            }
        });
        BukkitPlayerEntity.clearMobTargetsOf(player);
    }

    private void handleBossDeath(RPGEntityDeathEvent event) {
        if (bossUuid == null || encounterFinished) {
            return;
        }
        if (!event.getTarget().getUuid().equals(bossUuid)) {
            return;
        }

        encounterFinished = true;
        BukkitText.broadcast("§aThe End Boss has been defeated!");
        complete(GameStateResult.COMPLETE);
    }

    private void handleEncounterEnded(BossEncounterEndedEvent event) {
        if (bossUuid == null || encounterFinished) {
            return;
        }
        if (!event.getTarget().getUuid().equals(bossUuid)) {
            return;
        }

        encounterFinished = true;
        BossEncounterEndedEvent.Outcome outcome = event.getOutcome();
        UUID expectedBoss = bossUuid;
        scheduler.runTaskLater(() -> {
            if (!active || bossUuid == null || !bossUuid.equals(expectedBoss)) {
                return;
            }
            if (outcome == BossEncounterEndedEvent.Outcome.VICTORY) {
                complete(GameStateResult.COMPLETE);
            } else {
                jumpToState(PostGameState.NAME);
            }
        }, 1L);
    }

    private void teleportPlayersToBossSpawn(World world, ViewPoint3D spawnPoint) {
        if (spawnPoint == null) {
            return;
        }
        Location teleportLocation = new Location(world, spawnPoint.getX() + 0.5, spawnPoint.getY(),
                spawnPoint.getZ() + 0.5, spawnPoint.getYaw(), spawnPoint.getPitch());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (EntityManager.getInstance().isSpectator(player.getUniqueId())) {
                continue;
            }
            player.setFallDistance(0);
            player.teleport(teleportLocation);
        }
    }

    private boolean spawnBoss(World world, Location spawnLocation, FloorData floorData) {
        if (activeBoss != null) {
            bossFactory.despawn(activeBoss);
            activeBoss = null;
        }

        Entity existing = world.getNearbyEntities(spawnLocation, 1, 1, 1).stream()
                .filter(entity -> BukkitEntityMetadata.has(entity, "BOSS")).findFirst().orElse(null);
        if (existing != null) {
            existing.remove();
        }

        GameSettings settings = GameSettings.getCurrentSettings();
        BossDefinition definition = BossDefinitionRegistry.getInstance().getForFloor(settings.getFloor()).orElse(null);
        if (definition == null) {
            BukkitText.broadcast("§cBossState failed: no boss configured for floor " + settings.getFloor());
            return false;
        }

        removeLeftoverBossEntities(world, definition);

        BukkitBossEntity boss = bossFactory.spawn(definition, world, spawnLocation, floorData);
        this.activeBoss = boss;
        this.bossUuid = boss.getUuid();
        boss.getBukkitEntity()
                .ifPresent(living -> BukkitEntityMetadata.set(living, "BOSS", true));

        return true;
    }

    /**
     * Template worlds can contain a boss that was spawned while the arena was
     * being built. Entity metadata is in-memory only, so a saved boss comes back
     * without the "BOSS" tag and is neither managed by {@link EntityManager} nor
     * invulnerable. Remove those leftovers before spawning the real encounter
     * boss, otherwise the stale entity can be attacked (and killed) alongside it.
     */
    private void removeLeftoverBossEntities(World world, BossDefinition definition) {
        EntityType bossType;
        try {
            bossType = EntityType.valueOf(definition.getEntityType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.getType() != bossType) {
                continue;
            }
            if (EntityManager.getInstance().getEntity(entity.getUniqueId()).isPresent()) {
                continue;
            }
            entity.remove();
            removed++;
        }
        if (removed > 0) {
            Bukkit.getLogger().info("[Boss] Removed " + removed + " leftover " + bossType
                    + " entity/entities from arena before spawning " + definition.getId() + ".");
        }
    }

    private void prepareBossArena(String bossTemplate, GameSettings settings, FloorData floorData) {
        DMain.getInstance().getBossArenaManager().createInstance(bossTemplate).whenComplete((world, throwable) -> {
            if (throwable != null) {
                BukkitText.broadcast(
                        "§cBossState failed: could not load boss arena template. " + throwable.getMessage());
                complete(GameStateResult.COMPLETE);
                return;
            }

            try {
                spawnBossForArena(world, settings, floorData);
            } catch (RuntimeException t) {
                Bukkit.getLogger().log(Level.SEVERE,
                        "BossState failed while spawning the boss for floor " + settings.getFloor(), t);
                BukkitText.broadcast("§cBossState failed: could not spawn boss. See console for details.");
                complete(GameStateResult.COMPLETE);
            }
        });
    }

    private void spawnBossForArena(World world, GameSettings settings, FloorData floorData) {
        ViewPoint3D bossSpawnPoint = settings.getBossSpawnLocation(settings.getFloor());
        if (bossSpawnPoint == null) {
            BukkitText.broadcast(
                    "§cBossState failed: no boss spawn configured for floor " + settings.getFloor());
            complete(GameStateResult.COMPLETE);
            return;
        }

        ViewPoint3D playerSpawnPoint = settings.getBossPlayerSpawnLocation(settings.getFloor());
        if (playerSpawnPoint == null) {
            BukkitText.broadcast(
                    "§cBossState failed: no boss player spawn configured for floor " + settings.getFloor());
            complete(GameStateResult.COMPLETE);
            return;
        }

        // FloorData is now available for floor-specific arena preparation.
        // Example for Floor 1 Sealed Entity: validate required lists and spawn
        // pillars/chains/stalactites/locks/material spawners here before boss spawn.
        // Fail-fast with broadcast is handled by the floor's BossStage.onEnter as well,
        // but early validation here gives immediate feedback.
        // Floor-specific code can read floorData.getViewPointList("pillars") etc.

        teleportPlayersToBossSpawn(world, playerSpawnPoint);
        Location bossSpawn = new Location(world, bossSpawnPoint.getX() + 0.5, bossSpawnPoint.getY(),
                bossSpawnPoint.getZ() + 0.5, bossSpawnPoint.getYaw(), bossSpawnPoint.getPitch());
        if (!spawnBoss(world, bossSpawn, floorData)) {
            BukkitText.broadcast("§cBossState failed: could not spawn boss.");
            complete(GameStateResult.COMPLETE);
            return;
        }
    }
}
