package dev.core.game.settings;

import dev.core.game.coords.ViewPoint3D;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BossSettings {
    private int floor;
    private ViewPoint3D bossSpawnLocation;
    private String bossWorld;
    private ViewPoint3D bossPlayerSpawnLocation;

    public BossSettings(int floor) {
        this.floor = floor;
    }
}
