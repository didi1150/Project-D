package dev.bukkit.ability.behavior;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;

/**
 * Slime Boots passive: while worn, jumping from the ground while sprinting
 * launches the player forward in their look direction. The previous on-ground
 * state is tracked so falling off a ledge never triggers a launch and so no
 * air jump is possible.
 */
public class SlimeBootsBehavior implements AbilityBehavior {

    private static final double FORWARD_SPEED = 1.45;
    private static final double UPWARD_SPEED = 0.35;

    private ActiveAbility ctx;
    private boolean lastOnGround;

    public SlimeBootsBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        Player player = player();
        this.lastOnGround = player != null && player.isOnGround();
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onMove, PlayerMoveEvent.class));
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        this.lastOnGround = false;
    }

    private void onMove(PlayerMoveEvent event) {
        Player player = player();
        if (player == null || !event.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }
        if (!player.isOnline() || player.isDead()) {
            lastOnGround = false;
            return;
        }

        boolean onGround = player.isOnGround();
        boolean jumped = lastOnGround && !onGround && player.isSprinting()
                && player.getVelocity().getY() > 0.05;
        lastOnGround = onGround;
        if (!jumped) {
            return;
        }

        Vector direction = player.getLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            double yaw = Math.toRadians(player.getLocation().getYaw());
            direction = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        direction.normalize().multiply(FORWARD_SPEED).setY(UPWARD_SPEED);
        player.setVelocity(direction);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.8f, 1.15f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.25, 0.1, 0.25, 0.02);
    }

    private Player player() {
        if (ctx == null || ctx.getHolder() == null) {
            return null;
        }
        return org.bukkit.Bukkit.getPlayer(ctx.getHolder().getUuid());
    }
}
