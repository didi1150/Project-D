package dev.core.game;

public interface GameStateListener {

    void onStateComplete(GameState state, GameStateResult result);

    void onStateSkip(GameState state);

    void onStateStart(GameState state);

    void onStateEnd(GameState state);

    void onStateJump(GameState state, String target);

}
