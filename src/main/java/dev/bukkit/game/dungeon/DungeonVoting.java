package dev.bukkit.game.dungeon;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.utils.BukkitMessageSender;
import dev.core.event.EventAction;
import dev.core.game.ScheduledTask;
import dev.core.game.TaskScheduler;
import dev.core.game.settings.GameSettings;
import dev.core.game.voting.VotingSystem;
import dev.core.utils.MessageComponent;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class DungeonVoting {

    private final VotingSystem<Integer> votingSystem;
    private final String chatSubscriberId;

    private boolean votingActive = false;
    private ScheduledTask updateTask;

    public DungeonVoting() {
        this.votingSystem = new VotingSystem<>();

        // Default votes = floor 1
        for (Player player : Bukkit.getOnlinePlayers()) {
            votingSystem.castVote(player.getUniqueId(), 1);
        }

        EventAction<PlayerCommandPreprocessEvent> chatAction = new EventAction<>(this::handleChatVote,
                PlayerCommandPreprocessEvent.class);
        BukkitEventBus.getInstance().subscribe(chatAction);
        this.chatSubscriberId = chatAction.getId();
    }

    public void startVoting(TaskScheduler scheduler) {
        if (votingActive)
            return;
        votingActive = true;

        broadcastVotingInterface();

        // Update every 5s
        updateTask = scheduler.runTaskTimer(() -> {
            if (votingActive)
                broadcastVoteStatus();
        }, 100L, 100L);

        BukkitMessageSender sender = BukkitMessageSender.getInstance();
        sender.sendMessage(MessageComponent.of("<yellow><bold>=== DUNGEON FLOOR VOTING ===</bold></yellow>"));
        sender.sendMessage(MessageComponent.of("<gray>Vote for which floor you'd like to explore!</gray>"));
        sender.sendMessage(MessageComponent.of("<gray>Click an option below or type &f/vote <1-5></gray>"));
    }

    public int stopVoting() {
        if (!votingActive)
            return 1;
        votingActive = false;
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        BukkitEventBus.getInstance().unsubscribe(chatSubscriberId);

        int winner = getWinningFloor();
        BukkitMessageSender sender = BukkitMessageSender.getInstance();
        Bukkit.getOnlinePlayers().forEach(player -> {

            sender.sendCenteredMessage(player,
                    MessageComponent.of("<green><bold>=== VOTING COMPLETE ===</bold></green>"));
            sender.sendCenteredMessage(player,
                    MessageComponent.of("<green>Winning Floor: <white>Floor " + winner + "</white></green>"));

        });
        GameSettings.getCurrentSettings().setFloor(winner);
        broadcastFinalResults();
        return winner;
    }

    public boolean vote(UUID playerId, int floor) {
        if (!votingActive)
            return false;
        if (floor < 1 || floor > 5)
            return false;

        votingSystem.castVote(playerId, floor);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            BukkitMessageSender.getInstance().sendMessage(player,
                    MessageComponent.of("<green>Vote registered: Floor " + floor + "</green>"));
        }
        return true;
    }

    public int getWinningFloor() {
        int maxVotes = 0;
        int winning = 1;
        for (int floor = 1; floor <= 5; floor++) {
            long count = votingSystem.countVotesFor(floor);
            if (count > maxVotes) {
                maxVotes = (int) count;
                winning = floor;
            }
        }
        return winning;
    }

    private void broadcastVotingInterface() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Title line
            BukkitMessageSender.getInstance().sendCenteredMessage(player,
                    MessageComponent.of("<yellow>--- SELECT DUNGEON FLOOR ---</yellow>"));

            int currentVote = votingSystem.getVote(player.getUniqueId()).orElse(1);

            // Build a clickable row
            TextComponent row = new TextComponent();

            for (int floor = 1; floor <= 5; floor++) {
                boolean isSelected = currentVote == floor;

                String displayText = isSelected
                        ? ChatColor.GREEN.toString() + ChatColor.BOLD + "[" + floor + "] Floor " + floor + " ✓"
                        : ChatColor.GRAY + "[" + floor + "] Floor " + floor;

                TextComponent option = new TextComponent(displayText);

                // Click executes /vote <floor>
                option.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vote " + floor));

                // Hover text
                String hoverText = ChatColor.YELLOW + "Click to vote for Floor " + floor + "\n"
                        + (isSelected ? ChatColor.GREEN + "Currently selected" : ChatColor.YELLOW + "Click to select");
                option.setHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hoverText).create()));

                row.addExtra(option);

                // Add spacing between options
                if (floor < 5) {
                    row.addExtra(new TextComponent("  "));
                }
            }

            // Send interactive row
            player.spigot().sendMessage(row);

            BukkitMessageSender.getInstance().sendMessage(player,
                    MessageComponent.of("<gray><bold>(Click an option above to vote)</bold></gray>"));
        }
    }

    private void broadcastVoteStatus() {
        int total = votingSystem.totalVotes();
        if (total == 0)
            return;

        int leading = getWinningFloor();
        long leadingVotes = votingSystem.countVotesFor(leading);

        BukkitMessageSender.getInstance().sendMessage(MessageComponent.of(
                "<gold>Leading: Floor " + leading + " <gray>(" + leadingVotes + "/" + total + " votes)</gray></gold>"));
    }

    private void broadcastFinalResults() {
        BukkitMessageSender sender = BukkitMessageSender.getInstance();
        sender.sendMessage(MessageComponent.of("<gray>Final Results:</gray>"));
        for (int floor = 1; floor <= 5; floor++) {
            long count = votingSystem.countVotesFor(floor);
            boolean isWinner = floor == getWinningFloor();
            int finalFloor = floor;
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.playSound(player, Sound.BLOCK_ANVIL_FALL, 1f, .5f);
                String indicator = isWinner ? "<green><bold>► " : "<gray>  ";
                sender.sendCenteredMessage(player,
                        MessageComponent.of(indicator + "Floor " + finalFloor + (isWinner ? " <gray>- " : " - ") + count
                                + " vote" + (count == 1 ? "" : "s") + (isWinner ? "</gray>" : "")
                                + (isWinner ? "</bold></green>" : "</gray>")));
            });
        }
    }

    private void handleChatVote(PlayerCommandPreprocessEvent event) {
        if (!votingActive) {
            return;
        }
        String msg = event.getMessage().toLowerCase().trim();

        if (msg.startsWith("/vote ") || msg.matches("^[1-5]$")) {
            event.setCancelled(true);
            try {
                int floor = msg.startsWith("/vote ") ? Integer.parseInt(msg.substring(6).trim())
                        : Integer.parseInt(msg);

                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugins()[0],
                        () -> vote(event.getPlayer().getUniqueId(), floor));
            } catch (NumberFormatException e) {
                BukkitMessageSender.getInstance().sendMessage(event.getPlayer(),
                        MessageComponent.of("<red>Invalid vote! Use numbers 1-5.</red>"));
            }
        }
    }

    public boolean isVotingActive() {
        return votingActive;
    }
}
