package dev.bukkit;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import dev.bukkit.game.dungeon.buildassets.BuildAssetHelper;
import dev.bukkit.game.dungeon.buildassets.BuildAssetManager;
import dev.bukkit.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import dev.bukkit.ability.BukkitBladeDanceEffect;
import dev.bukkit.ability.BukkitBouncyArrowEffect;
import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.ability.BukkitExplosiveArrowEffect;
import dev.bukkit.ability.BukkitFocusBeamEffect;
import dev.bukkit.ability.BukkitParticleTestEffect;
import dev.bukkit.ability.BukkitShadowWeaverDashEffect;
import dev.bukkit.ability.BukkitShadowWeaverPlaceEffect;
import dev.bukkit.ability.BukkitShieldBashEffect;
import dev.bukkit.ability.BukkitSmashEffect;
import dev.bukkit.ability.BukkitSoulRecallEffect;
import dev.bukkit.ability.BukkitSoulStoreEffect;
import dev.bukkit.ability.BukkitSoulSummonEffect;
import dev.bukkit.ability.BukkitSpinjitzuEffect;
import dev.bukkit.ability.BukkitSpiritSceptreBatEffect;
import dev.bukkit.ability.BukkitSwingBoneEffect;
import dev.bukkit.command.CommandManager;
import dev.bukkit.entity.boss.BukkitBossStageTypeRegistry;
import dev.bukkit.entity.boss.BukkitBossStrategyRegistry;
import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.event.bukkitListeners.EventBusRegistry;
import dev.bukkit.event.subscribers.ThreatPassiveSubscriber;
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
import dev.bukkit.item.display.LoreLabels;
import dev.bukkit.status.BukkitStatusEffectManager;
import dev.bukkit.status.StatusEffectBehaviorRegistry;
import dev.bukkit.status.behavior.AirborneStatusEffectBehavior;
import dev.bukkit.status.behavior.RootedStatusEffectBehavior;
import dev.bukkit.status.behavior.SlowedStatusEffectBehavior;
import dev.bukkit.status.behavior.StunnedStatusEffectBehavior;
import dev.bukkit.storage.BukkitConfigManager;
import dev.bukkit.storage.progression.BukkitConfigProgressionDatabase;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.storage.progression.HashMapProgressionCache;
import dev.bukkit.utils.BackstabUtils;
import dev.bukkit.utils.BukkitMessageSender;
import dev.bukkit.utils.HealAuraUtils;
import dev.bukkit.utils.ManaDiscountUtils;
import dev.core.ability.Ability;
import dev.core.ability.AbilityBehaviorRegistry;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.ActiveAbilityRegistry;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.impl.ArcaneCleaveAbility;
import dev.core.ability.impl.ArcaneManaRestoreAbility;
import dev.core.ability.impl.BladeDanceAbility;
import dev.core.ability.impl.BouncyArrowAbility;
import dev.core.ability.impl.ExplosiveArrowAbility;
import dev.core.ability.impl.FocusBeamAbility;
import dev.core.ability.impl.OrbStealthPassiveAbility;
import dev.core.ability.impl.ParticleTestAbility;
import dev.core.ability.impl.ShadowWeaverStaffAbility;
import dev.core.ability.impl.ShieldBashAbility;
import dev.core.ability.impl.SmashAbility;
import dev.core.ability.impl.SmokeShroudAbility;
import dev.core.ability.impl.SoulCollectorAbility;
import dev.core.ability.impl.SoulRecallAbility;
import dev.core.ability.impl.SoulRecallShiftAbility;
import dev.core.ability.impl.SoulSummonAbility;
import dev.core.ability.impl.SpinjitzuAbility;
import dev.core.ability.impl.SpiritSceptreAbility;
import dev.core.ability.impl.StackerAbility;
import dev.core.ability.impl.SwingBoneAbility;
import dev.core.ability.impl.TriVolleyAbility;
import dev.core.ability.passive.SetPassiveRegistry;
import dev.core.ability.storage.AbilityLoader;
import dev.core.entity.EntityManager;
import dev.core.entity.boss.BossDefinitionLoader;
import dev.core.entity.boss.BossDefinitionRegistry;
import dev.core.entity.mob.MobDefinitionLoader;
import dev.core.entity.mob.MobDefinitionRegistry;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriberScanner;
import dev.core.game.GameStateController;
import dev.core.game.settings.GameSettings;
import dev.core.game.settings.GameSettingsLoader;
import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;
import dev.core.item.loader.RPGItemLoader;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.stat.DefaultStats;
import dev.core.stat.Stat;
import dev.core.stat.StatType;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.loader.StatLoader;
import dev.core.stat.loader.StatMetadataLoader;
import dev.core.status.StatusEffectType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.database.ProgressionCacheStrategy;
import dev.core.storage.database.ProgressionDatabaseStrategy;
import dev.core.utils.MessageSenderInterface;
import dev.bukkit.ability.BukkitSmokeShroudEffect;
import dev.bukkit.ability.BukkitTriVolleyEffect;
import dev.bukkit.ability.behavior.ArcaneCleaveBehavior;
import dev.bukkit.ability.behavior.ArcaneManaRestoreBehavior;
import dev.bukkit.ability.behavior.BackstabBehavior;
import dev.bukkit.ability.behavior.BladeDanceBehavior;
import dev.bukkit.ability.behavior.HealAuraBehavior;
import dev.bukkit.ability.behavior.HunterBowBehavior;
import dev.bukkit.ability.behavior.ManaDiscountBehavior;
import dev.bukkit.ability.behavior.ShadowWeaverBehavior;
import dev.bukkit.ability.behavior.StackerBehavior;
import dev.bukkit.ability.behavior.StealthPassiveBehavior;
import dev.bukkit.ability.behavior.ThreatBehavior;
import dev.bukkit.ability.behavior.TriVolleyBehavior;
import dev.bukkit.ability.behavior.VampirismBehavior;
import dev.bukkit.game.boss.BossArenaManager;
import dev.bukkit.hud.HudConfig;
import dev.bukkit.hud.HudConfigLoader;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.HunterHudFormatter;
import dev.bukkit.hud.TriHomingHudFormatter;

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
    private BossArenaManager bossArenaManager;

    private BuildAssetManager buildAssetManager;

    private BukkitDisplayEntityRegistry bukkitDisplayEntityRegistry;

    @Override
    public void onEnable() {
        instance = this;
        // Plugin startup logic
        Bukkit.getConsoleSender().sendMessage("Dmain started.");

        entityManager = EntityManager.getInstance();
        effectManagerInterface = BukkitEffectManager.getInstance();
        bukkitDisplayEntityRegistry = BukkitDisplayEntityRegistry.getInstance();
        itemRegistry = RPGItemRegistry.getInstance();
        messageSenderInterface = BukkitMessageSender.getInstance();
        // ---- Register abilities together with their Bukkit effects ----
        // One call per ability wires both the metadata (overridden by
        // abilities.yml below) and the in-game effect. Register BEFORE
        // abilities.yml loads so the config metadata is applied to them.
        AbilityRegistry.register(new ParticleTestAbility(), BukkitParticleTestEffect::new);
        AbilityRegistry.register(new SwingBoneAbility(), BukkitSwingBoneEffect::new);
        AbilityRegistry.register(new SpiritSceptreAbility(), BukkitSpiritSceptreBatEffect::new);
        AbilityRegistry.register(new FocusBeamAbility(), BukkitFocusBeamEffect::new);
        AbilityRegistry.register(new SpinjitzuAbility(), BukkitSpinjitzuEffect::new);
        AbilityRegistry.register(new SmashAbility(), BukkitSmashEffect::new);
        AbilityRegistry.register(new ShieldBashAbility(), BukkitShieldBashEffect::new);
        AbilityRegistry.register(new SmokeShroudAbility(), BukkitSmokeShroudEffect::new);
        AbilityRegistry.register(new SoulSummonAbility(), BukkitSoulSummonEffect::new);
        AbilityRegistry.register(new SoulRecallAbility(), BukkitSoulRecallEffect::new);
        AbilityRegistry.register(new SoulRecallShiftAbility(), BukkitSoulStoreEffect::new);
        AbilityRegistry.register(new SoulCollectorAbility()); // PASSIVE: lore-only, no effect
        AbilityRegistry.register(ShadowWeaverStaffAbility.place(), BukkitShadowWeaverPlaceEffect::new);
        AbilityRegistry.register(ShadowWeaverStaffAbility.dash(), BukkitShadowWeaverDashEffect::new);
        AbilityRegistry.register(new BouncyArrowAbility(), BukkitBouncyArrowEffect::new);
        AbilityRegistry.register(new ExplosiveArrowAbility(), BukkitExplosiveArrowEffect::new);
        AbilityRegistry.register(new TriVolleyAbility(), BukkitTriVolleyEffect::new);
        // Arcane Blade passives — PASSIVE abilities, runtime handled by per-holder
        // behaviors
        AbilityRegistry.register(new ArcaneManaRestoreAbility());
        AbilityRegistry.register(new ArcaneCleaveAbility());
        // Drain Blade passive — PASSIVE, flat ATTACK_DAMAGE stat modifier kept live by
        // StackerBehavior
        AbilityRegistry.register(new StackerAbility());
        // Assassin items — Blade Dance (active cone) & Orb of Stealth
        AbilityRegistry.register(new BladeDanceAbility(), BukkitBladeDanceEffect::new);
        AbilityRegistry.register(new OrbStealthPassiveAbility());

        // ---- Per-holder ability behaviors ----
        AbilityBehaviorRegistry.register(ArcaneManaRestoreAbility.ID, ArcaneManaRestoreBehavior::new);
        AbilityBehaviorRegistry.register(ArcaneCleaveAbility.ID, ArcaneCleaveBehavior::new);
        AbilityBehaviorRegistry.register(BladeDanceAbility.ID, BladeDanceBehavior::new);
        AbilityBehaviorRegistry.register(OrbStealthPassiveAbility.ID, StealthPassiveBehavior::new);
        AbilityBehaviorRegistry.register(BouncyArrowAbility.ID, HunterBowBehavior::new);
        AbilityBehaviorRegistry.register(ExplosiveArrowAbility.ID, HunterBowBehavior::new);
        AbilityBehaviorRegistry.register(TriVolleyAbility.ID, TriVolleyBehavior::new);
        AbilityBehaviorRegistry.register(ShadowWeaverStaffAbility.PLACE_ID, ShadowWeaverBehavior::new);
        AbilityBehaviorRegistry.register(ShadowWeaverStaffAbility.DASH_ID, ShadowWeaverBehavior::new);
        // Set passives unified into same tracking surface
        AbilityBehaviorRegistry.register(HealAuraUtils.PASSIVE_ID, HealAuraBehavior::new);
        AbilityBehaviorRegistry.register(BackstabUtils.PASSIVE_ID, BackstabBehavior::new);
        AbilityBehaviorRegistry.register(ManaDiscountUtils.PASSIVE_ID, ManaDiscountBehavior::new);
        AbilityBehaviorRegistry.register("THREAT", ThreatBehavior::new);
        AbilityBehaviorRegistry.register(VampirismBehavior.PASSIVE_ID, VampirismBehavior::new);
        AbilityBehaviorRegistry.register(StackerAbility.ID, StackerBehavior::new);

        // Status effect behaviors: how each CC type plays out on the vanilla
        // entity (stat engine / potions / AI / velocity). Types without a
        // behavior (e.g. CC immune) only block in the core manager.
        StatusEffectBehaviorRegistry.register(StatusEffectType.SLOWED, new SlowedStatusEffectBehavior());
        StatusEffectBehaviorRegistry.register(StatusEffectType.ROOTED, new RootedStatusEffectBehavior());
        StatusEffectBehaviorRegistry.register(StatusEffectType.STUNNED, new StunnedStatusEffectBehavior());
        StatusEffectBehaviorRegistry.register(StatusEffectType.AIRBORNE, new AirborneStatusEffectBehavior());

        // HUD overlay config — create manager early so hud.yml is available before
        // services start
        configManager = new BukkitConfigManager(this);
        HudConfig hudCfg = HudConfigLoader.load(configManager.getProvider("hud.yml"));
        HudOverlayService.getInstance().init(this, hudCfg);
        HunterHudFormatter.load(hudCfg);
        TriHomingHudFormatter.load(hudCfg);

        // Item set passives: registered before items.yml loads so the loader can
        // resolve the "passives:" lists of set bonuses.
        SetPassiveRegistry.register(ThreatPassiveSubscriber.MARKER);
        SetPassiveRegistry.register(BackstabUtils.MARKER);
        SetPassiveRegistry.register(HealAuraUtils.MARKER);
        SetPassiveRegistry.register(ManaDiscountUtils.MARKER);
        SetPassiveRegistry.register(VampirismBehavior.MARKER);

        // ==============================================[ Load lore.yml
        // ]==================================================
        // Presentation labels for item lore (headers, ability/cost/cooldown
        // lines, set + level footers). Must load before any item is rendered.
        LoreLabels.load(configManager.getProvider("lore.yml"));

        // ==============================================[ Load Default Stats
        // ]===============================================
        ConfigProvider statsConfig = configManager.getProvider("stats.yml");
        // Seed the StatRegistry from the StatType enum, then apply config-driven
        // metadata overrides (display name, symbol, color, percent) from the
        // statMetadata section of stats.yml.
        StatTypeAdapter.initializeStatTypes();
        StatMetadataLoader.loadStatMetadata(statsConfig);
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

        // Load & register item sets: their bonuses apply when enough pieces are
        // equipped at once (see EquipmentManager.recalcSets).
        Map<String, RPGItemSet> itemSets = RPGItemLoader.loadSets(itemsConfig);
        itemSets.values().forEach(itemRegistry::registerItemSet);
        Bukkit.getConsoleSender().sendMessage("Loaded " + itemSets.size() + " item set(s).");

        // ==============================================[ Load bosses.yml
        // ]=====================================================
        ConfigProvider bossesConfig = configManager.getProvider("bosses.yml");
        BossDefinitionRegistry.getInstance().registerAll(BossDefinitionLoader.loadAll(bossesConfig,
                new BukkitBossStageTypeRegistry(), new BukkitBossStrategyRegistry()));

        // ==============================================[ Load dungeon-mobs.yml
        // ]=============================================
        ConfigProvider dungeonMobsConfig = configManager.getProvider("dungeon-mobs.yml");
        MobDefinitionRegistry.getInstance().registerAll(MobDefinitionLoader.loadAll(dungeonMobsConfig).values());
        Bukkit.getConsoleSender()
                .sendMessage("Loaded " + MobDefinitionRegistry.getInstance().size() + " dungeon mob definition(s).");

        // ==============================================[ Setup Settings
        // ]=====================================================
        ConfigProvider setupConfig = configManager.getProvider("setup.yml");
        GameSettings gameSettings = GameSettings.getCurrentSettings();
        GameSettingsLoader gameSettingsLoader = new GameSettingsLoader(gameSettings, setupConfig);
        gameSettingsLoader.load();
        // Initialize boss arena manager from setup config
        this.bossArenaManager = BossArenaManager.createDefault(this);
        if (!gameSettings.isSetupMode() && gameSettings.getDungeonWorld() != null
                && !gameSettings.getDungeonWorld().isBlank()) {
            File dungeonWorldFolder = new File(Bukkit.getWorldContainer(), gameSettings.getDungeonWorld());
            if (dungeonWorldFolder.exists()) {
                Bukkit.getLogger()
                        .info("Resetting dungeon world '" + gameSettings.getDungeonWorld() + "' for a new run.");
                deleteWorld(dungeonWorldFolder);
            }
        }
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
        // Trinity bow arrows resolve their hits globally (see
        // TriVolleyBehavior.onGlobalProjectileHit): per-holder bindings detach
        // when the player swaps away from the bow mid-flight.
        eventBusInterface.subscribe(new EventAction<>(TriVolleyBehavior::onGlobalProjectileHit,
                org.bukkit.event.entity.ProjectileHitEvent.class, EventAction.HIGHEST_PRIORITY));

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
        gameStateController.addState(new ClearState(gameSettings.getHoleCenter(), eventBusInterface, progressionService,
                messageSenderInterface, this));
        gameStateController.addState(new BossState(eventBusInterface, progressionService));
        gameStateController.addState(new PostGameState(eventBusInterface, progressionService, this));
        gameStateController.start();

        CommandManager commandManager = CommandManager.getInstance(itemsConfig, progressionService, gameStateController,
                gameSettingsLoader, eventBusInterface);
        SimpleDungeonBuilderBukkit.initDungeonTestCommand(commandManager);

        buildAssetManager = new BuildAssetManager(this, "buildAssets/");
        BuildAssetHelper buildAssetHelper = new BuildAssetHelper(buildAssetManager);
        GameSetupHelper gameSetupHelper = new GameSetupHelper(gameSettingsLoader);
        SetupManager.getInstance().registerSetupHelpers(commandManager, eventBusInterface,
                (BukkitMessageSender) messageSenderInterface);

        commandManager.registerCommands(this);
        combatListener = new CombatListener(this);
        Bukkit.getPluginManager().registerEvents(combatListener, this);

        // Scanned last: subscriber constructors inject dependencies (e.g. the
        // plugin instance) that must all be initialised before they run.
        EventSubscriberScanner.scan(eventBusInterface, "dev", instance);
    }

    @Override
    public void onDisable() {
        gameStateController.stop();
        effectManagerInterface.cancelAll();
        BukkitStatusEffectManager.getInstance().cancelAll();
        bukkitDisplayEntityRegistry.removeAllDisplays();
        HudOverlayService.getInstance().shutdown();
        try {
            ActiveAbilityRegistry.getInstance().clear();
        } catch (Exception ignored) {
        }
        try {
            AbilityBehaviorRegistry.clear();
        } catch (Exception ignored) {
        }
        combatListener.cleanup();
        configManager.saveAll();
        eventBusInterface.getSubscribed().clear();
        SetupManager.getInstance().cleanUpSetupHelpers(this.getServer());
    }

    public static DMain getInstance() {
        return instance;
    }

    public BossArenaManager getBossArenaManager() {
        return bossArenaManager;
    }

    public CombatListener getCombatListener() {
        return combatListener;
    }

    public ClassProgressionService getProgressionService() {
        return progressionService;
    }

    public BukkitConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Deletes a world folder recursively, but only when the folder is a direct
     * child of the server's world container and its name exactly matches the
     * dungeon world configured in setup.yml. Children whose canonical path resolves
     * outside the world folder (e.g. symlinks) are skipped, so a misconfiguration
     * can never delete anything but the intended dungeon world.
     */
    public boolean deleteWorld(File path) {
        if (path == null || !path.exists()) {
            return false;
        }
        String configuredWorld = GameSettings.getCurrentSettings().getDungeonWorld();
        if (configuredWorld == null || configuredWorld.isBlank()
                || !isDirectChildOfWorldContainer(path, configuredWorld)) {
            Bukkit.getLogger().warning("Refusing to delete " + path.getAbsolutePath()
                    + ": it is not the configured disposable dungeon world.");
            return false;
        }
        return deleteWorldTree(path, path);
    }

    private boolean isDirectChildOfWorldContainer(File folder, String expectedName) {
        try {
            File worldContainer = Bukkit.getWorldContainer().getCanonicalFile();
            File worldFolder = folder.getCanonicalFile();
            return worldFolder.getParentFile() != null && worldFolder.getParentFile().equals(worldContainer)
                    && worldFolder.getName().equals(expectedName);
        } catch (IOException e) {
            Bukkit.getLogger()
                    .warning("Could not resolve world folder " + folder.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    private boolean deleteWorldTree(File root, File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        try {
            if (!isWithinCanonical(root, file)) {
                Bukkit.getLogger().warning(
                        "Skipping " + file.getAbsolutePath() + ": it resolves outside the dungeon world folder.");
                return false;
            }
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not verify path " + file.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteWorldTree(root, child);
                }
            }
        }
        try {
            return file.delete();
        } catch (SecurityException e) {
            Bukkit.getLogger().warning("Refused to delete " + file.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isWithinCanonical(File root, File child) throws IOException {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        return childPath.equals(rootPath) || childPath.startsWith(rootPath + File.separator);
    }
}
