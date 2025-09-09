package dev.bukkit.utils;

import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.core.item.display.StyleTagParser;
import dev.core.item.display.TextColor;
import dev.core.utils.MessageComponent;
import dev.core.utils.MessageLevel;
import dev.core.utils.MessageSenderInterface;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BukkitMessageSender implements MessageSenderInterface {

    public static final String PLUGIN_PREFIX = "Project-D";

    private static BukkitMessageSender instance;
    public static BukkitMessageSender getInstance() {
        if (instance == null) instance = new BukkitMessageSender();
        return instance;
    }

    private BukkitMessageSender() {}

    @Override
    public void sendMessage(MessageComponent messageComponent) {
        MessageLevel level = messageComponent.getLevel();
        send(Bukkit.getConsoleSender(), messageComponent, level.getColor(), level.getPrefix());
    }

    public void sendMessage(Player player, MessageComponent messageComponent) {
        MessageLevel level = messageComponent.getLevel();
        send(player, messageComponent, level.getColor(), level.getPrefix());
    }

    @Override
    public void sendDebugMessage(MessageComponent messageComponent) {
        send(Bukkit.getConsoleSender(), messageComponent, TextColor.GRAY, "DEBUG");
    }

    public void sendDebugMessage(Player player, MessageComponent messageComponent) {
        send(player, messageComponent, TextColor.GRAY, "DEBUG");
    }

    private void send(CommandSender sender, MessageComponent messageComponent, TextColor defaultColor, String levelPrefix) {
        String message = messageComponent.getMessage();

        while (message.contains("%s")) {
            message = message.replaceFirst("%s", messageComponent.getNextArg().toString());
        }

        message = "[" + PLUGIN_PREFIX + " | " + levelPrefix + "] " + message;

        StyleTagParser parser = new StyleTagParser(defaultColor);

        StringBuilder parsedString = new StringBuilder();
        for (StyleTagParser.StyledSegment seg : parser.parse(message)) {
            parsedString.append(BukkitTextColorAdapter.toChatFormatting(seg.style())).append(seg.text());
        }

        sender.sendMessage(parsedString.toString());
    }

}
