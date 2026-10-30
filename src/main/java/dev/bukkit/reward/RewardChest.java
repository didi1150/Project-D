package dev.bukkit.reward;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.bukkit.entity.BukkitDisplayEntityRegistry;
import dev.core.utils.AdventureText;

/**
 * A player-owned floating reward chest made of display entities. It has an
 * invisible interaction hitbox, a floating chest model, a label and a blue
 * particle ring. It is intentionally not a real chest block.
 */
public final class RewardChest {

    private static final DustOptions PARTICLE_DUST = new DustOptions(Color.fromRGB(70, 150, 255), 1.0f);

    private final UUID ownerId;
    private final InteractiveParts parts;
    private final Location base;
    private final ItemDisplay display;
    private final TextDisplay label;
    private final Interaction interaction;

    private BukkitTask task;
    private double angle;
    private boolean removed;

    private RewardChest(Player owner, Location base, ItemDisplay display, TextDisplay label,
            Interaction interaction) {
        this.ownerId = owner.getUniqueId();
        this.base = base.clone();
        this.display = display;
        this.label = label;
        this.interaction = interaction;
        this.parts = new InteractiveParts(display.getUniqueId(), interaction.getUniqueId());
    }

    public static RewardChest spawn(Plugin plugin, Player owner, Location location) {
        World world = location.getWorld();
        Location base = location.getBlock().getLocation().add(0.5, 0, 0.5);
        Location displayLocation = base.clone().add(0, 1.15, 0);

        BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();
        ItemDisplay display = registry.spawnDisplayEntity(displayLocation, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.CHEST));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setViewRange(48f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setVisibleByDefault(false);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.15f), new Quaternionf()));
        });

        TextDisplay label = registry.spawnDisplayEntity(base.clone().add(0, 2.25, 0),
                TextDisplay.class, d -> {
                    d.text(AdventureText.legacy("§6Reward Cache"));
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                    d.setBackgroundColor(Color.WHITE.setAlpha(0));
                    d.setPersistent(false);
                    d.setInvulnerable(true);
                    d.setGravity(false);
                    d.setViewRange(48f);
                    d.setVisibleByDefault(false);
                });

        Interaction interaction = world.spawn(base.clone().add(0, 1.1, 0), Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.5f);
            hitbox.setInteractionHeight(2.2f);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
            hitbox.setVisibleByDefault(false);
        });

        RewardChest chest = new RewardChest(owner, base, display, label, interaction);
        chest.applyVisibility(plugin, owner);
        chest.task = Bukkit.getScheduler().runTaskTimer(plugin, chest::tick, 0L, 2L);
        return chest;
    }

    /**
     * Every chest is a private per-player object, so it is only shown to its
     * owner. {@code setVisibleByDefault(false)} also keeps late joiners from
     * ever receiving the chest entities.
     */
    private void applyVisibility(Plugin plugin, Player owner) {
        owner.showEntity(plugin, display);
        owner.showEntity(plugin, label);
        owner.showEntity(plugin, interaction);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(ownerId)) {
                continue;
            }
            other.hideEntity(plugin, display);
            other.hideEntity(plugin, label);
            other.hideEntity(plugin, interaction);
        }
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getDisplayId() {
        return parts.displayId();
    }

    public UUID getInteractionId() {
        return parts.interactionId();
    }

    public boolean isInteraction(UUID entityId) {
        return parts.interactionId().equals(entityId);
    }

    public boolean isValid() {
        return !removed && display.isValid() && interaction.isValid();
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        if (task != null) {
            task.cancel();
        }
        BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();
        registry.removeDisplayEntity(display.getUniqueId());
        registry.removeDisplayEntity(label.getUniqueId());
        if (interaction.isValid()) {
            interaction.remove();
        }
    }

    private void tick() {
        if (removed || !display.isValid() || !interaction.isValid()) {
            remove();
            return;
        }
        angle += 0.10;
        double bob = Math.sin(angle * 1.6) * 0.12;
        display.teleport(base.clone().add(0, 1.15 + bob, 0));
        display.setRotation((float) Math.toDegrees(angle), 0f);
        if (label.isValid()) {
            label.teleport(base.clone().add(0, 2.25 + bob * 0.5, 0));
        }

        World world = base.getWorld();
        if (world == null) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.getWorld().equals(world)) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double a = angle * 1.4 + (Math.PI * 2 * i / 12.0);
            double x = Math.cos(a) * 0.9;
            double z = Math.sin(a) * 0.9;
            double y = 1.05 + Math.sin(angle * 2.0 + i) * 0.18;
            Location particle = base.clone().add(x, y, z);
            owner.spawnParticle(Particle.DUST, particle, 1, 0, 0, 0, 0, PARTICLE_DUST);
        }
        if (((int) (angle * 10)) % 8 == 0) {
            owner.spawnParticle(Particle.END_ROD, base.clone().add(0, 1.3 + bob, 0), 2, 0.3, 0.3, 0.3, 0.01);
        }
    }

    private record InteractiveParts(UUID displayId, UUID interactionId) {
    }
}
