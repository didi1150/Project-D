package dev.core.game;

import java.util.ArrayList;
import java.util.List;

public class TimeHelper {

    private static final TimeHelper instance = new TimeHelper();
    public static TimeHelper getInstance() {
        return instance;
    }

    private List<Updatable> subscribed;

    public TimeHelper() {
        subscribed = new ArrayList<>();
    }

    //should be called every tick (or second)
    public void update() {
        for (Updatable updatable : subscribed) {
            updatable.update();
        }
    }

    public void subscribe(Updatable updatable) {
        if (subscribed.contains(updatable)) return;
        subscribed.add(updatable);
    }

    public void unsubscribe(Updatable updatable) {
        subscribed.remove(updatable);
    }
}
