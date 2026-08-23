package dev.bukkit.event.subscribers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import dev.bukkit.DMain;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.core.entity.RPGEntity;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;
import dev.core.event.impl.RPGEntityHealEvent;

/**
 * Renders the floating green heal number whenever an RPG heal lands,
 * mirroring how {@link CombatListener} renders damage numbers on hit.
 */
@EventSubscriber
public class HealIndicatorSubscriber {

    @Subscribe
    public void onRpgHeal(RPGEntityHealEvent event) {
        if (event.isCancelled()) {
            return;
        }
        double amount = event.getAmount();
        if (amount <= 0.001) {
            return;
        }

        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }

        RPGEntity target = event.getTarget();
        if (target == null) {
            return;
        }
        Entity body = Bukkit.getEntity(target.getUuid());
        if (body == null || !body.isValid()) {
            return;
        }
        combatListener.showHealingIndicator(body.getLocation(), amount);
    }
}
