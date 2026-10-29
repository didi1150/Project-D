package dev.bukkit.utils.setup;

import java.util.*;
import java.util.stream.Collectors;

import dev.bukkit.DMain;
import dev.bukkit.game.coords.PointToLocation;
import dev.bukkit.reload.BossReloadService;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.settings.GameSettingsLoader;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;

import dev.bukkit.command.CommandManager;
import dev.bukkit.command.SubCommandBuilder;
import dev.bukkit.game.coords.LocToPoint;
import dev.bukkit.utils.BukkitMessageSender;
import dev.bukkit.utils.InterActionType;
import dev.bukkit.utils.ItemAbilityLore;
import dev.bukkit.utils.bossfight.ChainDisplay;
import dev.bukkit.utils.bossfight.ResourceCrateSpawner;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.coords.Point3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.settings.GameSettings;
import dev.core.utils.AdventureText;
import dev.core.utils.LegacyColors;
import dev.core.utils.MessageComponent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BossSetupHelper extends SetupHelper {

    private final GameSettingsLoader gameSettingsLoader;
    private final BossStageLocationConfig stageLocationConfig;
    private final Map<UUID, Integer> playerFloors = new HashMap<>();

    private static final String DISPLAY_NAME_CHAIN_TEST = "Chain Test Tool";
    private static final String DISPLAY_NAME_RESOURCE_CRATE_SPAWNER = "Resource Crate Spawner Tool";
    private static final String DISPLAY_NAME_PILLAR_LOCATION_SELECTOR = "Pillar Location Selector Tool";
    private static final String DISPLAY_NAME_CHAIN_LINK_LOCATION_SELECTOR = "Chain Link Location Selector Tool";
//    private static final String DISPLAY_NAME_HEIGHT_THRESHOLD_SELECTOR = "Height Threshold Selector Tool";
    private static final String DISPLAY_NAME_STALACTITE_LOCATION_SELECTOR = "Stalactite Location Selector Tool";
    private static final String DISPLAY_NAME_MOB_WAVES_SPAWN_LOCATION_SELECTOR = "Mob Waves Spawn Location Selector Tool";
    private static final String DISPLAY_NAME_REWARD_PLAYER_SPAWN_LOCATION_SELECTOR = "Reward Player Spawn Location Selector Tool";
    private static final String DISPLAY_NAME_REWARD_CHEST_LOCATION_SELECTOR = "Reward Chest Location Selector Tool";

    private final ChainDisplay chainTestDisplay = new ChainDisplay();
    private final Map<Integer, BlockPositionDisplay> pillars = new HashMap<>();
    private final Map<Integer, BlockPositionDisplay> chainLinks = new HashMap<>();
    private final List<Pair<ResourceCrateSpawner, BlockPositionDisplay>> resourceCrateSpawners = new ArrayList<>();
    private final List<BlockPositionDisplay> stalactites = new ArrayList<>();
    private final List<BlockPositionDisplay> mobWaveSpawns = new ArrayList<>();
    private SpawnPositionDisplay rewardPlayerSpawn;
    private BlockPositionDisplay rewardChest;

    public BossSetupHelper(GameSettingsLoader gameSettingsLoader, BossStageLocationConfig stageLocationConfig) {
        super("GameSetupMode");
        this.gameSettingsLoader = gameSettingsLoader;
        this.stageLocationConfig = stageLocationConfig;
    }

    private ItemStack getChainTestTool() {
        return SetupUtils.createSimpleItem(Material.IRON_CHAIN, DISPLAY_NAME_CHAIN_TEST,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select 1. Position", ""),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK_AIR, "Clear 1. Pos Selection", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_BLOCK, "Select 2. Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_AIR, "Clear 2. Pos Selection", ""),
                        new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Change the sag of the chain", "Current sag: " + 0f),
                        new ItemAbilityLore(InterActionType.SNEAK_RIGHT_CLICK, "Change the scale of the chain", "Current scale: " + 1f)
                ));
    }

    private ItemStack getResourceCrateSpawnerTool() {
        return SetupUtils.createSimpleItem(Material.BARREL, DISPLAY_NAME_RESOURCE_CRATE_SPAWNER,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select Block Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Pos Selection", "")
                ));
    }

    private ItemStack getPillarLocationSelectorTool(int pillar) {
        return SetupUtils.createSimpleItem(Material.QUARTZ_PILLAR, DISPLAY_NAME_PILLAR_LOCATION_SELECTOR,
                List.of(new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Switch Pillar number", "Current selected pillar: " + pillar),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select Block Position", "Set Block Position as base-level of the pillar"),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")
                ));
    }

    private ItemStack getChainLinkLocationSelectorTool(int link) {
        return SetupUtils.createSimpleItem(Material.IRON_CHAIN, DISPLAY_NAME_CHAIN_LINK_LOCATION_SELECTOR,
                List.of(new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Switch Chain Link number", "Current selected link: " + link),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select Block Position", "Set Block Position as start of the chain holding the boss"),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")
                ));
    }

