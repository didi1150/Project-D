package dev.bukkit.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.bukkit.utils.BukkitMessageSender;
import dev.core.utils.MessageComponent;
import me.kodysimpson.simpapi.command.CommandList;
import me.kodysimpson.simpapi.command.SubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class CoreCommand extends Command {
    private final ArrayList<SubCommand> subcommands;
    private final CommandList commandList;

    public CoreCommand(String name, String description, String usageMessage, CommandList commandList, List<String> aliases, ArrayList<SubCommand> subCommands) {
        super(name, description, usageMessage, aliases);
        this.subcommands = subCommands;
        this.commandList = commandList;
    }

    public ArrayList<SubCommand> getSubCommands() {
        return this.subcommands;
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (args.length > 0) {
            boolean executed = this.getSubCommands().stream()
                .filter(subcmd -> subcmd.getName().equalsIgnoreCase(args[0]) || 
                        (subcmd.getAliases() != null && subcmd.getAliases().contains(args[0])))
                .findFirst()
                .map(subcmd -> {
                    subcmd.perform(sender, args);
                    return true;
                })
                .orElse(false);
            
            if (executed) {
                return true;
            }
        }
        
        if (this.commandList == null) {
            displayUsage(sender);
        } else {
            this.commandList.displayCommandList(sender, this.subcommands);
        }
        return true;
    }

    private void displayUsage(CommandSender sender) {
        BukkitMessageSender.getInstance().sendLine(sender, ChatColor.AQUA.toString());
        for(SubCommand subcommand : this.subcommands) {
            String syntax = subcommand.getSyntax();
            BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of(ChatColor.GREEN + syntax + ChatColor.GOLD + " - " + subcommand.getDescription()));
        }
        BukkitMessageSender.getInstance().sendLine(sender, ChatColor.AQUA.toString());
    }

    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args) throws IllegalArgumentException {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        // Tab complete subcommand names at first argument
        if (args.length == 1) {
            List<String> subcommandsArguments = new ArrayList<>();
            for (SubCommand subcmd : this.getSubCommands()) {
                subcommandsArguments.add(subcmd.getName());
            }
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[args.length - 1], subcommandsArguments, completions);
            return completions;
        }

        // Tab complete subcommand arguments
        if (args.length >= 2) {
            for (SubCommand subcmd : this.getSubCommands()) {
                if (subcmd.getName().equalsIgnoreCase(args[0]) || 
                    (subcmd.getAliases() != null && subcmd.getAliases().contains(args[0]))) {
                    List<String> subCommandArgs = subcmd.getSubcommandArguments((Player) sender, args);
                    if (subCommandArgs != null) {
                        List<String> completions = new ArrayList<>();
                        StringUtil.copyPartialMatches(args[args.length - 1], subCommandArgs, completions);
                        return completions;
                    }
                    return Collections.emptyList();
                }
            }
        }

        return Collections.emptyList();
    }
}
