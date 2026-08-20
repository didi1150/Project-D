package dev.bukkit.event.subscribers;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.entity.MobRPGEntity;
import dev.bukkit.summon.SoulFragment;
import dev.bukkit.summon.SoulSkull;
import dev.bukkit.summon.SummonStats;
import dev.bukkit.summon.SummonedMobRPGEntity;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.SummonRegistry;
import dev.core.entity.mob.MobDefinition;
import dev.core.event.EventSubscriber;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.event.Subscribe;

/**
 * Spawns a purple soul skull whenever a player (or a player-owned summon)
 * kills a dungeon mob. The skull carries the mob's type and tier so a Support
 * player can capture it with their Soul Tome before it despawns.
 */
@EventSubscriber
public class SoulDropSubscriber {

    @Subscribe
    public void onRpgDeath(RPGEntityDeathEvent event) {
        RPGEntity victim = event.getTarget();
        if (victim == null) {
            return;
        }
        // Only dungeon mobs drop souls: never players, allied summons, or bosses
        // (BossRPGEntity is not a MobRPGEntity).
        if (victim.getEntityType() == EntityType.PLAYER
                || SummonRegistry.getInstance().isSummon(victim.getUuid())
                || !(victim instanceof MobRPGEntity mob)) {
            return;
        }

        UUID ownerId = resolveOwner(event.getKiller());
        if (ownerId == null) {
            return; // no player credit (environment or hostile-mob kill)
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }

        MobDefinition definition = mob.getDefinition();
        SoulFragment fragment = new SoulFragment(
                org.bukkit.entity.EntityType.valueOf(definition.getEntityType()),
                SummonStats.lowestTier(definition.getTiers()),
                definition.getId());

        org.bukkit.entity.Entity body = Bukkit.getEntity(victim.getUuid());
        Location loc = body != null ? body.getLocation() : owner.getLocation();
        SoulSkull.spawn(loc, fragment);
    }

    /**
     * Resolves the UUID of the player entitled to the kill: the killer when it
     * is a player, or the summon's owner when a player-owned summon landed the
     * killing blow.
     */
    private UUID resolveOwner(RPGEntity killer) {
        if (killer == null) {
            return null;
        }
        if (killer instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayer().map(Player::getUniqueId).orElse(null);
        }
        if (killer instanceof SummonedMobRPGEntity summon) {
            return summon.getOwnerId();
        }
        return null;
    }
}