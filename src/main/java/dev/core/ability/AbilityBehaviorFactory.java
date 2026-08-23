package dev.core.ability;

@FunctionalInterface
public interface AbilityBehaviorFactory {
    AbilityBehavior create(ActiveAbility ctx);
}
