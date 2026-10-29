package dev.bukkit.game.boss.pillar;

import java.util.OptionalInt;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class PillarResourceKeys {

    public static final NamespacedKey REPAIR_RESOURCE = new NamespacedKey("project_d", "pillar_repair_resource");
    public static final NamespacedKey PILLAR_INDEX = new NamespacedKey("project_d", "pillar_index");

    private PillarResourceKeys() {
    }

    public static void tagRepairResource(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(REPAIR_RESOURCE, PersistentDataType.BOOLEAN, true);
        stack.setItemMeta(meta);
    }

    public static boolean isRepairResource(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && Boolean.TRUE.equals(
                meta.getPersistentDataContainer().get(REPAIR_RESOURCE, PersistentDataType.BOOLEAN));
    }

    public static void tagPillar(Entity entity, int index) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(PILLAR_INDEX, PersistentDataType.INTEGER, index);
    }

    public static OptionalInt getPillarIndex(Entity entity) {
        if (entity == null) {
            return OptionalInt.empty();
        }
        Integer index = entity.getPersistentDataContainer().get(PILLAR_INDEX, PersistentDataType.INTEGER);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }
}
