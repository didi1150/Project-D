package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.PartyPlayers;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/**
 * Right-click effect for Teleportation Incident: every living party member's
 * location is randomly shuffled once. A Sattolo permutation is used so nobody
 * keeps their own location when there are at least two players.
 */
public class BukkitTeleportationIncidentEffect extends Effect {

    private static final Random RANDOM = new Random();

    public BukkitTeleportationIncidentEffect(String cooldownKey) {
        super(null, -1, false, cooldownKey);
    }

    @Override
    public boolean hasExpired(long now) {
        return true;
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        if (!(caster instanceof BukkitPlayerEntity playerEntity)) {
            return;
        }
        Player player = playerEntity.getPlayer().orElse(null);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }

        List<Player> players = new ArrayList<>(PartyPlayers.livingWith(player));
        if (players.size() < 2) {
            player.sendMessage("§cTeleportation Incident: not enough players to shuffle.");
            return;
        }

        List<Location> locations = new ArrayList<>(players.size());
        for (Player member : players) {
            locations.add(member.getLocation().clone());
        }
        sattoloShuffle(locations);

        for (int i = 0; i < players.size(); i++) {
            Player member = players.get(i);
            Location destination = locations.get(i);
            member.getWorld().spawnParticle(Particle.PORTAL, member.getLocation().add(0, 1, 0), 40, 0.5, 1.0, 0.5,
                    0.2);
            member.getWorld().playSound(member.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
            member.teleport(destination);
            member.getWorld().spawnParticle(Particle.REVERSE_PORTAL, destination.clone().add(0, 1, 0), 40, 0.5, 1.0,
                    0.5, 0.2);
        }

        for (Player member : players) {
            member.sendMessage("§dTeleportation Incident: the party's positions have been shuffled.");
        }
        cooldownSink.startCooldown();
    }

    private static void sattoloShuffle(List<Location> values) {
        for (int i = values.size(); i > 1; i--) {
            int j = RANDOM.nextInt(i - 1);
            Collections.swap(values, i - 1, j);
        }
    }

    @Override
    public void cancel() {
    }
}
