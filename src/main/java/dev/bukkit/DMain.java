package dev.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.command.CommandManager;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.event.bukkitListeners.CancelledListener;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.event.bukkitListeners.EventListener;
import dev.bukkit.event.subscribers.PlayerSubscriber;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.event.EventBusInterface;
import dev.core.item.RPGItemRegistry;

public final class DMain extends JavaPlugin {
	private EventBusInterface eventBusInterface;
	private EffectManagerInterface effectManagerInterface;
	private RPGItemRegistry itemRegistry;
	private EntityManager entityManager;
	private BukkitTask runTaskTimer;
	private CombatListener combatListener;

	private static DMain instance;

	@Override
	public void onEnable() {
		instance = this;
		// Plugin startup logic
		Bukkit.getConsoleSender().sendMessage("Dmain started.");

		entityManager = EntityManager.getInstance();
		effectManagerInterface = BukkitEffectManager.getInstance();
		itemRegistry = RPGItemRegistry.getInstance();

		eventBusInterface = BukkitEventBus.getInstance();
		CommandManager.getInstance().registerCommands(this);
		Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
		Bukkit.getPluginManager().registerEvents(new CancelledListener(this), this);
		combatListener = new CombatListener(this);
		Bukkit.getPluginManager().registerEvents(combatListener, this);
		new PlayerSubscriber(eventBusInterface, this).subscribe();

		runTaskTimer = Bukkit.getScheduler().runTaskTimer(this, () -> {
			effectManagerInterface.tick(System.currentTimeMillis());
			entityManager.tick(System.currentTimeMillis());
		}, 0, 1);
	}

	@Override
	public void onDisable() {
		runTaskTimer.cancel();
		effectManagerInterface.cancelAll();
		combatListener.cleanup();
	}

	public static DMain getInstance() {
		return instance;
	}
}
