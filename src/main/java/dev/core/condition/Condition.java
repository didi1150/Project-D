package dev.core.condition;

import java.util.Set;

public interface Condition {
    /**
     * Called once per matching RPGBossTriggerEvent. Returns true the moment the condition is
     * satisfied.
     */
//    boolean test(RPGBossTriggerEvent event, BossInstance instance);

    /**
     * Event types this condition cares about, so BossInstance can skip irrelevant
     * events cheaply.
     */
    Set<String> relevantEventTypes();
}
