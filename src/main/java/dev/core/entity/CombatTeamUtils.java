package dev.core.entity;

/**
 * Centralized allied-team resolution for the RPG damage pipeline.
 *
 * <p>
 * There are two teams: the <b>player team</b> (players and player-owned
 * summons) and the <b>mob team</b> (everything else - dungeon mobs, bosses,
 * vanilla mobs). Entities on the same team never damage each other:
 * <ul>
 * <li>players can never damage players (the old hard-coded no-PvP rule),</li>
 * <li>nobody can damage an allied summon,</li>
 * <li>summons never damage players or allied summons,</li>
 * <li>summons (and players) do fight mobs / the boss.</li>
 * </ul>
 */
public final class CombatTeamUtils {

    private CombatTeamUtils() {
    }

    /**
     * Whether the two fighters are on the same team and therefore cannot damage
     * each other. A {@code null} attacker (environment damage) is never denied.
     */
    public static boolean isAlly(RPGEntity a, RPGEntity b) {
        if (a == null || b == null) {
            return false;
        }
        return isPlayerTeam(a) == isPlayerTeam(b);
    }

    public static boolean isPlayerTeam(RPGEntity entity) {
        return entity.getEntityType() == EntityType.PLAYER
                || SummonRegistry.getInstance().isSummon(entity.getUuid());
    }
}