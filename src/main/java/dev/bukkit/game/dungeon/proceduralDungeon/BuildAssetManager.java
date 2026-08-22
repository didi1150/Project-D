package dev.bukkit.game.dungeon.proceduralDungeon;

import dev.bukkit.command.CommandManager;
import dev.bukkit.command.SubCommandBuilder;
import dev.bukkit.storage.BukkitConfigProvider;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BuildAssetManager {

    private final Plugin plugin;
    private final String directory;

    private final Map<String, BuildAsset> buildAssets;

    public BuildAssetManager(Plugin plugin, String directory) {
        this.plugin = plugin;
        this.directory = directory;
        buildAssets = new HashMap<>();
        loadAllAssets();
    }

    public BuildAsset getAsset(String name) {
        if (buildAssets.containsKey(name)) {
            return buildAssets.get(name);
        }
        System.err.println("No build asset found with name: " + name);
        return null;
    }

    public List<String> getAllAssetNames() {
        return buildAssets.keySet().stream().toList();
    }

    public void saveAsset(String name, World world, Vector3Int firstPos, Vector3Int secondPos) {
        Path path = Path.of(directory, name + ".yml");
        ConfigProvider provider = new BukkitConfigProvider(plugin, path.toString());
        ConfigSection section = provider.getRoot().getSection("area");

        Vector3Int startPos = new Vector3Int(Math.min(firstPos.x, secondPos.x), Math.min(firstPos.y, secondPos.y), Math.min(firstPos.z, secondPos.z));
        Vector3Int endPos = new Vector3Int(Math.max(firstPos.x, secondPos.x), Math.max(firstPos.y, secondPos.y), Math.max(firstPos.z, secondPos.z));

        section.set("startPos", new Vector3Int(0,0,0).toString());
        Vector3Int relativeEndPos = endPos.sub(startPos);
        section.set("endPos", relativeEndPos.toString());

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (int x = startPos.x; x <= endPos.x; x++) {
            for (int y = startPos.y; y <= endPos.y; y++) {
                for (int z = startPos.z; z <= endPos.z; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty()) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("pos", new Vector3Int(x, y, z).sub(startPos).toString());
                        map.put("material", block.getType().toString());
                        map.put("nbt", block.getBlockData().getAsString());
                        blocks.add(map);
                    }
                }
            }
        }
        section.set("blocks", blocks);

        List<Map<String, Object>> entities = new ArrayList<>();
        BoundingBox box = new BoundingBox(startPos.x, startPos.y, startPos.z, endPos.x, endPos.y, endPos.z);
        for (Entity entity : world.getNearbyEntities(box, e -> e.getType() != EntityType.PLAYER)) {
            Map<String, Object> map = new LinkedHashMap<>();
            Vector3f pos = entity.getLocation().toVector().toVector3f().sub(startPos.toVector3f());
            map.put("pos", "(" + pos.x + ", " + pos.y + ", " + pos.z + ")");
            map.put("type", entity.getType().toString());
            map.put("nbt", "none"); //TODO
            entities.add(map);
        }
        section.set("entities", entities);

        provider.save();

        buildAssets.put(name, loadAsset(name, path.getFileName()));
    }

    public void loadAllAssets() {
        buildAssets.clear();
        System.out.println("Loading all assets:");
        File dir = new File(plugin.getDataFolder(), directory);
        if (dir.exists() && dir.isDirectory()) {
            try (var paths = Files.walk(dir.toPath(),1).skip(1)) {
                paths.forEachOrdered(path -> {
                    String name = path.getFileName().toString();
                    name = name.substring(0, name.length() - 4);
                    buildAssets.put(name, loadAsset(name, path.getFileName()));
                });
                System.out.println("Loaded " + buildAssets.size() + " asset(s)" );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (!dir.mkdirs()) System.err.println("Couldn't create directory: " + dir);
        }
    }

    private BuildAsset loadAsset(String name, Path filename) {
        System.out.println("Loading asset for: " + filename);
        Path path = Path.of(directory, filename.toString());
        try {
            ConfigProvider provider = new BukkitConfigProvider(plugin, path.toString());
            ConfigSection root = provider.getRoot().getSection("area");
            Vector3Int startPos = parseVector3Int(root.getString("startPos", "(0,0,0)"));
            Vector3Int endPos = parseVector3Int(root.getString("endPos", "(0,0,0)"));
            List<BuildAssetBlock> blocks = loadBlocks(root.getSectionList("blocks"));
            List<BuildAssetEntity> entities = loadEntities(root.getSectionList("entities"));
            return new BuildAsset(name, startPos, endPos, blocks, entities);
        } catch (Exception e) {
            System.err.println("Couldn't correctly parse build asset file at: " + path);
            throw new IllegalArgumentException(e);
        }
    }

    private Vector3Int parseVector3Int(String pos) {
        try {
            String cut = pos.substring(1, pos.length() - 1);
            String[] values = cut.split(",");
            int x = Integer.parseInt(values[0].trim());
            int y = Integer.parseInt(values[1].trim());
            int z = Integer.parseInt(values[2].trim());
            return new Vector3Int(x, y, z);
        } catch (Exception e) {
            throw new IllegalArgumentException("Something went wrong when parsing a pos from a build asset file:\n" + e.getMessage());
        }
    }

    private Vector3f parseVector3f(String pos) {
        try {
            String cut = pos.substring(1, pos.length() - 1);
            String[] values = cut.split(",");
            float x = Float.parseFloat(values[0].trim());
            float y = Float.parseFloat(values[1].trim());
            float z = Float.parseFloat(values[2].trim());
            return new Vector3f(x, y, z);
        } catch (Exception e) {
            throw new IllegalArgumentException("Something went wrong when parsing a pos from a build asset file:\n" + e.getMessage());
        }
    }

    private List<BuildAssetBlock> loadBlocks(List<ConfigSection> sections) {
        try {
            List<BuildAssetBlock> blocks = new ArrayList<>();
            for (ConfigSection section : sections) {
                Vector3Int pos = parseVector3Int(section.getString("pos", "(0,0,0)"));
                Material material = Material.getMaterial(section.getString("material", "STONE"));
                String nbt = section.getString("nbt", "none");
                blocks.add(new BuildAssetBlock(pos, material, nbt));
            }
            return blocks;
        } catch (Exception e) {
            throw new IllegalArgumentException("Something went wrong when parsing the blocks from a build asset file:\n" + e.getMessage());
        }
    }

    private List<BuildAssetEntity> loadEntities(List<ConfigSection> sections) {
        try {
            List<BuildAssetEntity> entities = new ArrayList<>();
            for (ConfigSection section : sections) {
                Vector3f pos = parseVector3f(section.getString("pos", "(0,0,0)"));
                EntityType type = EntityType.valueOf(section.getString("type", "ARMOR_STAND"));
                String nbt = section.getString("nbt", "none");
                entities.add(new BuildAssetEntity(pos, type, nbt));
            }
            return entities;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Something went wrong when parsing the entities from a build asset file:\n" + e.getMessage());
        }
    }

    private Vector3Int firstPos;
    private Vector3Int secondPos;
    private BuildAsset lastPreviewedAsset;
    private Vector3Int lastPreviewStartPos;

    private boolean checkPerm(org.bukkit.entity.Player player, String node) {
        if (player.hasPermission(node) || player.hasPermission("projectd.admin") || player.isOp()) return true;
        player.sendMessage("§cNo permission: " + node);
        return false;
    }

    public void registerCommand(CommandManager cm) {
        cm.addSubCommand("project-d", SubCommandBuilder.startBuilding("asset")
                .setDescription("Build asset controls")
                .setPlayerCommandAction(1, "firstPos", (player, args) -> {
                    if (!checkPerm(player, "projectd.asset.use")) return;
                    firstPos = new Vector3Int(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                    player.sendMessage("Set firstPos to: " + firstPos);
                })
                .setPlayerCommandAction(1, "secondPos", (player, args) -> {
                    if (!checkPerm(player, "projectd.asset.use")) return;
                    secondPos = new Vector3Int(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                    player.sendMessage("Set secondPos to: " + secondPos);
                })
                .setPlayerCommandAction(2, "save", (player, args) -> {
                    if (!checkPerm(player, "projectd.asset.save")) return;
                    String name = args[1];
                    saveAsset(name, player.getWorld(), firstPos, secondPos);
                    player.sendMessage("Saving asset from " + firstPos + " to " + secondPos + " as: " + name);
                }).setCommandArgumentsList(1, "save", "name")
                .setPlayerCommandAction(2, "load", (player, args) -> {
                    if (!checkPerm(player, "projectd.asset.load")) return;
                    String name = args[1];
                    BuildAsset asset = getAsset(name);
                    if (asset == null){
                        player.sendMessage("No build asset found with name: " + name);
                        return;
                    }
                    Vector3Int pos = new Vector3Int(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                    asset.build(player.getServer(), player.getWorld(), pos);
                    player.sendMessage("Loading " + name + " asset at " + pos + " with info:");
                    player.sendMessage("firstPos=" + asset.startPos() + " secondPos=" + asset.endPos() + " blocks.size=" + asset.blocks().size() + " entities.size=" + asset.entities().size());
                }).setCommandArgumentsList(1, "load", getAllAssetNames(), "name")
                .setPlayerCommandAction(1, "reloadAssets", (player, args) -> {
                    if (!checkPerm(player, "projectd.asset.reload")) return;
                    loadAllAssets();
                    player.sendMessage("Reloaded " + getAllAssetNames().size() + " build assets");
                })
                .setPlayerCommandAction(2, "showPreview", (player, args) -> {
                    if (!checkPerm(player, "projectd.asset.preview")) return;
                    String name = args[1];
                    BuildAsset asset = getAsset(name);
                    if (asset == null){
                        player.sendMessage("No build asset found with name: " + name);
                        return;
                    }
                    Vector3Int pos = new Vector3Int(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                    lastPreviewedAsset = asset;
                    lastPreviewStartPos = pos;
                    asset.showPreview(player.getServer(), player.getWorld(), pos);
                    player.sendMessage("Showing Preview of " + name + " asset at " + pos + " with info:");
                    player.sendMessage("firstPos=" + asset.startPos() + " secondPos=" + asset.endPos() + " blocks.size=" + asset.blocks().size() + " entities.size=" + asset.entities().size());
                }).setCommandArgumentsList(1, "showPreview", this::getAllAssetNames, "name")
                .setPlayerCommandAction(1, "removeLastPreview", (player, args) -> {
                    if (!checkPerm(player, "projectd.asset.preview")) return;
                    if (lastPreviewedAsset == null){
                        player.sendMessage("No last build asset found");
                        return;
                    }
                    lastPreviewedAsset.removePreview(player.getWorld(), lastPreviewStartPos);
                    player.sendMessage("Removing Preview of " + lastPreviewedAsset.name() + " asset at " + lastPreviewStartPos);
                    lastPreviewedAsset = null;
                    lastPreviewStartPos = null;
                })
                .setCommandArgumentsList(0, List.of("firstPos", "secondPos", "save", "load", "reloadAssets", "showPreview", "removeLastPreview"))
        );
    }

}
