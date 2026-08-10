package dev.bukkit.game.states;

import dev.core.event.EventBusInterface;
import dev.core.event.impl.TickEvent;
import dev.core.game.GameState;

public class SetupState extends GameState{

    public static final String NAME = "SETUPSTATE";

    public SetupState(EventBusInterface eventBus) {
        super(NAME, -1, eventBus);
    }

    @Override
    protected void onStart() {
        
    }

    @Override
    protected void onStop() {
        
    }

    @Override
    protected void registerSubscribers() {
        
    }

}
