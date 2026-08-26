package dev.bukkit.utils;

import dev.bukkit.DMain;
import dev.bukkit.command.CommandManager;
import dev.bukkit.command.SubCommandBuilder;
import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.bukkit.game.coords.LocToPoint;
import dev.bukkit.game.coords.PointToLocation;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.settings.GameSettings;
import dev.core.game.settings.GameSettingsLoader;
import dev.core.utils.MessageComponent;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.stream.Collectors;

import static dev.bukkit.command.CommandManager.perm;

public class GameSetupHelper extends SetupHelper {

    private final GameSettingsLoader gameSettingsLoader;

    private static final String DISPLAY_NAME_PRE_LOBBY = "Pre-Lobby-Spawn Selection Tool";
    private static final String DISPLAY_NAME_CLASS_SELECTION = "Class-Selection-Spawn Selection Tool";
    private static final String DISPLAY_NAME_HOLE_CENTER = "Hole Center Selection Tool";
    private static final String DISPLAY_NAME_CLASS_BLOCK = "Class Block Selection Tool";
    private static final String DISPLAY_NAME_BOSS = "Boss Spawn Selection Tool";
    private static final String DISPLAY_NAME_BOSS_PLAYER = "Boss Player Spawn Selection Tool";

    private final SpawnPositionDisplay preLobbySpawnDisplay = new SpawnPositionDisplay(DISPLAY_NAME_PRE_LOBBY, "PreLobbySpawn", Material.YELLOW_STAINED_GLASS, Color.YELLOW);
    private final SpawnPositionDisplay classSelectionSpawnDisplay = new SpawnPositionDisplay(DISPLAY_NAME_CLASS_SELECTION, "ClassSelectionSpawn", Material.LIGHT_BLUE_STAINED_GLASS, Color.AQUA);
    private final BlockPositionDisplay holeCenterDisplay = new BlockPositionDisplay(DISPLAY_NAME_HOLE_CENTER, "HoleCenter", Material.GRAY_STAINED_GLASS, Color.GRAY);
    private final BlockPositionDisplay[] classBlockDisplays = new BlockPositionDisplay[5];
    private final SpawnPositionDisplay[] bossSpawnDisplays = new SpawnPositionDisplay[5];
    private final SpawnPositionDisplay[] bossPlayerSpawnDisplays = new SpawnPositionDisplay[5];

    public GameSetupHelper(GameSettingsLoader gameSettingsLoader) {
        super("GameSetupMode");
        this.gameSettingsLoader = gameSettingsLoader;
        for (int i = 0; i < 5; i++) {
            bossSpawnDisplays[i] = new SpawnPositionDisplay(DISPLAY_NAME_BOSS, "BossSpawnFloor" + (i + 1), Material.RED_STAINED_GLASS, Color.RED);
            bossPlayerSpawnDisplays[i] = new SpawnPositionDisplay(DISPLAY_NAME_BOSS_PLAYER, "BossPlayerSpawnFloor" + (i + 1), Material.WHITE_STAINED_GLASS, Color.WHITE);
        }
        classBlockDisplays[0] = new BlockPositionDisplay(DISPLAY_NAME_CLASS_BLOCK, RPGClassType.TANK.getDisplayName(), Material.GRAY_STAINED_GLASS, Color.GRAY);
        classBlockDisplays[1] = new BlockPositionDisplay(DISPLAY_NAME_CLASS_BLOCK, RPGClassType.ASSASSIN.getDisplayName(), Material.BLACK_STAINED_GLASS, Color.BLACK);
        classBlockDisplays[2] = new BlockPositionDisplay(DISPLAY_NAME_CLASS_BLOCK, RPGClassType.ARCHER.getDisplayName(), Material.RED_STAINED_GLASS, Color.RED);
        classBlockDisplays[3] = new BlockPositionDisplay(DISPLAY_NAME_CLASS_BLOCK, RPGClassType.MAGE.getDisplayName(), Material.BLUE_STAINED_GLASS, Color.BLUE);
        classBlockDisplays[4] = new BlockPositionDisplay(DISPLAY_NAME_CLASS_BLOCK, RPGClassType.SUPPORT.getDisplayName(), Material.GREEN_STAINED_GLASS, Color.GREEN);
    }

    private ItemStack getPreLobbySpawnItem() {
        return SetupUtils.createSimpleItem(Material.BLAZE_ROD, DISPLAY_NAME_PRE_LOBBY,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK, "Select Position", "Set current Player-Position as Spawn-Position"),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")));
    }

    private ItemStack getClassSelectionSpawnItem() {
        return SetupUtils.createSimpleItem(Material.BREEZE_ROD, DISPLAY_NAME_CLASS_SELECTION,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK, "Select Position", "Set current Player-Position as Spawn-Position"),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")));
    }

