package dev.core.game.settings;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Floor1BossSettings extends BossSettings {
    private int heightThreshold50Percent;
    private int heightThreshold100Percent;

    public Floor1BossSettings(int floor) {
        super(floor);
    }
}
