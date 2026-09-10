package dev.bukkit.game.states;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.game.coords.PointToLocation;
import dev.bukkit.game.dungeon.proceduralDungeon.SimpleDungeonBuilderBukkit;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.summon.SummonedEntityFactory;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.settings.GameSettings;
import dev.core.progression.PlayerClassProgression;

public class PostGameState extends GameState {

    private static final long DURATION = 20 * 60L;
    private static final String NAME = "POSTSTATE";
    private ClassProgressionService classProgressionService;
    private Plugin plugin;

    public PostGameState(EventBusInterface eventBus, ClassProgressionService classProgressionService, Plugin plugin) {
        super(NAME, DURATION, eventBus);
        this.classProgressionService = classProgressionService;
        this.plugin = plugin;
    }

    @Override
    protected void onStart() {
        // The run is over: clear all player-owned summons so the next run starts
        // clean. Souls stay on the tomes (the inventory is cleared again when the
        // new run begins), so a Support can carry dungeon souls into the boss
        // fight; this only runs after the boss fight has ended.
        SummonedEntityFactory.despawnAll();

        SimpleDungeonBuilderBukkit simpleDungeonBuilderBukkit = new SimpleDungeonBuilderBukkit(plugin,
                Bukkit.getWorld(GameSettings.getCurrentSettings().getDungeonWorld()));

        simpleDungeonBuilderBukkit.resetDungeonSpace(GameSettings.getCurrentSettings().getLastGenerator(), null);

//        DungeonBuilderBukkit dungeonBuilderBukkit = new DungeonBuilderBukkit(plugin,
//                Bukkit.getWorld(GameSettings.getCurrentSettings().getDungeonWorld()));
//        dungeonBuilderBukkit.resetDungeon(GameSettings.getCurrentSettings().getDungeon(), null);

        Bukkit.getOnlinePlayers().forEach(player -> {
            player.teleport(PointToLocation.viewToLoc(GameSettings.getCurrentSettings().getPreLobbySpawn()));
            EntityManager.getInstance().getDeadEntities().forEach(
                    entity -> EntityManager.getInstance().revive(entity.getUuid()));
        });
    }

    @Override
    protected void onStop() {
        scheduler.cancelAllTasks();
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerQuitEvent> quitAction = new EventAction<PlayerQuitEvent>(this::handleQuit,
                PlayerQuitEvent.class);
        EventAction<PlayerJoinEvent> joinAction = new EventAction<PlayerJoinEvent>(this::handleJoin,
                PlayerJoinEvent.class);

        EventAction<EntityDamageEvent> damageAction = new EventAction<EntityDamageEvent>(this::handleDamage,
                EntityDamageEvent.class);
        EventAction<BlockBreakEvent> blockBreakAction = new EventAction<BlockBreakEvent>(this::handleBlockBreak,
                BlockBreakEvent.class);
        EventAction<BlockPlaceEvent> blockPlaceAction = new EventAction<BlockPlaceEvent>(this::handleBlockPlace,
                BlockPlaceEvent.class);

        addSubscriber(quitAction);
        addSubscriber(joinAction);
        addSubscriber(blockPlaceAction);
        addSubscriber(blockBreakAction);
        addSubscriber(damageAction);
    }

    private void handleDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    private void handleBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    private void handleBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
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
    }

}
