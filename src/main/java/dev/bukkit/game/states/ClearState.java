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
import dev.bukkit.game.dungeon.DungeonBuilderBukkit;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.TickEvent;
import dev.core.game.GameState;
import dev.core.game.ScheduledTask;
import dev.core.game.coords.Point3D;
import dev.core.game.dungeon.Dungeon;
import dev.core.game.dungeon.DungeonGenerator;
import dev.core.game.dungeon.SpawnLocation;
import dev.core.game.settings.GameSettings;
import dev.core.progression.PlayerClassProgression;
import dev.core.utils.MessageSenderInterface;

public class ClearState extends GameState {

    public final static String NAME = "CLEARSTATE";

    private ScheduledTask scheduledTask;

    private long lastMillis;

    private EventBusInterface eventBus;

    private EffectManagerInterface effectManager;

    private EntityManager entityManager;

    private ClassProgressionService classProgressionService;

    private Dungeon dungeon;

    private static final int SPAWN_RADIUS_SQRD = 225;
    private List<Point3D> usedLocations;

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

    private Location dungeonSpawn;

    private MessageSenderInterface messageSender;

    private Plugin plugin;

    private World world;

    public ClearState(Point3D holeCenter, EventBusInterface eventBus, EffectManagerInterface effectManager,
            EntityManager entityManager, ClassProgressionService classProgressionService,
            MessageSenderInterface messageSender, Plugin plugin) {
        super(NAME, -1, eventBus);
        this.messageSender = messageSender;
        this.plugin = plugin;
        this.holeCenter = PointToLocation.blockToLoc(holeCenter);
        this.eventBus = eventBus;
        this.effectManager = effectManager;
        this.entityManager = entityManager;
        this.classProgressionService = classProgressionService;
        lastMillis = System.currentTimeMillis();
        this.usedLocations = new ArrayList<Point3D>();
    }

    @Override
    protected void onStart() {
        // Handle ground opening
        scheduler.runTaskLaterAsync(() -> {
            dungeon = new DungeonGenerator(0, messageSender)
                    .generateDungeon(roomCount(GameSettings.getCurrentSettings().getFloor()), new Point3D(0, 0, 0));
            world = Bukkit.getWorld(GameSettings.getCurrentSettings().getDungeonWorld());
            GameSettings.getCurrentSettings().setDungeon(dungeon);
            scheduler.runTaskLater(() -> {
                buildDungeon(dungeon, world);
            }, 0);
        }, 0);
    }

    private void buildDungeon(Dungeon dungeon, World world) {
        new DungeonBuilderBukkit(plugin, world).buildDungeon(dungeon, () -> {
            triggerGroundCrumble(() -> {
                teleportPlayerToDungeon(dungeon, world);
            });

        });
    }

    private void teleportPlayerToDungeon(Dungeon dungeon, World world) {
        scheduler.runTaskLater(() -> {
            Bukkit.getOnlinePlayers().forEach(player -> {
                Point3D center = dungeon.getStartRoom().getCenter();
                dungeonSpawn = new Location(world, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
                player.setFallDistance(0);
                player.teleport(dungeonSpawn);

                scheduledTask = scheduler.runTaskTimer(() -> {
                    float tickDelta = (System.currentTimeMillis() - lastMillis) / 1000f * 20f;
                    lastMillis = System.currentTimeMillis();
                    eventBus.sendEvent(new TickEvent(tickDelta));

                    effectManager.tick(System.currentTimeMillis());
                    entityManager.tick(System.currentTimeMillis());
                }, 0, 1);
            });
        }, 20L * 2);
    }

    @Override
    protected void onStop() {
        cancelTask();
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerQuitEvent> quitAction = new EventAction<PlayerQuitEvent>(this::handleQuit,
                PlayerQuitEvent.class);
        EventAction<PlayerJoinEvent> joinAction = new EventAction<PlayerJoinEvent>(this::handleJoin,
                PlayerJoinEvent.class);

        EventAction<PlayerMoveEvent> moveAction = new EventAction<PlayerMoveEvent>(this::handleMovement,
                PlayerMoveEvent.class);
        addSubscriber(quitAction);
        addSubscriber(joinAction);
        addSubscriber(moveAction);
    }

    private void cancelTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    private void handleMovement(PlayerMoveEvent event) {
        dungeon.getAllSpawnLocations().forEach(location -> {
            if (!usedLocations.contains(location.getPosition())
                    && location.getPosition().distanceSqrd(LocToPoint.locToBlock(event.getTo())) <= SPAWN_RADIUS_SQRD) {
                usedLocations.add(location.getPosition());

                spawnMob(location);
            }
        });
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
                0, // Small upward velocity
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
        }, 20L); // 1 second cleanup
    }

    private int roomCount(int floor) {
        Random random = new Random();
        return switch (floor) {
        case 1: {
            yield random.nextInt(5, 8);
        }
        case 2: {
            yield random.nextInt(8, 13);
        }
        case 3: {
            yield random.nextInt(13, 17);
        }
        case 4: {
            yield random.nextInt(17, 22);
        }
        case 5: {
            yield random.nextInt(22, 50);
        }
        default:
            yield 5;
        };
    }

    private void spawnMob(SpawnLocation spawnLocation) {
        BukkitEntityFactory.spawnHostileVanillaDungeonMob(spawnLocation.getMaxEnemyLevel(), spawnLocation, world);
    }

}
