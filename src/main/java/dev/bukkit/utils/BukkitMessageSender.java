package dev.bukkit.utils;

import dev.core.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.core.item.display.StyleTagParser;
import dev.core.item.display.TextColor;

public class BukkitMessageSender implements MessageSenderInterface {

    private static BukkitMessageSender instance;
    public static BukkitMessageSender getInstance() {
        if (instance == null) instance = new BukkitMessageSender();
        return instance;
    }

    private BukkitMessageSender() {}

    public void sendMessage(CommandSender commandSender, MessageComponent messageComponent) {
        MessageLevel level = messageComponent.getLevel();
        send(commandSender, messageComponent, level.getColor(), level.getPrefix(), false, true);
    }

    @Override
    public void sendMessage(MessageComponent messageComponent) {
        this.sendMessage(Bukkit.getConsoleSender(), messageComponent);
    }

    public void sendMessage(MessageComponent messageComponent, boolean shouldLineBreak) {
        MessageLevel level = messageComponent.getLevel();
        send(Bukkit.getConsoleSender(), messageComponent, level.getColor(), level.getPrefix(), false, shouldLineBreak);
    }

    @Override
    public void sendCenteredMessage(MessageComponent messageComponent) {
        MessageLevel level = messageComponent.getLevel();
        send(Bukkit.getConsoleSender(), messageComponent, level.getColor(), level.getPrefix(), true, true);
    }

    public void sendMessage(Player player, MessageComponent messageComponent) {
        this.sendMessage(((CommandSender) player), messageComponent);
    }

    public void sendMessage(Player player, MessageComponent messageComponent, boolean shouldLineBreak) {
        MessageLevel level = messageComponent.getLevel();
        send(player, messageComponent, level.getColor(), level.getPrefix(), false, shouldLineBreak);
    }

    public void sendCenteredMessage(Player player, MessageComponent messageComponent) {
        MessageLevel level = messageComponent.getLevel();
        send(player, messageComponent, level.getColor(), level.getPrefix(), true, true);
    }

    @Override
    public void sendDebugMessage(MessageComponent messageComponent) {
        send(Bukkit.getConsoleSender(), messageComponent, TextColor.GRAY,  MessageLevel.DEBUG_PREFIX, false, true);
    }

    public void sendDebugMessage(MessageComponent messageComponent, boolean shouldLineBreak) {
        send(Bukkit.getConsoleSender(), messageComponent, TextColor.GRAY,  MessageLevel.DEBUG_PREFIX, false, shouldLineBreak);
    }

    @Override
    public void sendCenteredDebugMessage(MessageComponent messageComponent) {
        send(Bukkit.getConsoleSender(), messageComponent, TextColor.GRAY, MessageLevel.DEBUG_PREFIX, true, true);
    }

    public void sendDebugMessage(Player player, MessageComponent messageComponent) {
        send(player, messageComponent, TextColor.GRAY, MessageLevel.DEBUG_PREFIX, false, true);
    }

    public void sendDebugMessage(Player player, MessageComponent messageComponent, boolean shouldLineBreak) {
        send(player, messageComponent, TextColor.GRAY, MessageLevel.DEBUG_PREFIX, false, shouldLineBreak);
    }

    public void sendCenteredDebugMessage(Player player, MessageComponent messageComponent) {
        send(player, messageComponent, TextColor.GRAY, MessageLevel.DEBUG_PREFIX, true, true);
    }

    public void sendLine(Player player, String colorCodes) {
        sendLine(((CommandSender) player), colorCodes);
    }

    public void sendLine(String colorCodes) {
        sendLine(Bukkit.getConsoleSender(), colorCodes);
    }