    private ItemStack getHoleCenterItem() {
        return SetupUtils.createSimpleItem(Material.NETHERITE_SHOVEL, DISPLAY_NAME_HOLE_CENTER,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select Block Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")));
    }

    private Material getClassMaterial(RPGClassType classType) {
        return switch (classType){
            case NONE -> null;
            case TANK -> Material.GRAY_WOOL;
            case ASSASSIN -> Material.BLACK_WOOL;
            case ARCHER -> Material.RED_WOOL;
            case MAGE -> Material.BLUE_WOOL;
            case SUPPORT -> Material.GREEN_WOOL;
        };
    }

    private ItemStack getClassBlockItem(RPGClassType classType) {
        return SetupUtils.createSimpleItem(getClassMaterial(classType), DISPLAY_NAME_CLASS_BLOCK,
                List.of(new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Switch Class", "Current selected class: " + classType),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select Block Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")));
    }

    private ItemStack getBossSpawnItem(int floor) {
        return SetupUtils.createSimpleItem(Material.SKULL_BANNER_PATTERN, DISPLAY_NAME_BOSS,
                List.of(new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Switch Floor", "Current selected floor: " + floor),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK, "Select Position", "Set current Player-Position as Spawn-Position"),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")));
    }

    private ItemStack getBossPlayerSpawnItem(int floor) {
        return SetupUtils.createSimpleItem(Material.BONE, DISPLAY_NAME_BOSS_PLAYER,
                List.of(new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Switch Floor", "Current selected floor: " + floor),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK, "Select Position", "Set current Player-Position as Spawn-Position"),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")));
    }

    @Override
    public void giveSetupItemsToPlayer(BukkitMessageSender ms, Player player) {
        List<ItemStack> items = new ArrayList<>(List.of(getPreLobbySpawnItem(), getClassSelectionSpawnItem(), getHoleCenterItem(), getClassBlockItem(RPGClassType.TANK), getBossSpawnItem(1), getBossPlayerSpawnItem(1)));
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }
        ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "You received: " + items.stream().map(i -> i.getItemMeta().getDisplayName()).collect(Collectors.joining(", "))));
    }

    @Override
    public void removeSetupItemsFromPlayer(Player player) {
        List<ItemStack> items = getAllSetupItems();
        for (ItemStack item : items) {
            player.getInventory().remove(item);
        }
    }

    private List<ItemStack> getAllSetupItems() {
        List<ItemStack> items = new ArrayList<>(List.of(getPreLobbySpawnItem(), getClassSelectionSpawnItem(), getHoleCenterItem()));
        for (int i = 1; i <= 5; i++) {
            items.add(getBossSpawnItem(i));
            items.add(getBossPlayerSpawnItem(i));
        }
        for (RPGClassType rpgClassType : RPGClassType.validTypes()) {
            items.add(getClassBlockItem(rpgClassType));
        }
        return items;
    }

    @Override
    public boolean removePlayerFromMode(Player player) {
        if (super.removePlayerFromMode(player)) {
            if (playersInMode.isEmpty()) {
                clearDisplays();
            }
            return true;
        }
        return false;
    }

    @Override
    public void cleanUp(Server server) {
        super.cleanUp(server);
        clearDisplays();
    }

    private void clearDisplays() {
        preLobbySpawnDisplay.clearDisplays(true, null);
        classSelectionSpawnDisplay.clearDisplays(true, null);
        holeCenterDisplay.clearDisplays(true, null);
        for (int i = 0; i < 5; i++) {
            bossSpawnDisplays[i].clearDisplays(true, null);
            bossPlayerSpawnDisplays[i].clearDisplays(true, null);
            classBlockDisplays[i].clearDisplays(true, null);
        }
    }

    @Override
    protected void cleanUp(Server server, UUID uuid, @Nullable Player player) {
        if (player != null)
            removePlayerFromMode(player);
    }

    @Override
    public void registerCommand(CommandManager cm, BukkitMessageSender ms) {
        // ==============================================================
        // setup tree -> /d setup <toggle|prelobby|selectionspawn|holecenter|classblock|dungeonworld|bossworld|minplayers|bossspawn|bossplayerspawn|loadbossworld|tpbossworld|savebossworld|quitbossworld|status>
        // ==============================================================
        cm.addSubCommand("project-d", SubCommandBuilder.startBuilding("setup")
                .setDescription("Setup controls")
                .setPlayerCommandAction(1, "toggle", perm("projectd.setup.toggle", (player, args) -> {
                    boolean mode = gameSettingsLoader.toggleSetup();
                    ms.sendLine(player, "<red></red>");
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>In Setup: %s</yellow>", mode));
                    ms.sendLine(player, "<red></red>");
                })).addAlias("t")
//                .setPlayerCommandAction(1, "prelobby", perm("projectd.setup.set", (player, args) -> {
//                    SetupUtils.giveViewLocationSetter(gameSettingsLoader, player, GameSettingsLoader.LOCATIONS_PRELOBBYSPAWN, eventBus, messageSender);
//                }))
//                .setPlayerCommandAction(1, "selectionspawn", perm("projectd.setup.set", (player, args) -> {
//                    SetupUtils.giveViewLocationSetter(gameSettingsLoader, player, GameSettingsLoader.LOCATIONS_SELECTIONSPAWN, eventBus, messageSender);
//                }))
//                .setPlayerCommandAction(1, "holecenter", perm("projectd.setup.set", (player, args) -> {
//                    SetupUtils.giveBlockLocationSetter(gameSettingsLoader, player, GameSettingsLoader.LOCATIONS_HOLECENTER, eventBus, messageSender);
//                }))
//                .setPlayerCommandAction(2, "classblock", perm("projectd.setup.set", (player, args) -> {
//                    try {
//                        RPGClassType classType = RPGClassType.valueOf(args[1].toUpperCase());
//                        SetupUtils.giveBlockLocationSetter(gameSettingsLoader, player, GameSettingsLoader.LOCATIONS_SELECTIONCLASSES + "." + classType.toString(), eventBus, messageSender);
//                    } catch (Exception e) {
//                        ms.sendLine(player, "<red></red>");
//                        ms.sendCenteredMessage(player, MessageComponent.of("<red>The classType <yellow>%s</yellow> does not exist!</red>", args[1]));
//                        ms.sendLine(player, "<red></red>");
//                    }
//                }))
                .setPlayerCommandAction(2, "dungeonworld", perm("projectd.setup.set", (player, args) -> {
                    String world = args[1];
                    SetupUtils.setDungeonWorld(world, gameSettingsLoader);
                    ms.sendMessage(player, MessageComponent.of("<yellow>The world %s will now be used for dungeon exploration!</yellow> ", world));
                }))
                .setPlayerCommandAction(3, "bossworld", perm("projectd.setup.set", (player, args) -> {
                    try {
                        String world = args[1]; int floor = Integer.parseInt(args[2]);
                        SetupUtils.setBossWorld(world, gameSettingsLoader, floor);
                        ms.sendMessage(player, MessageComponent.of("<yellow>The world %s has been set as boss arena of floor %s.</yellow> ", world, floor));
                    } catch (NumberFormatException e) {
                        ms.sendLine(player, "<red></red>");
                        ms.sendCenteredMessage(player, MessageComponent.of("<red>Invalid Number <yellow>%s</yellow></red>", args[1]));
                        ms.sendLine(player, "<red></red>");
                    }
                }))
                .setPlayerCommandAction(2, "minplayers", perm("projectd.setup.set", (player, args) -> {
                    try {
                        Integer count = Integer.valueOf(args[1]);
                        gameSettingsLoader.setMinPlayers(count);
                        ms.sendMessage(player, MessageComponent.of("<yellow>The game will now start at %s player!</yellow> ", args[1]));
                    } catch (NumberFormatException e) {
                        ms.sendLine(player, "<red></red>");
                        ms.sendCenteredMessage(player, MessageComponent.of("<red>Invalid Number <yellow>%s</yellow></red>", args[1]));
                        ms.sendLine(player, "<red></red>");
                    }
                }))
//                .setPlayerCommandAction(2, "bossspawn", perm("projectd.setup.set", (player, args) -> {
//                    try {
//                        int floor = Integer.parseInt(args[1]);
//                        Point3D point = new Point3D((int) Math.floor(player.getLocation().getX()), (int) Math.floor(player.getLocation().getY()), (int) Math.floor(player.getLocation().getZ()));
//                        gameSettingsLoader.setBossSpawnLocation(floor, point);
//                        ms.sendCenteredMessage(player, MessageComponent.of("<green>Boss spawn for floor %s has been set.</green>", floor));
//                    } catch (NumberFormatException e) {
//                        ms.sendLine(player, "<red></red>");
//                        ms.sendCenteredMessage(player, MessageComponent.of("<red>Invalid Number <yellow>%s</yellow></red>", args[1]));
//                        ms.sendLine(player, "<red></red>");
//                    }
//                }))
//                .setPlayerCommandAction(2, "bossplayerspawn", perm("projectd.setup.set", (player, args) -> {
//                    try {
//                        int floor = Integer.parseInt(args[1]);
//                        Point3D point = new Point3D((int) Math.floor(player.getLocation().getX()), (int) Math.floor(player.getLocation().getY()), (int) Math.floor(player.getLocation().getZ()));
//                        gameSettingsLoader.setBossPlayerSpawnLocation(floor, point);
//                        ms.sendCenteredMessage(player, MessageComponent.of("<green>Boss player spawn for floor %s has been set.</green>", floor));
//                    } catch (NumberFormatException e) {
//                        ms.sendLine(player, "<red></red>");
//                        ms.sendCenteredMessage(player, MessageComponent.of("<red>Invalid Number <yellow>%s</yellow></red>", args[1]));
//                        ms.sendLine(player, "<red></red>");
//                    }
//                }))
                .setPlayerCommandAction(2, "loadbossworld", perm("projectd.setup.set", (player, args) -> {
                    String worldId = args[1];
                    DMain.getInstance().getBossArenaManager().loadTemplateEditWorld(worldId).whenComplete((world, throwable) -> {
                        if (throwable != null) {
                            ms.sendCenteredMessage(player, MessageComponent.of("<red>Could not load boss template world <yellow>%s</yellow>.Traceback: %s</red>", worldId, throwable.getMessage()));
                            return;
                        }
                        ms.sendCenteredMessage(player, MessageComponent.of("<green>Boss template world <yellow>%s</yellow> loaded.</green>", worldId));
                        player.teleport(world.getSpawnLocation());
                    });
                }))
                .setPlayerCommandAction(2, "tpbossworld", perm("projectd.setup.set", (player, args) -> {
                    String worldId = args[1]; String worldName = "boss_template_edit_" + worldId.replaceAll("[^A-Za-z0-9_-]", "_");
                    org.bukkit.World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        ms.sendCenteredMessage(player, MessageComponent.of("<red>Boss template world <yellow>%s</yellow> is not loaded.</red>", worldId));
                        return;
                    }
                    player.teleport(world.getSpawnLocation());
                    ms.sendCenteredMessage(player, MessageComponent.of("<green>Teleported to boss template world <yellow>%s</yellow>.</green>", worldId));
                }))
                .setPlayerCommandAction(2, "savebossworld", perm("projectd.setup.set", (player, args) -> {
                    String worldId = args[1];
                    DMain.getInstance().getBossArenaManager().saveTemplateEditWorld(worldId).whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            ms.sendCenteredMessage(player, MessageComponent.of("<red>Could not save boss template world <yellow>%s</yellow>.</red>", worldId));
                            return;
                        }
                        ms.sendCenteredMessage(player, MessageComponent.of("<green>Boss template world <yellow>%s</yellow> saved back to template.</green>", worldId));
                    });
                }))
                .setPlayerCommandAction(2, "quitbossworld", perm("projectd.setup.set", (player, args) -> {
                    String worldId = args[1];
                    DMain.getInstance().getBossArenaManager().quitTemplateEditWorld(worldId, Bukkit.getWorlds().get(0)).whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            ms.sendCenteredMessage(player, MessageComponent.of("<red>Could not save boss template world <yellow>%s</yellow>.</red>", worldId));
                            return;
                        }
                        ms.sendCenteredMessage(player, MessageComponent.of("<green>Boss template world <yellow>%s</yellow> saved back to template.</green>", worldId));
                    });
                }))
                .setPlayerCommandAction(1, "status", perm("projectd.setup.status", (player, args) -> {
                    GameSettings settings = GameSettings.getCurrentSettings();
                    int floor = settings.getFloor();
                    boolean hasDungeonWorld = settings.getDungeonWorld() != null && !settings.getDungeonWorld().isBlank();
                    boolean hasPreLobby = settings.getPreLobbySpawn() != null && settings.getPreLobbySpawn().getWorld() != null;
                    boolean hasSelectionSpawn = settings.getSelectionSpawn() != null && settings.getSelectionSpawn().getWorld() != null;
                    boolean hasHoleCenter = settings.getHoleCenter() != null && settings.getHoleCenter().getWorld() != null;
                    boolean hasBossWorld = settings.getBossWorld() != null && !settings.getBossWorld().isBlank();
                    boolean hasBossSpawn = settings.getBossSpawnLocation(floor) != null;
                    boolean hasBossPlayerSpawn = settings.getBossPlayerSpawnLocation(floor) != null;
                    ms.sendCenteredMessage(player, MessageComponent.of(ChatColor.GOLD + "Setup Status"));
                    ms.sendLine(player, ChatColor.GOLD.toString());
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>Pre-lobby spawn:</yellow> %s", hasPreLobby ? "SET" : "MISSING"));
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>Selection spawn:</yellow> %s", hasSelectionSpawn ? "SET" : "MISSING"));
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>Selection hole center:</yellow> %s", hasHoleCenter ? "SET" : "MISSING"));
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>Dungeon world:</yellow> %s", hasDungeonWorld ? "SET" : "MISSING"));
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>Boss world for floor %s:</yellow> %s", floor, hasBossWorld ? "SET" : "MISSING"));
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>Boss spawn for floor %s:</yellow> %s", floor, hasBossSpawn ? "SET" : "MISSING"));
                    ms.sendCenteredMessage(player, MessageComponent.of("<yellow>Boss player spawn for floor %s:</yellow> %s", floor, hasBossPlayerSpawn ? "SET" : "MISSING"));
                    ms.sendLine(player, ChatColor.GOLD.toString());
                }))
                .setPlayerCommandAction(1, "toggleSetupMode", ((player, args) -> {
                    if (isPlayerInMode(player, ms, false)) {
                        removePlayerFromMode(player);
                    } else {
                        setPlayerInMode(ms, player);
                    }
                }))
                .setCommandArgumentsList(0, Arrays.asList("toggle",
//                        "prelobby","selectionspawn","holecenter","classblock","dungeonworld","bossspawn","bossplayerspawn",
                        "bossworld","minplayers","loadbossworld","tpbossworld","savebossworld","quitbossworld","status", "toggleSetupMode"))
