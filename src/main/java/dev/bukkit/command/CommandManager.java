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

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.game.states.SelectItemState;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.bukkit.utils.BukkitMessageSender;
import dev.bukkit.utils.DamageUtils;
import dev.bukkit.utils.SetupUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.GameStateController;
import dev.core.game.coords.Point3D;
import dev.core.game.settings.GameSettings;
import dev.core.game.settings.GameSettingsLoader;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemLoader;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.progression.PlayerClassProgression;
import dev.core.stat.StatType;
import dev.core.storage.config.ConfigProvider;
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
        if (sender.hasPermission(node) || sender.hasPermission("projectd.admin") || sender.isOp()) return true;
        if (!(sender instanceof Player) && sender != null) return true; // console
        return false;
    }
    public static SubCommandBuilder.PlayerCommandAction perm(String node, SubCommandBuilder.PlayerCommandAction action) {
        return (player, args) -> {
            if (!hasPerm(player, node)) {
                BukkitMessageSender.getInstance().sendMessage(player, MessageComponent.of("<red>No permission: %s</red>", node));
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

    private CommandManager(ConfigProvider configProvider, ClassProgressionService classProgressionService) {

        messageSender = BukkitMessageSender.getInstance();

        commandMap = new HashMap<>();

        // Strict absolute: only one MainCommand — project-d (alias d, pd). All trees extend from it.
        createCommand(MainCommandBuilder.startBuilding("project-d").setDescription("Main command for Project-D")
                .setUsage("/project-d <subcommand>").addAlias("d").addAlias("pd")
                .build());

        // ==============================================================
        // item tree -> /d item <give|save|reload|open>
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("item")
                .setDescription("Item controls")
                .setPlayerCommandAction(1, "give", perm("projectd.item.give", (player, args) -> {
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        String id = args[1];
                        RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                            player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, p));
                            player.sendMessage("Success! You received " + item.getName());
                        }, () -> player.sendMessage("This item does not exist"));
                        BukkitInventorySync.syncInventory(p, player);
                    });
                }))
                .setPlayerCommandAction(2, "give", perm("projectd.item.give", (player, args) -> {
                    // alias handled via give above with 1 arg; keep 2-arg variant for tab
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        String id = args[1];
                        RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                            player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, p));
                            player.sendMessage("Success! You received " + item.getName());
                        }, () -> player.sendMessage("This item does not exist"));
                        BukkitInventorySync.syncInventory(p, player);
                    });
                }))
                .setPlayerCommandAction(1, "save", perm("projectd.item.save", (player, args) -> {
                    // /d item save [<id>] -> 0 extra args means save held item, 1 means give as before? Keep both behaviors for compat
                    // This branch for 1 arg after item save -> treat as item id to give
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        String id = args[1];
                        RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                            player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, p));
                        }, () -> player.sendMessage("This item does not exist"));
                        BukkitInventorySync.syncInventory(p, player);
                    });
                }))
                .setPlayerCommandAction(0, "save", perm("projectd.item.save", (player, args) -> {
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        RPGItem item = p.getEquipmentManager().getEquippedItem(EquipmentSlot.MAIN_HAND);
                        if (item == null) player.sendMessage("You need to hold the item in your main hand");
                        else {
                            RPGItemLoader.saveItem(configProvider, item);
                            player.sendMessage(ChatColor.YELLOW + item.getId() + ChatColor.GREEN + " has been successfully saved to the config.");
                        }
                    });
                }))
                .setCommandAction(1, "reload", permAny("projectd.item.reload", (sender, args) -> {
                    try {
                        long t0 = System.currentTimeMillis();
                        dev.bukkit.utils.BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of("<yellow>Reloading items/abilities...</yellow>"));
                        var r = dev.bukkit.reload.ItemsAbilitiesReloadService.reload(DMain.getInstance().getConfigManager());
                        if (r.success) {
                            dev.bukkit.utils.BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of("<green>Reloaded: %s</green>", r.message));
                            Bukkit.getLogger().info("[items reload] " + r.message + " by " + sender.getName());
                        } else {
                            dev.bukkit.utils.BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of("<red>Reload failed: %s</red>", r.message));
                            if (r.error != null) r.error.printStackTrace();
                        }
                    } catch (Exception e) {
                        dev.bukkit.utils.BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of("<red>Items reload failed: %s</red>", e.getMessage()));
                        e.printStackTrace();
                    }
                }))
                .setPlayerCommandAction(0, "open", perm("projectd.item.open", (player, args) -> {
                    GameState current = gameStateController.getCurrentState();
                    if (current instanceof SelectItemState select) {
                        select.openShop(player);
                        player.sendMessage("§eOpening the item shop...");
                    } else player.sendMessage("§cThe item shop is only available during item selection.");
                }))
                .setCommandArgumentsList(0, Arrays.asList("give","save","reload","open"))
                .setCommandArgumentsList(1, "give", () -> RPGItemRegistry.getInstance().allItems().values().stream().map(RPGItem::getId).toList(), "itemName")
                .setCommandArgumentsList(1, "save", () -> RPGItemRegistry.getInstance().allItems().values().stream().map(RPGItem::getId).toList(), "itemName")
        );

        // ==============================================================
        // player tree -> /d player <showProgress|showStats|selectActive|setXp|revive>
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("player")
                .setDescription("Player controls")
                .setPlayerCommandAction(1, "showProgress", perm("projectd.player.showprogress", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty()) player.sendMessage(ChatColor.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        RPGClassType activeClass = playerEntity.getPlayerProgression().getActiveClass();
                        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                        player.sendMessage(ChatColor.YELLOW + "Active Class: " + ChatColor.GREEN + (activeClass != null ? activeClass.name() : "None"));
                        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                        for (Map.Entry<RPGClassType, PlayerClassProgression> entry : playerEntity.getPlayerProgression().getAllProgressions().entrySet()) {
                            RPGClassType classType = entry.getKey(); PlayerClassProgression progression = entry.getValue();
                            String classPrefix = classType.equals(activeClass) ? ChatColor.AQUA + "★ " : ChatColor.GRAY + "  ";
                            player.sendMessage(classPrefix + ChatColor.WHITE + classType.name() + ":");
                            player.sendMessage(ChatColor.GRAY + "  Level: " + ChatColor.YELLOW + progression.getLevel());
                            player.sendMessage(ChatColor.GRAY + "  XP: " + ChatColor.GREEN + progression.getXp());
                            player.sendMessage(ChatColor.GRAY + "  XP til next level: " + ChatColor.GREEN + progression.getXpToNextLevel());
                            player.sendMessage(ChatColor.GRAY + "  Usable Items: " + ChatColor.LIGHT_PURPLE + progression.getUsableItems());
                            player.sendMessage("");
                        }
                        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                        player.sendMessage(ChatColor.GRAY + "★ = Active Class");
                    }
                }))
                .setPlayerCommandAction(1, "showStats", perm("projectd.player.showstats", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty()) player.sendMessage(ChatColor.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                        long now = System.currentTimeMillis();
                        for (StatType type : playerEntity.getStatManager().getStats().keySet()) {
                            double value = playerEntity.getStatEngineAdapter().getCurrentValue(type, now);
                            player.sendMessage(BukkitTextColorAdapter.formatStat(type, value, false));
                        }
                        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                    }
                }))
                .setPlayerCommandAction(2, "selectActive", perm("projectd.player.select", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty()) player.sendMessage(ChatColor.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        playerEntity.getPlayerProgression().setActiveClass(RPGClassType.valueOf(args[1]), playerEntity.getStatManager());
                        classProgressionService.setActiveClass(playerEntity.getPlayerProgression());
                        messageSender.sendMessage(MessageComponent.of(MessageLevel.INFO_LEVEL, "Selected the %s-Class", RPGClassType.valueOf(args[1])));
                    }
                }))
                .setPlayerCommandAction(3, "setXp", perm("projectd.player.setxp", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty()) player.sendMessage(ChatColor.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        try {
                            RPGClassType targetClass = RPGClassType.valueOf(args[1]); int newXp = Integer.valueOf(args[2]);
                            playerEntity.getPlayerProgression().getProgression(targetClass).setXp(newXp);
                            classProgressionService.saveClassProgression(playerEntity.getUuid(), playerEntity.getPlayerProgression().getProgression(targetClass));
                            messageSender.sendMessage(MessageComponent.of(MessageLevel.INFO_LEVEL, "Set the %s-Class-XP to %s", RPGClassType.valueOf(args[1]), newXp));
                        } catch (Exception e) { player.sendMessage("/d player setXp <class> <xp>"); }
                    }
                }))
                .setPlayerCommandAction(1, "revive", perm("projectd.player.revive", (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty()) player.sendMessage(ChatColor.RED + "Could not find profile");
                    else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                        if (playerEntity.isAlive()) return;
                        EntityManager.getInstance().revive(playerEntity.getUuid());
                    }
                }))
                .setPlayerCommandAction(2, "revive", perm("projectd.player.revive", (player, args) -> {
                    String name = args[2]; Player target = Bukkit.getPlayer(name);
                    if (target == null) player.sendMessage(ChatColor.RED + "This player is not online.");
                    else {
                        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(target.getUniqueId());
                        if (optional.isEmpty()) player.sendMessage(ChatColor.RED + "Could not find profile");
                        else EntityManager.getInstance().revive(target.getUniqueId());
                    }
                }))
                .setCommandArgumentsList(0, Arrays.asList("showProgress","showStats","selectActive","setXp","revive"))
                .setCommandArgumentsList(1, "selectActive", Arrays.stream(RPGClassType.values()).filter(c->c!=RPGClassType.NONE).map(Enum::name).toList(), "className")
                .setCommandArgumentsList(1, "setXp", Arrays.stream(RPGClassType.values()).filter(c->c!=RPGClassType.NONE).map(Enum::name).toList(), "className")
                .setCommandArgumentsList(2, "setXp", "xpNumber(Integer)")
                .setCommandArgumentsList(1, "revive", Bukkit.getOnlinePlayers().stream().filter(p->EntityManager.getInstance().isDead(p.getUniqueId())).map(p->p.getName()).toList())
        );

        // ==============================================================
        // gamestate tree -> /d gamestate <next|current>
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("gamestate")
                .setDescription("Game state controls")
                .setCommandAction(1, "next", permAny("projectd.gamestate.next", (sender, args) -> {
                    if (sender instanceof Player p) messageSender.sendMessage(p, MessageComponent.of("<yellow>Skipping to next state...</yellow>"));
                    gameStateController.skipCurrentState();
                }))
                .setCommandAction(1, "current", permAny("projectd.gamestate.current", (sender, args) -> {
                    if (sender instanceof Player p) messageSender.sendCenteredDebugMessage(p, MessageComponent.of("<yellow>Current State: %s</yellow>", gameStateController.getCurrentState().getName()));
                    else sender.sendMessage("Current State: " + gameStateController.getCurrentState().getName());
                }))
                .setCommandArgumentsList(0, Arrays.asList("next","current"))
        );

        // ==============================================================
        // build tree -> /d build createHole ...
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("build")
                .setDescription("Build controls")
                .setPlayerCommandAction(1, "createHole", perm("projectd.build.createhole", (player, args) -> {
                    DamageUtils.playEpicHoleAnimation(DMain.getInstance(), player.getLocation(), 5, 5, new HashSet<Player>(Bukkit.getOnlinePlayers()));
                }))
                .setPlayerCommandAction(5, "createHole", perm("projectd.build.createhole", (player, args) -> {
                    DamageUtils.playEpicHoleAnimation(DMain.getInstance(), player.getLocation(), Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), new HashSet<Player>(Bukkit.getOnlinePlayers()));
                }))
                .setCommandArgumentsList(0, Arrays.asList("createHole"))
        );

        // ==============================================================
        // hud tree -> /d hud reload  (strict absolute, replaces top-level hud)
        // ==============================================================
        addSubCommand("project-d", SubCommandBuilder.startBuilding("hud")
                .setDescription("HUD overlay controls")
                .setCommandAction(1, "reload", permAny("projectd.hud.reload", (sender, args) -> {
                    try {
                        dev.core.storage.config.ConfigProvider reloaded = DMain.getInstance().getConfigManager().reloadProvider("hud.yml");
                        dev.bukkit.hud.HudConfig n = dev.bukkit.hud.HudConfigLoader.load(reloaded);
                        dev.bukkit.hud.HudOverlayService.getInstance().reload(n);
                        dev.bukkit.hud.HunterHudFormatter.load(n);
                        dev.bukkit.hud.TriHomingHudFormatter.load(n);
                        dev.bukkit.utils.BukkitMessageSender.getInstance().sendMessage(sender, dev.core.utils.MessageComponent.of("<green>HUD reloaded.</green>"));
                    } catch (Exception e) {
                        dev.bukkit.utils.BukkitMessageSender.getInstance().sendMessage(sender, dev.core.utils.MessageComponent.of("<red>HUD reload failed: %s</red>", e.getMessage()));
                    }
                }))
                .setCommandArgumentsList(0, Arrays.asList("reload"))
        );

        // Note: BuildAssetManager and SimpleDungeonBuilderBukkit add their own "asset"/"dungeon" branches under project-d via DMain hooks (already unified, not separate mains).

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
