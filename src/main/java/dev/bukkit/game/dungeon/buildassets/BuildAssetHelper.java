package dev.bukkit.game.dungeon.buildassets;

import dev.bukkit.command.CommandManager;
import dev.bukkit.command.SubCommandBuilder;
import dev.bukkit.utils.*;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.utils.MessageComponent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.*;

public class BuildAssetHelper extends SetupHelper {

//    private static final BuildAssetHelper INSTANCE = new BuildAssetHelper();
//
//    public static BuildAssetHelper getInstance() {
//        return INSTANCE;
//    }

//    private final List<UUID> playersInBuildMode;
    private final Map<UUID, PlayerBuildAssetData> playerData;
    private final BuildAssetManager buildAssetManager;

    public BuildAssetHelper(BuildAssetManager buildAssetManager) {
        super("BuildAssetMode");
        this.buildAssetManager = buildAssetManager;
//        playersInBuildMode = new LinkedList<>();
        playerData = new HashMap<>();
    }

//    public boolean isPlayerInBuildMode(Player player, BukkitMessageSender ms, boolean sendErrorMessage) {
//        if (!playersInBuildMode.contains(player.getUniqueId())) {
//            if (sendErrorMessage)
//                ms.sendMessage(player, MessageComponent.of(ChatColor.RED + "You can't use this command, without being in BuildMode!"));
//            return false;
//        }
//        return true;
//    }
//
//    public void setPlayerInBuildMode(BukkitMessageSender ms, Player player) {
//        if (playersInBuildMode.contains(player.getUniqueId()))
//            return;
//        playersInBuildMode.add(player.getUniqueId());
//        playerData.put(player.getUniqueId(), new PlayerBuildAssetData());
//
//        ItemStack item = getBoxSelectionTool();
//        player.getInventory().addItem(item);
//        ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "You received a " + item.getItemMeta().getDisplayName()));
//    }
//
//    public boolean removePlayerFromBuildMode(Player player) {
//        if (playersInBuildMode.contains(player.getUniqueId())) {
//            cleanUpBlockDisplaysForPlayer(player.getUniqueId(), player);
//            player.getInventory().remove(getBoxSelectionTool());
//        }
//        return playersInBuildMode.remove(player.getUniqueId());
//    }


    @Override
    public boolean setPlayerInMode(BukkitMessageSender ms, Player player) {
        if (super.setPlayerInMode(ms, player)) {
            playerData.put(player.getUniqueId(), new PlayerBuildAssetData());
            return true;
        }
        return false;
    }

    @Override
    public boolean removePlayerFromMode(Player player) {
        if (super.removePlayerFromMode(player)) {
            cleanUpBlockDisplaysForPlayer(player.getUniqueId(), player);
            return true;
        }
        return false;
    }

