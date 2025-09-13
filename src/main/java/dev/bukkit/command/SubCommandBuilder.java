package dev.bukkit.command;

import dev.bukkit.utils.BukkitMessageSender;
import dev.core.utils.MessageComponent;
import dev.core.utils.MessageText;
import me.kodysimpson.simpapi.command.SubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class SubCommandBuilder {

    private String mainCommandName;
    private final String name;
    private String description;
    private String syntax;
    private final List<String> aliases;

    private final List<CommandActionContainer> commandActions;

    private final List<CommandArgumentsContainer> tabSuggestions;

    private SubCommandBuilder(String name) {
        this.name = name;
        aliases = new ArrayList<>();
        tabSuggestions = new ArrayList<>();
        commandActions = new ArrayList<>();
    }

    public static SubCommandBuilder startBuilding(String name) {
        return new SubCommandBuilder(name);
    }

    public SubCommandBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public SubCommandBuilder setSyntax(String syntax) {
        this.syntax = syntax;
        return this;
    }

    public SubCommandBuilder addAlias(String alias) {
        this.aliases.add(alias);
        return this;
    }

    public SubCommandBuilder setCommandAction(int argsNumber, CommandAction commandAction) {
        commandActions.add(new CommandActionContainer(argsNumber, name, commandAction));
        return this;
    }

    public SubCommandBuilder setCommandAction(int argsNumber, String necessaryArg, CommandAction commandAction) {
        commandActions.add(new CommandActionContainer(argsNumber, necessaryArg, commandAction));
        return this;
    }

    public SubCommandBuilder setPlayerCommandAction(int argsNumber, PlayerCommandAction commandAction) {
        commandActions.add(new CommandActionContainer(argsNumber, name, commandAction));
        return this;
    }

    public SubCommandBuilder setPlayerCommandAction(int argsNumber, String necessaryArg, PlayerCommandAction commandAction) {
        commandActions.add(new CommandActionContainer(argsNumber, necessaryArg, commandAction));
        return this;
    }

    public SubCommandBuilder setCommandArgumentsList(int argsNumber, List<String> tabSuggestions) {
        return setCommandArgumentsList(argsNumber, name, tabSuggestions, null);
    }

    public SubCommandBuilder setCommandArgumentsList(int argsNumber, String necessaryArg, List<String> tabSuggestions) {
        return setCommandArgumentsList(argsNumber, necessaryArg, tabSuggestions, null);
    }

    public SubCommandBuilder setCommandArgumentsList(int argsNumber, List<String> tabSuggestions, String argType) {
        return setCommandArgumentsList(argsNumber, name, tabSuggestions, argType);
    }

    public SubCommandBuilder setCommandArgumentsList(int argsNumber, String argType) {
        return setCommandArgumentsList(argsNumber, name, List.of(), argType);
    }

    public SubCommandBuilder setCommandArgumentsList(int argsNumber, String necessaryArg, List<String> tabSuggestions, String argType) {
        this.tabSuggestions.add(new CommandArgumentsContainer(argsNumber, necessaryArg, tabSuggestions, argType));
        return this;
    }

    public void setMainCommand(String name) {
        this.mainCommandName = name;
    }

    private void sendUsageMessage(CommandSender sender) {
        BukkitMessageSender.getInstance().sendLine(sender, ChatColor.AQUA.toString());
        BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of(ChatColor.GREEN + syntax + ChatColor.GOLD + " - " + description));
        BukkitMessageSender.getInstance().sendLine(sender, ChatColor.AQUA.toString());
    }

    private void sendUsageMessage(CommandSender sender, String mainArg) {
        StringBuilder builder = new StringBuilder();
        builder.append("/").append(mainCommandName).append(" ").append(name);
        if (!name.equalsIgnoreCase(mainArg)) builder.append(" ").append(mainArg);
        if (!tabSuggestions.isEmpty()) {
            List<CommandArgumentsContainer> containers = tabSuggestions.stream()
                    .filter(c -> c.necessaryArg.equalsIgnoreCase(mainArg))
                    .sorted(Comparator.comparingInt(c -> c.argsNumber))
                    .toList();
            for (CommandArgumentsContainer container : containers) {
                builder.append(" < ").append(container.argType).append(" >");
            }
        }
        String usage = builder.toString();

        BukkitMessageSender.getInstance().sendLine(sender, ChatColor.AQUA.toString());
        BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of(ChatColor.GREEN + usage + ChatColor.GOLD + " - " + description));
        BukkitMessageSender.getInstance().sendLine(sender, ChatColor.AQUA.toString());
    }

    private void sendUsageMessage(CommandSender sender, String mainArg, MessageComponent error) {
        BukkitMessageSender.getInstance().sendMessage(sender, error);
        sendUsageMessage(sender, mainArg);
    }

    private Pair<CommandActionContainer, Boolean> getCorrespondingCommandAction(String mainArg, int argsNum) {
        List<CommandActionContainer> list = commandActions.stream().filter(container -> container.necessaryArg.equalsIgnoreCase(mainArg)).toList();
        Optional<CommandActionContainer> o = list.stream().filter(container -> container.argsNumber == argsNum).findFirst();
        if (o.isPresent()) {
            return new Pair<>(o.get(), true);
        } else {
            o = list.stream().findFirst();
            return o.map(container -> new Pair<>(container, false)).orElseGet(() -> new Pair<>(null, false));
        }
    }

    private List<String> getCommandSuggestions(String mainArg, int argsNum) {
        return tabSuggestions.stream()
                .filter(c -> c.necessaryArg.equalsIgnoreCase(mainArg))
                .filter(c -> c.argsNumber == argsNum)
                .map(c -> c.tabSuggestions)
                .findFirst()
                .orElse(List.of());
    }

    private Pair<Boolean, Integer> hasValidArgs(String mainArg, String[] args) {
        for (int i = 0; i < args.length; i++) {
            int finalI = i;
            List<String> suggestions = getCommandSuggestions(mainArg, i);
            if (!suggestions.isEmpty() && suggestions.stream().noneMatch(s -> s.equalsIgnoreCase(args[finalI]))) {
                return new Pair<>(false, i);
            }
            if (suggestions.isEmpty()) {
                CommandArgumentsContainer container = tabSuggestions.stream()
                        .filter(c -> c.necessaryArg.equalsIgnoreCase(mainArg))
                        .filter(c -> c.argsNumber == finalI)
                        .findFirst()
                        .orElse(null);
                if (container != null && container.argType.contains("(") && container.argType.contains(")")) {
                    String type = container.argType.substring(container.argType.indexOf('(') + 1, container.argType.indexOf(')'));
                    type = type.toLowerCase();
                    Runnable r = null;
                    switch (type) {
                        case "integer" -> r = () -> Integer.parseInt(args[finalI]);
                        case "long" -> r = () -> Long.parseLong(args[finalI]);
                        case "double" -> r = () -> Double.parseDouble(args[finalI]);
                        case "float" -> r = () -> Float.parseFloat(args[finalI]);
                    }
                    try {
                        if (r != null) r.run();
                    } catch (NumberFormatException e) {
                        return new Pair<>(false, i);
                    }
                }
            }
        }
        return new Pair<>(true, -1);
    }

    public SubCommand build() {
        if (description == null) description = "No Description";
        if (syntax == null) {
            StringBuilder builder = new StringBuilder();
            builder.append("/").append(mainCommandName).append(" ").append(name);
            if (!tabSuggestions.isEmpty()) {
                builder.append(" < ");
                boolean first = true;

                List<CommandArgumentsContainer> containers = tabSuggestions.stream()
                        .filter(c -> c.necessaryArg.equalsIgnoreCase(name))
                        .filter(c -> c.argsNumber == 0)
                        .toList();
                for (CommandArgumentsContainer container : containers) {
                    if (!first) builder.append(" | ");
                    else first = false;
                    builder.append(container.argType);
                }
                builder.append(" >");

                if (containers.size() == 1) {
                    builder = new StringBuilder();
                    builder.append("/").append(mainCommandName).append(" ").append(name);
                    containers = tabSuggestions.stream()
                            .filter(c -> c.necessaryArg.equalsIgnoreCase(name))
                            .sorted(Comparator.comparingInt(c -> c.argsNumber))
                            .toList();
                    for (CommandArgumentsContainer container : containers) {
                        builder.append(" < ").append(container.argType).append(" >");
                    }
                }
            }
            syntax = builder.toString();
        }

        return new SubCommand() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public List<String> getAliases() {
                return aliases;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getSyntax() {
                return syntax;
            }

            @Override
            public void perform(CommandSender sender, String[] args) {

                args = Arrays.stream(args).skip(1).toArray(String[]::new);

                int argNum = args.length;

                if (commandActions.isEmpty()) {
                    BukkitMessageSender.getInstance().sendMessage(sender, MessageComponent.of(MessageText.WARNING_COMMAND_NOT_IMPLEMENTED));
                    return;
                }

                String mainArg;

                if (args.length > 0) mainArg = args[0];
                else mainArg = name;

                Pair<CommandActionContainer, Boolean> pair = getCorrespondingCommandAction(mainArg, argNum);
                CommandActionContainer container = pair.first;

                if (container == null) {
                    pair = getCorrespondingCommandAction(name, argNum);
                    container = pair.first;
                    mainArg = name;
                    if (container == null) {
                        sendUsageMessage(sender);
                        return;
                    }
                }

                if (!pair.second) {
                    sendUsageMessage(sender, mainArg, MessageComponent.of(MessageText.ERROR_COMMAND_WRONG_NUMBER_OF_ARGS, argNum, container.argsNumber));
                    return;
                }

                Pair<Boolean, Integer> p = hasValidArgs(mainArg, args);
                if (!p.first) {
                    String finalMainArg = mainArg;
                    String expected = tabSuggestions.stream()
                            .filter(c -> c.necessaryArg.equalsIgnoreCase(finalMainArg))
                            .filter(c -> c.argsNumber == p.second)
                            .map(c -> c.argType)
                            .findFirst()
                            .orElse("not-defined");
                    sendUsageMessage(sender, mainArg, MessageComponent.of(MessageText.ERROR_COMMAND_INCORRECT_ARGUMENT, args[p.second], expected));
                    return;
                }

                container.commandAction.perform(sender, args);
                return;
            }

            @Override
            public List<String> getSubcommandArguments(Player player, String[] args) {

                List<String> list;

                int index = args.length - 1;

                if (index == 1) {
                    list = getCommandSuggestions(name, 0);
                } else {
                    String mainArg = args[1];

                    list = getCommandSuggestions(mainArg, index);
                }

                return list;
            }
        };
    }

    public record CommandActionContainer(int argsNumber, String necessaryArg, CommandAction commandAction){}

    public record CommandArgumentsContainer(int argsNumber, String necessaryArg, List<String> tabSuggestions, String argType){}

    public record Pair<K, V>(K first, V second){}

    public interface CommandAction {
        void perform(CommandSender commandSender, String[] args);
    }

    public interface PlayerCommandAction extends CommandAction {
        @Override
        default void perform(CommandSender commandSender, String[] args){
            if (!(commandSender instanceof Player)){
                BukkitMessageSender.getInstance().sendMessage(MessageComponent.of(MessageText.ERROR_COMMAND_ONLY_USABLE_BY_PLAYER));
                return;
            }
            perform(((Player) commandSender), args);
        };
        void perform(Player player, String[] args);
    }

}
