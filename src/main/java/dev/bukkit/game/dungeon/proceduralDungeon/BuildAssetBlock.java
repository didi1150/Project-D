package dev.bukkit.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;

public class BuildAssetBlock {

    private final Vector3Int pos;
    private final Material material;
    private final String nbt;

    public BuildAssetBlock(Vector3Int pos, Material material, String nbt) {
        this.pos = pos;
        this.material = material;
        this.nbt = nbt;
    }

    public void place(Server server, World world, Vector3Int startPos) {
        Vector3Int position = startPos.add(pos);
        Block block = world.getBlockAt(position.x, position.y, position.z);
        block.setType(material, false);
        if (!nbt.equalsIgnoreCase("none")) {
            BlockData data = server.createBlockData(nbt);
            block.setBlockData(data);
        }
    }

    public void placePreview(Server server, World world, Vector3Int startPos) {
        Vector3Int position = startPos.add(pos);
        Location loc = new Location(world, position.x, position.y, position.z);
        BlockDisplay display = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        if (!nbt.equalsIgnoreCase("none")) {
            BlockData data = server.createBlockData(nbt);
            display.setBlock(data);
        } else {
            display.setBlock(material.createBlockData());
        }
    }
}
