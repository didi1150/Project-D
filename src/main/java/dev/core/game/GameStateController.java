package dev.core.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;

public class GameStateController implements GameStateListener {

    protected final TaskScheduler scheduler;
    private final List<GameState> states;
    private int currentStateIndex = -1;
    private GameState currentState;
    private boolean running = false;

    private EffectManagerInterface effectManager;
    private EntityManager entityManager;
    private ScheduledTask tickTask;

    public GameStateController(TaskScheduler scheduler) {
        this.scheduler = scheduler;
        this.states = new ArrayList<>();
    }

    public GameStateController(TaskScheduler scheduler, EffectManagerInterface effectManager,
            EntityManager entityManager) {
        this(scheduler);
        this.effectManager = effectManager;
        this.entityManager = entityManager;
    }

    /**
     * Wire the entities/effects the game should tick every server tick. The tick
     * is owned by the controller (not by individual states) so it is active in
     * EVERY state, from the first state until the machine stops.
     */
    public void setTickables(EffectManagerInterface effectManager, EntityManager entityManager) {
        this.effectManager = effectManager;
        this.entityManager = entityManager;
    }

    public GameStateController addState(GameState state) {
        states.add(state);
        return this;
    }

    public GameStateController addStates(GameState... states) {
        this.states.addAll(Arrays.asList(states));
        return this;
    }

    // Start the state machine
    public void start() {
        if (running || states.isEmpty()) {
            return;
        }

        running = true;
        currentStateIndex = -1;
        nextState();
        startGameTick();
    }

    // Stop the current state and the entire machine
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        stopGameTick();

        if (currentState != null) {
            currentState.stop();
            currentState = null;
        }
    }

    /**
     * The single global game tick: runs in every state from start() until the
     * machine stops or the list of states is exhausted.
     */
    private void startGameTick() {
        if (tickTask != null && !tickTask.isCancelled()) {
            return;
        }
        if (scheduler == null || (effectManager == null && entityManager == null)) {
            return;
        }
        tickTask = scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            if (effectManager != null) {
                effectManager.tick(now);
            }
            if (entityManager != null) {
                entityManager.tick(now);
            }
        }, 0L, 1L);
    }

    private void stopGameTick() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        tickTask = null;
    }

    // Skip current state (public method for external control)
    public void skipCurrentState() {
        if (currentState != null && currentState.canSkip()) {
            currentState.complete(GameStateResult.SKIP);
        }
    }

    // Go to a specific state by name
    public void goToState(String stateName) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).getName().equals(stateName)) {
                if (currentState != null) {
                    currentState.stop();
                }
                currentStateIndex = i - 1; // Will be incremented in nextState()
                nextState();
                return;
            }
        }
    }

    private void nextState() {
        if (!running) {
            return;
        }

        currentStateIndex++;

        if (currentStateIndex >= states.size()) {
            // All states completed
            running = false;
            onAllStatesComplete();
            return;
        }

        if (currentState != null) {
            currentState.stop();
        }

        currentState = states.get(currentStateIndex);
        currentState.start(this, scheduler);
        System.out.println("Current Game State: " + currentState.name);
    }

    private void jumpToState(String target) {
        if (!running) {
            return;
        }
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).getName().equals(target)) {
                currentStateIndex = i;
                break;
            }
        }

        if (currentStateIndex >= states.size()) {
            // All states completed
            running = false;
            onAllStatesComplete();
            return;
        }

        if (currentState != null) {
            currentState.stop();
        }

        currentState = states.get(currentStateIndex);
        currentState.start(this, scheduler);
    }

    @Override
    public void onStateComplete(GameState state, GameStateResult result) {
        if (state != currentState) {
            return;
        }

        switch (result) {
        case COMPLETE:
            nextState();
            break;
        case RESTART:
            // Restart current state
            currentState.stop();
            currentState.start(this, scheduler);
            break;
        case SKIP:
            nextState();
            break;
        default:
            break;
        }
    }

    @Override
    public void onStateSkip(GameState state) {
        if (state == currentState) {
            nextState();
        }
    }

    @Override
    public void onStateStart(GameState state) {
    }

    @Override
    public void onStateEnd(GameState state) {
    }

    @Override
    public void onStateJump(GameState state, String target) {
        if (state == currentState) {
            jumpToState(target);
        }
    }

    // Called when all states are completed
    protected void onAllStatesComplete() {
        stopGameTick();
    }

    // Getters
    public GameState getCurrentState() {
        return currentState;
    }

    public boolean isRunning() {
        return running;
    }

    public int getCurrentStateIndex() {
        return currentStateIndex;
    }

    public List<GameState> getStates() {
        return new ArrayList<>(states);
    }

}
