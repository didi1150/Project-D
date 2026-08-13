package dev.bukkit.event.subscribers;

import org.bukkit.plugin.Plugin;

import dev.core.event.EventBusInterface;

public class BlockSubscriber {
    private Plugin plugin;
    private EventBusInterface eventBus;

    public BlockSubscriber(EventBusInterface eventBus, Plugin plugin) {
        this.eventBus = eventBus;
        this.plugin = plugin;
    }
    
    
}
