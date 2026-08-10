package dev.bukkit.utils;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.bukkit.game.coords.LocToPoint;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.settings.GameSettingsLoader;
import dev.core.utils.MessageComponent;
import dev.core.utils.MessageSenderInterface;

public class SetupUtils {

    public static void giveBlockLocationSetter(GameSettingsLoader gameSettingsLoader, Player player, String path,
            EventBusInterface eventBus, MessageSenderInterface messageSender) {
        ItemStack itemStack = new ItemStack(Material.BEDROCK);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "(Place to set Location)");
        itemStack.setItemMeta(meta);

        player.getInventory().addItem(itemStack);
        EventAction<BlockPlaceEvent> blockAction = new EventAction<BlockPlaceEvent>(event -> {
            ItemStack item = event.getItemInHand();
            if (item != null) {
                if (item.getItemMeta() != null
                        && item.getItemMeta().getDisplayName().contains("Place to set Location")) {
                    event.setCancelled(true);
                    Location loc = event.getBlock().getLocation();
                    Point3D point3D = LocToPoint.locToBlock(loc);
                    gameSettingsLoader.setLocation(path, point3D);
                    player.getInventory().remove(event.getItemInHand());
                    if (messageSender instanceof BukkitMessageSender bukkitMessageSender) {
                        bukkitMessageSender.sendLine("<green></green>");
                        bukkitMessageSender.sendCenteredMessage(player, MessageComponent
                                .of("<green>Location %s has been set to <yellow>%s</yellow></green>", path, point3D));
                        bukkitMessageSender.sendLine("<green></green>");
                    }
                }
            }
        }, BlockPlaceEvent.class);
        eventBus.subscribeOnCondition(blockAction, event -> {
            return !hasSetterItemInInventory(event.getPlayer(), "Place to set your Location");
        });
    }

    public static void giveViewLocationSetter(GameSettingsLoader gameSettingsLoader, Player player, String path,
            EventBusInterface eventBus, MessageSenderInterface messageSender) {
        ItemStack itemStack = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "(Right click to set your Location)");
        itemStack.setItemMeta(meta);
        player.getInventory().addItem(itemStack);

        EventAction<PlayerInteractEvent> interactAction = new EventAction<PlayerInteractEvent>(event -> {
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            if (item != null) {
                if (item.getItemMeta() != null
                        && item.getItemMeta().getDisplayName().contains("Right click to set your Location")) {
                    event.setCancelled(true);
                    Location loc = event.getPlayer().getLocation();
                    List<World> worlds = Bukkit.getWorlds();
                    System.out.println(Arrays.toString(worlds.toArray()));
                    ViewPoint3D point3D = LocToPoint.viewToLoc(loc);
                    gameSettingsLoader.setViewLocation(path, point3D);
                    player.getInventory().remove(item);
                    if (messageSender instanceof BukkitMessageSender bukkitMessageSender) {
                        bukkitMessageSender.sendLine("<green></green>");
                        bukkitMessageSender.sendCenteredMessage(player, MessageComponent
                                .of("<green>Location %s has been set to <yellow>%s</yellow></green>", path, point3D));
                        bukkitMessageSender.sendLine("<green></green>");
                    }
                }
            }
        }, PlayerInteractEvent.class);
        eventBus.subscribeOnCondition(interactAction, event -> {
            return !hasSetterItemInInventory(event.getPlayer(), "Right click to set your Location");
        });
    }

    private static boolean hasSetterItemInInventory(Player player, String contains) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getItemMeta() != null && item.getItemMeta().getDisplayName().contains(contains)) {
                return true;
            }
        }
        return false;
    }

    public static void setDungeonWorld(String world, GameSettingsLoader gameSettingsLoader) {
        gameSettingsLoader.setDungeonWorld(world);
    }

    public static void setBossWorld(String world, GameSettingsLoader gameSettingsLoader, int floor) {
        gameSettingsLoader.setBossWorld(world, floor);
    }

}
