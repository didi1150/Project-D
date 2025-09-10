package dev.core.utils;

public interface MessageSenderInterface {

    public void sendMessage(MessageComponent messageComponent);
    public void sendCenteredMessage(MessageComponent messageComponent);

    public void sendDebugMessage(MessageComponent messageComponent);
    public void sendCenteredDebugMessage(MessageComponent messageComponent);

}
