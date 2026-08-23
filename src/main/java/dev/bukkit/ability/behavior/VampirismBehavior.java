package dev.bukkit.ability.behavior;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.CombatRelation;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.ability.passive.SetPassive;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.impl.RPGEntityDamageEvent;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.event.impl.RPGEntityHealEvent.HealReason;
import dev.core.event.impl.TickEvent;

/**
 * Drain Tank set passive ("VAMPIRISM"): every enemy entity that dies within
 * {@link #VICINITY_BLOCKS} of the wearer heals them for
 * {@link #HEAL_PERCENT_OF_MAX}% of their max health — but only while the
 * wearer is in <b>active combat</b>: they must have dealt or taken damage
 * within the last {@link #COMBAT_WINDOW_MS} ms. Kills scored while out of
 * combat heal for nothing, so the passive cannot be farmed from idle cleanup.
 *
 * <p><b>Combat tracking</b> rides the internal bus: every hit that flows
 * through {@code RPGEntity#dealRPGDamage} fires an
 * {@link RPGEntityDamageEvent}, which carries both the attacker
 * ({@code getSource()}) and the victim ({@code getTarget()}). A single
 * subscription therefore covers damage dealt by and damage taken by this
 * holder alike.</p>
 *
 * <p><b>Status feedback</b>: while the passive is live a red particle aura
 * spirals around the wearer. Entering combat plays an activation cue and
 * combat lapsing plays a deactivation cue, so the wearer always knows
 * whether kills will currently heal them. Unequip ends the aura (with its
 * cue) instantly.</p>
 *
 * <p>Like the other per-holder behaviors, all subscriptions go through
 * {@code ctx.getSubscriptions()} and are torn down automatically when the
 * ability unbinds; the combat timestamp lives on this instance so it resets
 * on re-equip.</p>
 */
public class VampirismBehavior implements AbilityBehavior {

    public static final String PASSIVE_ID = "VAMPIRISM";

    /** Combat window: damage dealt or taken within this period counts as active combat. */
    public static final long COMBAT_WINDOW_MS = 8000;
    /** Heal per nearby enemy kill, as a percent of the wearer's max health. */
    public static final double HEAL_PERCENT_OF_MAX = 5.0;
    /** Maximum distance between the dying enemy and the wearer. */
    public static final double VICINITY_BLOCKS = 10.0;

