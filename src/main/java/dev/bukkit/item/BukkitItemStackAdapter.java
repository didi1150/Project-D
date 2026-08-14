package dev.bukkit.item;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.bukkit.item.display.BukkitLoreRenderer;
import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.core.entity.RPGEntity;
import dev.core.item.RPGItem;
import dev.core.item.display.RPGItemLoreRenderer;
import dev.core.item.display.TextColor;

public class BukkitItemStackAdapter {

    private static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("project_d", "rpgitem_id");
    public static final NamespacedKey UUID_ID_KEY = new NamespacedKey("project_d", "uuid");
    public static final NamespacedKey UPDATEABLE_KEY = new NamespacedKey("project_d", "updateable_timestamp");
    private static RPGItemLoreRenderer renderer = new BukkitLoreRenderer();

    public static void setRenderer(RPGItemLoreRenderer renderer) {
        BukkitItemStackAdapter.renderer = renderer;
    }

    public static ItemStack toItemStack(RPGItem rpgItem) {
        return toItemStack(rpgItem, null);
    }

    /**
     * Creates the Bukkit stack for an item as seen by a specific holder (may be
     * {@code null}): the lore is rendered holder-aware so holder-specific
     * modifiers (e.g. the Mage Set's mana cost discount) are reflected.
     */
    public static ItemStack toItemStack(RPGItem rpgItem, RPGEntity holder) {
        ItemStack itemStack = new ItemStack(Material.valueOf(rpgItem.getMaterial()));
        ItemMeta meta = itemStack.getItemMeta();

        if (meta == null) {
            return itemStack;
        }

        meta.setDisplayName(BukkitTextColorAdapter.colored(TextColor.GOLD, rpgItem.getName()));
        meta.setLore(renderer.render(rpgItem, holder));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta.getAttributeModifiers() != null) {
            meta.getAttributeModifiers().clear();
        }

        applyVisuals(meta, rpgItem);

        // Persist item id & uuid
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, rpgItem.getId());
        pdc.set(UUID_ID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());

        itemStack.setItemMeta(meta);
        return itemStack;
    }

    /**
     * Applies the item's configured visuals to the meta: a leather armor tint
     * and/or a player head skin (custom base64 texture, falling back to a named
     * owner).
     */
    private static void applyVisuals(ItemMeta meta, RPGItem rpgItem) {
        if (meta instanceof LeatherArmorMeta leatherArmor) {
            rpgItem.getLeatherColor().ifPresent(color -> {
                leatherArmor.setColor(Color.fromRGB(color));
                leatherArmor.addItemFlags(ItemFlag.HIDE_DYE);
            });
        }
        if (meta instanceof SkullMeta skullMeta) {
            if (rpgItem.getSkullTexture().isPresent()) {
                if (!applySkullTexture(skullMeta, rpgItem.getSkullTexture().get(),
                        rpgItem.getSkullOwner().orElse(rpgItem.getName()))) {
                    rpgItem.getSkullOwner().ifPresent(skullMeta::setOwner);
                }
            } else {
                rpgItem.getSkullOwner().ifPresent(skullMeta::setOwner);
            }
        }
    }

    /**
     * Sets a fully custom base64 {@code textures} skin on a {@link SkullMeta}.
     * Plain Spigot has no public API for textures values, so the profile is
     * injected into the CraftBukkit skull meta via reflection; if that fails
     * (e.g. on a non-CraftBukkit server or a remapped internals layout) the
     * caller's fallback owner name is the best we can do.
     */
    private static boolean applySkullTexture(SkullMeta meta, String base64Texture, String fallbackName) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            UUID uuid = UUID.nameUUIDFromBytes(base64Texture.getBytes(StandardCharsets.UTF_8));
            Object profile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(uuid,
                    fallbackName);
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures",
                    base64Texture);
            Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures",
                    property);

            String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
            Class<?> craftSkullMetaClass = Class.forName(craftPackage + ".inventory.CraftMetaSkull");
            Method setProfile = craftSkullMetaClass.getMethod("setProfile", gameProfileClass);
            setProfile.invoke(meta, profile);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            Bukkit.getLogger().warning("Could not apply skull texture for item '" + meta.getDisplayName() + "': "
                    + e.getMessage());
            return false;
        }
    }

    /**
     * Renders a level-locked item as a barrier stack for the draft menu: the
     * original item's id is kept in the PDC (so click handling and re-renders can
     * resolve it) but the stack is a barrier that gives nothing when clicked.
     */
    public static ItemStack toLockedItemStack(RPGItem rpgItem) {
        ItemStack locked = new ItemStack(Material.BARRIER);
        ItemMeta meta = locked.getItemMeta();
        if (meta == null) {
            return locked;
        }
        meta.setDisplayName("§c" + rpgItem.getName() + " §7(Locked)");
        List<String> lore = new ArrayList<>();
        lore.add("§7Requires Level §c" + rpgItem.getUnlockLevel());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // Keep the item id on the stack so the shop can resolve the barrier.
        meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, rpgItem.getId());

        locked.setItemMeta(meta);
        return locked;
    }

    public static String getRpgItemId(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(ITEM_ID_KEY, PersistentDataType.STRING);
    }

    public static UUID getUUID(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String name = stack.getItemMeta().getPersistentDataContainer().get(UUID_ID_KEY, PersistentDataType.STRING);
        return name == null ? null : UUID.fromString(name);
    }

    /**
     * Set the timestamp when this item becomes updateable again. Pass -1 to make it
     * permanently unupdateable.
     */
    public static void setUpdateableTimestamp(ItemStack stack, long timestamp) {
        if (stack == null || !stack.hasItemMeta())
            return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(UPDATEABLE_KEY, PersistentDataType.LONG, timestamp);
        stack.setItemMeta(meta);
    }

    /**
     * Get the timestamp when this item can be updated again. Returns -1 if
     * permanently locked.
     */
    public static long getUpdateableTimestamp(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta())
            return 0;
        return stack.getItemMeta().getPersistentDataContainer().getOrDefault(UPDATEABLE_KEY, PersistentDataType.LONG,
                0L);
    }

    /**
     * Checks if the item is currently updateable.
     */
    public static boolean isUpdateable(ItemStack stack) {
        long ts = getUpdateableTimestamp(stack);
        if (ts == -1)
            return false; // permanently locked
        return ts <= System.currentTimeMillis();
    }

}