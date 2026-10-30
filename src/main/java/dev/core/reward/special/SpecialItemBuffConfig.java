package dev.core.reward.special;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Loaded buff choices for the special reward items. */
public final class SpecialItemBuffConfig {

    private final List<SpecialItemBuff> buffs;

    public SpecialItemBuffConfig(List<SpecialItemBuff> buffs) {
        this.buffs = buffs == null ? List.of() : List.copyOf(buffs);
    }

    public List<SpecialItemBuff> getBuffs() {
        return buffs;
    }

    public boolean isEmpty() {
        return buffs.isEmpty();
    }

    public Optional<SpecialItemBuff> random(Random random) {
        List<SpecialItemBuff> usable = buffs.stream().filter(SpecialItemBuff::isUsable).toList();
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(usable.get(random.nextInt(usable.size())));
    }
}