    /** Aura points per spiral strand; keeps the swirl smooth without particle spam. */
    private static final int AURA_POINTS = 12;
    /** Horizontal distance of the aura strands from the wearer's center. */
    private static final double AURA_RADIUS = 0.7;
    /** Vertical span the aura covers on the wearer's body. */
    private static final double AURA_HEIGHT = 2.0;
    /** Blood-red dust color for the aura strands. */
    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(170, 20, 25), 1.4f);

    /** Marker so the registry can resolve the passive id from config. */
    public static final SetPassive MARKER = new SetPassive() {
        @Override
        public String getId() {
            return PASSIVE_ID;
        }
    };

    private ActiveAbility ctx;
    /**
     * Last time this holder dealt or took RPG damage. {@code Long.MIN_VALUE}
     * means "never fought since equip" so a fresh equip never counts as being
     * in combat.
     */
    private long lastCombatAt = Long.MIN_VALUE;
    /**
     * Whether the aura (and its state sounds) are currently live: equipped,
     * alive and inside the combat window.
     */
    private boolean auraActive = false;

    public VampirismBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onDamage, RPGEntityDamageEvent.class));
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onDeath, RPGEntityDeathEvent.class));
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onTick, TickEvent.class));
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        // Unequip/breaking the set ends the passive instantly — the aura goes
        // with it, announced by the same deactivation cue as combat lapsing.
        Player holder = holderPlayer();
        if (auraActive && holder != null && holder.isOnline()) {
            playStateSound(holder, false);
        }
        auraActive = false;
    }

    // =========================- Combat tracking ==========================

    private void onDamage(RPGEntityDamageEvent event) {
        java.util.UUID holderId = ctx.getHolder().getUuid();
        boolean involved = holderId.equals(event.getSource() != null ? event.getSource().getUuid() : null)
                || holderId.equals(event.getTarget() != null ? event.getTarget().getUuid() : null);
        if (!involved || event.isCancelled()) {
            return;
        }
        lastCombatAt = System.currentTimeMillis();
    }

    // =========================- Heal proc ==========================

    private void onDeath(RPGEntityDeathEvent event) {
        Player holderPlayer = holderPlayer();
        if (holderPlayer == null || holderPlayer.isDead() || !holderPlayer.isOnline()) {
            return;
        }
        RPGEntity holder = ctx.getHolder();
        if (!holder.isAlive() || EntityManager.getInstance().isGhost(holder.getUuid())) {
            return;
        }

        // Active-combat gate: the wearer must have traded damage recently.
        long now = System.currentTimeMillis();
        if (lastCombatAt == Long.MIN_VALUE || now - lastCombatAt > COMBAT_WINDOW_MS) {
            return;
        }

        // Only enemy deaths count (players and player-owned summons never do).
        RPGEntity victim = event.getTarget();
        if (victim == null || CombatRelation.isPlayerTeam(victim)) {
            return;
        }

        Entity victimBukkit = BukkitPlayerEntity.bukkitSourceOf(victim);
        if (victimBukkit == null || victimBukkit.getWorld() != holderPlayer.getWorld()) {
            return;
        }
        Location victimLoc = victimBukkit.getLocation();
        if (victimLoc.distanceSquared(holderPlayer.getLocation()) > VICINITY_BLOCKS * VICINITY_BLOCKS) {
            return;
        }

        double healAmount = holder.getMaxHealth() * HEAL_PERCENT_OF_MAX / 100.0;
        holder.healRPGEntity(holder, holder, healAmount, HealReason.LIFESTEAL);
    }

    // =========================- Aura status ==========================

    private void onTick(TickEvent event) {
        Player holder = holderPlayer();
        if (holder == null || !holder.isOnline() || holder.isDead()) {
            auraActive = false;
            return;
        }
        boolean inCombat = isInCombat(System.currentTimeMillis());
        if (inCombat != auraActive) {
            playStateSound(holder, inCombat);
            auraActive = inCombat;
        }
        if (auraActive) {
            renderAura(holder);
        }
    }

    private boolean isInCombat(long now) {
        return lastCombatAt != Long.MIN_VALUE && now - lastCombatAt <= COMBAT_WINDOW_MS;
    }

    /**
     * Audible cue for the aura flipping state, heard by everyone near the
     * wearer: a dark anchor charge with a bite layer on activation, a soft
     * wind-down plus extinguish hiss when combat lapses or the set breaks.
     */
    private void playStateSound(Player holder, boolean activated) {
        World world = holder.getWorld();
        Location loc = holder.getLocation();
        if (activated) {
            world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.65f);
            world.playSound(loc, Sound.ENTITY_FOX_BITE, 0.9f, 0.5f);
        } else {
            world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 0.75f);
            world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 0.85f);
        }
    }

    /**
     * Renders the active-combat aura: two counter-rotating red dust strands
     * spiraling up the wearer's body. Runs every tick while the passive is
     * live (~24 particles, cheap) and stops the moment combat lapses or the
     * set breaks.
     */
    private void renderAura(Player holder) {
        World world = holder.getWorld();
        Location center = holder.getLocation();

        // Slow rotation so the strands visibly spin instead of flickering.
        double angleOffset = Math.toRadians((System.currentTimeMillis() / 50) % 360);
        double stepY = AURA_HEIGHT / AURA_POINTS;
        for (int i = 0; i < AURA_POINTS; i++) {
            double angle = angleOffset + 2 * Math.PI * i / AURA_POINTS;
            double y = 0.1 + i * stepY;

            world.spawnParticle(Particle.DUST,
                    center.clone().add(Math.cos(angle) * AURA_RADIUS, y, Math.sin(angle) * AURA_RADIUS),
                    1, 0, 0, 0, 0, BLOOD_DUST);
            world.spawnParticle(Particle.DUST,
                    center.clone().add(Math.cos(-angle + Math.PI) * AURA_RADIUS * 0.7, y + 0.05,
                            Math.sin(-angle + Math.PI) * AURA_RADIUS * 0.7),
                    1, 0, 0, 0, 0, BLOOD_DUST);
        }
    }

    private Player holderPlayer() {
        return ctx.getHolder() instanceof BukkitPlayerEntity bpe ? bpe.getPlayer().orElse(null) : null;
    }
}