//    private ItemStack getHeightThresholdSelectorTool() {
//        return SetupUtils.createSimpleItem(Material.GLOBE_BANNER_PATTERN, DISPLAY_NAME_HEIGHT_THRESHOLD_SELECTOR,
//                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK, "Select 50%-Height Y-Position", ""),
//                        new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Clear 50%-Height Pos Selection", ""),
//                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Select 100%-Height Y-Position", ""),
//                        new ItemAbilityLore(InterActionType.SNEAK_RIGHT_CLICK, "Clear 100%-Height Pos Selection", "")
//                ));
//    }

    private ItemStack getStalactiteLocationSelectorTool() {
        return SetupUtils.createSimpleItem(Material.POINTED_DRIPSTONE, DISPLAY_NAME_STALACTITE_LOCATION_SELECTOR,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Add Block Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_BLOCK, "Remove Block Position", "")
                ));
    }

    private ItemStack getMobWavesSpawnLocationSelectorTool() {
        return SetupUtils.createSimpleItem(Material.ZOMBIE_SPAWN_EGG, DISPLAY_NAME_MOB_WAVES_SPAWN_LOCATION_SELECTOR,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Add Block Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_BLOCK, "Remove Block Position", "")
                ));
    }

    private ItemStack getRewardPlayerSpawnLocationSelectorTool() {
        return SetupUtils.createSimpleItem(Material.DIAMOND, DISPLAY_NAME_REWARD_PLAYER_SPAWN_LOCATION_SELECTOR,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK, "Select Position", "Set current Player-Position as Spawn-Position"),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")
                ));
    }

    private ItemStack getRewardChestLocationSelectorTool() {
        return SetupUtils.createSimpleItem(Material.CHEST, DISPLAY_NAME_REWARD_CHEST_LOCATION_SELECTOR,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select Block Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Selection", "")
                ));
    }

    @Override
    public void giveSetupItemsToPlayer(BukkitMessageSender ms, Player player) {
        ArrayList<ItemStack> items = new ArrayList<>();
        if (getSetupFloor(player) == 1) {
            items.add(getChainTestTool());
            items.add(getResourceCrateSpawnerTool());
            items.add(getStalactiteLocationSelectorTool());
            items.add(getMobWavesSpawnLocationSelectorTool());
            items.add(getPillarLocationSelectorTool(1));
            items.add(getChainLinkLocationSelectorTool(1));
        }
        items.add(getRewardPlayerSpawnLocationSelectorTool());
        items.add(getRewardChestLocationSelectorTool());
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }
        ms.sendMessage(player, MessageComponent.of(LegacyColors.GREEN + "You received: " + items.stream().map(i -> AdventureText.plainOrEmpty(i.getItemMeta().displayName())).collect(Collectors.joining(", "))));
    }

    @Override
    public void removeSetupItemsFromPlayer(Player player) {
        List<ItemStack> items = getAllSetupItems();
        for (ItemStack item : items) {
            player.getInventory().remove(item);
        }
    }

    private List<ItemStack> getAllSetupItems() {
        ArrayList<ItemStack> items = new ArrayList<>(List.of(getChainTestTool(), getResourceCrateSpawnerTool(),
                getStalactiteLocationSelectorTool(), getMobWavesSpawnLocationSelectorTool(), getRewardChestLocationSelectorTool(), getRewardPlayerSpawnLocationSelectorTool()));
        for (int i = 1; i <= 4; i++) {
            items.add(getPillarLocationSelectorTool(i));
            items.add(getChainLinkLocationSelectorTool(i));
        }
        return items;
    }

    @Override
    public boolean removePlayerFromMode(Player player) {
        if (super.removePlayerFromMode(player)) {
            playerFloors.remove(player.getUniqueId());
            if (playersInMode.isEmpty()) {
                clearDisplays();
            }
            return true;
        }
        return false;
    }

    private int getSetupFloor(Player player) {
        Integer floor = playerFloors.get(player.getUniqueId());
        if (floor != null && floor > 0) {
            return floor;
        }
        int currentFloor = GameSettings.getCurrentSettings().getFloor();
        return currentFloor > 0 ? currentFloor : 1;
    }

    @Override
    public void cleanUp(Server server) {
        super.cleanUp(server);
        clearDisplays();
    }

    private void clearDisplays() {
        chainTestDisplay.clearDisplays(true, null);
        pillars.forEach((pillar, display) -> {
            display.clearDisplays();
        });
        pillars.clear();
        chainLinks.forEach((chainLink, display) -> {
            display.clearDisplays();
        });
        chainLinks.clear();
        for (Pair<ResourceCrateSpawner, BlockPositionDisplay> pair : resourceCrateSpawners) {
            pair.first.destroy();
            pair.second.clearDisplays();
        }
        resourceCrateSpawners.clear();
        for (BlockPositionDisplay stalactite : stalactites) {
            stalactite.clearDisplays();
        }
        stalactites.clear();
        for (BlockPositionDisplay mobWaveSpawn : mobWaveSpawns) {
            mobWaveSpawn.clearDisplays();
        }
        mobWaveSpawns.clear();
        if (rewardPlayerSpawn != null) {
            rewardPlayerSpawn.clearDisplays();
        }
        if (rewardChest != null) {
            rewardChest.clearDisplays();
        }
    }

    @Override
    protected void cleanUp(Server server, UUID uuid, @Nullable Player player) {
        if (player != null)
            removePlayerFromMode(player);
    }

    private void loadDisplaysFromFile(World world, int floor) {
        gameSettingsLoader.load();
        GameSettings gameSettings = GameSettings.getCurrentSettings();

        stageLocationConfig.load(floor).ifPresent(locations -> {
            locations.pillarLocations().forEach((pillar, point3D) -> {
                Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Pillar " + pillar, Material.WHITE_STAINED_GLASS, Color.WHITE, pos);
                blockPositionDisplay.spawnDisplay(world);
                pillars.put(pillar, blockPositionDisplay);
            });
            locations.chainLinkLocations().forEach((chainLink, point3D) -> {
                Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Chain " + chainLink, Material.GRAY_STAINED_GLASS, Color.GRAY, pos);
                blockPositionDisplay.spawnDisplay(world);
                chainLinks.put(chainLink, blockPositionDisplay);
            });
            for (Point3D point3D : locations.crateSpawnerLocations()) {
                Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                ResourceCrateSpawner crateSpawner = new ResourceCrateSpawner(Material.QUARTZ, 5, 5, pos);
                crateSpawner.spawnDisplay(world);
                BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Crate", Material.BROWN_STAINED_GLASS, Color.MAROON, pos);
                blockPositionDisplay.spawnDisplay(world);
                resourceCrateSpawners.add(new Pair<>(crateSpawner, blockPositionDisplay));
            }
            for (Point3D point3D : locations.stalactiteLocations()) {
                Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Stalactite", Material.GREEN_STAINED_GLASS, Color.GREEN, pos);
                blockPositionDisplay.spawnDisplay(world);
                stalactites.add(blockPositionDisplay);
            }
            for (Point3D point3D : locations.mobWaveSpawnLocations()) {
                Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Mob Spawn", Material.RED_STAINED_GLASS, Color.RED, pos);
                blockPositionDisplay.spawnDisplay(world);
                mobWaveSpawns.add(blockPositionDisplay);
            }
        });

        ViewPoint3D rewardPlayerSpawnPoint = gameSettings.getRewardPlayerSpawnLocation(floor);
        if (rewardPlayerSpawnPoint != null) {
            rewardPlayerSpawn = new SpawnPositionDisplay("Reward Player Spawn", Material.ORANGE_STAINED_GLASS, Color.ORANGE, rewardPlayerSpawnPoint);
            rewardPlayerSpawn.spawnDisplay(world);
        }
        Point3D rewardChestPoint = gameSettings.getRewardChestLocation(floor);
        if (rewardChestPoint != null) {
            Vector3Int pos = Vector3Int.fromPoint3D(rewardChestPoint);
            rewardChest = new BlockPositionDisplay("Reward Chest", Material.YELLOW_STAINED_GLASS, Color.YELLOW, pos);
            rewardChest.spawnDisplay(world);
        }
    }

    @Override
    public void registerCommand(CommandManager cm, BukkitMessageSender ms) {
        cm.addSubCommand("project-d", SubCommandBuilder.startBuilding("bossSetup")
                        .setPlayerCommandAction(1, "toggleSetupMode",
                                ((player, _) -> toggleSetupMode(ms, player, defaultSetupFloor())))
                        .setPlayerCommandAction(2, "toggleSetupMode", ((player, args) -> {
                            try {
                                int floor = Integer.parseInt(args[1]);
                                if (floor <= 0) {
                                    ms.sendMessage(player, MessageComponent.of("<red>Floor must be greater than 0.</red>"));
                                    return;
                                }
                                toggleSetupMode(ms, player, floor);
                            } catch (NumberFormatException e) {
                                ms.sendMessage(player, MessageComponent.of("<red>Usage: /d bossSetup toggleSetupMode [floor]</red>"));
                            }
                        }))
                        .setPlayerCommandAction(3, "setHeightThreshold", ((player, args) -> {
                            if (isPlayerInMode(player, ms, true)) {
                                int threshold = Integer.parseInt(args[1]);
                                int height = Integer.parseInt(args[2]);
                                ms.sendMessage(player, MessageComponent.of("<green>Set height threshold for %s% to <yellow>%s</yellow></green>", threshold, height));
                                if (threshold == 50) {
                                    gameSettingsLoader.setHeightThreshold50Percent(1, height);
                                } else if (threshold == 100) {
                                    gameSettingsLoader.setHeightThreshold100Percent(1, height);
                                }
                            }
                        }))
                        .setCommandArgumentsList(1, "toggleSetupMode", List.of("1", "2", "3", "4", "5"))
                        .setCommandArgumentsList(1, "setHeightThreshold", List.of("50", "100"))
                        .setCommandArgumentsList(2, "setHeightThreshold", "relativeHeight(Integer)")
                        .setCommandArgumentsList(0, List.of("toggleSetupMode", "setHeightThreshold"))
        );
    }

    private void toggleSetupMode(BukkitMessageSender ms, Player player, int floor) {
        if (isPlayerInMode(player, ms, false)) {
            removePlayerFromMode(player);
            return;
        }
        playerFloors.put(player.getUniqueId(), floor);
        if (!setPlayerInMode(ms, player)) {
            playerFloors.remove(player.getUniqueId());
            return;
        }
        if (playersInMode.size() == 1) {
            loadDisplaysFromFile(player.getWorld(), floor);
        }
    }

    private int defaultSetupFloor() {
        int floor = GameSettings.getCurrentSettings().getFloor();
        return floor > 0 ? floor : 1;
    }

    private void sendStageConfigError(BukkitMessageSender ms, Player player, int floor) {
        ms.sendMessage(player, MessageComponent.of(
                "<red>Could not save stage locations: no GROW stage found for floor %s.</red>", floor));
    }

    private void reloadBossStages() {
        BossReloadService.reload(DMain.getInstance().getConfigManager());
    }

    @Override
    public void registerEvents(EventBusInterface eventBus, BukkitMessageSender ms) {
        GameSettings gameSettings = GameSettings.getCurrentSettings();
        eventBus.subscribe(new EventAction<>(event -> {
            Player player = event.getPlayer();
            if (event.getHand() == EquipmentSlot.HAND && isPlayerInMode(player, ms, false)) {
                event.setCancelled(true);
                int floor = getSetupFloor(player);

                //any left click
                if (event.getAction().isLeftClick()) {
                    //sneak left click
                    if (player.isSneaking()) {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            int oldSag = (int) (chainTestDisplay.getSag() * 100);
                            int newSag = oldSag;
                            if (oldSag < 10) {
                                newSag += 2;
                            } else if (oldSag < 20) {
                                newSag += 5;
                            } else if (oldSag < 100) {
                                newSag += 10;
                            } else {
                                newSag = 0;
                            }
                            SetupUtils.setValueInItemLore(event.getItem(), newSag + "%", 9);
                            ms.sendMessage(player, MessageComponent.of("<green>Set sag to <yellow>%s</yellow></green>", newSag + "%"));
                            chainTestDisplay.setSag(player.getWorld(), newSag / 100f);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_PILLAR_LOCATION_SELECTOR)) {
                            int oldPillar = Integer.parseInt(SetupUtils.getValueFromItemLore(event.getItem(), 1));
                            int newPillar = (oldPillar % 4) + 1;
                            SetupUtils.setValueInItemLore(event.getItem(), String.valueOf(newPillar), 1);
                            ms.sendMessage(player, MessageComponent.of("<green>Set pillar number to <yellow>%s</yellow></green>", newPillar));
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_LINK_LOCATION_SELECTOR)) {
                            int oldChainLink = Integer.parseInt(SetupUtils.getValueFromItemLore(event.getItem(), 1));
                            int newChainLink = (oldChainLink % 4) + 1;
                            SetupUtils.setValueInItemLore(event.getItem(), String.valueOf(newChainLink), 1);
                            ms.sendMessage(player, MessageComponent.of("<green>Set chain link number to <yellow>%s</yellow></green>", newChainLink));
                            return;
                        }
//                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_HEIGHT_THRESHOLD_SELECTOR)) {
//                            int height = 0;
//                            ms.sendMessage(player, MessageComponent.of("<green>Set height threshold for 50% to <yellow>%s</yellow></green>", height));
//                            gameSettingsLoader.setHeightThreshold50Percent(1, height);
//                            return;
//                        }
                    }
//                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_HEIGHT_THRESHOLD_SELECTOR)) {
//                        int height = player.getLocation().getBlockY();
//                        ms.sendMessage(player, MessageComponent.of("<green>Set height threshold for 50% to <yellow>%s</yellow></green>", height));
//                        gameSettingsLoader.setHeightThreshold50Percent(1, height);
//                        return;
//                    }
                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_REWARD_PLAYER_SPAWN_LOCATION_SELECTOR)) {
                        Location loc = player.getLocation();
                        ViewPoint3D point3D = LocToPoint.viewToLoc(loc);
                        ms.sendMessage(player, MessageComponent.of("<green>Set pos: <yellow>%s</yellow></green>", point3D));
                        if (rewardPlayerSpawn != null) {
                            rewardPlayerSpawn.clearDisplays();
                        }
                        rewardPlayerSpawn = new SpawnPositionDisplay("Reward Player Spawn", Material.ORANGE_STAINED_GLASS, Color.ORANGE, point3D);
                        rewardPlayerSpawn.spawnDisplay(player.getWorld());
                        gameSettingsLoader.setRewardPlayerSpawnLocation(floor, point3D);
                        return;
                    }
                }
                //any right click
                if (event.getAction().isRightClick()) {
                    //sneak right click
                    if (player.isSneaking()) {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            float oldScale = chainTestDisplay.getScale();
                            float newScale = oldScale;
                            if (oldScale < 6) {
                                newScale += 1;
                            } else if (oldScale < 16) {
                                newScale += 2;
                            } else if (oldScale < 40) {
                                newScale += 4;
                            } else {
                                newScale = 1;
                            }
                            SetupUtils.setValueInItemLore(event.getItem(), String.valueOf(newScale), 11);
                            ms.sendMessage(player, MessageComponent.of("<green>Set scale to <yellow>%s</yellow></green>", newScale));
                            chainTestDisplay.setScale(player.getWorld(), newScale);
                            return;
                        }
//                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_HEIGHT_THRESHOLD_SELECTOR)) {
//                            int height = 0;
//                            ms.sendMessage(player, MessageComponent.of("<green>Set height threshold for 100% to <yellow>%s</yellow></green>", height));
//                            gameSettingsLoader.setHeightThreshold100Percent(1, height);
//                            return;
//                        }
                    }
                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_REWARD_PLAYER_SPAWN_LOCATION_SELECTOR)) {
                        if (gameSettingsLoader.removeRewardPlayerSpawnLocation(floor)) {
                            ms.sendMessage(player, MessageComponent.of("<yellow>Cleared pos</yellow>"));
                            if (rewardPlayerSpawn != null) {
                                rewardPlayerSpawn.clearDisplays();
                                rewardPlayerSpawn = null;
                            }
                        }
                        return;
                    }
                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_REWARD_CHEST_LOCATION_SELECTOR)) {
                        if (gameSettingsLoader.removeRewardChestLocation(floor)) {
                            ms.sendMessage(player, MessageComponent.of("<yellow>Cleared pos</yellow>"));
                            if (rewardChest != null) {
                                rewardChest.clearDisplays();
                                rewardChest = null;
                            }
                        }
                        return;
                    }
