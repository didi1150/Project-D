package dev.bukkit.ability;

import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.reward.special.TeleportationStaffService;
import dev.bukkit.utils.PlayerTargeting;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/** Right-click effect for the Teleportation Staff reward item. */
public class BukkitTeleportationStaffEffect extends Effect {

    public BukkitTeleportationStaffEffect(String cooldownKey) {
        super(null, -1, false, cooldownKey);
    }

    @Override
    public boolean hasExpired(long now) {
        return true;
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        if (!(caster instanceof BukkitPlayerEntity playerEntity)) {
            return;
        }
        Player player = playerEntity.getPlayer().orElse(null);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        Player target = PlayerTargeting.raycastOtherPlayer(player);
        if (target == null) {
            player.sendMessage("§cTeleportation Staff: no player in sight.");
            return;
        }
        if (TeleportationStaffService.getInstance().start(player, target)) {
            cooldownSink.startCooldown();
        }
    }

    @Override
    public void cancel() {
    }
}
