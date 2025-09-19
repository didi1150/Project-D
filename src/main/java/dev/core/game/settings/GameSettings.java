package dev.core.game.settings;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.dungeon.Dungeon;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameSettings {

    private static GameSettings currentSettings;

    private int floor = 1;
    private ViewPoint3D preLobbySpawn;

    private ViewPoint3D selectionSpawn;
    private Map<RPGClassType, Point3D> selectionLocations;
    private Point3D holeCenter;
    private int minPlayers;
    private boolean setupMode;

    private Dungeon dungeon;
    private String dungeonWorld;

    private GameSettings() {
        selectionLocations = new HashMap<RPGClassType, Point3D>();
    }

    public void setSelectionLocations(Map<RPGClassType, Point3D> selectionLocations) {
        this.selectionLocations = selectionLocations;
    }

    public static GameSettings getCurrentSettings() {
        if (currentSettings == null) {
            currentSettings = new GameSettings();
        }
        return currentSettings;
    }

}
