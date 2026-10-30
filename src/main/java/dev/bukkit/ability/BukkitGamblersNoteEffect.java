package dev.bukkit.ability;

import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.reward.special.GamblersNoteService;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/** Right-click effect for the Gambler's Note reward item. */
public class BukkitGamblersNoteEffect extends Effect {

    public BukkitGamblersNoteEffect(String cooldownKey) {
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
        if (player == null) {
            return;
        }
        GamblersNoteService.getInstance().start(player);
    }

    @Override
    public void cancel() {
    }
}
