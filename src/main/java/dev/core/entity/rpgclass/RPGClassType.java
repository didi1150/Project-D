package dev.core.entity.rpgclass;

import dev.core.item.display.TextColor;

public enum RPGClassType {
    NONE("None", TextColor.WHITE), 
    TANK("Tank", TextColor.GRAY), 
    ASSASSIN("Assassin", TextColor.DARK_GRAY),
    ARCHER("Archer", TextColor.RED), 
    MAGE("Mage", TextColor.BLUE), 
    SUPPORT("Support", TextColor.GREEN);

    private String displayName;
    private TextColor color;

    private RPGClassType(String displayName, TextColor color) {
        this.displayName = displayName;
        this.color = color;
    }
    
    public TextColor getColor() {
        return color;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public static RPGClassType[] validTypes() {
        RPGClassType[] types = new RPGClassType[5];
        types[0] = TANK;
        types[1] = ASSASSIN;
        types[2] = ARCHER;
        types[3] = MAGE;
        types[4] = SUPPORT;
        return types;
    }
}
