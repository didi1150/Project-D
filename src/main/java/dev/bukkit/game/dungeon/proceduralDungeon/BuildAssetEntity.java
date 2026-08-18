package dev.bukkit.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.joml.Vector3f;

public class BuildAssetEntity {

    private final Vector3f pos;
    private final EntityType type;
    private final String nbt;

    public BuildAssetEntity(Vector3f pos, EntityType type, String nbt) {
        this.pos = pos;
        this.type = type;
        this.nbt = nbt;
    }

    public void summon(World world, Vector3Int startPos) {
        Vector3f position = startPos.toVector3f().add(pos);
        Location loc = new Location(world, position.x, position.y, position.z);
        world.spawnEntity(loc, type);
    }
}
