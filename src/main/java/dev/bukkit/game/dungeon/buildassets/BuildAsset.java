package dev.bukkit.game.dungeon.buildassets;

import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;

import java.util.List;

public record BuildAsset(String name, Vector3Int startPos, Vector3Int endPos, List<BuildAssetBlock> blocks,
                         List<BuildAssetEntity> entities) {

    public void build(Server server, World world, Vector3Int startPos) {
        for (BuildAssetBlock block : blocks) {
            block.place(server, world, startPos);
        }
        for (BuildAssetEntity entity : entities) {
            entity.summon(world, startPos);
        }
    }

    public void buildAtCenter(Server server, World world, Vector3Int startPos) {
        Vector3Int center = endPos.mul(0.5, 0, 0.5);
        startPos = startPos.sub(center);
        for (BuildAssetBlock block : blocks) {
            block.place(server, world, startPos);
        }
        for (BuildAssetEntity entity : entities) {
            entity.summon(world, startPos);
        }
    }

    public void showPreview(Server server, World world, Vector3Int startPos) {
        for (BuildAssetBlock block : blocks) {
            block.placePreview(server, world, startPos);
        }
    }

    public void showPreviewAtCenter(Server server, World world, Vector3Int startPos) {
        Vector3Int center = endPos.mul(0.5, 0, 0.5);
        startPos = startPos.sub(center);
        for (BuildAssetBlock block : blocks) {
            block.placePreview(server, world, startPos);
        }
    }

    public void removePreview(World world, Vector3Int firstPos, Vector3Int secondPos) {
        BoundingBox box = new BoundingBox(firstPos.x, firstPos.y, firstPos.z, secondPos.x, secondPos.y, secondPos.z).expand(1);
        for (Entity entity : world.getNearbyEntities(box, e -> e.getType() == EntityType.BLOCK_DISPLAY)) {
            entity.remove();
        }
    }

    public void removePreview(World world, Vector3Int startPos) {
        Vector3Int secondPos = startPos.add(endPos);
        BoundingBox box = new BoundingBox(startPos.x, startPos.y, startPos.z, secondPos.x, secondPos.y, secondPos.z).expand(1);
        System.out.println("Removing from " + startPos + " to " + secondPos);
        System.out.println("box: " + box);
        for (Entity entity : world.getNearbyEntities(box, e -> e.getType() == EntityType.BLOCK_DISPLAY)) {
            entity.remove();
        }
    }

}
