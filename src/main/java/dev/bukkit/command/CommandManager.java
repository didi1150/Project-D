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
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.game.dungeon.BukkitStoneWorldGenerator;
import dev.bukkit.game.dungeon.DungeonBuilderBukkit;
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
import dev.core.game.GameStateController;
import dev.core.game.coords.Point3D;
import dev.core.game.dungeon.DecorationElement;
import dev.core.game.dungeon.Dungeon;
import dev.core.game.dungeon.DungeonGenerator;
import dev.core.game.dungeon.DungeonRoom;
import dev.core.game.dungeon.DungeonStatistics;
import dev.core.game.dungeon.SpawnLocation;
import dev.core.game.settings.GameSettingsLoader;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemLoader;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.progression.PlayerClassProgression;
import dev.core.stat.Stat;
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

//    private final Map<String, CommandExecutor> oldCommandMap;
    private final Map<String, MainCommandBuilder.MainCommand> commandMap;

    private final BukkitMessageSender messageSender;

    private CommandManager(ConfigProvider configProvider, ClassProgressionService classProgressionService) {
//        oldCommandMap = new HashMap<>();
//        oldCommandMap.put("giveItem", new GiveItemCommand());
//        oldCommandMap.put("saveItem", new SaveItemCommand(configProvider));
//        oldCommandMap.put("showProgress", new ShowProgressCommand());
//        oldCommandMap.put("selectActive", new SelectActiveCommand(classProgressionService));
//        oldCommandMap.put("showStats", new ShowStatsCommand());
//        oldCommandMap.put("setXp", new SetXPCommand(classProgressionService));
//        oldCommandMap.put("dungeon", new DungeonCommand());

        messageSender = BukkitMessageSender.getInstance();

        commandMap = new HashMap<>();

        createCommand(MainCommandBuilder.startBuilding("setup").setDescription("Main command for Setup")
                .addSubCommand(SubCommandBuilder.startBuilding("toggle").addAlias("t")
                        .setDescription("Toggles the setup mode").setPlayerCommandAction(0, (player, args) -> {
                            // TODO: Toggle setup mode
                            boolean mode = gameSettingsLoader.toggleSetup();
                            messageSender.sendLine(player, "<red></red>");
                            messageSender.sendCenteredMessage(player,
                                    MessageComponent.of("<yellow>In Setup: %s</yellow>", mode));
                            messageSender.sendLine(player, "<red></red>");
                        }))
                .addSubCommand(
                        SubCommandBuilder.startBuilding("set").setPlayerCommandAction(1, "prelobby", (player, args) -> {
                            /*
                             * - PreLobby Spawn location - Selection Spawn Location - Selection Class
                             * Locations - Hole Center Location
                             */
                            SetupUtils.giveViewLocationSetter(gameSettingsLoader, player,
                                    GameSettingsLoader.LOCATIONS_PRELOBBYSPAWN, eventBus, messageSender);

                        }).setPlayerCommandAction(1, "selectionspawn", (player, args) -> {
                            SetupUtils.giveViewLocationSetter(gameSettingsLoader, player,
                                    GameSettingsLoader.LOCATIONS_SELECTIONSPAWN, eventBus, messageSender);
                            System.out.println("Called selectionspawn");
                        }).setPlayerCommandAction(1, "holecenter", (player, args) -> {
                            SetupUtils.giveBlockLocationSetter(gameSettingsLoader, player,
                                    GameSettingsLoader.LOCATIONS_HOLECENTER, eventBus, messageSender);

                        }).setPlayerCommandAction(2, "classblock", (player, args) -> {
                            try {
                                RPGClassType classType = RPGClassType.valueOf(args[1].toUpperCase());
                                SetupUtils.giveBlockLocationSetter(gameSettingsLoader, player,
                                        GameSettingsLoader.LOCATIONS_SELECTIONCLASSES + "." + classType.toString(),
                                        eventBus, messageSender);
                            } catch (Exception e) {
                                messageSender.sendLine(player, "<red></red>");
                                messageSender.sendCenteredMessage(player, MessageComponent
                                        .of("<red>The classType <yellow>%s</yellow> does not exist!</red>", args[1]));
                                messageSender.sendLine(player, "<red></red>");
                            }
                        }).setPlayerCommandAction(2, "dungeonworld", (player, args) -> {
                            String world = args[1];
                            SetupUtils.setDungeonWorld(world, gameSettingsLoader);
                            messageSender.sendMessage(player, MessageComponent.of(
                                    "<yellow>The world %s will now be used for dungeon exploration!</yellow> ", world));
                        }).setPlayerCommandAction(2, "minplayers", (player, args) -> {
                            try {
                                Integer count = Integer.valueOf(args[1]);
                                gameSettingsLoader.setMinPlayers(count);
                                messageSender.sendMessage(player, MessageComponent.of(
                                        "<yellow>The game will now start at %s player!</yellow> ", args[1]));
                            } catch (NumberFormatException e) {
                                messageSender.sendLine(player, "<red></red>");
                                messageSender.sendCenteredMessage(player, MessageComponent
                                        .of("<red>Invalid Number <yellow>%s</yellow></red>", args[1]));
                                messageSender.sendLine(player, "<red></red>");
                            }
                        }).setCommandArgumentsList(0,
                                Arrays.asList("selectionspawn", "holecenter", "prelobby", "classblock", "dungeonworld",
                                        "minplayers"))
                                .setCommandArgumentsList(1, "classblock", Arrays.asList(RPGClassType.validTypes())
                                        .stream().map(type -> type.toString()).toList()))
                .build());

        createCommand(MainCommandBuilder.startBuilding("project-d").setDescription("Main command for Project-D")
                .setUsage("/project-d").addAlias("d").addSubCommand(SubCommandBuilder.startBuilding("giveItem")
                        .setDescription("to give items").setPlayerCommandAction(1, (player, args) -> {
                            EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                                String id = args[0];
                                RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                                    player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item));
                                    player.sendMessage("Success! You received " + item.getName());
                                }, () -> {
                                    player.sendMessage("This item does not exist");
                                });
                                BukkitInventorySync.syncInventory(p, player);
                            });
                        }).addAlias("g").setCommandArgumentsList(0,
                                RPGItemRegistry.getInstance().allItems().values().stream().map(RPGItem::getId).toList(),
                                "itemName"))
                .addSubCommand(SubCommandBuilder.startBuilding("saveItem").setDescription("to save an item")
                        .setPlayerCommandAction(0, (player, args) -> {
                            EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                                RPGItem item = p.getEquipmentManager().getEquippedItem(EquipmentSlot.MAIN_HAND);
                                if (item == null) {
                                    player.sendMessage("You need to hold the item in your main hand");
                                } else {
                                    RPGItemLoader.saveItem(configProvider, item);
                                    player.sendMessage(ChatColor.YELLOW + item.getId() + ChatColor.GREEN
                                            + " has been successfully saved to the config.");
                                }
                            });
                        }).setPlayerCommandAction(1, (player, args) -> {
                            EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                                String id = args[0];
                                RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                                    player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item));
                                }, () -> {
                                    player.sendMessage("This item does not exist");
                                });
                                BukkitInventorySync.syncInventory(p, player);
                            });
                        }).setCommandArgumentsList(0,
                                RPGItemRegistry.getInstance().allItems().values().stream().map(RPGItem::getId).toList(),
                                "itemName"))
                .addSubCommand(SubCommandBuilder.startBuilding("showProgress")
                        .setDescription("to show your leveling progress / class milestones")
                        .setPlayerCommandAction(0, (player, args) -> {
                            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                            if (optional.isEmpty()) {
                                player.sendMessage(ChatColor.RED + "Could not find profile");
                            } else {
                                BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();

                                // Display active class
                                RPGClassType activeClass = playerEntity.getPlayerProgression().getActiveClass();
                                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                                player.sendMessage(ChatColor.YELLOW + "Active Class: " + ChatColor.GREEN
                                        + (activeClass != null ? activeClass.name() : "None"));
                                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");

                                // Display all class progressions
                                for (Map.Entry<RPGClassType, PlayerClassProgression> entry : playerEntity
                                        .getPlayerProgression().getAllProgressions().entrySet()) {
                                    RPGClassType classType = entry.getKey();
                                    PlayerClassProgression progression = entry.getValue();

                                    // Highlight active class
                                    String classPrefix = classType.equals(activeClass) ? ChatColor.AQUA + "★ "
                                            : ChatColor.GRAY + "  ";

                                    player.sendMessage(classPrefix + ChatColor.WHITE + classType.name() + ":");
                                    player.sendMessage(
                                            ChatColor.GRAY + "  Level: " + ChatColor.YELLOW + progression.getLevel());
                                    player.sendMessage(
                                            ChatColor.GRAY + "  XP: " + ChatColor.GREEN + progression.getXp());
                                    player.sendMessage(ChatColor.GRAY + "  XP til next level: " + ChatColor.GREEN
                                            + progression.getXpToNextLevel());
                                    player.sendMessage(ChatColor.GRAY + "  Usable Items: " + ChatColor.LIGHT_PURPLE
                                            + progression.getUsableItems());
                                    player.sendMessage(""); // Empty line for spacing
                                }

                                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                                player.sendMessage(ChatColor.GRAY + "★ = Active Class");
                            }
                        }))
                .addSubCommand(
                        SubCommandBuilder.startBuilding("setXp").setDescription("to set your current xp of a class")
                                .setPlayerCommandAction(2, (player, args) -> {
                                    Optional<RPGEntity> optional = EntityManager.getInstance()
                                            .getEntity(player.getUniqueId());
                                    if (optional.isEmpty()) {
                                        player.sendMessage(ChatColor.RED + "Could not find profile");
                                    } else {
                                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                                        try {
                                            RPGClassType targetClass = RPGClassType.valueOf(args[0]);
                                            int newXp = Integer.valueOf(args[1]);

                                            playerEntity.getPlayerProgression().getProgression(targetClass)
                                                    .setXp(newXp);
                                            classProgressionService.saveClassProgression(playerEntity.getUuid(),
                                                    playerEntity.getPlayerProgression().getProgression(targetClass));
                                            messageSender.sendMessage(MessageComponent.of(MessageLevel.INFO_LEVEL,
                                                    "Set the %s-Class-XP to %s", RPGClassType.valueOf(args[0]), newXp));
                                        } catch (Exception e) {
                                            player.sendMessage("/setXp <class> <xp>");
                                        }
                                    }
                                })
                                .setCommandArgumentsList(0, Arrays.stream(RPGClassType.values())
                                        .filter(classType -> classType != RPGClassType.NONE).map(Enum::name).toList(),
                                        "className")
                                .setCommandArgumentsList(1, "xpNumber(Integer)"))
                .build());
        addSubCommand("project-d", SubCommandBuilder.startBuilding("showStats").setDescription("to see your stats")
                .setPlayerCommandAction(0, (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "Could not find profile");
                    } else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();

                        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                        for (Map.Entry<StatType, Stat> entry : playerEntity.getStatManager().getStats().entrySet()) {
                            StatType type = entry.getKey();
                            player.sendMessage(BukkitTextColorAdapter.colored(type.getColor(),
                                    type.formatValue(entry.getValue().getCurrent(System.currentTimeMillis()), false)));

                        }
                        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                    }
                }));
        addSubCommand("project-d", SubCommandBuilder.startBuilding("selectActive").setDescription("to select a class")
                .setPlayerCommandAction(1, (player, args) -> {
                    Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                    if (optional.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "Could not find profile");
                    } else {
                        BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();

                        playerEntity.getPlayerProgression().setActiveClass(RPGClassType.valueOf(args[0]),
                                playerEntity.getStatManager());
                        classProgressionService.setActiveClass(playerEntity.getPlayerProgression());
                        messageSender.sendMessage(MessageComponent.of(MessageLevel.INFO_LEVEL, "Selected the %s-Class",
                                RPGClassType.valueOf(args[0])));
                    }
                }).setCommandArgumentsList(0, Arrays.stream(RPGClassType.values())
                        .filter(classType -> classType != RPGClassType.NONE).map(Enum::name).toList(), "className"));
        addSubCommand("project-d", SubCommandBuilder.startBuilding("dungeon").setDescription("Main dungeon command")
//                        .setSyntax("/dungeon <generate|tp> [world_name]")
                .setPlayerCommandAction(2, (player, args) -> {
                    Plugin plugin = DMain.getInstance();

                    World world = Bukkit
                            .createWorld(new WorldCreator(args[1]).generator(new BukkitStoneWorldGenerator()));

                    int tmpRoomCount = 10;

                    try {
                        tmpRoomCount = Integer.parseInt(args[0]);
                        tmpRoomCount = Math.max(1, tmpRoomCount); // Limit between 1-50
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid room count! Using default: 10");
                    }

                    int roomCount = tmpRoomCount;

                    player.sendMessage("§aGenerating dungeon with " + roomCount + " rooms...");

                    // Generate dungeon in async task
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        DungeonGenerator generator = new DungeonGenerator(System.currentTimeMillis(), messageSender);
                        Point3D startPoint = new Point3D(0, 64, // Fixed Y level for dungeons
                                0);

                        Dungeon dungeon = generator.generateDungeon(roomCount, startPoint);

                        // Access spawn information
                        for (DungeonRoom room : dungeon.getRooms()) {
                            List<SpawnLocation> roomSpawns = room.getSpawnLocations();
                            List<DecorationElement> roomDecorations = room.getDecorations();

                            System.out.println("Room " + room.getId() + ": " + roomSpawns.size() + " spawns, "
                                    + roomDecorations.size() + " decorations");
                        }

                        // Get dungeon-wide statistics
                        DungeonStatistics stats = dungeon.getStatistics();
                        player.sendMessage("§6" + stats.toString());

                        // Build dungeon on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            DungeonBuilderBukkit builder = new DungeonBuilderBukkit(plugin, world);

                            // Build dungeon after clearing (delay by 5 seconds)
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                builder.buildDungeon(dungeon, () -> {
                                    player.sendMessage("§aDungeon generation complete! Generated "
                                            + dungeon.getRooms().size() + " rooms.");

                                    // Teleport player to start room
                                    DungeonRoom startRoom = dungeon.getStartRoom();
                                    if (startRoom != null) {
                                        Point3D center = startRoom.getCenter();
                                        player.teleport(new org.bukkit.Location(world, center.getX() + 0.5,
                                                center.getY() + 1, center.getZ() + 0.5));
                                    }
                                });
                            }, 100L); // 5 second delay
                        });
                    });
                }).setCommandArgumentsList(0, "roomSize(Integer)").setCommandArgumentsList(1, "worldName"));
        addSubCommand("project-d",
                SubCommandBuilder.startBuilding("createHole").setDescription("Creates a Hole in the ground")
                        .addAlias("ch").setPlayerCommandAction(0, (player, args) -> {
                            DamageUtils.playEpicHoleAnimation(DMain.getInstance(), player.getLocation(), 5, 5,
                                    new HashSet<Player>(Bukkit.getOnlinePlayers()));
                        }).setPlayerCommandAction(4, (player, args) -> {
                            DamageUtils.playEpicHoleAnimation(DMain.getInstance(), player.getLocation(),
                                    Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                                    Integer.parseInt(args[3]), new HashSet<Player>(Bukkit.getOnlinePlayers()));
                        }).setSyntax(
                                "/project-d createHole <baseRadius> <depth> | <baseRadius> <depth> <baseDelay> <upwardLifespan>"));
        addSubCommand("project-d",
                SubCommandBuilder.startBuilding("gameState").setPlayerCommandAction(1, "next", (player, args) -> {
                    messageSender.sendMessage(player,
                            MessageComponent.of("<yellow>Skipping to next state...</yellow>"));
                    gameStateController.skipCurrentState();
                }).setPlayerCommandAction(1, "current", (player, args) -> {
                    messageSender.sendCenteredDebugMessage(player, MessageComponent
                            .of("<yellow>Current State: %s</yellow>", gameStateController.getCurrentState().getName()));
                }).setCommandArgumentsList(0, Arrays.asList("next", "current")));
    }

    public void registerCommands(JavaPlugin javaPlugin) {
//        oldCommandMap.forEach((name, commandExecutor) -> {
//            javaPlugin.getCommand(name).setExecutor(commandExecutor);
//        });

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

//    public void createCommand(String commandName, String commandDescription, String commandUsage) {
//        createCommand(commandName, commandDescription, commandUsage, null, List.of(), new ArrayList<>());
//    }
//
//    public void createCommand(String commandName, String commandDescription, String commandUsage, CommandList commandList) {
//        createCommand(commandName, commandDescription, commandUsage, commandList, List.of(), new ArrayList<>());
//    }
//
//    public void createCommand(String commandName, String commandDescription, String commandUsage, CommandList commandList, List<String> aliases) {
//        createCommand(commandName, commandDescription, commandUsage, commandList, aliases, new ArrayList<>());
//    }
//
//    public void createCommand(String commandName, String commandDescription, String commandUsage, List<String> aliases) {
//        createCommand(commandName, commandDescription, commandUsage, null, aliases, new ArrayList<>());
//    }

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
