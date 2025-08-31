package dev.core.event;

public abstract class Event {

    private String name;

    public String getName() {
        if (name == null) name = this.getClass().getSimpleName();
        return name;
    }
}
