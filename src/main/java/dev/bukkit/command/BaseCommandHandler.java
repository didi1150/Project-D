package dev.bukkit.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for command handlers to reduce boilerplate in command execution.
 * Provides common patterns for permission checking, argument validation, and execution.
 */
public abstract class BaseCommandHandler {

    /**
     * Check if sender has required permission.
     */
    protected boolean hasPermission(@NotNull CommandSender sender, @NotNull String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("You don't have permission to execute this command.");
            return false;
        }
        return true;
    }

    /**
     * Require player sender.
     */
    protected Optional<Player> requirePlayer(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by players.");
            return Optional.empty();
        }
        return Optional.of(player);
    }

    /**
     * Require minimum argument count.
     */
    protected boolean requireArgs(@NotNull CommandSender sender, String[] args, int minArgs, String usage) {
        if (args.length < minArgs) {
            sender.sendMessage("Incorrect Syntax! " + usage);
            return false;
        }
        return true;
    }

    /**
     * Send error message.
     */
    protected void sendError(@NotNull CommandSender sender, @NotNull String message) {
        sender.sendMessage("§c" + message);
    }

    /**
     * Send success message.
     */
    protected void sendSuccess(@NotNull CommandSender sender, @NotNull String message) {
        sender.sendMessage("§a" + message);
    }

    /**
     * Send info message.
     */
    protected void sendInfo(@NotNull CommandSender sender, @NotNull String message) {
        sender.sendMessage("§b" + message);
    }

    /**
     * Execute consumer if optional is present.
     */
    protected <T> void ifPresent(Optional<T> optional, Consumer<T> consumer) {
        optional.ifPresent(consumer);
    }

    /**
     * Get tab completion suggestions with partial matching.
     */
    protected List<String> getPartialMatches(String input, List<String> options) {
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                matches.add(option);
            }
        }
        return matches;
    }
}
