package dev.bukkit;

import java.io.File;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.command.CommandManager;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.event.bukkitListeners.CancelledListener;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.event.bukkitListeners.EventListener;
import dev.bukkit.event.subscribers.PlayerSubscriber;
import dev.bukkit.game.dungeon.BukkitStoneWorldGenerator;
import dev.bukkit.game.scheduler.BukkitTaskScheduler;
import dev.bukkit.game.states.BossState;
import dev.bukkit.game.states.ClearState;
import dev.bukkit.game.states.PostGameState;
import dev.bukkit.game.states.PreLobbyState;
import dev.bukkit.game.states.SelectClassState;
import dev.bukkit.game.states.SelectItemState;
import dev.bukkit.game.states.SetupState;
import dev.bukkit.storage.BukkitConfigManager;
import dev.bukkit.storage.progression.BukkitConfigProgressionDatabase;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.storage.progression.HashMapProgressionCache;
import dev.bukkit.utils.BukkitMessageSender;
import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.storage.AbilityLoader;
import dev.core.entity.EntityManager;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.game.GameStateController;
import dev.core.game.settings.GameSettings;
import dev.core.game.settings.GameSettingsLoader;
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
import dev.core.utils.MessageSenderInterface;

public final class DMain extends JavaPlugin {
    private EventBusInterface eventBusInterface;
    private EffectManagerInterface effectManagerInterface;
    private RPGItemRegistry itemRegistry;
    private EntityManager entityManager;
    private CombatListener combatListener;

    private static DMain instance;
    private BukkitConfigManager configManager;
    private ClassProgressionService progressionService;
    private ProtocolManager protocolManager;
    private MessageSenderInterface messageSenderInterface;
    private GameStateController gameStateController;

    @Override
    public void onEnable() {
        instance = this;
        // Plugin startup logic
        Bukkit.getConsoleSender().sendMessage("Dmain started.");

        entityManager = EntityManager.getInstance();
        effectManagerInterface = BukkitEffectManager.getInstance();
        itemRegistry = RPGItemRegistry.getInstance();
        protocolManager = ProtocolLibrary.getProtocolManager();
        messageSenderInterface = BukkitMessageSender.getInstance();
        AbilityRegistry.preregister();

        configManager = new BukkitConfigManager(this);
        // ==============================================[ Load Default Stats
        // ]===============================================
        ConfigProvider statsConfig = configManager.getProvider("stats.yml");
        Map<RPGClassType, Map<StatType, Stat>> defaultStats = StatLoader.loadDefaultStats(statsConfig);
        DefaultStats.loadAll(defaultStats);

        // ==============================================[ Load abilities.yml
        // ]================================================
        ConfigProvider abilitiesConfig = configManager.getProvider("abilities.yml");
        Map<String, Ability> abilities = AbilityLoader.loadAll(abilitiesConfig);
        Bukkit.getConsoleSender().sendMessage("Loaded " + abilities.size() + " abilities(s).");
        AbilityRegistry.updateAll(abilities);

        // ==============================================[ Load items.yml
        // ]=====================================================
        ConfigProvider itemsConfig = configManager.getProvider("items.yml");
        Map<String, RPGItem> items = RPGItemLoader.loadAll(itemsConfig);
        Bukkit.getConsoleSender().sendMessage("Loaded " + items.size() + " item(s).");
        itemRegistry.addAll(items);

        // ==============================================[ Setup Settings
        // ]=====================================================
        ConfigProvider setupConfig = configManager.getProvider("setup.yml");
        GameSettings gameSettings = GameSettings.getCurrentSettings();
        GameSettingsLoader gameSettingsLoader = new GameSettingsLoader(gameSettings, setupConfig);
        gameSettingsLoader.load();
        if (gameSettings.getDungeonWorld() != null && Bukkit.getWorld(gameSettings.getDungeonWorld()) == null) {
            Bukkit.createWorld(
                    new WorldCreator(gameSettings.getDungeonWorld()).generator(new BukkitStoneWorldGenerator()));
            System.out.println("Could not find world " + gameSettings.getDungeonWorld() + ", generating new one.");
        }

        // ==============================================[ Player Progression
        // ]=================================================
        ConfigProvider provider = configManager.getProvider("progression.yml");
        ProgressionCacheStrategy cache = new HashMapProgressionCache();
        ProgressionDatabaseStrategy database = new BukkitConfigProgressionDatabase(provider);
        progressionService = new ClassProgressionService(cache, database);

        // ==============================================[ Events
        // ]=============================================================
        eventBusInterface = BukkitEventBus.getInstance();

        gameStateController = new GameStateController(new BukkitTaskScheduler(this));
        if (gameSettings.isSetupMode()) {
            gameStateController.addState(new SetupState(eventBusInterface));
        }
        gameStateController.addState(new PreLobbyState(gameSettings.getPreLobbySpawn(), gameSettings.getMinPlayers(),
                15, eventBusInterface));
        gameStateController.addState(new SelectClassState(gameSettings.getHoleCenter(),
                gameSettings.getSelectionSpawn(), gameSettings.getSelectionLocations(), eventBusInterface));
        gameStateController.addState(new SelectItemState(eventBusInterface));
        gameStateController.addState(new ClearState(gameSettings.getHoleCenter(), eventBusInterface,
                effectManagerInterface, entityManager, progressionService, messageSenderInterface, this));
        gameStateController.addState(new BossState(eventBusInterface, progressionService));
        gameStateController.addState(new PostGameState(eventBusInterface, progressionService, this));
        gameStateController.start();

        CommandManager.getInstance(itemsConfig, progressionService, gameStateController, gameSettingsLoader,
                eventBusInterface).registerCommands(this);
        Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CancelledListener(this, protocolManager), this);
        combatListener = new CombatListener(this);
        Bukkit.getPluginManager().registerEvents(combatListener, this);
        new PlayerSubscriber(eventBusInterface, this).subscribe();
    }

    @Override
    public void onDisable() {
        gameStateController.stop();
        effectManagerInterface.cancelAll();
        combatListener.cleanup();
        configManager.saveAll();

        World dungeonWorld = Bukkit.getWorld(GameSettings.getCurrentSettings().getDungeonWorld());
        unloadWorld(dungeonWorld);
        deleteWorld(dungeonWorld.getWorldFolder());
    }

    public static DMain getInstance() {
        return instance;
    }

    public void unloadWorld(World world) {
        if (!world.equals(null)) {
            Bukkit.getServer().unloadWorld(world, true);
        }
    }

    public boolean deleteWorld(File path) {
        if (path.exists()) {
            File files[] = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteWorld(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }
}
