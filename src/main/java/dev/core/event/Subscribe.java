package dev.core.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event subscription. The method must take exactly one
 * parameter: the event type it listens to. An alternative to manually calling
 * {@link EventBusInterface#subscribe(EventAction)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    /**
     * Priority of the subscription, ranging from 1 to 5
     * (see {@link EventAction} priority constants).
     */
    int priority() default EventAction.NORMAL_PRIORITY;

}