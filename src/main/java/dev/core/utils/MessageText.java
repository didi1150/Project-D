package dev.core.utils;

import static dev.core.utils.MessageLevel.*;

public enum MessageText {

    INFO_PLAYER_JOINED(INFO_LEVEL, "Player %s joined"),
    WARNING_COMMAND_FAILED(WARNING_LEVEL, "Command %s has failed");

    private final MessageLevel level;
    private final String message;

    MessageText(MessageLevel level, String message) {
        this.level = level;
        this.message = message;
    }

    public MessageLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

}
