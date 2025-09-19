package dev.core.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GameStateController implements GameStateListener {

    protected final TaskScheduler scheduler;
    private final List<GameState> states;
    private int currentStateIndex = -1;
    private GameState currentState;
    private boolean running = false;

    public GameStateController(TaskScheduler scheduler) {
        this.scheduler = scheduler;
        this.states = new ArrayList<>();
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
    }

    // Stop the current state and the entire machine
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (currentState != null) {
            currentState.stop();
            currentState = null;
        }
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