//                .setCommandArgumentsList(1, "classblock", Arrays.asList(RPGClassType.validTypes()).stream().map(type -> type.toString()).toList())
        );
    }

    @Override
    public void registerEvents(EventBusInterface eventBus, BukkitMessageSender ms) {
        GameSettings gameSettings = GameSettings.getCurrentSettings();
        eventBus.subscribe(new EventAction<>(event -> {
            Player player = event.getPlayer();
            if (isPlayerInMode(player, ms, false)) {
                if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CLASS_BLOCK)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }, BlockPlaceEvent.class));
        eventBus.subscribe(new EventAction<>(event -> {
            Player player = event.getPlayer();
            if (isPlayerInMode(player, ms, false)) {
                ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());
                if (prevItem != null && prevItem.hasItemMeta() && prevItem.getItemMeta().hasDisplayName()) {
                    if (prevItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_PRE_LOBBY)) {
                        preLobbySpawnDisplay.clearDisplays(false, player);
                    } else if (prevItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_CLASS_SELECTION)) {
                        classSelectionSpawnDisplay.clearDisplays(false, player);
                    } else if (prevItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_HOLE_CENTER)) {
                        holeCenterDisplay.clearDisplays(false, player);
                    } else if (prevItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_CLASS_BLOCK)) {
                        for (int i = 0; i < 5; i++) {
                            classBlockDisplays[i].clearDisplays(false, player);
                        }
                    } else if (prevItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_BOSS)) {
                        for (int i = 0; i < 5; i++) {
                            bossSpawnDisplays[i].clearDisplays(false, player);
                        }
                    } else if (prevItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_BOSS_PLAYER)) {
                        for (int i = 0; i < 5; i++) {
                            bossPlayerSpawnDisplays[i].clearDisplays(false, player);
                        }
                    }
                }
                ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
                if (newItem != null && newItem.hasItemMeta() && newItem.getItemMeta().hasDisplayName()) {
                    if (newItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_PRE_LOBBY)) {
                        ViewPoint3D point = gameSettings.getPreLobbySpawn();
                        if (point != null) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point);
                            Location loc = PointToLocation.viewToLoc(point);
                            preLobbySpawnDisplay.updateDisplays(loc.getWorld(), pos, loc);
                        }
                    } else if (newItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_CLASS_SELECTION)) {
                        ViewPoint3D point = gameSettings.getSelectionSpawn();
                        if (point != null) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point);
                            Location loc = PointToLocation.viewToLoc(point);
                            classSelectionSpawnDisplay.updateDisplays(loc.getWorld(), pos, loc);
                        }
                    } else if (newItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_HOLE_CENTER)) {
                        Point3D point = gameSettings.getHoleCenter();
                        if (point != null) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point);
                            Location loc = PointToLocation.blockToLoc(point);
                            holeCenterDisplay.updateDisplays(loc.getWorld(), pos);
                        }
                    } else if (newItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_CLASS_BLOCK)) {
                        RPGClassType classType = getClassFromItemLore(newItem);
                        int index = getClassIndex(classType);
                        Point3D point = gameSettings.getSelectionLocations().get(classType);
                        if (point != null) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point);
                            Location loc = PointToLocation.blockToLoc(point);
                            classBlockDisplays[index].updateDisplays(loc.getWorld(), pos);
                        }
                    } else if (newItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_BOSS)) {
                        int floor = getFloorFromItemLore(newItem);
                        ViewPoint3D point = gameSettings.getBossSpawnLocation(floor);
                        if (point != null) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point);
                            Location loc = PointToLocation.viewToLoc(point);
                            bossSpawnDisplays[floor - 1].updateDisplays(loc.getWorld(), pos, loc);
                        }
                    } else if (newItem.getItemMeta().getDisplayName().contains(DISPLAY_NAME_BOSS_PLAYER)) {
                        int floor = getFloorFromItemLore(newItem);
                        ViewPoint3D point = gameSettings.getBossPlayerSpawnLocation(floor);
                        if (point != null) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point);
                            Location loc = PointToLocation.viewToLoc(point);
                            bossPlayerSpawnDisplays[floor - 1].updateDisplays(loc.getWorld(), pos, loc);
                        }
                    }
                }
            }
        }, PlayerItemHeldEvent.class));
        eventBus.subscribe(new EventAction<>(event -> {
            Player player = event.getPlayer();
            if (event.getHand() == EquipmentSlot.HAND && isPlayerInMode(player, ms, false)) {
                event.setCancelled(true);
                if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {

                    if (player.isSneaking()) {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_BOSS) || SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_BOSS_PLAYER)) {
                            int prevFloor = getFloorFromItemLore(event.getItem());
                            int newFloor = (prevFloor % 5) + 1;
                            setFloorInItemLore(event.getItem(), newFloor);
                            ms.sendMessage(player, MessageComponent.of("<green>Changed floor from <yellow>%s</yellow> to <yellow>%s</yellow></green>", prevFloor, newFloor));

                            if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_BOSS)) {
                                bossSpawnDisplays[prevFloor - 1].clearDisplays(false, player);
                                ViewPoint3D point = gameSettings.getBossSpawnLocation(newFloor);
                                if (point != null) {
                                    Vector3Int pos = Vector3Int.fromPoint3D(point);
                                    Location loc = PointToLocation.viewToLoc(point);
                                    bossSpawnDisplays[newFloor - 1].updateDisplays(loc.getWorld(), pos, loc);
                                }
                            } else {
                                bossPlayerSpawnDisplays[prevFloor - 1].clearDisplays(false, player);
                                ViewPoint3D point = gameSettings.getBossPlayerSpawnLocation(newFloor);
                                if (point != null) {
                                    Vector3Int pos = Vector3Int.fromPoint3D(point);
                                    Location loc = PointToLocation.viewToLoc(point);
                                    bossPlayerSpawnDisplays[newFloor - 1].updateDisplays(loc.getWorld(), pos, loc);
                                }
                            }
                            return;
                        } else if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CLASS_BLOCK)) {
                            RPGClassType prevClass = getClassFromItemLore(event.getItem());
                            int index = getClassIndex(prevClass);
                            int newIndex = (index + 1) % 5;
                            RPGClassType newClass = RPGClassType.validTypes()[newIndex];
//                            setClassInItemLore(event.getItem(), newClass);
                            player.getInventory().remove(getClassBlockItem(prevClass));
                            player.getInventory().setItemInMainHand(getClassBlockItem(newClass));
                            ms.sendMessage(player, MessageComponent.of("<green>Changed class from <yellow>%s</yellow> to <yellow>%s</yellow></green>", prevClass, newClass));

                            classBlockDisplays[index].clearDisplays(false, player);
                            Point3D point = gameSettings.getSelectionLocations().get(newClass);
                            if (point != null) {
                                Vector3Int pos = Vector3Int.fromPoint3D(point);
                                Location loc = PointToLocation.blockToLoc(point);
                                classBlockDisplays[newIndex].updateDisplays(loc.getWorld(), pos);
                            }
                            return;
                        }
                    }

                    Location loc = player.getLocation();
                    ViewPoint3D point3D = LocToPoint.viewToLoc(loc);
                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_PRE_LOBBY)) {
                        gameSettingsLoader.setViewLocation(GameSettingsLoader.LOCATIONS_PRELOBBYSPAWN, point3D);
                        ms.sendMessage(player, MessageComponent.of("<green>Location %s has been set to <yellow>%s</yellow></green>", preLobbySpawnDisplay.name, point3D));
                        Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                        preLobbySpawnDisplay.updateDisplays(player.getWorld(), pos, PointToLocation.viewToLoc(point3D));
                        return;
                    } else if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CLASS_SELECTION)) {
                        gameSettingsLoader.setViewLocation(GameSettingsLoader.LOCATIONS_SELECTIONSPAWN, point3D);
                        ms.sendMessage(player, MessageComponent.of("<green>Location %s has been set to <yellow>%s</yellow></green>", classSelectionSpawnDisplay.name, point3D));
                        Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                        classSelectionSpawnDisplay.updateDisplays(player.getWorld(), pos, PointToLocation.viewToLoc(point3D));
                        return;
                    } else if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_BOSS)) {
                        int floor = getFloorFromItemLore(event.getItem());
                        gameSettingsLoader.setBossSpawnLocation(floor, point3D);
                        ms.sendMessage(player, MessageComponent.of("<green>Location %s has been set to <yellow>%s</yellow></green>", bossSpawnDisplays[floor - 1].name, point3D));
                        Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                        bossSpawnDisplays[floor - 1].updateDisplays(player.getWorld(), pos, PointToLocation.viewToLoc(point3D));
                        return;
                    } else if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_BOSS_PLAYER)) {
                        int floor = getFloorFromItemLore(event.getItem());
                        gameSettingsLoader.setBossPlayerSpawnLocation(floor, point3D);
                        ms.sendMessage(player, MessageComponent.of("<green>Location %s has been set to <yellow>%s</yellow></green>", bossPlayerSpawnDisplays[floor - 1].name, point3D));
                        Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                        bossPlayerSpawnDisplays[floor - 1].updateDisplays(player.getWorld(), pos, PointToLocation.viewToLoc(point3D));
                        return;
                    }
                }
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    ViewPoint3D resetPoint = new ViewPoint3D(0, 64, 0, "", 0, 0);
                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_PRE_LOBBY)) {
                        gameSettingsLoader.setViewLocation(GameSettingsLoader.LOCATIONS_PRELOBBYSPAWN, resetPoint);
                        ms.sendMessage(player, MessageComponent.of("<yellow>Selection %s has been reset</yellow>", "prelobbyspawn"));
                        preLobbySpawnDisplay.clearDisplays(true, player);
                        return;
                    } else if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CLASS_SELECTION)) {
                        gameSettingsLoader.setViewLocation(GameSettingsLoader.LOCATIONS_SELECTIONSPAWN, resetPoint);
                        ms.sendMessage(player, MessageComponent.of("<yellow>Selection %s has been reset</yellow>", "prelobbyspawn"));
                        classSelectionSpawnDisplay.clearDisplays(true, player);
                        return;
                    }
                }
                switch (event.getAction()) {
                    case LEFT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        Point3D point3D = LocToPoint.locToBlock(loc);
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_HOLE_CENTER)) {
                            gameSettingsLoader.setLocation(GameSettingsLoader.LOCATIONS_HOLECENTER, point3D);
                            ms.sendMessage(player, MessageComponent.of("<green>Location %s has been set to <yellow>%s</yellow></green>", holeCenterDisplay.name, point3D));
                            Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                            holeCenterDisplay.updateDisplays(player.getWorld(), pos);
                            return;
                        } else if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CLASS_BLOCK)) {
                            RPGClassType classType = getClassFromItemLore(event.getItem());
                            int index = getClassIndex(classType);
                            gameSettingsLoader.setClassBlockLocation(classType, point3D);
                            ms.sendMessage(player, MessageComponent.of("<green>Location %s has been set to <yellow>%s</yellow></green>", classBlockDisplays[index].name, point3D));
                            Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                            classBlockDisplays[index].updateDisplays(player.getWorld(), pos);
                            return;
                        }
                    }
                    case RIGHT_CLICK_BLOCK -> {

                    }
                    case LEFT_CLICK_AIR -> {

                    }
                    case RIGHT_CLICK_AIR -> {

                    }
                }
                event.setCancelled(false);
            }
        }, PlayerInteractEvent.class));
    }

    private int getClassIndex(RPGClassType rpgClassType) {
        for (int i = 0; i < RPGClassType.validTypes().length; i++) {
            RPGClassType classType = RPGClassType.validTypes()[i];
            if (classType == rpgClassType)
                return i;
        }
        return 0;
    }

    private RPGClassType getClassFromItemLore(ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();
        List<String> lore = itemMeta.getLore();
        String line = lore.get(1);
        return RPGClassType.valueOf(line.substring(line.indexOf(":") + 2));
    }

    private void setClassInItemLore(ItemStack item, RPGClassType classType) {
        ItemMeta itemMeta = item.getItemMeta();
        List<String> lore = itemMeta.getLore();
        String line = lore.get(1);
        lore.set(1, line.substring(0, line.indexOf(":") + 2) + classType);
        itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
    }

    private int getFloorFromItemLore(ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();
        List<String> lore = itemMeta.getLore();
        String line = lore.get(1);
        return Integer.parseInt(line.substring(line.indexOf(":") + 2));
    }

    private void setFloorInItemLore(ItemStack item, int newFloor) {
        ItemMeta itemMeta = item.getItemMeta();
        List<String> lore = itemMeta.getLore();
        String line = lore.get(1);
        lore.set(1, line.substring(0, line.indexOf(":") + 2) + newFloor);
        itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
    }

    public static ItemDisplay spawnDirectionArrowItemDisplay(World world, Location location) {
        location.add(0.5,1.5,0.5);

        ItemDisplay display = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(location, ItemDisplay.class);
        display.setItemStack(new ItemStack(Material.ARROW));

        double yawRad = Math.toRadians(90 - location.getYaw());
        double pitchRad = Math.toRadians(location.getPitch() + 45);
        display.setTransformation(new Transformation(
                new Vector3f(0),
                new Quaternionf().rotationY((float) yawRad),
                new Vector3f(1),
                new Quaternionf().rotationZ((float) pitchRad)
        ));

        return display;
    }

    private void repositionArrowItemDisplay(World world, Location location, ItemDisplay display) {
        double yawRad = Math.toRadians(90 - location.getYaw());
        double pitchRad = Math.toRadians(location.getPitch() + 45);
        location.setYaw(0);
        location.setPitch(0);
        display.teleport(location.add(0.5,1.5,0.5));

        display.setTransformation(new Transformation(
                new Vector3f(0),
                new Quaternionf().rotationY((float) yawRad),
                new Vector3f(1),
                new Quaternionf().rotationZ((float) pitchRad)
        ));
    }

    private boolean isItemBeingUsedByOtherPlayers(Player ignoredPlayer, String itemName) {
        boolean isBeingUsed = false;
        for (UUID uuid : playersInMode) {
            if (uuid.equals(ignoredPlayer.getUniqueId())) continue;
            Player player = DMain.getInstance().getServer().getPlayer(uuid);
            if (player != null && SetupUtils.hasItemInMainHand(player, itemName)) {
                isBeingUsed = true;
                break;
            }
        }
        return isBeingUsed;
    }

    private class SpawnPositionDisplay {
        private final String itemName;
        private final String name;
        private final Material material;
        private final Color glowColor;

        private BlockDisplay boundingBox;
        private TextDisplay nameDisplay;
        private ItemDisplay directionDisplay;

        public SpawnPositionDisplay(String itemName, String name, Material material, Color glowColor) {
            this.itemName = itemName;
            this.name = name;
            this.material = material;
            this.glowColor = glowColor;
        }

        public void updateDisplays(World world, Vector3Int pos, Location loc) {
            if (boundingBox == null) {
                boundingBox = SetupUtils.spawnGlowingBlockDisplay(world, pos, pos.add(0,1,0), material, glowColor);
                nameDisplay = SetupUtils.spawnTextDisplayInBlockCenter(world, pos, name);
                directionDisplay = spawnDirectionArrowItemDisplay(world, loc);
            } else {
                SetupUtils.repositionBlockDisplay(world, pos, boundingBox);
                SetupUtils.repositionTextDisplay(world, pos, nameDisplay);
                repositionArrowItemDisplay(world, loc, directionDisplay);
            }
        }

        public void clearDisplays(boolean force, Player ignoredPlayer) {
            if (boundingBox != null) {
                if (!force) {
                    if (isItemBeingUsedByOtherPlayers(ignoredPlayer, itemName)) return;
                }
                boundingBox.remove();
                boundingBox = null;
                nameDisplay.remove();
                nameDisplay = null;
                directionDisplay.remove();
                directionDisplay = null;
            }
        }
    }

    private class BlockPositionDisplay {
        private final String itemName;
        private final String name;
        private final Material material;
        private final Color glowColor;

        private BlockDisplay boundingBox;
        private TextDisplay nameDisplay;

        public BlockPositionDisplay(String itemName, String name, Material material, Color glowColor) {
            this.itemName = itemName;
            this.name = name;
            this.material = material;
            this.glowColor = glowColor;
        }

        public void updateDisplays(World world, Vector3Int pos) {
            if (boundingBox == null) {
                boundingBox = SetupUtils.spawnGlowingBlockDisplay(world, pos, material, glowColor);
                nameDisplay = SetupUtils.spawnTextDisplayInBlockCenter(world, pos, name);
            } else {
                SetupUtils.repositionBlockDisplay(world, pos, boundingBox);
                SetupUtils.repositionTextDisplay(world, pos, nameDisplay);
            }
        }

        public void clearDisplays(boolean force, Player ignoredPlayer) {
            if (boundingBox != null) {
                if (!force) {
                    if (isItemBeingUsedByOtherPlayers(ignoredPlayer, itemName)) return;
                }
                boundingBox.remove();
                boundingBox = null;
                nameDisplay.remove();
                nameDisplay = null;
            }
        }
    }
}
