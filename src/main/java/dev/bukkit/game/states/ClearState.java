package dev.bukkit.game.states;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitEntityFactory;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.game.coords.LocToPoint;
import dev.bukkit.game.coords.PointToLocation;
import dev.bukkit.game.dungeon.proceduralDungeon.SimpleDungeonBuilderBukkit;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.coords.Point3D;
import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.RoomFirstDungeonGenerator3D;
import dev.core.game.dungeon.proceduralDungeon.SimpleRandomWalkDungeonGenerator.SimpleRandomWalkParameters;
import dev.core.game.dungeon.proceduralDungeon.util.DungeonSpawnManager;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnLocation;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.settings.GameSettings;
import dev.core.progression.PlayerClassProgression;
import dev.core.utils.MessageSenderInterface;

public class ClearState extends GameState {

    public final static String NAME = "CLEARSTATE";

    private ClassProgressionService classProgressionService;

    private static final int SPAWN_RADIUS_SQRD = 225;

    private static final List<Point3D> CACHED_CRUMBLE_OFFSETS = new ArrayList<>();
    private static final int CRUMBLE_RADIUS = 9;
    private static final Random RANDOM = new Random();

    static {
        for (int x = -CRUMBLE_RADIUS; x <= CRUMBLE_RADIUS; x++) {
            for (int z = -CRUMBLE_RADIUS; z <= CRUMBLE_RADIUS; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= CRUMBLE_RADIUS) {
                    CACHED_CRUMBLE_OFFSETS.add(new Point3D(x, 0, z));
                }
            }
        }
    }

    private Location holeCenter;

    private Plugin plugin;

    private World world;

    private SimpleDungeonBuilderBukkit simpleDungeonBuilderBukkit;

    public ClearState(Point3D holeCenter, EventBusInterface eventBus,
            ClassProgressionService classProgressionService, MessageSenderInterface messageSender, Plugin plugin) {
        super(NAME, -1, eventBus);
        this.plugin = plugin;
        this.holeCenter = PointToLocation.blockToLoc(holeCenter);
        this.eventBus = eventBus;
        this.classProgressionService = classProgressionService;
    }

    @Override
    protected void onStart() {
        // Fill every player's resource stats to their max so nobody carries a
        // depleted health/mana pool into the next round.
        refillPlayerResources();

        // Handle ground opening
        int minRoomWidth = new Random().nextInt(10, 20);
        int minRoomHeight = new Random().nextInt(5, 8);
        int minRoomLength = new Random().nextInt(10, 20);
        int dungeonWidth = 55
                + (GameSettings.getCurrentSettings().getFloor() * GameSettings.getCurrentSettings().getFloor()) * 5;
        int dungeonHeight = 30
                + (GameSettings.getCurrentSettings().getFloor() * GameSettings.getCurrentSettings().getFloor()) * 5;
        int dungeonLength = 55
                + (GameSettings.getCurrentSettings().getFloor() * GameSettings.getCurrentSettings().getFloor()) * 5;

        int roomOffset = 1;
        boolean randomWalkRooms = true;
        int corridorWidth = new Random().nextInt(3, 5);

        int iterations = 50;
        int walkLength = 15;
        boolean startRandomlyEachIteration = true;
        SimpleRandomWalkParameters parameters = new SimpleRandomWalkParameters(iterations, walkLength,
                startRandomlyEachIteration);

        Vector3Int startPoint = new Vector3Int(0, 64, 0);
        BoundingBox space = new BoundingBox(startPoint, startPoint.add(dungeonWidth, dungeonHeight, dungeonLength));
        RoomFirstDungeonGenerator3D dungeonGenerator = new RoomFirstDungeonGenerator3D(startPoint, parameters,
                minRoomWidth, minRoomHeight, minRoomLength, dungeonWidth, dungeonHeight, dungeonLength, roomOffset,
                randomWalkRooms, corridorWidth);

        dungeonGenerator.generateDungeon(1786367046024l);
        world = Bukkit.getWorld(GameSettings.getCurrentSettings().getDungeonWorld());
        simpleDungeonBuilderBukkit = new SimpleDungeonBuilderBukkit(plugin, world);

        simpleDungeonBuilderBukkit.buildDungeon(dungeonGenerator, () -> {
            GameSettings.getCurrentSettings()
                    .setLastGeneratedDungeon(new BoundingBox(space.getMinPoint().add(0, -1, 0), space.getMaxPoint())); // -1
                                                                                                                       // for
                                                                                                                       // testing
            GameSettings.getCurrentSettings().setLastGenerator(dungeonGenerator);

//            triggerGroundCrumble(() -> {
//                Bukkit.getOnlinePlayers().forEach(player -> {
//                    var startRoom = dungeonGenerator.getStartRoom();
//                    if (startRoom == null) return;
//                    Vector3Int center = startRoom.get3DCenter();
//                    // Teleport into the center of the room (center + 0.5, +1 to stand above floor)
//                    Location spawn = new Location(player.getWorld(), center.getX() + 0.5, center.getY() + 1,
//                            center.getZ() + 0.5);
//                    player.setFallDistance(0);
//                    player.teleport(spawn);
//                });
//            });
            triggerGroundCrumble(() -> {
                // Per-tick TickEvent is now published by the base GameState for every
                // state, so nothing extra is needed here.
            });

        }, false);
    }

    @Override
    protected void onStop() {
    }

    /**
     * Sets all resource stats (health and mana) of every registered player to
     * their current max value, as derived through the StatEngine (so item/set
     * bonuses are included).
     */
    private void refillPlayerResources() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(rpg -> {
                rpg.setHealth(rpg.getMaxHealth());
                rpg.setMana(rpg.getMaxMana());
            });
        });
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerQuitEvent> quitAction = new EventAction<PlayerQuitEvent>(this::handleQuit,
                PlayerQuitEvent.class);
        EventAction<PlayerJoinEvent> joinAction = new EventAction<PlayerJoinEvent>(this::handleJoin,
                PlayerJoinEvent.class);
        EventAction<PlayerPortalEvent> portalAction = new EventAction<PlayerPortalEvent>(this::handlePortal,
                PlayerPortalEvent.class);
        EventAction<PlayerMoveEvent> moveAction = new EventAction<PlayerMoveEvent>(this::handleMovement,
                PlayerMoveEvent.class);
        addSubscriber(quitAction);
        addSubscriber(joinAction);
        addSubscriber(portalAction);
        addSubscriber(moveAction);
    }

    private void handleMovement(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (!event.getFrom().getWorld().getName().contains("dungeon")
                && !event.getFrom().getWorld().getName().contains("boss")) {
            return;
        }
        if (!EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId()).get().isAlive()) {
            return;
        }
        // TODO:
        /*
         * 
         * Suggested Implementation Steps (High Level) Create PlayerLocationTracker: A
         * singleton service responsible for maintaining the map of players to rooms and
         * handling location change events.
         * 
         * Modify Event Listener: Update your primary event listener to call
         * PlayerLocationTracker.updateLocation(player, newRoomId) upon detecting
         * movement.
         * 
         * Refactor Spawning Logic: Move the spawn logic out of individual room classes
         * and into a dedicated service that accepts (Player player, Room room) as
         * parameters. This service will use the SpawnTier (from your current file) to
         * determine difficulty and then instantiate/spawn mobs in that specific
         * context.
         */

        if (GameSettings.getCurrentSettings().getLastGenerator() instanceof RoomFirstDungeonGenerator3D) {
            Point3D toPoint = LocToPoint.locToBlock(event.getTo());
            int currentChunkX = DungeonSpawnManager.chunkFromBlock(toPoint.getX());
            int currentChunkY = DungeonSpawnManager.chunkFromBlock(toPoint.getY());
            int currentChunkZ = DungeonSpawnManager.chunkFromBlock(toPoint.getZ());

            DungeonSpawnManager.getInstance()
                    .getNearbyActiveSpawnLocations(currentChunkX, currentChunkY, currentChunkZ, 1).stream()
                    .filter(location -> location.getPosition().distanceSqrd(toPoint) <= SPAWN_RADIUS_SQRD)
                    .forEach(location -> {
                        if (DungeonSpawnManager.getInstance().consumeSpawnLocation(location)) {
                            spawnMob(location);
                        }
                    });
        }
    }

    private void handlePortal(PlayerPortalEvent event) {

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
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
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
        player.teleport(holeCenter);
    }

    private void triggerGroundCrumble(Runnable onComplete) {
        World world = holeCenter.getWorld();
        if (world == null) {
            return;
        }

        // Play initial crumbling sound
        world.playSound(holeCenter, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 3.0f, 0.2f);

        // Get all blocks to crumble using cached offsets
        List<Block> blocksToDestroy = new ArrayList<>();

        for (Point3D offset : CACHED_CRUMBLE_OFFSETS) {
            Block block = world.getBlockAt(holeCenter.getBlockX() + offset.getX(),
                    holeCenter.getBlockY() + offset.getY(), holeCenter.getBlockZ() + offset.getZ());
            blocksToDestroy.add(block);
        }

        // Crumble all blocks at once with slight delay for dramatic effect
        scheduler.runTaskLater(() -> {
            // Play additional impact sound
            world.playSound(holeCenter, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2.0f, 0.1f);

            // Convert all blocks to falling blocks simultaneously
            for (Block block : blocksToDestroy) {
                crumbleBlockInstantly(block, world);
            }

            // Announce the ground collapse
            Bukkit.broadcastMessage("§c§lThe ground crumbles beneath your feet!");
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 2, 2, false, false));
            });
            if (onComplete != null) {
                onComplete.run();
            }
        }, 10L); // Small delay for dramatic effect
    }

    /**
     * Converts a single block into a falling block with physics
     */
    private void crumbleBlockInstantly(Block block, World world) {
        Material blockType = block.getType();
        Location blockLoc = block.getLocation().add(0.5, 0.1, 0.5); // Slightly above center

        // Set block to air first
        block.setType(Material.AIR);

        // Create falling block entity
        FallingBlock fallingBlock = world.spawnFallingBlock(blockLoc, blockType.createBlockData());

        // Add random velocity for realistic crumbling effect
        Vector velocity = new Vector((RANDOM.nextDouble() - 0.5) * 0.4, // Random X velocity
                RANDOM.nextDouble() * 0.15, // Small upward velocity
                (RANDOM.nextDouble() - 0.5) * 0.4 // Random Z velocity
        );

        fallingBlock.setVelocity(velocity);

        // Configure falling block properties
        fallingBlock.setDropItem(false); // Don't drop items (cleaner effect)
        fallingBlock.setHurtEntities(false); // Don't hurt players

        // Remove falling block after some time if it doesn't land properly
        scheduler.runTaskLater(() -> {
            if (fallingBlock.isValid() && !fallingBlock.isDead()) {
                fallingBlock.remove();
            }
        }, 200L); // 10 seconds cleanup
    }

    private void spawnMob(SpawnLocation spawnLocation) {
        if (spawnLocation == null || world == null) {
            return;
        }
        BukkitEntityFactory.spawnHostileVanillaDungeonMob(spawnLocation.getMaxEnemyLevel(), spawnLocation, world);
    }

}
