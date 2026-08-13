package dev.core.game.coords;

import lombok.Getter;

@Getter
public class ViewPoint3D extends Point3D {

    private float yaw;
    private float pitch;

    public ViewPoint3D(int x, int y, int z) {
        super(x, y, z);
    }

    public ViewPoint3D(int x, int y, int z, String world, float yaw, float pitch) {
        super(x, y, z, world);
        this.yaw = yaw;
        this.pitch = pitch;
    }

}
