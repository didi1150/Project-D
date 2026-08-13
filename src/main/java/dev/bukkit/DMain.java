package dev.bukkit;

import java.io.File;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.ability.BukkitEffectRegistry;
import dev.bukkit.ability.BukkitParticleTestEffect;
import dev.bukkit.ability.BukkitSwingBoneEffect;
import dev.bukkit.command.CommandManager;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.event.bukkitListeners.EventBusRegistry;
import dev.bukkit.event.bukkitListeners.EventListener;
import dev.bukkit.event.subscribers.CancelSubscriber;
import dev.bukkit.event.subscribers.PlayerSubscriber;
import dev.bukkit.entity.boss.BukkitBossStageTypeRegistry;
import dev.bukkit.entity.boss.BukkitBossStrategyRegistry;
import dev.bukkit.game.dungeon.proceduralDungeon.BukkitVoidWorldGenerator;
import dev.bukkit.game.dungeon.proceduralDungeon.SimpleDungeonBuilderBukkit;
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
import dev.core.entity.boss.BossDefinitionLoader;
import dev.core.entity.mob.MobDefinitionLoader;
import dev.core.entity.mob.MobDefinitionRegistry;
import dev.core.entity.boss.BossDefinitionRegistry;
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
    private MessageSenderInterface messageSenderInterface;
    private GameStateController gameStateController;
    private dev.bukkit.game.boss.BossArenaManager bossArenaManager;

    @Override
    public void onEnable() {
        instance = this;
        // Plugin startup logic
        Bukkit.getConsoleSender().sendMessage("Dmain started.");

        entityManager = EntityManager.getInstance();
        effectManagerInterface = BukkitEffectManager.getInstance();
        itemRegistry = RPGItemRegistry.getInstance();
        messageSenderInterface = BukkitMessageSender.getInstance();
        AbilityRegistry.preregister();

        // ---- Extension point: wire ability ids to their Bukkit effects ----
        // (register additional abilities via AbilityRegistry.register(...) BEFORE
        //  abilities.yml loads below so the config metadata is applied to them.)
        BukkitEffectRegistry.register("PARTICLE_TEST_ABILITY", BukkitParticleTestEffect::new);
        BukkitEffectRegistry.register("BONE_SWING", BukkitSwingBoneEffect::new);

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

        // ==============================================[ Load bosses.yml
        // ]=====================================================
        ConfigProvider bossesConfig = configManager.getProvider("bosses.yml");
        BossDefinitionRegistry.getInstance().registerAll(BossDefinitionLoader.loadAll(bossesConfig,
                new BukkitBossStageTypeRegistry(), new BukkitBossStrategyRegistry()));

        // ==============================================[ Load dungeon-mobs.yml
        // ]=============================================
        ConfigProvider dungeonMobsConfig = configManager.getProvider("dungeon-mobs.yml");
        MobDefinitionRegistry.getInstance()
                .registerAll(MobDefinitionLoader.loadAll(dungeonMobsConfig).values());
        Bukkit.getConsoleSender().sendMessage(
                "Loaded " + MobDefinitionRegistry.getInstance().size() + " dungeon mob definition(s).");

        // ==============================================[ Setup Settings
        // ]=====================================================
        ConfigProvider setupConfig = configManager.getProvider("setup.yml");
        GameSettings gameSettings = GameSettings.getCurrentSettings();
        GameSettingsLoader gameSettingsLoader = new GameSettingsLoader(gameSettings, setupConfig);
        gameSettingsLoader.load();
        // Initialize boss arena manager from setup config
        this.bossArenaManager = dev.bukkit.game.boss.BossArenaManager.createDefault(this);
        if (gameSettings.getDungeonWorld() != null && Bukkit.getWorld(gameSettings.getDungeonWorld()) == null) {
            Bukkit.createWorld(
                    new WorldCreator(gameSettings.getDungeonWorld()).generator(new BukkitVoidWorldGenerator()));
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
        EventBusRegistry.registerAll(instance);

        gameStateController = new GameStateController(new BukkitTaskScheduler(this), effectManagerInterface,
                entityManager);
        if (gameSettings.isSetupMode()) {
            gameStateController.addState(new SetupState(eventBusInterface));
        }
        gameStateController.addState(new PreLobbyState(gameSettings.getPreLobbySpawn(), gameSettings.getMinPlayers(),
                15, eventBusInterface));
        gameStateController.addState(new SelectClassState(gameSettings.getHoleCenter(),
                gameSettings.getSelectionSpawn(), gameSettings.getSelectionLocations(), eventBusInterface));
        gameStateController.addState(new SelectItemState(eventBusInterface));
        gameStateController.addState(new ClearState(gameSettings.getHoleCenter(), eventBusInterface,
                progressionService, messageSenderInterface, this));
        gameStateController.addState(new BossState(eventBusInterface, progressionService));
        gameStateController.addState(new PostGameState(eventBusInterface, progressionService, this));
        gameStateController.start();

        CommandManager commandManager = CommandManager.getInstance(itemsConfig, progressionService, gameStateController,
                gameSettingsLoader, eventBusInterface);
        SimpleDungeonBuilderBukkit.initDungeonTestCommand(commandManager);
        commandManager.registerCommands(this);
        Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
//        new CancelledListener(instance);
        new CancelSubscriber(eventBusInterface, instance);
//        Bukkit.getPluginManager().registerEvents(new CancelledListener(this, protocolManager), this);
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
        eventBusInterface.getSubscribed().clear();

        World dungeonWorld = Bukkit.getWorld(GameSettings.getCurrentSettings().getDungeonWorld());
        if (dungeonWorld != null) {
            unloadWorld(dungeonWorld);
            deleteWorld(dungeonWorld.getWorldFolder());
        } else {
            Bukkit.getLogger().info("No dungeon world found during shutdown; skipping unload/delete.");
        }
    }

    public static DMain getInstance() {
        return instance;
    }

    public dev.bukkit.game.boss.BossArenaManager getBossArenaManager() {
        return bossArenaManager;
    }

    public CombatListener getCombatListener() {
        return combatListener;
    }

    public void unloadWorld(World world) {
        if (world != null) {
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
