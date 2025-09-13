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
            for (int i = 0; i < this.getSubCommands().size(); ++i) {
                if (args[0].equalsIgnoreCase(((SubCommand) this.getSubCommands().get(i)).getName()) || ((SubCommand) this.getSubCommands().get(i)).getAliases() != null && ((SubCommand) this.getSubCommands().get(i)).getAliases().contains(args[0])) {
                    ((SubCommand) this.getSubCommands().get(i)).perform(sender, args);
                    return true;
                }
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
        if (args.length == 1) {
            ArrayList<String> subcommandsArguments = new ArrayList();
            for(int i = 0; i < this.getSubCommands().size(); ++i) {
                subcommandsArguments.add(((SubCommand)this.getSubCommands().get(i)).getName());
            }
            return subcommandsArguments;
        } else {
            if (args.length >= 2) {
                for(int i = 0; i < this.getSubCommands().size(); ++i) {
                    List<String> names = new ArrayList<>(List.of(this.getSubCommands().get(i).getName()));
                    names.addAll(this.getSubCommands().get(i).getAliases());
                    if (names.stream().anyMatch(s -> s.equalsIgnoreCase(args[0]))) {
                        List<String> subCommandArgs = ((SubCommand)this.getSubCommands().get(i)).getSubcommandArguments((Player)sender, args);
                        if (subCommandArgs != null) {
                            return subCommandArgs;
                        }
                        return Collections.emptyList();
                    }
                }
            }

            return Collections.emptyList();
        }
    }
}
