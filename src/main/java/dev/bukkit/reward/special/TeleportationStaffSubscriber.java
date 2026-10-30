package dev.bukkit.reward.special;

import org.bukkit.event.player.PlayerQuitEvent;

import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

/** Cleans up pending Teleportation Staff timers when a participant leaves. */
@EventSubscriber
public final class TeleportationStaffSubscriber {

    @Subscribe
    public void onQuit(PlayerQuitEvent event) {
        TeleportationStaffService.getInstance().handleQuit(event.getPlayer());
    }
}
