package dev.core.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level marker for event subscriber classes. Classes annotated with
 * {@code @EventSubscriber} are discovered and registered automatically by
 * {@link EventSubscriberScanner}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventSubscriber {

}