package dev.bukkit.event.subscribers;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.utils.BukkitMessageSender;
import dev.core.ability.AbilityAction;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.progression.PlayerClassProgression;
import dev.core.utils.MessageComponent;
import dev.core.utils.MessageText;

public class PlayerSubscriber {

    private EventBusInterface eventBus;
    private ClassProgressionService classProgressionService;

    final double STILL = -0.0784000015258789;
    private Plugin plugin;

    public PlayerSubscriber(ClassProgressionService classProgressionService, EventBusInterface eventBus,
            Plugin plugin) {
        this.classProgressionService = classProgressionService;
        this.eventBus = eventBus;
        this.plugin = plugin;
    }

    public void subscribe() {
        subscribeJoin();
        subscribeQuit();
        subscribeInteract();
        subscribeQuit();
        subscribeSwap();
        subscribeShift();
        subscribeJump();
        subscribeDeath();
        subscribeRespawn();
        subscribeInventoryClick();
        subscribeSpawnWorld();
        subscribeSwapHands();
        subscribeInventoryDrag();
        subscribeInventoryClose();
        subscribePickup();
        subscribeDropItem();
    }

    public void subscribeJoin() {
        eventBus.subscribe(new EventAction<>(event -> {
            BukkitPlayerEntity playerEntity = new BukkitPlayerEntity(event.getPlayer());
            EntityManager.getInstance().registerEntity(playerEntity);
            BukkitInventorySync.syncInventory(playerEntity, event.getPlayer());
            for (RPGClassType rpgClassType : RPGClassType.values()) {
                PlayerClassProgression progression = playerEntity.getPlayerProgression().getProgression(rpgClassType);
                PlayerClassProgression cachedProgression = classProgressionService
                        .getProgression(event.getPlayer().getUniqueId(), rpgClassType);
                progression.setLevel(cachedProgression.getLevel());
                progression.setUsableItems(cachedProgression.getUsableItems());
                progression.setXp(cachedProgression.getXp());
            }
            playerEntity.getPlayerProgression().setActiveClass(
                    classProgressionService.getActiveClass(playerEntity.getUuid()), playerEntity.getStatManager());
        }, PlayerJoinEvent.class));
        eventBus.subscribe(new EventAction<>(event -> {
            BukkitMessageSender.getInstance().sendLine(event.getPlayer(), ChatColor.AQUA.toString());
            BukkitMessageSender.getInstance().sendCenteredMessage(event.getPlayer(),
                    MessageComponent.of(MessageText.INFO_PLAYER_JOINED, event.getPlayer().getName()));
            BukkitMessageSender.getInstance().sendLine(event.getPlayer(), ChatColor.AQUA.toString());
            event.setJoinMessage("");
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("&a&m                                                                             "), false);
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("<red>Okay, so, this message is longer than 1 line of text. The color code should pass and it should center</red>"));
//			BukkitMessageSender.getInstance().sendDebugMessage(event.getPlayer(), MessageComponent.of("If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default. Show the changes recorded in the stash entry as a diff between the stashed contents and the commit back when the stash entry was first created. By default, the command shows the diffstat, but it will accept any format known to git diff (e.g., git stash show -p stash@{1} to view the second most recent entry in patch form). If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default."));
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("&a&m                                                                             "));
//			BukkitMessageSender.getInstance().sendCenteredMessage(event.getPlayer(), MessageComponent.of("<red>Okay, so, this message is longer than 1 line of text. The color code should pass and it should center</red>"));
//			BukkitMessageSender.getInstance().sendCenteredMessage(MessageComponent.of("Okay, so, this message is longer than 1 line of text. The color code should pass and it should center"));
//			BukkitMessageSender.getInstance().sendLine(event.getPlayer(), "<bold><blue> </blue></bold>");
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("&a&m                                                                              "), false);
//			BukkitMessageSender.getInstance().sendLine(event.getPlayer(), ChatColor.AQUA.toString());
//			BukkitMessageSender.getInstance().sendCenteredDebugMessage(event.getPlayer(), MessageComponent.of( "If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default. Show the changes recorded in the stash entry as a diff between the stashed contents and the commit back when the stash entry was first created. By default, the command shows the diffstat, but it will accept any format known to git diff (e.g., git stash show -p stash@{1} to view the second most recent entry in patch form). If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default."));
        }, PlayerJoinEvent.class, EventAction.LOWEST_PRIORITY));
    }

    public void subscribeSpawnWorld() {
        eventBus.subscribe(new EventAction<>(event -> {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
            optional.ifPresent(rpgEntity -> {
                BukkitInventorySync.syncInventory(rpgEntity, event.getPlayer());
            });
        }, PlayerSpawnLocationEvent.class));
    }

    public void subscribeQuit() {
        eventBus.subscribe(new EventAction<>(event -> {
            BukkitPlayerEntity playerEntity = new BukkitPlayerEntity(event.getPlayer());
            classProgressionService.saveAll(playerEntity.getUuid());
            EntityManager.getInstance().removeEntity(playerEntity.getUuid());
        }, PlayerQuitEvent.class));
    }

    public void subscribeSwap() {
        eventBus.subscribe(new EventAction<>(event -> {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
            optional.ifPresent(rpgEntity -> {
                BukkitInventorySync.updateMainHand(rpgEntity, event.getPlayer(), event.getNewSlot());
            });
        }, PlayerItemHeldEvent.class));
    }

    public void subscribeSwapHands() {
        eventBus.subscribe(new EventAction<>(event -> {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
            optional.ifPresent(rpgEntity -> {
                BukkitInventorySync.updateMainAndOffHand(rpgEntity, event.getPlayer(), event.getOffHandItem(),
                        event.getMainHandItem());
            });
        }, PlayerSwapHandItemsEvent.class));
    }

    public void subscribeInteract() {
        eventBus.subscribe(new EventAction<>(event -> {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
            if (optional.isPresent()) {
                if (event.getAction().toString().contains("RIGHT_CLICK")) {
                    if (event.getPlayer().isSneaking()) {
                        optional.get().triggerAbility(AbilityAction.SHIFT_RIGHT_CLICK);
                        optional.get().triggerAbility(AbilityAction.RIGHT_CLICK);
                    } else {
                        optional.get().triggerAbility(AbilityAction.RIGHT_CLICK);
                    }
                }

                if (event.getAction().toString().contains("LEFT_CLICK")) {
                    if (event.getPlayer().isSneaking()) {
                        optional.get().triggerAbility(AbilityAction.SHIFT_LEFT_CLICK);
                        optional.get().triggerAbility(AbilityAction.LEFT_CLICK);
                    } else {
                        optional.get().triggerAbility(AbilityAction.LEFT_CLICK);
                    }
                }
            }
        }, PlayerInteractEvent.class));
    }

    public void subscribeJump() {
        eventBus.subscribe(new EventAction<>(event -> {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
            if (optional.isPresent()) {
                Player player = event.getPlayer();
                boolean isJumping = player.getVelocity().getY() > STILL;
                if (isJumping) {
                    optional.get().triggerAbility(AbilityAction.JUMP);
                }
            }
        }, PlayerMoveEvent.class));
    }

    public void subscribeShift() {
        eventBus.subscribe(new EventAction<>(event -> {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
            if (optional.isPresent()) {
                if (event.isSneaking()) {
                    optional.get().triggerAbility(AbilityAction.SHIFT);
                }
            }
        }, PlayerToggleSneakEvent.class));
    }

    public void subscribeRespawn() {
        eventBus.subscribe(new EventAction<>(event -> {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
            if (optional.isPresent()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BukkitInventorySync.syncInventory(optional.get(), event.getPlayer());
                });
            }
        }, PlayerRespawnEvent.class));
    }

    public void subscribeInventoryClick() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getWhoClicked() instanceof Player player) {
                Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                if (optional.isPresent()) {
                    BukkitInventorySync.syncInventoryDiff(optional.get(), player);
                }
            }
        }, InventoryClickEvent.class));
    }

    public void subscribeInventoryDrag() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getWhoClicked() instanceof Player player) {
                Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                if (optional.isPresent()) {
                    BukkitInventorySync.syncInventoryDiff(optional.get(), player);
                }
            }
        }, InventoryDragEvent.class));
    }

    public void subscribeInventoryClose() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getPlayer() instanceof Player player) {
                EntityManager.getInstance().getEntity(player.getUniqueId())
                        .ifPresent(rpg -> BukkitInventorySync.syncInventoryDiff(rpg, player));
            }
        }, InventoryCloseEvent.class));
    }

    public void subscribePickup() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getEntity() instanceof Player player) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(rpg -> {
                        BukkitInventorySync.syncInventoryDiff(rpg, player);
                    });
                });
            }
        }, EntityPickupItemEvent.class));
    }

    public void subscribeDropItem() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getPlayer() instanceof Player player) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(rpg -> {
                        BukkitInventorySync.syncInventoryDiff(rpg, player);
                    });
                });
            }
        }, PlayerDropItemEvent.class));
    }

    public void subscribeDeath() {
        eventBus.subscribe(new EventAction<>(event -> {
            if (event.getTarget() instanceof BukkitPlayerEntity entity) {
                entity.getPlayer().setGameMode(GameMode.SPECTATOR);
                Bukkit.broadcastMessage(
                        "Player " + entity.getPlayer().getDisplayName() + " is dead. Respawning in 3s.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    EntityManager.getInstance().revive(entity.getUuid());
                    entity.setHealth(entity.getMaxHealth());
                    entity.getPlayer().setGameMode(GameMode.SURVIVAL);
                    entity.setAlive(true);
                }, 20 * 3);
            }
        }, RPGEntityDeathEvent.class));
    }
}
