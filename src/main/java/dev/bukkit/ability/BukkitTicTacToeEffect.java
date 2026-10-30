package dev.bukkit.ability;

import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.reward.special.TicTacToeService;
import dev.bukkit.utils.PlayerTargeting;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/** Right-click effect for the Tic Tac Toe reward item. */
public class BukkitTicTacToeEffect extends Effect {

    public BukkitTicTacToeEffect(String cooldownKey) {
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
            player.sendMessage("§cTic Tac Toe: no player in sight.");
            return;
        }
        TicTacToeService.getInstance().start(player, target);
    }

    @Override
    public void cancel() {
    }
}
