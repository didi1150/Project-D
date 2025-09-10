package dev.bukkit.entity;

import org.bukkit.entity.Player;

import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.stat.BukkitStatManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.progression.PlayerProgression;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class BukkitPlayerEntity extends RPGEntity {

    private Player player;
    private PlayerProgression playerProgression;
    private BukkitStatManager bukkitStatManager;

    public BukkitPlayerEntity(Player player) {
        super(player.getUniqueId(), player.getName(), EntityType.PLAYER, BukkitEffectManager.getInstance(),
                BukkitEventBus.getInstance());
        this.player = player;
        this.playerProgression = new PlayerProgression(player.getUniqueId());
        this.bukkitStatManager = new BukkitStatManager(player, getStatManager());
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public void tick(long now) {
        super.tick(now);
        if (isAlive()) {
            updateDisplay();
            bukkitStatManager.tick(now, this::onDeath);
//            BukkitInventorySync.syncInventoryDiff(this, player);
        }
    }

    private void updateDisplay() {
        String combinedText = String.format("§c %,.0f§7/§c%,.0f ❤   §b %,.0f§7/§b%,.0f ✦", getHealth(), getMaxHealth(),
                getMana(), getMaxMana());

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(combinedText));
    }

    public PlayerProgression getPlayerProgression() {
        return playerProgression;
    }

}
