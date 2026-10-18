package dev.bukkit.utils.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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

public class BossSetupHelper extends SetupHelper {

    private static final String DISPLAY_NAME_CHAIN_TEST = "Chain Test Tool";
    private static final String DISPLAY_NAME_RESOURCE_PACKAGE_DROPPER = "Resource Package Dropper Tool";

    private final ChainDisplay chainTestDisplay = new ChainDisplay();
    private ResourceCrateSpawner resourceCrateSpawner;

    public BossSetupHelper() {
        super("GameSetupMode");
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

    private ItemStack getResourcePackageDropperTool() {
        return SetupUtils.createSimpleItem(Material.BARREL, DISPLAY_NAME_RESOURCE_PACKAGE_DROPPER,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select Block Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK, "Clear Pos Selection", "")
                        ));
    }

    @Override
    public void giveSetupItemsToPlayer(BukkitMessageSender ms, Player player) {
        List<ItemStack> items = getAllSetupItems();
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
        return new ArrayList<>(List.of(getChainTestTool(), getResourcePackageDropperTool()));
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
        chainTestDisplay.clearDisplays(true, null);
        if (resourceCrateSpawner != null) {
            resourceCrateSpawner.clearDisplay();
            resourceCrateSpawner = null;
        }
    }

    @Override
    protected void cleanUp(Server server, UUID uuid, @Nullable Player player) {
        if (player != null)
            removePlayerFromMode(player);
    }

    @Override
    public void registerCommand(CommandManager cm, BukkitMessageSender ms) {
        cm.addSubCommand("project-d", SubCommandBuilder.startBuilding("bossSetup")
                        .setPlayerCommandAction(1, "toggleSetupMode", ((player, _) -> {
                            if (isPlayerInMode(player, ms, false)) {
                                removePlayerFromMode(player);
                            } else {
                                setPlayerInMode(ms, player);
                            }
                        }))
                        .setCommandArgumentsList(0, Arrays.asList("toggleSetupMode"))
        );
    }

    @Override
    public void registerEvents(EventBusInterface eventBus, BukkitMessageSender ms) {
        GameSettings gameSettings = GameSettings.getCurrentSettings();
        eventBus.subscribe(new EventAction<>(event -> {
            Player player = event.getPlayer();
            if (event.getHand() == EquipmentSlot.HAND && isPlayerInMode(player, ms, false)) {
                event.setCancelled(true);

                //any left click
                if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
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
                    }

                }
                //any right click
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_RESOURCE_PACKAGE_DROPPER)) {
                        if (resourceCrateSpawner != null) {
                            ms.sendMessage(player, MessageComponent.of("<yellow>Cleared pos</yellow>"));
                            resourceCrateSpawner.clearDisplay();
                            resourceCrateSpawner = null;
                        }
                        return;
                    }
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
                    }
                }
                switch (event.getAction()) {
                    case LEFT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        Point3D point3D = LocToPoint.locToBlock(loc);
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                            ms.sendMessage(player, MessageComponent.of("<green>Set firstPos to: <yellow>%s</yellow></green>", pos));
                            chainTestDisplay.setFirstPos(player.getWorld(), pos);
                            return;
                        }
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_RESOURCE_PACKAGE_DROPPER)) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                            if (resourceCrateSpawner != null) {
                                resourceCrateSpawner.clearDisplay();
                            }
                            ms.sendMessage(player, MessageComponent.of("<green>Set pos to: <yellow>%s</yellow></green>", pos));
                            resourceCrateSpawner = new ResourceCrateSpawner(Material.QUARTZ, 5, 5, pos);
                            resourceCrateSpawner.spawnDisplay(player.getWorld());
                            return;
                        }
                    }
                    case RIGHT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        Point3D point3D = LocToPoint.locToBlock(loc);
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                            ms.sendMessage(player, MessageComponent.of("<green>Set secondPos to: <yellow>%s</yellow></green>", pos));
                            chainTestDisplay.setSecondPos(player.getWorld(), pos);
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

}