    @Override
    public void giveSetupItemsToPlayer(BukkitMessageSender ms, Player player) {
        player.getInventory().addItem(getBoxSelectionTool());
        ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "You received a " + getBoxSelectionTool().getItemMeta().getDisplayName()));
    }

    @Override
    public void removeSetupItemsFromPlayer(Player player) {
        player.getInventory().remove(getBoxSelectionTool());
    }

    private ItemStack getBoxSelectionTool() {
        return SetupUtils.createSimpleItem(Material.NETHERITE_AXE, "Box Selection Tool",
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select 1. Position", ""),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK_AIR, "Clear 1. Pos Selection", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_BLOCK, "Select 2. Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_AIR, "Clear 2. Pos Selection", "")));
    }

    @Override
    public void cleanUp(Server server, UUID uuid, Player player) {
        if (player != null)
            removePlayerFromMode(player);
        else
            cleanUpBlockDisplaysForPlayer(uuid, player);
    }

    public void cleanUpBlockDisplaysForPlayer(UUID uuid, Player player) {
        var data = playerData.remove(uuid);
        if (data != null) {
            if (data.firstPosDisplay != null) {
                data.firstPosDisplay.remove();
                data.firstPosTextDisplay.remove();
            }
            if (data.secondPosDisplay != null) {
                data.secondPosDisplay.remove();
                data.secondPosTextDisplay.remove();
            }
            if (data.fullBoxDisplay != null)
                data.fullBoxDisplay.remove();
            if (player == null) {
                System.err.println("Player was null when trying to remove a preview of the " + data.lastPreviewedAsset.name() + " build asset!");
                return;
            }
            if (data.lastPreviewedAsset != null)
                data.lastPreviewedAsset.removePreview(player.getWorld(), data.lastPreviewStartPos);
        }
    }

    @Override
    public void registerCommand(CommandManager cm, BukkitMessageSender ms) {
        cm.addSubCommand("project-d", SubCommandBuilder.startBuilding("asset")
                .setPermission("projectd.asset")
                .setPlayerCommandAction(1, "firstPos", (player, args) -> {
                    if (isPlayerInMode(player, ms, true)) {
                        selectFirstPos(ms, player, player.getLocation());
                    }
                })
                .setPlayerCommandAction(1, "secondPos", (player, args) -> {
                    if (isPlayerInMode(player, ms, true)) {
                        selectSecondPos(ms, player, player.getLocation());
                    }
                })
                .setPlayerCommandAction(2, "save", (player, args) -> {
                    if (isPlayerInMode(player, ms, true)) {
                        String name = args[1];
                        var data = playerData.get(player.getUniqueId());
                        if (data == null || data.firstPos == null || data.secondPos == null) {
                            ms.sendMessage(player, MessageComponent.of(ChatColor.RED + "Missing firstPos/secondPos to save asset!"));
                            return;
                        }
                        buildAssetManager.saveAsset(name, player.getWorld(), data.firstPos, data.secondPos);
                        ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "Saving asset from <yellow>%s</yellow> to <yellow>%s</yellow> as: <yellow>%s</yellow>", data.firstPos, data.secondPos, name));
                    }
                }).setCommandArgumentsList(1, "save", "name")
                .setPlayerCommandAction(2, "load", (player, args) -> {
                    String name = args[1];
                    BuildAsset asset = buildAssetManager.getAsset(name);
                    if (asset == null) {
                        player.sendMessage("No build asset found with name: " + name);
                        return;
                    }
                    Vector3Int pos = new Vector3Int(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                    asset.build(player.getServer(), player.getWorld(), pos);
                    ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "Loading <yellow>%s</yellow> asset at <yellow>%s</yellow> with info:", name, pos));
                    ms.sendMessage(player, MessageComponent.of(ChatColor.DARK_GREEN + "firstPos=%s secondPos=%s blocks.size=%s entities.size=%s", asset.startPos(), asset.endPos(), asset.blocks().size(), asset.entities().size()));
                }).setCommandArgumentsList(1, "load", buildAssetManager::getAllAssetNames, "name")
                .setPlayerCommandAction(1, "reloadAssets", (player, args) -> {
                    buildAssetManager.loadAllAssets();
                    ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "Reloaded %s build assets", buildAssetManager.getAllAssetNames().size()));
                })
                .setPlayerCommandAction(2, "showPreview", (player, args) -> {
                    if (isPlayerInMode(player, ms, true)) {
                        String name = args[1];
                        BuildAsset asset = buildAssetManager.getAsset(name);
                        if (asset == null) {
                            player.sendMessage("No build asset found with name: " + name);
                            return;
                        }
                        Vector3Int pos = new Vector3Int(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                        var data = playerData.get(player.getUniqueId());
                        data.lastPreviewedAsset = asset;
                        data.lastPreviewStartPos = pos;
                        asset.showPreview(player.getServer(), player.getWorld(), pos);
                        ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "Showing Preview of <yellow>%s</yellow> asset at <yellow>%s</yellow> with info:", name, pos));
                        ms.sendMessage(player, MessageComponent.of(ChatColor.DARK_GREEN + "firstPos=%s secondPos=%s blocks.size=%s entities.size=%s", asset.startPos(), asset.endPos(), asset.blocks().size(), asset.entities().size()));
                    }
                }).setCommandArgumentsList(1, "showPreview", buildAssetManager::getAllAssetNames, "name")
                .setPlayerCommandAction(1, "removeLastPreview", (player, args) -> {
                    if (isPlayerInMode(player, ms, true)) {
                        var data = playerData.get(player.getUniqueId());
                        if (data.lastPreviewedAsset == null) {
                            ms.sendMessage(player, MessageComponent.of(ChatColor.RED + "No last build asset preview found to remove!"));
                        }
                        data.lastPreviewedAsset.removePreview(player.getWorld(), data.lastPreviewStartPos);
                        ms.sendMessage(player, MessageComponent.of(ChatColor.YELLOW + "Removing Preview of %s asset at %s", data.lastPreviewedAsset.name(), data.lastPreviewStartPos));
                        data.lastPreviewedAsset = null;
                        data.lastPreviewStartPos = null;
                    }
                })
                .setPlayerCommandAction(1, "toggleBuildMode", (player, args) -> {
                    if (isPlayerInMode(player, ms, false)) {
                        removePlayerFromMode(player);
                    } else {
                        setPlayerInMode(ms, player);
                    }
                })
                .setCommandArgumentsList(0, List.of("firstPos", "secondPos", "save", "load", "reloadAssets", "showPreview", "removeLastPreview", "toggleBuildMode"))
        );
    }

    @Override
    public void registerEvents(EventBusInterface eventBus, BukkitMessageSender ms) {
        EventAction<PlayerInteractEvent> blockSelection = new EventAction<PlayerInteractEvent>(event -> {
            Player player = event.getPlayer();
            if (event.getHand() == EquipmentSlot.HAND && isPlayerInMode(player, ms, false) && SetupUtils.hasItemInMainHand(player, "Box Selection Tool")) {
                event.setCancelled(true);
                switch (event.getAction()) {
                    case LEFT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        selectFirstPos(ms, player, loc);
                    }
                    case RIGHT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        selectSecondPos(ms, player, loc);
                    }
                    case LEFT_CLICK_AIR -> {
                        var data = playerData.get(player.getUniqueId());
                        if (data.firstPos != null)
                            ms.sendMessage(player, MessageComponent.of("<yellow>Cleared firstPos</yellow>"));
                        data.firstPos = null;
                        if (data.firstPosDisplay != null) {
                            data.firstPosDisplay.remove();
                            data.firstPosDisplay = null;
                            data.firstPosTextDisplay.remove();
                            data.firstPosTextDisplay = null;
                            checkForFullBox(data, player);
                        }
                    }
                    case RIGHT_CLICK_AIR -> {
                        var data = playerData.get(player.getUniqueId());
                        if (data.secondPos != null)
                            ms.sendMessage(player, MessageComponent.of("<yellow>Cleared secondPos</yellow>"));
                        data.secondPos = null;
                        if (data.secondPosDisplay != null) {
                            data.secondPosDisplay.remove();
                            data.secondPosDisplay = null;
                            data.secondPosTextDisplay.remove();
                            data.secondPosTextDisplay = null;
                            checkForFullBox(data, player);
                        }
                    }
                }
            }
        }, PlayerInteractEvent.class);
        eventBus.subscribe(blockSelection);
    }

    private void selectFirstPos(BukkitMessageSender ms, Player player, Location loc) {
        var data = playerData.get(player.getUniqueId());
        data.firstPos = new Vector3Int(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        ms.sendMessage(player, MessageComponent.of("<green>Set firstPos to: <yellow>%s</yellow></green>", data.firstPos));
        if (data.firstPosDisplay == null) {
            data.firstPosDisplay = SetupUtils.spawnGlowingBlockDisplay(player.getWorld(), data.firstPos, Material.LIME_STAINED_GLASS, Color.LIME);
            data.firstPosTextDisplay = SetupUtils.spawnTextDisplayInBlockCenter(player.getWorld(), data.firstPos, "1");
        } else {
            Location location = new Location(player.getWorld(), data.firstPos.x - 0.05f, data.firstPos.y - 0.05f, data.firstPos.z - 0.05f);
            data.firstPosDisplay.teleport(location);
            location = new Location(player.getWorld(), data.firstPos.x + 0.5f, data.firstPos.y + 0.3f, data.firstPos.z + 0.5f);
            data.firstPosTextDisplay.teleport(location);
        }
        checkForFullBox(data, player);
    }

    private void selectSecondPos(BukkitMessageSender ms, Player player, Location loc) {
        var data = playerData.get(player.getUniqueId());
        data.secondPos = new Vector3Int(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        ms.sendMessage(player, MessageComponent.of("<green>Set secondPos to: <yellow>%s</yellow></green>", data.secondPos));
        if (data.secondPosDisplay == null) {
            data.secondPosDisplay = SetupUtils.spawnGlowingBlockDisplay(player.getWorld(), data.secondPos, Material.RED_STAINED_GLASS, Color.RED);
            data.secondPosTextDisplay = SetupUtils.spawnTextDisplayInBlockCenter(player.getWorld(), data.secondPos, "2");
        } else {
            Location location = new Location(player.getWorld(), data.secondPos.x - 0.05f, data.secondPos.y - 0.05f, data.secondPos.z - 0.05f);
            data.secondPosDisplay.teleport(location);
            location = new Location(player.getWorld(), data.secondPos.x + 0.5f, data.secondPos.y + 0.3f, data.secondPos.z + 0.5f);
            data.secondPosTextDisplay.teleport(location);
        }
        checkForFullBox(data, player);
    }

    private void checkForFullBox(PlayerBuildAssetData data, Player player) {
        if (data.firstPos != null && data.secondPos != null) {
            Vector3Int minPos = new Vector3Int(Math.min(data.firstPos.x, data.secondPos.x), Math.min(data.firstPos.y, data.secondPos.y), Math.min(data.firstPos.z, data.secondPos.z));
            Vector3Int maxPos = new Vector3Int(Math.max(data.firstPos.x, data.secondPos.x), Math.max(data.firstPos.y, data.secondPos.y), Math.max(data.firstPos.z, data.secondPos.z));
            float offset = 0.025f;
            if (data.fullBoxDisplay == null) {
                data.fullBoxDisplay = SetupUtils.spawnGlowingBlockDisplay(player.getWorld(), minPos, maxPos, Material.BLUE_STAINED_GLASS, Color.BLUE, offset);
            } else {
                Transformation transformation = data.fullBoxDisplay.getTransformation();
                Vector3f scaleVec = maxPos.sub(minPos).add(1,1,1).toVector3f();
                transformation.getScale().set(scaleVec.add(new Vector3f(offset * 2)));
                data.fullBoxDisplay.setTransformation(transformation);
                Location loc = new Location(player.getWorld(), minPos.x - offset, minPos.y - offset, minPos.z - offset);
                data.fullBoxDisplay.teleport(loc);
            }
        } else if (data.fullBoxDisplay != null) {
            data.fullBoxDisplay.remove();
            data.fullBoxDisplay = null;
        }
    }

    private static class PlayerBuildAssetData {
        private Vector3Int firstPos;
        private Vector3Int secondPos;
        private BuildAsset lastPreviewedAsset;
        private Vector3Int lastPreviewStartPos;
        private BlockDisplay firstPosDisplay;
        private TextDisplay firstPosTextDisplay;
        private BlockDisplay secondPosDisplay;
        private TextDisplay secondPosTextDisplay;
        private BlockDisplay fullBoxDisplay;

        public PlayerBuildAssetData() {}
    }

}
