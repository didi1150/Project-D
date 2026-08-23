package dev.bukkit.utils;

public enum InterActionType {
    RIGHT_CLICK, LEFT_CLICK, RIGHT_CLICK_AIR, LEFT_CLICK_AIR, SHIFT_RIGHT_CLICK, SHIFT_LEFT_CLICK;

    @Override
    public String toString() {
        return super.toString().replace("_", " ");
    }
}
