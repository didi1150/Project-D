package dev.bukkit.scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.utils.AdventureColorConverter;
import dev.core.entity.EntityManager;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.objective.ObjectiveDisplaySlot;
import net.megavex.scoreboardlibrary.api.objective.ObjectiveManager;
import net.megavex.scoreboardlibrary.api.objective.ScoreFormat;
import net.megavex.scoreboardlibrary.api.objective.ScoreboardObjective;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.team.ScoreboardTeam;
import net.megavex.scoreboardlibrary.api.team.TeamManager;
import net.megavex.scoreboardlibrary.api.team.enums.CollisionRule;

public class BukkitScoreboardService {
    private static BukkitScoreboardService INSTANCE;
    private Plugin plugin;

    private ScoreboardLibrary scoreboardLibrary;
    private TeamManager teamManager;
    private ObjectiveManager objectiveManager;

    private Sidebar sidebar;
    private ScoreboardObjective healthObjective;

    private Map<UUID, ScoreboardTeam> currentTeam;

    private EventBusInterface eventBusInterface;
    private BukkitTask updateTimer;

    private BukkitScoreboardService(Plugin plugin, EventBusInterface eventBusInterface) {
        this.plugin = plugin;
        this.eventBusInterface = eventBusInterface;
    }

    public static BukkitScoreboardService getInstance(Plugin plugin, EventBusInterface eventBusInterface) {
        if (INSTANCE == null) {
            INSTANCE = new BukkitScoreboardService(plugin, eventBusInterface);
        }
        return INSTANCE;
    }

    public void init() {
        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(plugin);
        } catch (NoPacketAdapterAvailableException e) {
            // If server version is not yet supported, you can fallback to the no-op
            // implementation:
            scoreboardLibrary = new NoopScoreboardLibrary();
            plugin.getLogger().warning("Server version unsupported, scoreboard functionality will not be visible!");
        }
        teamManager = scoreboardLibrary.createTeamManager();
        objectiveManager = scoreboardLibrary.createObjectiveManager();
        sidebar = scoreboardLibrary.createSidebar();

        currentTeam = new HashMap<>();

        EventAction<PlayerJoinEvent> joinSub = new EventAction<>(e -> {
            teamManager.addPlayer(e.getPlayer());
            objectiveManager.addPlayer(e.getPlayer());
        }, PlayerJoinEvent.class);

        EventAction<PlayerQuitEvent> quitSub = new EventAction<>(e -> {
            teamManager.removePlayer(e.getPlayer());
            objectiveManager.removePlayer(e.getPlayer());
        }, PlayerQuitEvent.class);

        eventBusInterface.subscribe(quitSub);
        eventBusInterface.subscribe(joinSub);

    }

    public void close() {
        teamManager.close();
        objectiveManager.close();
        sidebar.close();

        scoreboardLibrary.close();
        if (updateTimer != null) {
            updateTimer.cancel();
        }
    }

    public ScoreboardObjective createHealthObjective() {
        ScoreboardObjective scoreboardObjective = objectiveManager.create("health");
        scoreboardObjective.value(Component.text("❤").style(Style.style(NamedTextColor.RED)));
        scoreboardObjective.defaultScoreFormat(ScoreFormat.styled(NamedTextColor.RED));
        objectiveManager.display(ObjectiveDisplaySlot.belowName(), scoreboardObjective);
        return scoreboardObjective;
    }

    public void tick() {
        updateHealthObjectives();
    }

    public void initScoreboard() {
        healthObjective = createHealthObjective();
        objectiveManager.addPlayers(new ArrayList<>(Bukkit.getOnlinePlayers()));
        createTeams();

        updateTimer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void updateHealthObjectives() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            UUID id = player.getUniqueId();
            if (EntityManager.getInstance().isAlive(id)) {
                healthObjective.score(player.getName(),
                        (int) EntityManager.getInstance().getEntity(id).get().getHealth());
            }
        });
    }

    public void createTeams() {
        for (RPGClassType rpgClassType : RPGClassType.values()) {
            createTeam(rpgClassType);
        }
    }

    public void assignTeamViewers() {
        teamManager.addPlayers(new ArrayList<>(Bukkit.getOnlinePlayers()));
    }

    private void createTeam(RPGClassType rpgClassType) {
        ScoreboardTeam team = teamManager.createIfAbsent(rpgClassType.name());
        team.defaultDisplay().canSeeFriendlyInvisibles(true);
        team.defaultDisplay().collisionRule(CollisionRule.PUSH_OTHER_TEAMS);
        team.defaultDisplay().prefix(Component.text("[" + rpgClassType.name().substring(0, 1) + "] ")
                .style(Style.style(AdventureColorConverter.toAdventure(rpgClassType.getColor()))));
        team.defaultDisplay().playerColor(AdventureColorConverter.toAdventure(rpgClassType.getColor()));
    }

    public void assignTeam(Player player, RPGClassType rpgClassType) {
        if (currentTeam.containsKey(player.getUniqueId())) {
            currentTeam.get(player.getUniqueId()).defaultDisplay().removeEntry(player.getName());
        }

        ScoreboardTeam team = teamManager.createIfAbsent(rpgClassType.name());
        team.defaultDisplay().addEntry(player.getName());
        currentTeam.put(player.getUniqueId(), team);
    }
}