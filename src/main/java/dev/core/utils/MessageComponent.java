package dev.core.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MessageComponent {

    private final String message;
    private final MessageLevel level;
    private final List<Object> args;

    private MessageComponent(String message, MessageLevel level, List<Object> args) {
        this.message = message;
        this.level = level;
        this.args = args;
    }

    public String getMessage() {
        return message;
    }

    public MessageLevel getLevel() {
        return level;
    }

    public Object getNextArg() {
        if (args.isEmpty()) return "<_>";
        return args.remove(0);
    }

    public static MessageComponent of(MessageText messageText, Object ... args) {
        return new MessageComponent(messageText.getMessage(), messageText.getLevel(), Arrays.stream(args).collect(Collectors.toList()));
    }

    public static MessageComponent of(MessageLevel level, MessageText messageText, Object ... args) {
        return new MessageComponent(messageText.getMessage(), level, Arrays.stream(args).collect(Collectors.toList()));
    }

    public static MessageComponent of(String message, Object ... args) {
        return of(MessageLevel.NORMAL_LEVEL, message, args);
    }

    public static MessageComponent of(MessageLevel level, String message, Object ... args) {
        return new MessageComponent(message, level, Arrays.stream(args).collect(Collectors.toList()));
    }

}
