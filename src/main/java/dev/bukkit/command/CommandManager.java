package dev.bukkit.command;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.entity.DebugRpgEntityService;
import dev.bukkit.game.boss.stalactite.DebugStalactiteService;
import dev.bukkit.game.states.SelectItemState;
import dev.bukkit.hud.HudConfig;
import dev.bukkit.hud.HudConfigLoader;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.HunterHudFormatter;
import dev.bukkit.hud.StaffHudFormatter;
import dev.bukkit.hud.TriHomingHudFormatter;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.bukkit.reload.BossReloadService;
import dev.bukkit.reload.BossReloadService.ReloadResult;
import dev.bukkit.reload.ItemsAbilitiesReloadService;
import dev.bukkit.reload.MobReloadService;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.utils.BukkitMessageSender;
import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.boss.BossDefinition;
import dev.core.entity.boss.BossDefinitionRegistry;
import dev.core.entity.mob.MobDefinition;
import dev.core.entity.mob.MobDefinitionRegistry;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.GameStateController;
import dev.core.game.settings.GameSettingsLoader;
import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemLoader;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.progression.PlayerClassProgression;
import dev.core.reward.RewardConfigLoader;
import dev.core.stat.StatType;
import dev.core.storage.config.ConfigProvider;
import dev.core.utils.LegacyColors;
import dev.core.utils.MessageComponent;
import dev.core.utils.MessageLevel;
import me.kodysimpson.simpapi.command.CommandList;
import me.kodysimpson.simpapi.command.SubCommand;

public class CommandManager {

    private static CommandManager instance;

    private static GameStateController gameStateController;

    private static GameSettingsLoader gameSettingsLoader;

    private static EventBusInterface eventBus;

    public static CommandManager getInstance(ConfigProvider configProvider,
            ClassProgressionService classProgressionService, GameStateController gameStateController,
            GameSettingsLoader gameSettingsLoader, EventBusInterface eventBus) {
        CommandManager.gameStateController = gameStateController;
        CommandManager.gameSettingsLoader = gameSettingsLoader;
        CommandManager.eventBus = eventBus;
        if (instance == null)
            instance = new CommandManager(configProvider, classProgressionService);
        return instance;
    }

    private final Map<String, MainCommandBuilder.MainCommand> commandMap;

    private final BukkitMessageSender messageSender;

    // permission helpers
    private static boolean hasPerm(CommandSender sender, String node) {
        if (sender.hasPermission(node) || sender.hasPermission("projectd.admin") || sender.isOp())
            return true;
        if (!(sender instanceof Player) && sender != null)
            return true; // console
        return false;
    }

    public static SubCommandBuilder.PlayerCommandAction perm(String node,
            SubCommandBuilder.PlayerCommandAction action) {
        return (player, args) -> {
            if (!hasPerm(player, node)) {
                BukkitMessageSender.getInstance().sendMessage(player,
                        MessageComponent.of("<red>No permission: %s</red>", node));
                return;
            }
            action.perform(player, args);
        };
    }

    private SubCommandBuilder.CommandAction permAny(String node, SubCommandBuilder.CommandAction action) {
        return (sender, args) -> {
            if (!hasPerm(sender, node)) {
                messageSender.sendMessage(sender, MessageComponent.of("<red>No permission: %s</red>", node));
                return;
            }
            action.perform(sender, args);
        };
    }

