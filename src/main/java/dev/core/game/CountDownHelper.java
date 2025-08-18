package dev.core.game;

import java.util.function.Consumer;

public class CountDownHelper implements Updatable{

    private final int timeInTicks;
    private int currentTicks;
    private final Consumer<Integer> actionOnUpdate;
    private final boolean callActionOnlyOnSeconds;
    private final Runnable actionOnFinish;

    public CountDownHelper(int timeInTicks, Consumer<Integer> actionOnUpdate, boolean callActionOnlyOnSeconds, Runnable actionOnFinish) {
        this.timeInTicks = timeInTicks;
        this.actionOnUpdate = actionOnUpdate;
        this.callActionOnlyOnSeconds = callActionOnlyOnSeconds;
        this.actionOnFinish = actionOnFinish;
    }

    public CountDownHelper(float timeInSeconds, Consumer<Integer> actionOnUpdate, boolean callActionOnlyOnSeconds, Runnable actionOnFinish) {
        this((int) (timeInSeconds*20), actionOnUpdate, callActionOnlyOnSeconds, actionOnFinish);
    }

    public void startCountDown() {
        currentTicks = 0;
        TimeHelper.getInstance().subscribe(this);
    }

    @Override
    public void update() {
        currentTicks++;
        if (currentTicks == timeInTicks) {
            TimeHelper.getInstance().unsubscribe(this);
            actionOnFinish.run();
        } else {
            if (callActionOnlyOnSeconds && (currentTicks % 20 != 0 || currentTicks == 0)) return;
            actionOnUpdate.accept(callActionOnlyOnSeconds ? currentTicks / 20 : currentTicks);
        }
    }

}
