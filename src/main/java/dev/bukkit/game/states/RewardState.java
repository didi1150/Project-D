package dev.bukkit.game.states;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import dev.bukkit.game.coords.PointToLocation;
import dev.bukkit.reward.RewardCaseService;
import dev.bukkit.reward.RewardChest;
import dev.bukkit.utils.BukkitText;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.settings.GameSettings;

public class RewardState extends GameState {

    private List<RewardChest> rewardChests = new ArrayList<>();

    public RewardState(EventBusInterface eventBus) {
        super("REWARDSTATE", 20 * 60L, eventBus);
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        int floor = GameSettings.getCurrentSettings().getFloor();

        Location playerSpawn = PointToLocation
                .viewToLoc(GameSettings.getCurrentSettings().getRewardPlayerSpawnLocation(floor));

        Location chestSpawn = PointToLocation
                .blockToLoc(GameSettings.getCurrentSettings().getRewardChestLocation(floor));

        if (playerSpawn == null) {
            BukkitText.broadcast("§cRewardstate failed: no reward player spawn configured for floor " + floor);
            return;
        }

        if (chestSpawn == null) {
            BukkitText.broadcast("§cRewardstate failed: no reward chest spawn configured for floor " + floor);
            return;
        }

        Bukkit.getOnlinePlayers().forEach(p -> {
            rewardChests.add(RewardCaseService.getInstance().spawnChest(p, chestSpawn.clone().add(0, 1.5, 0)));
            p.teleport(playerSpawn);
        });

    }

    @Override
    protected void onStop() {
        rewardChests.forEach(chest -> {
            chest.remove();
        });
        rewardChests = null;
    }

    @Override
    protected void registerSubscribers() {

    }

}
