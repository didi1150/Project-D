package dev.bukkit;

import java.util.Map;

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
import dev.bukkit.storage.BukkitConfigManager;
import dev.bukkit.storage.progression.BukkitConfigProgressionDatabase;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.storage.progression.HashMapProgressionCache;
import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.storage.AbilityLoader;
import dev.core.entity.EntityManager;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemLoader;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.stat.DefaultStats;
import dev.core.stat.Stat;
import dev.core.stat.StatType;
import dev.core.stat.loader.StatLoader;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.database.ProgressionCacheStrategy;
import dev.core.storage.database.ProgressionDatabaseStrategy;

public final class DMain extends JavaPlugin {
    private EventBusInterface eventBusInterface;
    private EffectManagerInterface effectManagerInterface;
    private RPGItemRegistry itemRegistry;
    private EntityManager entityManager;
    private BukkitTask runTaskTimer;
    private CombatListener combatListener;

    private static DMain instance;
    private BukkitConfigManager configManager;
    private ClassProgressionService progressionService;

    @Override
    public void onEnable() {
        instance = this;
        // Plugin startup logic
        Bukkit.getConsoleSender().sendMessage("Dmain started.");

        entityManager = EntityManager.getInstance();
        effectManagerInterface = BukkitEffectManager.getInstance();
        itemRegistry = RPGItemRegistry.getInstance();
        AbilityRegistry.preregister();

        configManager = new BukkitConfigManager(this);
        // Load Default Stats
        ConfigProvider statsConfig = configManager.getProvider("stats.yml");
        Map<RPGClassType, Map<StatType, Stat>> defaultStats = StatLoader.loadDefaultStats(statsConfig);
        DefaultStats.loadAll(defaultStats);

        // Load abilities.yml
        ConfigProvider abilitiesConfig = configManager.getProvider("abilities.yml");
        Map<String, Ability> abilities = AbilityLoader.loadAll(abilitiesConfig);
        Bukkit.getConsoleSender().sendMessage("Loaded " + abilities.size() + " abilities(s).");
        AbilityRegistry.updateAll(abilities);

        // Load items.yml
        ConfigProvider itemsConfig = configManager.getProvider("items.yml");
        Map<String, RPGItem> items = RPGItemLoader.loadAll(itemsConfig);
        Bukkit.getConsoleSender().sendMessage("Loaded " + items.size() + " item(s).");
        itemRegistry.addAll(items);

        ConfigProvider provider = configManager.getProvider("progression.yml");

        ProgressionCacheStrategy cache = new HashMapProgressionCache();
        ProgressionDatabaseStrategy database = new BukkitConfigProgressionDatabase(provider);

        progressionService = new ClassProgressionService(cache, database);

        eventBusInterface = BukkitEventBus.getInstance();
        CommandManager.getInstance(itemsConfig, progressionService).registerCommands(this);
        Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CancelledListener(this), this);
        combatListener = new CombatListener(this);
        Bukkit.getPluginManager().registerEvents(combatListener, this);
        new PlayerSubscriber(progressionService, eventBusInterface, this).subscribe();

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
        configManager.saveAll();
    }

    public static DMain getInstance() {
        return instance;
    }
}
