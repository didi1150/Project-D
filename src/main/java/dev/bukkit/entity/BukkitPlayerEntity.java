package dev.bukkit.entity;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.event.BukkitEventBus;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class BukkitPlayerEntity extends RPGEntity {

    private Player player;
//    private BukkitPlayerInventoryUpdater inventoryUpdater;

    public BukkitPlayerEntity(Player player) {
        super(player.getUniqueId(), player.getName(), EntityType.PLAYER, BukkitEffectManager.getInstance(),
                BukkitEventBus.getInstance());
        this.player = player;
//        this.inventoryUpdater = new BukkitPlayerInventoryUpdater(player, 5);
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public void tick(long now) {
        super.tick(now);
        if (isAlive()) {
            updatePlayerVanillaHealth();
            updateDisplay();
//            inventoryUpdater.tick();
        }
    }

    private double calculateVanillaHearts(double rpgHealth) {
        if (rpgHealth <= 100) {
            // Below or at base health: direct conversion (100 HP = 10 hearts)
            return rpgHealth / 10.0;
        }

        // Above base health: use scaling formula
        double hearts = (100 + (rpgHealth - 100) * 2) / 10.0;

        // Cap at 20 hearts (2 rows maximum)
        return Math.min(hearts, 20.0);
    }

    public void updatePlayerVanillaHealth() {
        double vanillaHearts = calculateVanillaHearts(getMaxHealth());
        double vanillaHP = vanillaHearts * 2; // Minecraft uses half-hearts (20 HP = 10 hearts)

        // Set max health attribute
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(vanillaHP);
        }

        // Update current health proportionally
        double healthPercentage = getHealth() / getMaxHealth();
        double health = vanillaHP * healthPercentage;
        if (health <= 0 || getHealth() <= 0) {
            onDeath();
        } else {
            player.setHealth(health);
        }
    }

    private void updateDisplay() {
        String combinedText = String.format("§c %,.0f§7/§c%,.0f ❤   §b %,.0f§7/§b%,.0f ✦", getHealth(), getMaxHealth(),
                getMana(), getMaxMana());

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(combinedText));
    }

//    public BukkitPlayerInventoryUpdater getInventoryUpdater() {
//        return inventoryUpdater;
//    }

}
