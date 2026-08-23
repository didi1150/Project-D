package dev.core.ability;

import dev.core.entity.RPGEntity;
import dev.core.event.EventBusInterface;
import dev.core.event.EventSubscriptionManager;

/**
 * Per-holder binding of a shared {@link Ability} template to a specific
 * {@link RPGEntity}. One instance exists per (holder, abilityId) while the
 * holder has the ability equipped (or granted as a set bonus / temporary
 * ability). Holds per-user mutable state and the bukkit behavior instance.
 *
 * <p>Centralizes what was previously scattered across per-manager static
 * state maps (holder-keyed platform/bounce/hit-counter maps).</p>
 */
public class ActiveAbility {

    private final RPGEntity holder;
    private final Ability ability;
    private final EventBusInterface bus;
    private final EventSubscriptionManager subscriptions;
    private Object state;
    private AbilityBehavior behavior;

    public ActiveAbility(RPGEntity holder, Ability ability, EventBusInterface bus) {
        this.holder = holder;
        this.ability = ability;
        this.bus = bus;
        this.subscriptions = new EventSubscriptionManager(bus);
        this.state = ability.createState();
    }

    public RPGEntity getHolder() {
        return holder;
    }

    public Ability getAbility() {
        return ability;
    }

    public String getAbilityId() {
        return ability.getId();
    }

    public EventSubscriptionManager getSubscriptions() {
        return subscriptions;
    }

    public Object getState() {
        return state;
    }

    public void setState(Object state) {
        this.state = state;
    }

    @SuppressWarnings("unchecked")
    public <T> T getStateAs(Class<T> type) {
        if (state == null) return null;
        if (type.isInstance(state)) return (T) state;
        throw new IllegalStateException("ActiveAbility state for " + ability.getId()
                + " is " + state.getClass().getSimpleName() + ", not " + type.getSimpleName());
    }

    public AbilityBehavior getBehavior() {
        return behavior;
    }

    public void setBehavior(AbilityBehavior behavior) {
        this.behavior = behavior;
    }

    public EventBusInterface getBus() {
        return bus;
    }
}
