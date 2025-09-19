package dev.bukkit.entity;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.stat.BukkitStatManager;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.progression.PlayerProgression;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class BukkitPlayerEntity extends RPGEntity {

    private PlayerProgression playerProgression;
    private BukkitStatManager bukkitStatManager;
    private ItemStack[] inventoryContents;

    public BukkitPlayerEntity(Player player) {
        super(player.getUniqueId(), player.getName(), EntityType.PLAYER, BukkitEffectManager.getInstance(),
                BukkitEventBus.getInstance());
        this.playerProgression = new PlayerProgression(player.getUniqueId());
        this.bukkitStatManager = new BukkitStatManager(player,  getStatManager());
    }

    public Optional<Player> getPlayer() {
        return Optional.ofNullable(Bukkit.getPlayer(getUuid()));
    }

    @Override
    public void tick(long now) {
        super.tick(now);
        if (isAlive()) {
            updateDisplay();
            bukkitStatManager.tick(now, this::onDeath);
//            BukkitInventorySync.syncInventoryDiff(this, player);
            this.inventoryContents = getPlayer().get().getInventory().getContents();
        }
    }

    @Override
    public void onDeath() {
        super.onDeath();

        toGhostState();
    }

    public void syncState() {
        if (isAlive()) {
            toPlayingState();
        } else {
            toGhostState();
        }
    }

    public void toGhostState() {
        Optional<Player> optional = getPlayer();
        if (optional.isEmpty()) {
            System.out.println("Could not find player " + getUuid() + ", no ghost state.");
            return;
        }

        Player player = optional.get();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, -1, 1, false, false));

        EntityManager.getInstance().getAliveEntities().forEach(entity -> {
            if (entity instanceof BukkitPlayerEntity playerEntity) {
                playerEntity.getPlayer().ifPresent(other -> {
                    if (other.canSee(player) && !EntityManager.getInstance().isSpectator(other.getUniqueId())) {
                        other.hidePlayer(DMain.getInstance(), player);
                    }
                });
            }
        });
    }

    public void toPlayingState() {
        Optional<Player> optional = getPlayer();
        if (optional.isEmpty()) {
            return;
        }

        Player player = optional.get();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getInventory().clear();
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        if (inventoryContents != null) {
            player.getInventory().setContents(inventoryContents);
        }

        EntityManager.getInstance().getAliveEntities().forEach(entity -> {
            if (entity instanceof BukkitPlayerEntity playerEntity) {
                playerEntity.getPlayer().ifPresent(other -> {
                    if (!other.canSee(player)) {
                        other.showPlayer(DMain.getInstance(), player);
                    }
                });
            }
        });
    }

    private void updateDisplay() {
        String combinedText = String.format("§c %,.0f§7/§c%,.0f ❤   §b %,.0f§7/§b%,.0f ✦", getHealth(), getMaxHealth(),
                getMana(), getMaxMana());
        getPlayer().ifPresent(
                player -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(combinedText)));
    }

    public PlayerProgression getPlayerProgression() {
        return playerProgression;
    }

    @Override
    public boolean onRevive() {
        Optional<Player> player = getPlayer();
        if (player.isEmpty()) {
            return false;
        }

        setAlive(true);
//      TODO: Remove GHOST
        syncState();
        return super.onRevive();
    }
}