    public void sendLine(CommandSender sender, String colorCodes) {
//        String line = "§m                                                                        ";
        String line = "§m                                                                              ";
        StyleTagParser parser = new StyleTagParser(TextColor.WHITE);
        StringBuilder parsedString = new StringBuilder();
        for (StyleTagParser.StyledSegment seg : parser.parse(colorCodes)) {
            parsedString.append(BukkitTextColorAdapter.toChatFormatting(seg.style())).append(seg.text());
        }
        String parsedColors = parsedString.toString().trim();
        parsedColors = parsedColors.replace(ChatColor.BOLD.toString(), "");
        MessageComponent component = MessageComponent.of(parsedColors + line);
        send(sender, component, TextColor.WHITE, "", false, false);
    }

    private void send(CommandSender sender, MessageComponent messageComponent, TextColor defaultColor, String levelPrefix, boolean centered, boolean shouldLineBreak) {
        String message = messageComponent.getMessage();

        while (message.contains("%s")) {
            message = message.replaceFirst("%s", messageComponent.getNextArg().toString());
        }

        message = levelPrefix + message;

        StyleTagParser parser = new StyleTagParser(defaultColor);

        StringBuilder parsedString = new StringBuilder();
        for (StyleTagParser.StyledSegment seg : parser.parse(message)) {
            parsedString.append(BukkitTextColorAdapter.toChatFormatting(seg.style())).append(seg.text());
        }

        String finalMessage = parsedString.toString();

        if (centered) finalMessage = insertNewLines(finalMessage, shouldLineBreak);

        for (String line : finalMessage.lines().toList()) {
            if (centered) line = getCenteredMessage(line);
            if (sender instanceof ConsoleCommandSender) {
                line = MinecraftColorTranslator.translateToAnsi(line);
            }
            sender.sendMessage(line);
        }
//        if (centered) finalMessage = getCenteredMessage(finalMessage);
//        sender.sendMessage(finalMessage);
    }

    private final static int CENTER_PX = 310/2;
    private final static int MAX_MESSAGE_PX = CENTER_PX*2 - 70;

    private String getCenteredMessage(String message) {
        if (message == null || message.isEmpty()) return "";
        message = ChatColor.translateAlternateColorCodes('&', message);

        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for (char c : message.toCharArray()) {
            if (c == '§') {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                if (c == 'l' || c == 'L') {
                    isBold = true;
                } else isBold = false;
            } else {
                DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = CENTER_PX - halvedMessageSize;
        int spaceLength = DefaultFontInfo.SPACE.getLength() + 1;
        int compensated = 0;
        StringBuilder sb = new StringBuilder();
        while (compensated < toCompensate) {
            sb.append(" ");
            compensated += spaceLength;
        }
        return sb.toString() + message;
    }

    private String insertNewLines(String message, boolean shouldLineBreak) {
        if (message == null || message.isEmpty()) return "";
        message = ChatColor.translateAlternateColorCodes('&', message);

        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        String prefix = "";

//        String lines = message;
        int startIndex = 0;
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < message.length(); i++) {
            char c = message.toCharArray()[i];
            if (c == '§') {
                previousCode = true;
                String code = "§" + message.toCharArray()[i+1];
                prefix += code;
                if (code.equals("§r")) prefix = "";
            } else if (previousCode) {
                previousCode = false;
                if (c == 'l' || c == 'L') {
                    isBold = true;
                } else isBold = false;
            } else {
                DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
            if (c == '\n') messagePxSize = 0;
            if ((shouldLineBreak && messagePxSize >= MAX_MESSAGE_PX && c == DefaultFontInfo.SPACE.getCharacter()) || messagePxSize >= CENTER_PX*2) {
                messagePxSize = 0;
                if (startIndex != 0) builder.append("\n").append(prefix);
                builder.append(message, startIndex, i + 1);
                startIndex = i + 1;
                //lines = message.substring(0, i + 1) + "\n" + prefix + message.substring(i + 1);
            }
        }
        if (startIndex != message.length()) {
            if (startIndex != 0) builder.append("\n").append(prefix);
            builder.append(message, startIndex, message.length());
        }
        return builder.toString();
    }



}
