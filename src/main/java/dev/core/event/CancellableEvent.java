package dev.core.event;

public class CancellableEvent extends Event {

    private boolean canceled = false;

    public boolean isCancelled() {
        return canceled;
    }

    public void setCancelled(boolean canceled) {
        this.canceled = canceled;
    }
}
