package dev.bukkit.utils;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;

public class ScoreboardProvider {

    private static ScoreboardProvider INSTANCE;

    private ScoreboardProvider() {
    }

    private Scoreboard mainScoreboard;

    public ScoreboardProvider getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ScoreboardProvider();
        }

        return INSTANCE;
    }

    public Scoreboard getMainScoreboard() {
        if (mainScoreboard == null) {
            mainScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        }
        return mainScoreboard;
    }

    // TODO: Provide Scoreboard for all players
    // TODO: Scoreboard 

}
