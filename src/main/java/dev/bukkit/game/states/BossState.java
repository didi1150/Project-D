package dev.bukkit.game.states;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.entity.boss.BukkitWitherBoss;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.GameStateResult;
import dev.core.game.coords.Point3D;
import dev.core.game.settings.GameSettings;
import dev.core.progression.PlayerClassProgression;

public class BossState extends GameState {
    private final static String NAME = "BOSSSTATE";
    // Single global boss arena — no party id required.

    private final ClassProgressionService classProgressionService;
    private final EventBusInterface eventBus;
    private UUID bossUuid;
    private BukkitWitherBoss activeBoss;

    public BossState(EventBusInterface eventBus, ClassProgressionService classProgressionService) {
        super(NAME, -1, eventBus);
        this.eventBus = eventBus;
        this.classProgressionService = classProgressionService;
    }

    @Override
    protected void onStart() {
        GameSettings settings = GameSettings.getCurrentSettings();
        String bossTemplate = settings.getBossWorld();
        if (bossTemplate == null || bossTemplate.isBlank()) {
            Bukkit.broadcastMessage(
                    "§cBossState failed: no boss arena template configured for floor " + settings.getFloor());
            complete(GameStateResult.COMPLETE);
            return;
        }

        prepareBossArena(bossTemplate, settings);
    }

    @Override
    protected void onStop() {
        if (activeBoss != null) {
            activeBoss.shutdown();
            activeBoss = null;
        }
        bossUuid = null;
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerQuitEvent> quitAction = new EventAction<>(this::handleQuit, PlayerQuitEvent.class);
        EventAction<PlayerJoinEvent> joinAction = new EventAction<>(this::handleJoin, PlayerJoinEvent.class);
        EventAction<EntityDeathEvent> deathAction = new EventAction<>(this::handleBossDeath, EntityDeathEvent.class);

        addSubscriber(quitAction);
        addSubscriber(joinAction);
        addSubscriber(deathAction);
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
    }

    private void handleBossDeath(EntityDeathEvent event) {
        if (bossUuid == null) {
            return;
        }
        if (!event.getEntity().getUniqueId().equals(bossUuid)) {
            return;
        }

        Bukkit.broadcastMessage("§aThe End Boss has been defeated!");
        complete(GameStateResult.COMPLETE);
    }

    private void teleportPlayersToBossSpawn(World world, Point3D spawnPoint) {
        if (spawnPoint == null) {
            return;
        }
        Location teleportLocation = new Location(world, spawnPoint.getX() + 0.5, spawnPoint.getY(),
                spawnPoint.getZ() + 0.5);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (EntityManager.getInstance().isSpectator(player.getUniqueId())) {
                continue;
            }
            player.setFallDistance(0);
            player.teleport(teleportLocation);
        }
    }

    private boolean spawnBoss(World world, Location spawnLocation) {
        if (activeBoss != null) {
            activeBoss.shutdown();
        }

        Entity existing = world.getNearbyEntities(spawnLocation, 1, 1, 1).stream()
                .filter(entity -> entity.hasMetadata("BOSS")).findFirst().orElse(null);
        if (existing != null) {
            existing.remove();
        }

        Entity entity = world.spawnEntity(spawnLocation, EntityType.WITHER);
        if (!(entity instanceof LivingEntity wither)) {
            throw new IllegalStateException("Failed to spawn wither boss entity");
        }

        wither.setCustomName("§c§lWither Boss");
        wither.setCustomNameVisible(true);
        wither.setRemoveWhenFarAway(false);
        wither.setAI(false);

        BukkitWitherBoss boss = new BukkitWitherBoss(entity.getUniqueId(), "End Boss", eventBus);
        boss.spawn(spawnLocation);

        this.activeBoss = boss;
        this.bossUuid = boss.getUuid();
        if (Bukkit.getEntity(boss.getUuid()) instanceof LivingEntity living) {
            living.setMetadata("BOSS", new FixedMetadataValue(DMain.getInstance(), true));
            living.setMetadata("DUNGEON", new FixedMetadataValue(DMain.getInstance(), true));
            if (living instanceof org.bukkit.entity.Creature creature) {
                creature.setRemoveWhenFarAway(false);
            }
        }
        return true;
    }

    private void prepareBossArena(String bossTemplate, GameSettings settings) {
        DMain.getInstance().getBossArenaManager().createInstance(bossTemplate).whenComplete((world, throwable) -> {
            if (throwable != null) {
                Bukkit.broadcastMessage(
                        "§cBossState failed: could not load boss arena template. " + throwable.getMessage());
                complete(GameStateResult.COMPLETE);
                return;
            }

            Point3D bossSpawnPoint = settings.getBossSpawnLocation(settings.getFloor());
            if (bossSpawnPoint == null) {
                Bukkit.broadcastMessage(
                        "§cBossState failed: no boss spawn configured for floor " + settings.getFloor());
                complete(GameStateResult.COMPLETE);
                return;
            }

            Point3D playerSpawnPoint = settings.getBossPlayerSpawnLocation(settings.getFloor());
            if (playerSpawnPoint == null) {
                Bukkit.broadcastMessage(
                        "§cBossState failed: no boss player spawn configured for floor " + settings.getFloor());
                complete(GameStateResult.COMPLETE);
                return;
            }

            teleportPlayersToBossSpawn(world, playerSpawnPoint);
            Location bossSpawn = new Location(world, bossSpawnPoint.getX() + 0.5, bossSpawnPoint.getY(),
                    bossSpawnPoint.getZ() + 0.5);
            if (!spawnBoss(world, bossSpawn)) {
                Bukkit.broadcastMessage("§cBossState failed: could not spawn boss.");
                complete(GameStateResult.COMPLETE);
                return;
            }

            Bukkit.broadcastMessage("§cThe Wither boss has awakened in the dungeon's deepest room!");
        });
    }
}
