package dev.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.command.CommandManager;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.event.EventListener;
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

	@Override
	public void onEnable() {
		// Plugin startup logic
		Bukkit.getConsoleSender().sendMessage("Dmain started.");

		entityManager = EntityManager.getInstance();
		effectManagerInterface = BukkitEffectManager.getInstance();
		itemRegistry = RPGItemRegistry.getInstance();

		eventBusInterface = BukkitEventBus.getInstance();
		CommandManager.getInstance().registerCommands(this);
		Bukkit.getPluginManager().registerEvents(new EventListener(), this);

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
	}
}
