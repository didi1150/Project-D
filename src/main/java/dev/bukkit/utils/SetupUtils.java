package dev.bukkit.utils;

import java.util.Arrays;
import java.util.List;

import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.bukkit.game.coords.LocToPoint;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.settings.GameSettingsLoader;
import dev.core.utils.MessageComponent;
import dev.core.utils.MessageSenderInterface;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

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

    public static boolean hasItemInMainHand(Player player, String displayName) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getItemMeta() != null && item.getItemMeta().getDisplayName().contains(displayName);
    }

    public static void setDungeonWorld(String world, GameSettingsLoader gameSettingsLoader) {
        gameSettingsLoader.setDungeonWorld(world);
    }

    public static void setBossWorld(String world, GameSettingsLoader gameSettingsLoader, int floor) {
        gameSettingsLoader.setBossWorld(world, floor);
    }

    public static ItemStack createSimpleItem(Material material, String name, List<ItemAbilityLore> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(lore.stream().flatMap(l -> l.getLore().stream()).toList());
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta.getAttributeModifiers() != null) {
            meta.getAttributeModifiers().clear();
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static TextDisplay spawnTextDisplayInBlockCenter(World world, Vector3Int pos, String text) {
        Vector3f spawnPoint = pos.toVector3f().add(0.5f, 0.3f, 0.5f);
        Location loc = new Location(world, spawnPoint.x, spawnPoint.y, spawnPoint.z);
        return BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(loc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setText(text);
            d.setSeeThrough(true);
            d.setBackgroundColor(d.getBackgroundColor().setAlpha(0));
            Transformation transformation = d.getTransformation();
            transformation.getScale().set(new Vector3f(1.5f));
            d.setTransformation(transformation);
        });
    }

    public static void repositionTextDisplay(World world, Vector3Int pos, TextDisplay textDisplay) {
        Location location = new Location(world, pos.x + 0.5f, pos.y + 0.3f, pos.z + 0.5f);
        textDisplay.teleport(location);
    }

    public static BlockDisplay spawnGlowingBlockDisplay(World world, Vector3Int pos, Material material, Color glowColor) {
        return spawnGlowingBlockDisplay(world, pos, pos, material, glowColor, 0.05f);
    }

    public static BlockDisplay spawnGlowingBlockDisplay(World world, Vector3Int firstPos, Vector3Int secondPos, Material material, Color glowColor) {
        return spawnGlowingBlockDisplay(world, firstPos, secondPos, material, glowColor, 0.05f);
    }

    public static BlockDisplay spawnGlowingBlockDisplay(World world, Vector3Int firstPos, Vector3Int secondPos, Material material, Color glowColor, float offset) {
        Vector3f spawnPoint = firstPos.toVector3f().sub(new Vector3f(offset));
        Location loc = new Location(world, spawnPoint.x, spawnPoint.y, spawnPoint.z);
        return BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(loc, BlockDisplay.class, d -> {
            d.setBlock(Bukkit.createBlockData(material));
            d.setBrightness(new Display.Brightness(15,15));
            d.setGlowing(true);
            d.setGlowColorOverride(glowColor);

            Transformation transformation = d.getTransformation();
            Vector3f scaleVec = secondPos.sub(firstPos).add(1,1,1).toVector3f();
            transformation.getScale().set(scaleVec.add(new Vector3f(offset * 2)));
            d.setTransformation(transformation);
        });
    }

    public static void repositionBlockDisplay(World world, Vector3Int pos, BlockDisplay blockDisplay) {
        Location location = new Location(world, pos.x - 0.05f, pos.y - 0.05f, pos.z - 0.05f);
        blockDisplay.teleport(location);
    }
}
