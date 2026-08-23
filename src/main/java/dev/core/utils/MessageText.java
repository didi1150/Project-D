package dev.core.utils;

import static dev.core.utils.MessageLevel.*;

public enum MessageText {

    INFO_PLAYER_JOINED(INFO_LEVEL, "Player %s joined"),
    WARNING_COMMAND_FAILED(WARNING_LEVEL, "Command %s has failed"),
    WARNING_COMMAND_NOT_IMPLEMENTED(WARNING_LEVEL, "This command is not yet implemented"),
    ERROR_COMMAND_ONLY_USABLE_BY_PLAYER(ERROR_LEVEL, "This command can only be executed by a player!"),
    ERROR_COMMAND_WRONG_NUMBER_OF_ARGS(ERROR_LEVEL, "Command Error: Wrong number of arguments, were %s but should be %s"),
    ERROR_COMMAND_INCORRECT_ARGUMENT(ERROR_LEVEL, "Command Error: Incorrect argument, was '%s' but should be a valid '%s'"),
    ERROR_COMMAND_NO_PERMISSION(ERROR_LEVEL, "Permission Error: You aren't allowed to use this command, no permission for: %s");

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
