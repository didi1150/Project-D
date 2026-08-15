package dev.bukkit.game.states;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitInventorySync;
import dev.core.entity.EntityManager;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;

public class SetupState extends GameState{

    public static final String NAME = "SETUPSTATE";

    public SetupState(EventBusInterface eventBus) {
        super(NAME, -1, eventBus);
    }

    @Override
    protected void onStart() {
        // Players may already be online when the state starts; register them so
        // their entity/equipment/effect ticking is active immediately.
        Bukkit.getOnlinePlayers().forEach(this::registerPlayer);
    }

    @Override
    protected void onStop() {
        
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerJoinEvent> joinAction = new EventAction<>(event -> registerPlayer(event.getPlayer()),
                PlayerJoinEvent.class);
        EventAction<PlayerQuitEvent> quitAction = new EventAction<>(event -> {
            EntityManager.getInstance().removeEntity(event.getPlayer().getUniqueId());
        }, PlayerQuitEvent.class);

        addSubscriber(joinAction);
        addSubscriber(quitAction);
    }

    private void registerPlayer(Player player) {
        if (EntityManager.getInstance().getEntity(player.getUniqueId()).isPresent()) {
            return;
        }
        BukkitPlayerEntity playerEntity = new BukkitPlayerEntity(player);
        EntityManager.getInstance().registerEntity(playerEntity);
        playerEntity.syncState();
        BukkitInventorySync.syncInventory(playerEntity, player);
    }

}