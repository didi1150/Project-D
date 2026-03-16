package dev.bukkit.scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import dev.bukkit.utils.AdventureColorConverter;
import dev.core.entity.EntityManager;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.stat.Stat;
import dev.core.stat.StatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.objective.ObjectiveDisplaySlot;
import net.megavex.scoreboardlibrary.api.objective.ObjectiveManager;
import net.megavex.scoreboardlibrary.api.objective.ScoreFormat;
import net.megavex.scoreboardlibrary.api.objective.ScoreboardObjective;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent.Builder;
import net.megavex.scoreboardlibrary.api.team.ScoreboardTeam;
import net.megavex.scoreboardlibrary.api.team.TeamManager;
import net.megavex.scoreboardlibrary.api.team.enums.CollisionRule;

public class BukkitScoreboardService {
    private static BukkitScoreboardService INSTANCE;
    private Plugin plugin;

    private ScoreboardLibrary scoreboardLibrary;
    private TeamManager teamManager;
    private ObjectiveManager objectiveManager;

    private Map<UUID, Sidebar> sidebars;
//    private ScoreboardObjective healthObjective;

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

        sidebars = new HashMap<UUID, Sidebar>();

        currentTeam = new HashMap<>();

        EventAction<PlayerJoinEvent> joinSub = new EventAction<>(e -> {
            if (!teamManager.players().contains(e.getPlayer()))
                teamManager.addPlayer(e.getPlayer());
            if (!objectiveManager.players().contains(e.getPlayer()))
                objectiveManager.addPlayer(e.getPlayer());
            UUID id = e.getPlayer().getUniqueId();
            sidebars.putIfAbsent(id, scoreboardLibrary.createSidebar());
        }, PlayerJoinEvent.class);

        EventAction<PlayerQuitEvent> quitSub = new EventAction<>(e -> {
            sidebars.remove(e.getPlayer().getUniqueId()).close();
        }, PlayerQuitEvent.class);

        eventBusInterface.subscribe(quitSub);
        eventBusInterface.subscribe(joinSub);

    }

    public void close() {
        teamManager.close();
        objectiveManager.close();
        scoreboardLibrary.close();

        sidebars.forEach((id, bar) -> {
            bar.close();
        });
        sidebars.clear();

        if (updateTimer != null) {
            updateTimer.cancel();
        }
    }

    public void initSidebars() {
        sidebars.keySet().forEach(id -> {
            sidebars.put(id, scoreboardLibrary.createSidebar());
        });

    }

    private void updateSidebar(UUID id) {
        Sidebar sidebar = sidebars.get(id);
        Builder builder = SidebarComponent.builder();
        for (Entry<StatType, Stat> entry : EntityManager.getInstance().getEntity(id).get().getStatManager().getStats()
                .entrySet()) {
            builder.addDynamicLine(() -> {
                return Component
                        .text(entry.getKey().getFormattedName() + " "
                                + (int) entry.getValue().getCurrent(System.currentTimeMillis()))
                        .style(Style.style(AdventureColorConverter.toAdventure(entry.getKey().getColor())));
            });
        }
        SidebarComponent comp = builder.build();
        sidebar.addPlayer(Bukkit.getPlayer(id));
        ComponentSidebarLayout layout = new ComponentSidebarLayout(
                SidebarComponent.staticLine(Component.text("Stats").style(Style.style(NamedTextColor.GOLD))), comp);
        layout.apply(sidebar);
    }

    public ScoreboardObjective createHealthObjective(ObjectiveDisplaySlot slot) {
        ScoreboardObjective scoreboardObjective = objectiveManager.create("health");
        scoreboardObjective.value(Component.text("❤").style(Style.style(NamedTextColor.RED)));
        scoreboardObjective.defaultScoreFormat(ScoreFormat.styled(NamedTextColor.RED));
        objectiveManager.display(slot, scoreboardObjective);
        return scoreboardObjective;
    }

    public void tick() {
//        updateHealthObjectives();
        updateSidebars();
        updateTeams();
    }

    private void updateSidebars() {
        sidebars.keySet().forEach(id -> {
            updateSidebar(id);
        });
    }

    public void initScoreboard() {
//        healthObjective = createHealthObjective(ObjectiveDisplaySlot.belowName());
        objectiveManager.addPlayers(new ArrayList<>(Bukkit.getOnlinePlayers()));
        createTeams();

        updateTimer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

//    private void updateHealthObjectives() {
//        Bukkit.getOnlinePlayers().forEach(player -> {
//            UUID id = player.getUniqueId();
//            if (EntityManager.getInstance().isAlive(id)) {
//                healthObjective.score(player.getName(),
//                        (int) EntityManager.getInstance().getEntity(id).get().getHealth());
//            }
//        });
//    }

    public void createTeams() {
        for (RPGClassType rpgClassType : RPGClassType.values()) {
            createTeam(rpgClassType);
        }
        assignTeamViewers();
    }

    public void assignTeamViewers() {
        teamManager.addPlayers(new ArrayList<>(Bukkit.getOnlinePlayers()));
    }

    private void createTeam(RPGClassType rpgClassType) {
        ScoreboardTeam team = teamManager.createIfAbsent(rpgClassType.name());
        team.defaultDisplay().canSeeFriendlyInvisibles(true);
        team.defaultDisplay().collisionRule(CollisionRule.NEVER);
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

    public void updateTeams() {
        teamManager.teams().forEach(this::updateTeam);
    }

    private void updateTeam(ScoreboardTeam team) {
        @NotNull
        Collection<String> entries = team.defaultDisplay().entries();
        if (entries.isEmpty()) {
            return;
        }
        String playerName = entries.stream().findFirst().get();
        UUID id = Bukkit.getPlayer(playerName).getUniqueId();
        ComponentLike suffixComp = Component.text(" [Dead]").style(Style.style(NamedTextColor.RED, TextDecoration.BOLD));
        if (EntityManager.getInstance().isAlive(id)) {
            int hp = (int) EntityManager.getInstance().getEntity(id).get().getStatManager()
                    .getCurrentValue(StatType.HEALTH_RESOURCE, System.currentTimeMillis());
            suffixComp = Component.text(" " + hp + StatType.HEALTH_RESOURCE.getSymbol()).style(Style.style(NamedTextColor.RED));
        }
        team.defaultDisplay().suffix(suffixComp);
    }
}