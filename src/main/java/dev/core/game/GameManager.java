package dev.core.game;

public class GameManager {

    private static final GameManager instance = new GameManager();
    public static GameManager getInstance() {
        return instance;
    }

    //create a countDownHelper for every needed situation
    //private CountDownHelper lobbyCountDown;

    private GameState gameState;

    public GameManager() {
        gameState = GameState.LOBBY;
    }

    //Called on every tick
    public void update(float deltaTime) {
        updateGameState();
        //update all managers
    }

    //update gameState according to certain conditions
    private void updateGameState() {
        switch (gameState) {
            case LOBBY -> {
            }
            case WAITING_FOR_PLAYERS -> {
            }
            case SELECTING_CLASS -> {
            }
            case SELECTING_EQUIPMENT -> {
            }
            case EXPLORING_DUNGEON -> {
            }
            case BOSSFIGHT -> {
            }
            case DUNGEON_COMPLETED -> {
            }
        }
    }

    public void nextStage() {
        for (int i = 0; i < GameState.values().length; i++) {
            if (GameState.values()[i] == gameState) {
                gameState = GameState.values()[(i+1) % GameState.values().length];
                break;
            }
        }
    }
}
