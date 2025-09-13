package dev.bukkit.command;

import me.kodysimpson.simpapi.command.CommandList;
import me.kodysimpson.simpapi.command.SubCommand;

import java.util.ArrayList;
import java.util.List;

public class MainCommandBuilder {

    private final String name;
    private String description;
    private String usage;
    private CommandList commandList;
    private final List<String> aliases;
    private final List<SubCommand> subCommands;

    private MainCommandBuilder(String name) {
        this.name = name;
        this.aliases = new ArrayList<>();
        this.subCommands = new ArrayList<>();
    }

    public static MainCommandBuilder startBuilding(String name) {
        return new MainCommandBuilder(name);
    }

    public MainCommandBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public MainCommandBuilder setUsage(String usage) {
        this.usage = usage;
        return this;
    }

    public MainCommandBuilder setCommandList(CommandList commandList) {
        this.commandList = commandList;
        return this;
    }

    public MainCommandBuilder addAlias(String alias) {
        this.aliases.add(alias);
        return this;
    }

    public MainCommandBuilder addSubCommand(SubCommandBuilder subCommandBuilder) {
        subCommandBuilder.setMainCommand(name);
        this.subCommands.add(subCommandBuilder.build());
        return this;
    }

    public MainCommand build() {
        if (description == null) description = "No Description";
        if (usage == null) {
            StringBuilder builder = new StringBuilder();
            builder.append("/").append(name);
            if (!subCommands.isEmpty()) {
                builder.append(" < ");
                boolean first = true;
                for (SubCommand subCommand : subCommands) {
                    if (!first) builder.append(" | ");
                    else first = false;
                    builder.append(subCommand.getName());
                }
                builder.append(" >");
            }
            usage = builder.toString();
        }
        return new MainCommand(name, description, usage, commandList, aliases, subCommands);
    }

    public record MainCommand (String name, String description, String usage, CommandList commandList, List<String> aliases, List<SubCommand> subCommands) {

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String usage() {
            return usage;
        }

        @Override
        public CommandList commandList() {
            return commandList;
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }

        @Override
        public List<SubCommand> subCommands() {
            return subCommands;
        }
    }
}
