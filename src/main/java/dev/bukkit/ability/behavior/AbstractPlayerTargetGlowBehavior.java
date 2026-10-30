package dev.bukkit.ability.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import dev.bukkit.DMain;
import dev.bukkit.glow.PlayerGlowService;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.PlayerTargeting;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Shared per-holder targeting outline for reward items that can only target
 * other players. Mirrors the Utility Staff's live outline, but uses
 * {@link PlayerTargeting} instead of ally-only targeting.
 */
public abstract class AbstractPlayerTargetGlowBehavior implements AbilityBehavior {

    private ActiveAbility ctx;
    private Entity lastGlowingTarget;

    protected abstract String itemId();

    protected abstract NamedTextColor glowColor();

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onTick, TickEvent.class));
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        clearGlow(ctx == null ? null : Bukkit.getPlayer(ctx.getHolder().getUuid()));
    }

    private void onTick(TickEvent event) {
        if (ctx == null) {
            return;
        }
        Player player = Bukkit.getPlayer(ctx.getHolder().getUuid());
        if (player == null || !player.isOnline() || player.isDead()) {
            clearGlow(null);
            return;
        }
        if (!itemId().equals(BukkitItemStackAdapter.getRpgItemId(player.getInventory().getItemInMainHand()))) {
            clearGlow(player);
            return;
        }

        Player target = PlayerTargeting.raycastOtherPlayer(player);
        if (target != null) {
            if (!target.equals(lastGlowingTarget)) {
                clearGlow(player);
                lastGlowingTarget = target;
            }
            PlayerGlowService glow = DMain.getInstance() == null ? null : DMain.getInstance().getPlayerGlowService();
            if (glow != null) {
                glow.setGlowing(target, player, glowColor());
            }
        } else if (lastGlowingTarget != null) {
            clearGlow(player);
        }
    }

    private void clearGlow(Player viewer) {
        if (lastGlowingTarget == null) {
            return;
        }
        if (viewer != null && viewer.isOnline()) {
            try {
                PlayerGlowService glow = DMain.getInstance() == null ? null
                        : DMain.getInstance().getPlayerGlowService();
                if (glow != null) {
                    glow.unsetGlowing(lastGlowingTarget, viewer);
                }
            } catch (Exception ignored) {
            }
        }
        lastGlowingTarget = null;
    }
}
