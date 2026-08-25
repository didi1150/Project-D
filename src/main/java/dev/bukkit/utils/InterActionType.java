package dev.bukkit.utils;

public enum InterActionType {
    RIGHT_CLICK, LEFT_CLICK, RIGHT_CLICK_AIR, LEFT_CLICK_AIR, RIGHT_CLICK_BLOCK, LEFT_CLICK_BLOCK,
    SNEAK_RIGHT_CLICK, SNEAK_LEFT_CLICK;

    @Override
    public String toString() {
        return super.toString().replace("_", " ");
    }
}
