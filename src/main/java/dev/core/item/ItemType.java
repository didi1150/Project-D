package dev.core.item;

/**
 * Broad weapon/armor category of an item, used by gameplay systems (e.g. the
 * bow arrow-slot mechanic keys off {@link #BOW}). Future types can be added
 * here as the item library grows.
 */
public enum ItemType {
    SWORD, AXE, BOW, STAFF, MISC, ORB, THROWABLE, SHIELD, HELMET, CHESTPLATE, LEGGINGS, BOOTS
}