    private static void giveItemSetToPlayer(Player player, String setId) {
        EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
            RPGItemRegistry registry = RPGItemRegistry.getInstance();
            Optional<RPGItemSet> optSet = registry.getItemSet(setId);
            if (optSet.isEmpty()) {
                optSet = registry.allItemSets().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(setId))
                        .map(Map.Entry::getValue).findFirst();
            }
            if (optSet.isEmpty()) {
                player.sendMessage("§cItem set '" + setId + "' does not exist");
                return;
            }
            RPGItemSet set = optSet.get();
            List<String> pieceIds = set.getPieceIds();
            if (pieceIds == null || pieceIds.isEmpty()) {
                player.sendMessage("§cItem set '" + set.getId() + "' has no pieces");
                return;
            }
            int given = 0;
            List<String> missing = new ArrayList<>();
            for (String pieceId : pieceIds) {
                Optional<RPGItem> optItem = registry.getItem(pieceId);
                if (optItem.isEmpty()) {
                    optItem = registry.allItems().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(pieceId))
                            .map(Map.Entry::getValue).findFirst();
                }
                if (optItem.isEmpty()) {
                    missing.add(pieceId);
                    continue;
                }
                RPGItem item = optItem.get();
                ItemStack stack = BukkitItemStackAdapter.toItemStack(item, p);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
                given++;
            }
            BukkitInventorySync.syncInventory(p, player);
            player.sendMessage("§aSuccess! You received item set §e" + set.getName() + " §a(" + given + "/"
                    + pieceIds.size() + " pieces)");
            if (!missing.isEmpty()) {
                player.sendMessage("§cMissing pieces (not registered): " + String.join(", ", missing));
            }
            Bukkit.getLogger().info("[give itemset] " + player.getName() + " received set " + set.getId() + " (" + given
                    + "/" + pieceIds.size() + ")");
        });
    }

    private static MobDefinition findMobDefinition(String id) {
        if (id == null) {
            return null;
        }
        MobDefinitionRegistry registry = MobDefinitionRegistry.getInstance();
        Optional<MobDefinition> opt = registry.get(id);
        if (opt.isPresent()) {
            return opt.get();
        }
        for (MobDefinition def : registry.snapshot().values()) {
            if (def.getId().equalsIgnoreCase(id)) {
                return def;
            }
        }
        return null;
    }

    private static BossDefinition findBossDefinition(String id) {
        if (id == null) {
            return null;
        }
        BossDefinitionRegistry registry = BossDefinitionRegistry.getInstance();
        Optional<BossDefinition> opt = registry.getById(id);
        if (opt.isPresent()) {
            return opt.get();
        }
        for (List<BossDefinition> list : registry.snapshot().values()) {
            for (BossDefinition def : list) {
                if (def.getId().equalsIgnoreCase(id)) {
                    return def;
                }
            }
        }
        return null;
    }

    private CommandManager(ConfigProvider configProvider, ClassProgressionService classProgressionService) {

        messageSender = BukkitMessageSender.getInstance();

        commandMap = new HashMap<>();

        // Strict absolute: only one MainCommand — project-d (alias d, pd). All trees
        // extend from it.
        createCommand(MainCommandBuilder.startBuilding("project-d").setDescription("Main command for Project-D")
                .setUsage("/project-d <subcommand>").addAlias("d").addAlias("pd").build());

        // ==============================================================
        // item tree -> /d item <give|save|reload|open>
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("item").setDescription("Item controls")
                .setPlayerCommandAction(1, "give", perm("projectd.item.give", (player, args) -> {
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        String id = args[1];
                        RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                            player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, p));
                            player.sendMessage("Success! You received " + item.getName());
                        }, () -> player.sendMessage("This item does not exist"));
                        BukkitInventorySync.syncInventory(p, player);
                    });
                })).setPlayerCommandAction(2, "give", perm("projectd.item.give", (player, args) -> {
                    // alias handled via give above with 1 arg; keep 2-arg variant for tab
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        String id = args[1];
                        RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                            player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, p));
                            player.sendMessage("Success! You received " + item.getName());
                        }, () -> player.sendMessage("This item does not exist"));
                        BukkitInventorySync.syncInventory(p, player);
                    });
                })).setPlayerCommandAction(3, "give", perm("projectd.item.give", (player, args) -> {
                    if (!args[1].equalsIgnoreCase("itemset")) {
                        player.sendMessage("This item does not exist");
                        return;
                    }
                    giveItemSetToPlayer(player, args[2]);
                })).setPlayerCommandAction(1, "save", perm("projectd.item.save", (player, args) -> {
                    // /d item save [<id>] -> 0 extra args means save held item, 1 means give as
                    // before? Keep both behaviors for compat
                    // This branch for 1 arg after item save -> treat as item id to give
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        String id = args[1];
                        RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                            player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, p));
                        }, () -> player.sendMessage("This item does not exist"));
                        BukkitInventorySync.syncInventory(p, player);
                    });
                })).setPlayerCommandAction(0, "save", perm("projectd.item.save", (player, args) -> {
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        RPGItem item = p.getEquipmentManager().getEquippedItem(EquipmentSlot.MAIN_HAND);
                        if (item == null)
                            player.sendMessage("You need to hold the item in your main hand");
                        else {
                            RPGItemLoader.saveItem(configProvider, item);
                            player.sendMessage(LegacyColors.YELLOW + item.getId() + LegacyColors.GREEN
                                    + " has been successfully saved to the config.");
                        }
                    });
                })).setCommandAction(1, "reload", permAny("projectd.item.reload", (sender, args) -> {
                    try {
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<yellow>Reloading items/abilities...</yellow>"));
                        var r = ItemsAbilitiesReloadService.reload(DMain.getInstance().getConfigManager());
                        if (r.success) {
                            BukkitMessageSender.getInstance().sendMessage(sender,
                                    MessageComponent.of("<green>Reloaded: %s</green>", r.message));
                            Bukkit.getLogger().info("[items reload] " + r.message + " by " + sender.getName());
                        } else {
                            BukkitMessageSender.getInstance().sendMessage(sender,
                                    MessageComponent.of("<red>Reload failed: %s</red>", r.message));
                            if (r.error != null)
                                r.error.printStackTrace();
                        }
                    } catch (Exception e) {
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<red>Items reload failed: %s</red>", e.getMessage()));
                        e.printStackTrace();
                    }
                })).setPlayerCommandAction(0, "open", perm("projectd.item.open", (player, args) -> {
                    GameState current = gameStateController.getCurrentState();
                    if (current instanceof SelectItemState select) {
                        select.openShop(player);
                        player.sendMessage("§eOpening the item shop...");
                    } else
                        player.sendMessage("§cThe item shop is only available during item selection.");
                })).setCommandArgumentsList(0, Arrays.asList("give", "save", "reload", "open"))
                .setCommandArgumentsList(1, "give", () -> {
                    List<String> ids = new ArrayList<>(
                            RPGItemRegistry.getInstance().allItems().values().stream().map(RPGItem::getId).toList());
                    ids.add("itemset");
                    return ids;
                }, "itemName|itemset")
                .setCommandArgumentsList(2, "give",
                        () -> RPGItemRegistry.getInstance().allItemSets().keySet().stream().toList(), "itemSetName")
                .setCommandArgumentsList(1, "save",
                        () -> RPGItemRegistry.getInstance().allItems().values().stream().map(RPGItem::getId).toList(),
                        "itemName"));

        // ==============================================================
        // caseopening tree -> /d caseopening [chest|unlock|reload]
        // Temporary debug wiring for the reward-case mechanisms. A future
        // RewardState can call RewardCaseService directly.
        // ==============================================================
        addSubCommand("project-d",
                SubCommandBuilder.startBuilding("caseopening").setDescription("Reward case debug controls")
                        .setPlayerCommandAction(0, perm("projectd.caseopening", (player, args) -> {
                            if (DMain.getInstance().getRewardCaseService() == null) {
                                player.sendMessage("§cReward case service is not initialized.");
                                return;
                            }
                            DMain.getInstance().getRewardCaseService().openCase(player);
                        })).setPlayerCommandAction(1, "chest", perm("projectd.caseopening", (player, args) -> {
                            if (DMain.getInstance().getRewardCaseService() == null) {
                                player.sendMessage("§cReward case service is not initialized.");
                                return;
                            }
                            if (DMain.getInstance().getRewardCaseService().spawnChestInFront(player) == null) {
                                player.sendMessage("§cCould not spawn the reward chest here.");
                            } else {
                                player.sendMessage("§aSpawned a reward cache. Right-click it to open the case.");
                            }
                        })).setPlayerCommandAction(1, "reload", perm("projectd.caseopening", (player, args) -> {
                            try {
                                var provider = DMain.getInstance().getConfigManager().reloadProvider("rewards.yml");
                                DMain.getInstance().getRewardCaseService().reload(RewardConfigLoader.load(provider));
                                player.sendMessage("§aReloaded rewards.yml.");
                            } catch (Exception e) {
                                player.sendMessage("§cReward reload failed: " + e.getMessage());
                            }
                        })).setPlayerCommandAction(2, "unlock", perm("projectd.caseopening", (player, args) -> {
                            String itemId = args[1];
                            var rewardItem = RPGItemRegistry.getInstance().getItem(itemId).orElse(null);
                            if (rewardItem == null || !rewardItem.isRewardOnly()) {
                                player.sendMessage("§c'" + itemId + "' is not a registered reward-only item.");
                                return;
                            }
                            boolean added = DMain.getInstance().getProgressionService().unlockItem(player.getUniqueId(),
                                    itemId);
                            player.sendMessage(added ? "§aUnlocked reward item §e" + itemId
                                    : "§eItem §f" + itemId + "§e was already unlocked.");
                        }))
                        .setCommandArgumentsList(0, Arrays.asList("chest", "reload", "unlock"))
                        .setCommandArgumentsList(1, "unlock",
                                () -> RPGItemRegistry.getInstance().allItems().values().stream()
                                        .filter(RPGItem::isRewardOnly).map(RPGItem::getId).toList(),
                                "rewardItemId"));

        // ==============================================================
        // player tree -> /d player <showProgress|showStats|selectActive|setXp|revive>
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("player").setDescription("Player controls")
                .setPlayerCommandAction(1, "showProgress", perm("projectd.player.showprogress", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty())
                        player.sendMessage(LegacyColors.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        RPGClassType activeClass = playerEntity.getPlayerProgression().getActiveClass();
                        player.sendMessage(LegacyColors.GOLD + "═══════════════════════════════════");
                        player.sendMessage(LegacyColors.YELLOW + "Active Class: " + LegacyColors.GREEN
                                + (activeClass != null ? activeClass.name() : "None"));
                        player.sendMessage(LegacyColors.GOLD + "═══════════════════════════════════");
                        for (Map.Entry<RPGClassType, PlayerClassProgression> entry : playerEntity.getPlayerProgression()
                                .getAllProgressions().entrySet()) {
                            RPGClassType classType = entry.getKey();
                            PlayerClassProgression progression = entry.getValue();
                            String classPrefix = classType.equals(activeClass) ? LegacyColors.AQUA + "★ "
                                    : LegacyColors.GRAY + "  ";
                            player.sendMessage(classPrefix + LegacyColors.WHITE + classType.name() + ":");
                            player.sendMessage(
                                    LegacyColors.GRAY + "  Level: " + LegacyColors.YELLOW + progression.getLevel());
                            player.sendMessage(LegacyColors.GRAY + "  XP: " + LegacyColors.GREEN + progression.getXp());
                            player.sendMessage(LegacyColors.GRAY + "  XP til next level: " + LegacyColors.GREEN
                                    + progression.getXpToNextLevel());
                            player.sendMessage(LegacyColors.GRAY + "  Usable Items: " + LegacyColors.LIGHT_PURPLE
                                    + progression.getUsableItems());
                            player.sendMessage("");
                        }
                        player.sendMessage(LegacyColors.GOLD + "═══════════════════════════════════");
                        player.sendMessage(LegacyColors.GRAY + "★ = Active Class");
                    }
                })).setPlayerCommandAction(1, "showStats", perm("projectd.player.showstats", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty())
                        player.sendMessage(LegacyColors.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        player.sendMessage(LegacyColors.GOLD + "═══════════════════════════════════");
                        long now = System.currentTimeMillis();
                        for (StatType type : playerEntity.getStatManager().getStats().keySet()) {
                            double value = playerEntity.getStatEngineAdapter().getCurrentValue(type, now);
                            player.sendMessage(BukkitTextColorAdapter.formatStat(type, value, false));
                        }
                        player.sendMessage(LegacyColors.GOLD + "═══════════════════════════════════");
                    }
                })).setPlayerCommandAction(2, "selectActive", perm("projectd.player.select", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty())
                        player.sendMessage(LegacyColors.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        playerEntity.getPlayerProgression().setActiveClass(RPGClassType.valueOf(args[1]),
                                playerEntity.getStatManager());
                        classProgressionService.setActiveClass(playerEntity.getPlayerProgression());
                        messageSender.sendMessage(MessageComponent.of(MessageLevel.INFO_LEVEL, "Selected the %s-Class",
                                RPGClassType.valueOf(args[1])));
                    }
                })).setPlayerCommandAction(3, "setXp", perm("projectd.player.setxp", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty())
                        player.sendMessage(LegacyColors.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        try {
                            RPGClassType targetClass = RPGClassType.valueOf(args[1]);
                            int newXp = Integer.valueOf(args[2]);
                            classProgressionService.setExperience(playerEntity.getUuid(), targetClass, newXp);
                            messageSender.sendMessage(MessageComponent.of(MessageLevel.INFO_LEVEL,
                                    "Set the %s-Class-XP to %s", RPGClassType.valueOf(args[1]), newXp));
                        } catch (Exception e) {
                            player.sendMessage("/d player setXp <class> <xp>");
                        }
                    }
                })).setPlayerCommandAction(1, "revive", perm("projectd.player.revive", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty())
                        player.sendMessage(LegacyColors.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        if (playerEntity.isAlive())
                            return;
                        EntityManager.getInstance().revive(playerEntity.getUuid());
                    }
                })).setPlayerCommandAction(2, "revive", perm("projectd.player.revive", (player, args) -> {
                    String name = args[2];
                    Player target = Bukkit.getPlayer(name);
                    if (target == null)
                        player.sendMessage(LegacyColors.RED + "This player is not online.");
                    else {
                        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(target.getUniqueId());
                        if (optional.isEmpty())
                            player.sendMessage(LegacyColors.RED + "Could not find profile");
                        else
                            EntityManager.getInstance().revive(target.getUniqueId());
                    }
                }))
                .setCommandArgumentsList(0,
                        Arrays.asList("showProgress", "showStats", "selectActive", "setXp", "revive"))
                .setCommandArgumentsList(1, "selectActive",
                        Arrays.stream(RPGClassType.values()).filter(c -> c != RPGClassType.NONE).map(Enum::name)
                                .toList(),
                        "className")
                .setCommandArgumentsList(1, "setXp",
                        Arrays.stream(RPGClassType.values()).filter(c -> c != RPGClassType.NONE).map(Enum::name)
                                .toList(),
                        "className")
                .setCommandArgumentsList(2, "setXp", "xpNumber(Integer)").setCommandArgumentsList(1, "revive",
                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> EntityManager.getInstance().isDead(p.getUniqueId())).map(p -> p.getName())
                                .toList()));

        // ==============================================================
        // gamestate tree -> /d gamestate <next|current>
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("gamestate").setDescription("Game state controls")
                .setCommandAction(1, "next", permAny("projectd.gamestate.next", (sender, args) -> {
                    if (sender instanceof Player p)
                        messageSender.sendMessage(p, MessageComponent.of("<yellow>Skipping to next state...</yellow>"));
                    gameStateController.skipCurrentState();
                })).setCommandAction(1, "current", permAny("projectd.gamestate.current", (sender, args) -> {
                    if (sender instanceof Player p)
                        messageSender.sendCenteredDebugMessage(p, MessageComponent.of(
                                "<yellow>Current State: %s</yellow>", gameStateController.getCurrentState().getName()));
                    else
                        sender.sendMessage("Current State: " + gameStateController.getCurrentState().getName());
                })).setCommandArgumentsList(0, Arrays.asList("next", "current")));

        // ==============================================================
        // build tree -> /d build createHole ...
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("build").setDescription("Build controls")
                .setPlayerCommandAction(1, "createHole", perm("projectd.build.createhole", (player, args) -> {
                    DamageUtils.playEpicHoleAnimation(DMain.getInstance(), player.getLocation(), 5, 5,
                            new HashSet<Player>(Bukkit.getOnlinePlayers()));
                })).setPlayerCommandAction(5, "createHole", perm("projectd.build.createhole", (player, args) -> {
                    DamageUtils.playEpicHoleAnimation(DMain.getInstance(), player.getLocation(),
                            Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                            Integer.parseInt(args[4]), new HashSet<Player>(Bukkit.getOnlinePlayers()));
                })).setCommandArgumentsList(0, Arrays.asList("createHole")));

        // ==============================================================
        // hud tree -> /d hud reload (strict absolute, replaces top-level hud)
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("hud").setDescription("HUD overlay controls")
                .setCommandAction(1, "reload", permAny("projectd.hud.reload", (sender, args) -> {
                    try {
                        ConfigProvider reloaded = DMain.getInstance().getConfigManager().reloadProvider("hud.yml");
                        HudConfig n = HudConfigLoader.load(reloaded);
                        HudOverlayService.getInstance().reload(n);
                        HunterHudFormatter.load(n);
                        TriHomingHudFormatter.load(n);
                        StaffHudFormatter.load(n);
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<green>HUD reloaded.</green>"));
                    } catch (Exception e) {
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<red>HUD reload failed: %s</red>", e.getMessage()));
                    }
                })).setCommandArgumentsList(0, Arrays.asList("reload")));

        // ==============================================================
        // boss tree -> /d boss <reload|minion> (bosses.yml + floor-data, minion areas)
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("boss").setDescription("Boss controls")
                .setCommandAction(1, "reload", permAny("projectd.boss.reload", (sender, args) -> {
                    try {
                        long t0 = System.currentTimeMillis();
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<yellow>Reloading bosses...</yellow>"));
                        ReloadResult r = BossReloadService.reload(DMain.getInstance().getConfigManager());
                        if (r.success) {
                            BukkitMessageSender.getInstance().sendMessage(sender,
                                    MessageComponent.of("<green>Reloaded: %s</green>", r.message));
                            Bukkit.getLogger().info("[boss reload] " + r.message + " by " + sender.getName() + " in "
                                    + (System.currentTimeMillis() - t0) + "ms");
                        } else {
                            BukkitMessageSender.getInstance().sendMessage(sender,
                                    MessageComponent.of("<red>Reload failed: %s</red>", r.message));
                            if (r.error != null)
                                r.error.printStackTrace();
                        }
                    } catch (Exception e) {
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<red>Boss reload failed: %s</red>", e.getMessage()));
                        e.printStackTrace();
                    }
                })));

        // ==============================================================
        // mob tree -> /d mob reload (dungeon-mobs.yml)
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("mob").setDescription("Mob controls")
                .setCommandAction(1, "reload", permAny("projectd.mob.reload", (sender, args) -> {
                    try {
                        long t0 = System.currentTimeMillis();
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<yellow>Reloading mobs...</yellow>"));
                        var r = MobReloadService.reload(DMain.getInstance().getConfigManager());
                        if (r.success) {
                            BukkitMessageSender.getInstance().sendMessage(sender,
                                    MessageComponent.of("<green>Reloaded: %s</green>", r.message));
                            Bukkit.getLogger().info("[mob reload] " + r.message + " by " + sender.getName() + " in "
                                    + (System.currentTimeMillis() - t0) + "ms");
                        } else {
                            BukkitMessageSender.getInstance().sendMessage(sender,
                                    MessageComponent.of("<red>Reload failed: %s</red>", r.message));
                            if (r.error != null)
                                r.error.printStackTrace();
                        }
                    } catch (Exception e) {
                        BukkitMessageSender.getInstance().sendMessage(sender,
                                MessageComponent.of("<red>Mob reload failed: %s</red>", e.getMessage()));
                        e.printStackTrace();
                    }
                })).setCommandArgumentsList(0, Arrays.asList("reload")));

        // ==============================================================
        // stalactite tree -> /d stalactite [spawn] [<debug|respawn> ...]
        // Debug harness: spawns a bukkit-local stalactite system above the player.
        // Options:
        // - no extra arg (/d stalactite or /d stalactite spawn) -> single stalactite
        // that doesn't regrow, no hitbox
        // - debug -> shows hitbox highlights
        // - respawn -> respawning stalactite (regrows 20s after fall)
        // Remove despawns displays + cancels tasks.
        // ==============================================================
        addSubCommand("project-d",
                SubCommandBuilder.startBuilding("stalactite").setDescription("Debug stalactite controls")
                        // bare /d stalactite -> single (no extra arg)
                        .setPlayerCommandAction(0, perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = false, respawn = false;
                                for (String a : args) {
                                    if (a.equalsIgnoreCase("debug"))
                                        debug = true;
                                    if (a.equalsIgnoreCase("respawn"))
                                        respawn = true;
                                }
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }))
                        // /d stalactite debug and /d stalactite respawn (top-level shortcuts)
                        .setPlayerCommandAction(1, "debug", perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = true, respawn = false;
                                for (String a : args)
                                    if (a.equalsIgnoreCase("respawn"))
                                        respawn = true;
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })).setPlayerCommandAction(1, "respawn", perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = false, respawn = true;
                                for (String a : args)
                                    if (a.equalsIgnoreCase("debug"))
                                        debug = true;
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })).setPlayerCommandAction(2, "debug", perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = true, respawn = false;
                                for (String a : args)
                                    if (a.equalsIgnoreCase("respawn"))
                                        respawn = true;
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })).setPlayerCommandAction(2, "respawn", perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = false, respawn = true;
                                for (String a : args)
                                    if (a.equalsIgnoreCase("debug"))
                                        debug = true;
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }))
                        // spawn variants: /d stalactite spawn [<debug|respawn> ...]
                        .setPlayerCommandAction(1, "spawn", perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = false, respawn = false;
                                for (String a : args) {
                                    if (a.equalsIgnoreCase("debug"))
                                        debug = true;
                                    if (a.equalsIgnoreCase("respawn"))
                                        respawn = true;
                                }
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })).setPlayerCommandAction(2, "spawn", perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = false, respawn = false;
                                for (String a : args) {
                                    if (a.equalsIgnoreCase("debug"))
                                        debug = true;
                                    if (a.equalsIgnoreCase("respawn"))
                                        respawn = true;
                                }
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })).setPlayerCommandAction(3, "spawn", perm("projectd.stalactite.debug", (player, args) -> {
                            try {
                                boolean debug = false, respawn = false;
                                for (String a : args) {
                                    if (a.equalsIgnoreCase("debug"))
                                        debug = true;
                                    if (a.equalsIgnoreCase("respawn"))
                                        respawn = true;
                                }
                                DebugStalactiteService.getInstance().spawnAt(player, respawn, debug);
                            } catch (Exception e) {
                                player.sendMessage("§c[Stalactite Debug] Spawn failed: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })).setPlayerCommandAction(1, "remove", perm("projectd.stalactite.debug", (player, args) -> {
                            int n = DebugStalactiteService.getInstance().removeAt(player);
                            if (n > 0)
                                player.sendMessage("§a[Stalactite Debug] Removed " + n + " system(s) nearby.");
                            else
                                player.sendMessage(
                                        "§c[Stalactite Debug] No debug stalactite nearby. Try /d stalactite removeAll");
                        })).setCommandAction(1, "removeAll", permAny("projectd.stalactite.debug", (sender, args) -> {
                            int n = DebugStalactiteService.getInstance().removeAll();
                            sender.sendMessage("§a[Stalactite Debug] Removed all " + n
                                    + " debug system(s). Displays + tasks cancelled.");
                            if (sender instanceof Player p)
                                p.sendMessage("§7Impact will no longer fire until you /d stalactite spawn again.");
                        })).setPlayerCommandAction(1, "count", perm("projectd.stalactite.debug", (player, args) -> {
                            int c = DebugStalactiteService.getInstance().count();
                            player.sendMessage("§7[Stalactite Debug] Active systems: §e" + c);
                        }))
                        .setCommandArgumentsList(0,
                                Arrays.asList("spawn", "remove", "removeAll", "count", "debug", "respawn"))
                        .setCommandArgumentsList(1, "spawn", Arrays.asList("debug", "respawn"))
                        .setCommandArgumentsList(2, "spawn", Arrays.asList("debug", "respawn"))
                        .setCommandArgumentsList(1, "debug", Arrays.asList("respawn"))
                        .setCommandArgumentsList(1, "respawn", Arrays.asList("debug")));

        // ==============================================================
        // give tree -> /d give itemset <setName>
        // Drops all armor pieces of an item set into the executor's inventory.
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("give").setDescription("Give commands")
                .setPlayerCommandAction(2, "itemset",
                        perm("projectd.item.give", (player, args) -> giveItemSetToPlayer(player, args[1])))
                .setCommandArgumentsList(0, Arrays.asList("itemset")).setCommandArgumentsList(1, "itemset",
                        () -> RPGItemRegistry.getInstance().allItemSets().keySet().stream().toList(), "itemSetName"));

        // ==============================================================
        // entity tree -> /d entity spawn <entityId> [level] / despawn
        // [all|nearest|<uuid>] / list / count
        // Spawns RPG mobs/bosses by their config id (dungeon-mobs.yml / bosses.yml),
        // registers in EntityManager, and tracks for debug despawn.
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("entity").setDescription("RPG entity debug spawns")
                .setPlayerCommandAction(2, "spawn", perm("projectd.entity.spawn", (player, args) -> {
                    String id = args[1];
                    int level = 1;
                    MobDefinition mobDef = findMobDefinition(id);
                    BossDefinition bossDef = null;
                    if (mobDef == null) {
                        bossDef = findBossDefinition(id);
                    }
                    if (mobDef == null && bossDef == null) {
                        player.sendMessage("§cUnknown entity id '" + id + "'. Use /d entity list to see mob/boss ids.");
                        return;
                    }
                    if (mobDef != null) {
                        Location loc = player.getLocation();
                        LivingEntity spawned = DebugRpgEntityService.getInstance().spawnMob(mobDef, loc, level);
                        if (spawned == null) {
                            player.sendMessage("§cFailed to spawn mob '" + mobDef.getId() + "'. Check entity-type.");
                            return;
                        }
                        player.sendMessage("§aSpawned mob §e" + mobDef.getId() + " §aat level " + level + " ("
                                + spawned.getUniqueId().toString().substring(0, 8) + ")");
                        Bukkit.getLogger()
                                .info("[entity spawn] " + player.getName() + " spawned mob " + mobDef.getId()
                                        + " level " + level + " at " + loc.getBlockX() + "," + loc.getBlockY() + ","
                                        + loc.getBlockZ());
                    } else {
                        Location loc = player.getLocation();
                        try {
                            var boss = DebugRpgEntityService.getInstance().spawnBoss(bossDef, loc);
                            player.sendMessage("§aSpawned boss §e" + bossDef.getId() + " §a(floor " + bossDef.getFloor()
                                    + ") stage=" + boss.getCurrentStageId() + " immune=" + boss.isDamageImmune()
                                    + " (" + boss.getUuid().toString().substring(0, 8) + ")");
                            Bukkit.getLogger()
                                    .info("[entity spawn] " + player.getName() + " spawned boss " + bossDef.getId()
                                            + " floor " + bossDef.getFloor() + " at " + loc.getBlockX() + ","
                                            + loc.getBlockY() + "," + loc.getBlockZ());
                        } catch (Exception e) {
                            player.sendMessage("§cFailed to spawn boss '" + bossDef.getId() + "': " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                })).setPlayerCommandAction(3, "spawn", perm("projectd.entity.spawn", (player, args) -> {
                    String id = args[1];
                    int level;
                    try {
                        level = Integer.parseInt(args[2]);
                        if (level < 1)
                            level = 1;
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid level '" + args[2] + "'. Must be integer >=1.");
                        return;
                    }
                    MobDefinition mobDef = findMobDefinition(id);
                    BossDefinition bossDef = null;
                    if (mobDef == null) {
                        bossDef = findBossDefinition(id);
                    }
                    if (mobDef == null && bossDef == null) {
                        player.sendMessage("§cUnknown entity id '" + id + "'.");
                        return;
                    }
                    if (mobDef != null) {
                        Location loc = player.getLocation();
                        LivingEntity spawned = DebugRpgEntityService.getInstance().spawnMob(mobDef, loc, level);
                        if (spawned == null) {
                            player.sendMessage("§cFailed to spawn mob '" + mobDef.getId() + "'.");
                            return;
                        }
                        player.sendMessage("§aSpawned mob §e" + mobDef.getId() + " §aat level " + level + " ("
                                + spawned.getUniqueId().toString().substring(0, 8) + ")");
                        Bukkit.getLogger().info("[entity spawn] " + player.getName() + " spawned mob " + mobDef.getId()
                                + " level " + level);
                    } else {
                        if (args.length > 2) {
                            player.sendMessage("§eNote: level is ignored for bosses (fixed stats). Spawning '"
                                    + bossDef.getId() + "' anyway.");
                        }
                        Location loc = player.getLocation();
                        try {
                            var boss = DebugRpgEntityService.getInstance().spawnBoss(bossDef, loc);
                            player.sendMessage("§aSpawned boss §e" + bossDef.getId() + " §a(floor " + bossDef.getFloor()
                                    + ") stage=" + boss.getCurrentStageId() + " immune=" + boss.isDamageImmune()
                                    + " (" + boss.getUuid().toString().substring(0, 8) + ")");
                        } catch (Exception e) {
                            player.sendMessage("§cFailed to spawn boss '" + bossDef.getId() + "': " + e.getMessage());
                        }
                    }
                })).setPlayerCommandAction(1, "despawn", perm("projectd.entity.despawn", (player, args) -> {
                    int n = DebugRpgEntityService.getInstance().despawnNearest(player, 10);
                    if (n > 0) {
                        player.sendMessage("§aDespawned nearest debug entity.");
                    } else {
                        int c = DebugRpgEntityService.getInstance().count();
                        if (c == 0) {
                            player.sendMessage("§cNo debug entities to despawn. Use /d entity spawn <id> first.");
                        } else {
                            player.sendMessage(
                                    "§cNo debug entity within 10 blocks. Use /d entity despawn all or /d entity despawn <uuid> (count: "
                                            + c + ")");
                        }
                    }
                })).setPlayerCommandAction(2, "despawn", perm("projectd.entity.despawn", (player, args) -> {
                    String arg = args[1];
                    if (arg.equalsIgnoreCase("all")) {
                        int n = DebugRpgEntityService.getInstance().despawnAll();
                        player.sendMessage("§aDespawned " + n + " debug entity(s). Removed from EntityManager.");
                        return;
                    }
                    if (arg.equalsIgnoreCase("nearest")) {
                        int n = DebugRpgEntityService.getInstance().despawnNearest(player, 10);
                        if (n > 0)
                            player.sendMessage("§aDespawned nearest debug entity.");
                        else
                            player.sendMessage("§cNo debug entity within 10 blocks.");
                        return;
                    }
                    try {
                        UUID uuid = UUID.fromString(arg);
                        boolean ok = DebugRpgEntityService.getInstance().despawn(uuid);
                        if (ok)
                            player.sendMessage("§aDespawned entity " + uuid.toString().substring(0, 8));
                        else
                            player.sendMessage("§cNo tracked debug entity with uuid " + arg);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("§cUnknown despawn argument '" + arg + "'. Use all, nearest, or a uuid.");
                    }
                })).setPlayerCommandAction(1, "list", perm("projectd.entity.spawn", (player, args) -> {
                    List<String> mobs = MobDefinitionRegistry.getInstance().getAllIds();
                    List<String> bosses = BossDefinitionRegistry.getInstance().snapshot().values().stream()
                            .flatMap(List::stream).map(BossDefinition::getId).toList();
                    player.sendMessage("§6=== RPG Entities (" + (mobs.size() + bosses.size()) + ") ===");
                    if (!mobs.isEmpty())
                        player.sendMessage("§eMobs: §7" + String.join(", ", mobs));
                    if (!bosses.isEmpty())
                        player.sendMessage("§cBosses: §7" + String.join(", ", bosses));
                    int tracked = DebugRpgEntityService.getInstance().count();
                    player.sendMessage("§7Tracked debug spawns: " + tracked);
                    if (tracked > 0) {
                        String uuids = String.join(", ", DebugRpgEntityService.getInstance().getTrackedUuids().stream()
                                .map(u -> u.toString().substring(0, 8)).toList());
                        player.sendMessage("§7UUIDs: " + uuids);
                    }
                })).setPlayerCommandAction(1, "count", perm("projectd.entity.spawn", (player, args) -> {
                    int c = DebugRpgEntityService.getInstance().count();
                    player.sendMessage("§7Tracked debug entities: §e" + c);
                })).setCommandAction(1, "list", permAny("projectd.entity.spawn", (sender, args) -> {
                    List<String> mobs = MobDefinitionRegistry.getInstance().getAllIds();
                    List<String> bosses = BossDefinitionRegistry.getInstance().snapshot().values().stream()
                            .flatMap(List::stream).map(BossDefinition::getId).toList();
                    sender.sendMessage("§6=== RPG Entities (" + (mobs.size() + bosses.size()) + ") ===");
                    if (!mobs.isEmpty())
                        sender.sendMessage("§eMobs: §7" + String.join(", ", mobs));
                    if (!bosses.isEmpty())
                        sender.sendMessage("§cBosses: §7" + String.join(", ", bosses));
                    sender.sendMessage("§7Tracked debug spawns: " + DebugRpgEntityService.getInstance().count());
                })).setCommandAction(1, "count", permAny("projectd.entity.spawn", (sender, args) -> {
                    sender.sendMessage("Tracked debug entities: " + DebugRpgEntityService.getInstance().count());
                })).setCommandArgumentsList(0, Arrays.asList("spawn", "despawn", "list", "count"))
                .setCommandArgumentsList(1, "spawn", () -> {
                    List<String> ids = new ArrayList<>();
                    ids.addAll(MobDefinitionRegistry.getInstance().getAllIds());
                    ids.addAll(BossDefinitionRegistry.getInstance().snapshot().values().stream().flatMap(List::stream)
                            .map(BossDefinition::getId).toList());
                    return ids;
                }, "entityId").setCommandArgumentsList(2, "spawn", "level(Integer)")
                .setCommandArgumentsList(1, "despawn", () -> {
                    List<String> opts = new ArrayList<>();
                    opts.add("all");
                    opts.add("nearest");
                    opts.addAll(DebugRpgEntityService.getInstance().getTrackedUuids().stream().map(UUID::toString)
                            .toList());
                    return opts;
                }, "all|nearest|uuid").setCommandArgumentsList(0, "list", "noArgs")
                .setCommandArgumentsList(0, "count", "noArgs"));

        // Note: BuildAssetManager and SimpleDungeonBuilderBukkit add their own
        // "asset"/"dungeon" branches under project-d via DMain hooks (already unified,
        // not separate mains).

    }

    public void registerCommands(JavaPlugin javaPlugin) {

        commandMap.forEach((name, command) -> {
            createCoreCommand(javaPlugin, name, command.description(), command.usage(), command.commandList(),
                    command.aliases(), command.subCommands());
        });

    }

    private void createCoreCommand(JavaPlugin plugin, String commandName, String commandDescription,
            String commandUsage, @Nullable CommandList commandList, List<String> aliases,
            List<SubCommand> subcommands) {
        try {
            ArrayList<SubCommand> commands = new ArrayList<>(subcommands);
            Objects.requireNonNull(commands);
            Field commandField = plugin.getServer().getClass().getDeclaredField("commandMap");
            commandField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandField.get(plugin.getServer());
            commandMap.register(commandName,
                    new CoreCommand(commandName, commandDescription, commandUsage, commandList, aliases, commands));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void createCommand(MainCommandBuilder.MainCommand mainCommand) {
        if (commandMap.containsKey(mainCommand.name()))
            throw new IllegalArgumentException("Command already present");
        commandMap.put(mainCommand.name(), mainCommand);
    }

    public void addSubCommand(String mainCommandName, SubCommandBuilder subCommandBuilder) {
        if (!commandMap.containsKey(mainCommandName))
            throw new IllegalArgumentException("MainCommand not present, must first be created");
        subCommandBuilder.setMainCommand(mainCommandName);
        commandMap.get(mainCommandName).subCommands().add(subCommandBuilder.build());
    }

    /**
     * IMPORTANT: When setting the name for a sub sub command, use the following
     * format: <br>
     * [MainCommand] [name]
     */
    public void addSubCommand(String mainCommandName, SubCommand subCommand) {
        if (!commandMap.containsKey(mainCommandName)) {
            throw new IllegalArgumentException("MainCommand not present, must first be created");
        }
        commandMap.get(mainCommandName).subCommands().add(subCommand);
    }

}
