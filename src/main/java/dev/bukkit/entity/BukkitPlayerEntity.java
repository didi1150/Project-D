package dev.bukkit.entity;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.entity.boss.BukkitBossEntity;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.stat.BukkitStatManager;
import dev.bukkit.utils.HealAuraUtils;
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
    // Support set passive ("HEAL_AURA"): next tick at which the aura heals
    // nearby teammates.
    private long nextAuraHealAt = 0;

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
            if (getEquipmentManager().hasSetPassive(HealAuraUtils.PASSIVE_ID) && now >= nextAuraHealAt) {
                nextAuraHealAt = now + HealAuraUtils.HEAL_INTERVAL_MS;
                HealAuraUtils.tick(this);
            }
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
        clearMobTargetsOf(player);
    }

    /**
     * Vanilla mobs keep chasing whatever player they targeted before he became
     * a ghost. Clear their target so they stop attacking the invisible ghost.
     */
    public static void clearMobTargetsOf(Player player) {
        for (World world : Bukkit.getWorlds()) {
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                LivingEntity target = mob.getTarget();
                if (target != null && target.getUniqueId().equals(player.getUniqueId())) {
                    mob.setTarget(null);
                }
            }
        }
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

        super.onRevive();
        syncState();
        return true;
    }

    @Override
    protected void playHitReaction(RPGEntity attacker) {
        getPlayer().ifPresent(player -> player.damage(0.001, bukkitSourceOf(attacker)));
    }

    /**
     * Resolves the Bukkit {@link Entity} (if any) backing an attacker, so the
     * 0-damage hurt poke is attributed to the right source.
     */
    public static Entity bukkitSourceOf(RPGEntity attacker) {
        if (attacker == null) {
            return null; // vanilla/environmental damage has no RPG source
        }
        if (attacker instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayer().orElse(null);
        }
        if (attacker instanceof BukkitBossEntity bossEntity) {
            return bossEntity.getLivingEntity().orElse(null);
        }
        Entity entity = Bukkit.getEntity(attacker.getUuid());
        return entity instanceof LivingEntity living ? living : null;
    }
}