//                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_HEIGHT_THRESHOLD_SELECTOR)) {
//                        int height = player.getLocation().getBlockY();
//                        ms.sendMessage(player, MessageComponent.of("<green>Set height threshold for 100% to <yellow>%s</yellow></green>", height));
//                        gameSettingsLoader.setHeightThreshold100Percent(1, height);
//                        return;
//                    }
                }
                switch (event.getAction()) {
                    case LEFT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        Point3D point3D = LocToPoint.locToBlock(loc);
                        Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            ms.sendMessage(player, MessageComponent.of("<green>Set firstPos to: <yellow>%s</yellow></green>", pos));
                            chainTestDisplay.setFirstPos(player.getWorld(), pos);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_PILLAR_LOCATION_SELECTOR)) {
                            int pillar = Integer.parseInt(SetupUtils.getValueFromItemLore(event.getItem(), 1));
                            if (!stageLocationConfig.setPillarLocation(floor, pillar, point3D)) {
                                sendStageConfigError(ms, player, floor);
                                return;
                            }
                            reloadBossStages();
                            if (pillars.containsKey(pillar)) {
                                pillars.get(pillar).clearDisplays();
                            }
                            ms.sendMessage(player, MessageComponent.of("<green>Set pillar %s pos: <yellow>%s</yellow></green>", pillar, pos));
                            BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Pillar " + pillar, Material.WHITE_STAINED_GLASS, Color.WHITE, pos);
                            blockPositionDisplay.spawnDisplay(player.getWorld());
                            pillars.put(pillar, blockPositionDisplay);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_LINK_LOCATION_SELECTOR)) {
                            int chainLink = Integer.parseInt(SetupUtils.getValueFromItemLore(event.getItem(), 1));
                            if (!stageLocationConfig.setChainLinkLocation(floor, chainLink, point3D)) {
                                sendStageConfigError(ms, player, floor);
                                return;
                            }
                            reloadBossStages();
                            if (chainLinks.containsKey(chainLink)) {
                                chainLinks.get(chainLink).clearDisplays();
                            }
                            ms.sendMessage(player, MessageComponent.of("<green>Set chain link %s pos: <yellow>%s</yellow></green>", chainLink, pos));
                            BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Chain " + chainLink, Material.GRAY_STAINED_GLASS, Color.GRAY, pos);
                            blockPositionDisplay.spawnDisplay(player.getWorld());
                            chainLinks.put(chainLink, blockPositionDisplay);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_RESOURCE_CRATE_SPAWNER)) {
                            if (resourceCrateSpawners.stream().anyMatch(c -> c.first.getPos().equals(pos))) {
                                ms.sendMessage(player, MessageComponent.of("<red>Already added this pos!</red>", pos));
                                return;
                            }
                            if (!stageLocationConfig.addCrateSpawnerLocation(floor, point3D)) {
                                sendStageConfigError(ms, player, floor);
                                return;
                            }
                            reloadBossStages();
                            ms.sendMessage(player, MessageComponent.of("<green>Added pos: <yellow>%s</yellow></green>", pos));
                            ResourceCrateSpawner crateSpawner = new ResourceCrateSpawner(Material.QUARTZ, 5, 5, pos);
                            crateSpawner.spawnDisplay(player.getWorld());
                            BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Crate", Material.BROWN_STAINED_GLASS, Color.MAROON, pos);
                            blockPositionDisplay.spawnDisplay(player.getWorld());
                            resourceCrateSpawners.add(new Pair<>(crateSpawner, blockPositionDisplay));
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_STALACTITE_LOCATION_SELECTOR)) {
                            if (stalactites.stream().anyMatch(b -> b.pos.equals(pos))) {
                                ms.sendMessage(player, MessageComponent.of("<red>Already added this pos!</red>", pos));
                                return;
                            }
                            if (!stageLocationConfig.addStalactiteLocation(floor, point3D)) {
                                sendStageConfigError(ms, player, floor);
                                return;
                            }
                            reloadBossStages();
                            ms.sendMessage(player, MessageComponent.of("<green>Added pos: <yellow>%s</yellow></green>", pos));
                            BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Stalactite", Material.GREEN_STAINED_GLASS, Color.GREEN, pos);
                            blockPositionDisplay.spawnDisplay(player.getWorld());
                            stalactites.add(blockPositionDisplay);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_MOB_WAVES_SPAWN_LOCATION_SELECTOR)) {
                            if (mobWaveSpawns.stream().anyMatch(b -> b.pos.equals(pos))) {
                                ms.sendMessage(player, MessageComponent.of("<red>Already added this pos!</red>", pos));
                                return;
                            }
                            if (!stageLocationConfig.addMobWaveSpawnLocation(floor, point3D)) {
                                sendStageConfigError(ms, player, floor);
                                return;
                            }
                            reloadBossStages();
                            ms.sendMessage(player, MessageComponent.of("<green>Added pos: <yellow>%s</yellow></green>", pos));
                            BlockPositionDisplay blockPositionDisplay = new BlockPositionDisplay("Mob Spawn", Material.RED_STAINED_GLASS, Color.RED, pos);
                            blockPositionDisplay.spawnDisplay(player.getWorld());
                            mobWaveSpawns.add(blockPositionDisplay);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_REWARD_CHEST_LOCATION_SELECTOR)) {
                            ms.sendMessage(player, MessageComponent.of("<green>Set pos: <yellow>%s</yellow></green>", pos));
                            if (rewardChest != null) {
                                rewardChest.clearDisplays();
                            }
                            rewardChest = new BlockPositionDisplay("Reward Chest", Material.YELLOW_STAINED_GLASS, Color.YELLOW, pos);
                            rewardChest.spawnDisplay(player.getWorld());
                            gameSettingsLoader.setRewardChestLocation(floor, point3D);
                            return;
                        }
                    }
                    case RIGHT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        Point3D point3D = LocToPoint.locToBlock(loc);
                        Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            ms.sendMessage(player, MessageComponent.of("<green>Set secondPos to: <yellow>%s</yellow></green>", pos));
                            chainTestDisplay.setSecondPos(player.getWorld(), pos);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_PILLAR_LOCATION_SELECTOR)) {
                            if (stageLocationConfig.removePillarLocation(floor, point3D)) {
                                reloadBossStages();
                                ms.sendMessage(player, MessageComponent.of("<yellow>Cleared pos</yellow>"));
                                int pillar = pillars.keySet().stream().filter(p -> pillars.get(p).pos.equals(pos)).findFirst().orElse(0);
                                if (pillar != 0) {
                                    pillars.get(pillar).clearDisplays();
                                    pillars.remove(pillar);
                                }
                            }
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_LINK_LOCATION_SELECTOR)) {
                            if (stageLocationConfig.removeChainLinkLocation(floor, point3D)) {
                                reloadBossStages();
                                ms.sendMessage(player, MessageComponent.of("<yellow>Cleared pos</yellow>"));
                                int chainLink = chainLinks.keySet().stream().filter(p -> chainLinks.get(p).pos.equals(pos)).findFirst().orElse(0);
                                if (chainLink != 0) {
                                    chainLinks.get(chainLink).clearDisplays();
                                    chainLinks.remove(chainLink);
                                }
                            }
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_RESOURCE_CRATE_SPAWNER)) {
                            if (stageLocationConfig.removeCrateSpawnerLocation(floor, point3D)) {
                                reloadBossStages();
                                ms.sendMessage(player, MessageComponent.of("<yellow>Removed pos</yellow>"));
                                Pair<ResourceCrateSpawner, BlockPositionDisplay> pair = resourceCrateSpawners.stream().filter(c -> pos.equals(c.first.getPos())).findFirst().orElse(null);
                                if (pair != null) {
                                    pair.first.destroy();
                                    pair.second.clearDisplays();
                                    resourceCrateSpawners.remove(pair);
                                }
                            }
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_STALACTITE_LOCATION_SELECTOR)) {
                            if (stageLocationConfig.removeStalactiteLocation(floor, point3D)) {
                                reloadBossStages();
                                ms.sendMessage(player, MessageComponent.of("<yellow>Removed pos</yellow>"));
                                BlockPositionDisplay blockPositionDisplay = stalactites.stream().filter(b -> b.pos.equals(pos)).findFirst().orElse(null);
                                if (blockPositionDisplay != null) {
                                    blockPositionDisplay.clearDisplays();
                                    stalactites.remove(blockPositionDisplay);
                                }
                            }
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_MOB_WAVES_SPAWN_LOCATION_SELECTOR)) {
                            if (stageLocationConfig.removeMobWaveSpawnLocation(floor, point3D)) {
                                reloadBossStages();
                                ms.sendMessage(player, MessageComponent.of("<yellow>Removed pos</yellow>"));
                                BlockPositionDisplay blockPositionDisplay = mobWaveSpawns.stream().filter(b -> b.pos.equals(pos)).findFirst().orElse(null);
                                if (blockPositionDisplay != null) {
                                    blockPositionDisplay.clearDisplays();
                                    mobWaveSpawns.remove(blockPositionDisplay);
                                }
                            }
                            return;
                        }
                    }
                    case LEFT_CLICK_AIR -> {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            if (chainTestDisplay.getFirstPos() != null) {
                                ms.sendMessage(player, MessageComponent.of("<yellow>Cleared firstPos</yellow>"));
                                chainTestDisplay.setFirstPos(player.getWorld(), null);
                            }
                            return;
                        }
                    }
                    case RIGHT_CLICK_AIR -> {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            if (chainTestDisplay.getSecondPos() != null) {
                                ms.sendMessage(player, MessageComponent.of("<yellow>Cleared secondPos</yellow>"));
                                chainTestDisplay.setSecondPos(player.getWorld(), null);
                            }
                            return;
                        }
                    }
                }
                event.setCancelled(false);
            }
        }, PlayerInteractEvent.class));
    }

    private record Pair<K,V>(K first, V second){}

    private class BlockPositionDisplay {
        private final String name;
        private final Material material;
        private final Color glowColor;
        private final Vector3Int pos;

        private BlockDisplay boundingBox;
        private TextDisplay nameDisplay;

        public BlockPositionDisplay(String name, Material material, Color glowColor, Vector3Int pos) {
            this.name = name;
            this.material = material;
            this.glowColor = glowColor;
            this.pos = pos;
        }

        public void spawnDisplay(World world) {
            if (boundingBox == null) {
                boundingBox = SetupUtils.spawnGlowingBlockDisplay(world, pos, material, glowColor);
                nameDisplay = SetupUtils.spawnTextDisplayInBlockCenter(world, pos, name);
            }
        }

        public void clearDisplays() {
            if (boundingBox != null) {
                boundingBox.remove();
                boundingBox = null;
                nameDisplay.remove();
                nameDisplay = null;
            }
        }
    }

    private class SpawnPositionDisplay {
        private final String name;
        private final Material material;
        private final Color glowColor;
        private final ViewPoint3D point;

        private BlockDisplay boundingBox;
        private TextDisplay nameDisplay;
        private ItemDisplay directionDisplay;

        public SpawnPositionDisplay(String name, Material material, Color glowColor, ViewPoint3D point) {
            this.name = name;
            this.material = material;
            this.glowColor = glowColor;
            this.point = point;
        }

        public void spawnDisplay(World world) {
            Vector3Int pos = Vector3Int.fromPoint3D(point);
            Location loc = PointToLocation.viewToLoc(point);
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

        public void clearDisplays() {
            if (boundingBox != null) {
                boundingBox.remove();
                boundingBox = null;
                nameDisplay.remove();
                nameDisplay = null;
                directionDisplay.remove();
                directionDisplay = null;
            }
        }

        public ItemDisplay spawnDirectionArrowItemDisplay(World world, Location location) {
            location.add(0.5,1.5,0.5);
            double yawRad = Math.toRadians(90 - location.getYaw());
            double pitchRad = Math.toRadians(location.getPitch() + 45);
            location.setYaw(0);
            location.setPitch(0);
            ItemDisplay display = world.spawn(location, ItemDisplay.class);
            display.setItemStack(new ItemStack(Material.ARROW));
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
    }
}
