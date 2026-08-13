package dev.core.game;

public enum GameStateResult {

    CONTINUE,   // State continues running
    COMPLETE,   // State finished naturally
    SKIP,       // State should be skipped to the next
    RESTART;    // State should restart
    
}
