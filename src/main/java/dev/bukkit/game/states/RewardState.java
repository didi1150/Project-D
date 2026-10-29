package dev.bukkit.game.states;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.bukkit.DMain;
import dev.bukkit.reward.RewardCaseService;
import dev.bukkit.reward.RewardChest;
import dev.bukkit.utils.BukkitText;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.game.settings.GameSettings;

public class RewardState extends GameState {

    public static final String NAME = "REWARDSTATE";
    public static final long DURATION = 20 * 60L;

    private List<RewardChest> rewardChests = new ArrayList<>();

    public RewardState(EventBusInterface eventBus) {
        super(NAME, DURATION, eventBus);
    }

    @Override
    protected void onStart() {
        rewardChests = new ArrayList<>();
        GameSettings settings = GameSettings.getCurrentSettings();
        int floor = settings.getFloor();

        ViewPoint3D playerSpawnPoint = settings.getRewardPlayerSpawnLocation(floor);
        if (playerSpawnPoint == null) {
            BukkitText.broadcast("§cRewardstate failed: no reward player spawn configured for floor " + floor);
            return;
        }

        Point3D chestSpawnPoint = settings.getRewardChestLocation(floor);
        if (chestSpawnPoint == null) {
            BukkitText.broadcast("§cRewardstate failed: no reward chest spawn configured for floor " + floor);
            return;
        }

        World arenaWorld = DMain.getInstance().getBossArenaManager().getActiveWorld();
        if (arenaWorld == null) {
            BukkitText.broadcast("§cRewardstate failed: boss arena is not available.");
            return;
        }

        Location playerSpawn = new Location(arenaWorld, playerSpawnPoint.getX(), playerSpawnPoint.getY(),
                playerSpawnPoint.getZ(), playerSpawnPoint.getYaw(), playerSpawnPoint.getPitch());
        Location chestSpawn = new Location(arenaWorld, chestSpawnPoint.getX(), chestSpawnPoint.getY(),
                chestSpawnPoint.getZ());

        Bukkit.getOnlinePlayers().forEach(p -> {
            rewardChests.add(RewardCaseService.getInstance().spawnChest(p, chestSpawn.clone().add(0, 1.5, 0)));
            p.teleport(playerSpawn);
        });

    }

    @Override
    protected void onStop() {
        updateCountdownXP(remainingTicks / 20, DURATION / 20);
        rewardChests.forEach(chest -> {
            if (chest != null && chest.isValid()) {
                chest.remove();
            }
        });
        rewardChests = null;
    }

    @Override
    protected void onTickSecond(long secondsRemaining) {
        updateCountdownXP(secondsRemaining, DURATION / 20);
        if (secondsRemaining <= 0) {
            jumpToState(PostGameState.NAME);
        }
    }

    protected final void updateCountdownXP(long secondsRemaining, long totalSeconds) {
        if (totalSeconds <= 0) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setLevel((int) secondsRemaining);

            float progress = (float) secondsRemaining / (float) totalSeconds;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            player.setExp(progress);
        }
    }

    @Override
    protected void registerSubscribers() {

    }

}
