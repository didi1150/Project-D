package dev.bukkit.event.subscribers;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitInventorySync;
import dev.core.ability.AbilityAction;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;

public class PlayerSubscriber {

	private EventBusInterface eventBus;

	final double STILL = -0.0784000015258789;
	private Plugin plugin;

	public PlayerSubscriber(EventBusInterface eventBus, Plugin plugin) {
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
	}

	public void subscribeJoin() {
		eventBus.subscribe(new EventAction<>(event -> {
			BukkitPlayerEntity playerEntity = new BukkitPlayerEntity(new StatManager(DefaultStats.baseBaseStats()),
					event.getPlayer());
			Bukkit.getScheduler().runTask(plugin, () -> {
				EntityManager.getInstance().registerEntity(playerEntity);
				BukkitInventorySync.syncInventory(playerEntity, event.getPlayer());
			});
		}, PlayerJoinEvent.class));
	}

	public void subscribeSpawnWorld() {
		eventBus.subscribe(new EventAction<>(event -> {
			Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
			System.out.println("Player found: " + optional.isPresent());
			optional.ifPresent(rpgEntity -> {
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					BukkitInventorySync.syncInventory(rpgEntity, event.getPlayer());
				}, 10);
			});
		}, PlayerSpawnLocationEvent.class));
	}

	public void subscribeQuit() {
		eventBus.subscribe(new EventAction<>(event -> {
			BukkitPlayerEntity playerEntity = new BukkitPlayerEntity(new StatManager(DefaultStats.baseBaseStats()),
					event.getPlayer());
			EntityManager.getInstance().removeEntity(playerEntity.getUuid());
		}, PlayerQuitEvent.class));
	}

	public void subscribeSwap() {
		eventBus.subscribe(new EventAction<>(event -> {
			Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
			optional.ifPresent(rpgEntity -> {
				Bukkit.getScheduler().runTask(plugin, () -> {
					BukkitInventorySync.updateSlotByIndex(rpgEntity, event.getPlayer(), event.getNewSlot());
				});
			});
		}, PlayerItemHeldEvent.class));
	}

	public void subscribeInteract() {
		eventBus.subscribe(new EventAction<>(event -> {
			Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
			if (optional.isPresent()) {
				if (event.getAction().toString().contains("RIGHT_CLICK")) {
					if (event.getPlayer().isSneaking()) {
						optional.get().triggerAbility(AbilityAction.SHIFT_RIGHT_CLICK);
					} else {
						optional.get().triggerAbility(AbilityAction.RIGHT_CLICK);
					}
				}

				if (event.getAction().toString().contains("LEFT_CLICK")) {
					if (event.getPlayer().isSneaking()) {
						optional.get().triggerAbility(AbilityAction.SHIFT_LEFT_CLICK);
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

	public void subscribeDeath() {
		eventBus.subscribe(new EventAction<>(event -> {
			// TODO
		}, PlayerDeathEvent.class));
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
					int slot = event.getSlot();
					Bukkit.getScheduler().runTask(plugin, () -> {
						BukkitInventorySync.updateSlotByIndex(optional.get(), player, slot);
					});
				}
			}
		}, InventoryClickEvent.class));

	}
}
