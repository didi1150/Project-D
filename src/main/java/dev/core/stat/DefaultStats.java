package dev.core.stat;

import java.util.HashMap;
import java.util.Map;

public class DefaultStats {

	public static Map<StatType, Stat> baseBaseStats() {
		/*
		 * 
		 * 100 hp/100mana 10 armor/10mr 5 ad/0 ap 1 AS 0 Lethality/Armorpen 0% Crit
		 * Chance 100 ms Health Regen: 2 Mana Regen: 4
		 * 
		 */

		Map<StatType, Stat> stats = new HashMap<StatType, Stat>();
		CombatStat healthMaxStat = new CombatStat("HEALTH_MAX", 100);
		stats.put(StatType.HEALTH_MAX, healthMaxStat);
		CombatStat manaMaxStat = new CombatStat("MANA_MAX", 100);
		stats.put(StatType.MANA_MAX, manaMaxStat);
		stats.put(StatType.ARMOR, new CombatStat("ARMOR", 10));
		stats.put(StatType.MAGIC_RESIST, new CombatStat("MAGIC_RESIST", 10));
		stats.put(StatType.ATTACK_DAMAGE, new CombatStat("ATTACK_DAMAGE", 5));
		stats.put(StatType.ABILITY_POWER, new CombatStat("ABILITY_POWER", 0));
		stats.put(StatType.ATTACK_SPEED, new CombatStat("ATTACK_SPEED", 1));
		stats.put(StatType.LETHALITY, new CombatStat("LETHALITY", 0));
		stats.put(StatType.ARMOR_PENETRATION, new CombatStat("ARMOR_PENETRATION", 0));
		stats.put(StatType.CRIT_CHANCE, new CombatStat("CRIT_CHANCE", 0));
		stats.put(StatType.MOVE_SPEED, new CombatStat("MOVE_SPEED", 100));
		CombatStat healthRegenStat = new CombatStat("HEALTH_REGEN", 2);
		stats.put(StatType.HEALTH_REGEN, healthRegenStat);
		CombatStat manaRegenStat = new CombatStat("MANA_REGEN", 4);
		stats.put(StatType.MANA_REGEN, manaRegenStat);
		CombatStat healAndShieldPowerStat = new CombatStat("HEAL_AND_SHIELD_POWER", 0);
		stats.put(StatType.HEAL_AND_SHIELD_POWER, healAndShieldPowerStat);

		stats.put(StatType.HEALTH_RESOURCE,
				new ResourceStat("HEALTH_RESOURCE", t -> healthMaxStat.getCurrent(t),
						t -> healthRegenStat.getCurrent(t) * (1 + healAndShieldPowerStat.getCurrent(t) / 100),
						System.currentTimeMillis()));
		stats.put(StatType.MANA_RESOURCE, new ResourceStat("MANA_RESOURCE", t -> manaMaxStat.getCurrent(t),
				t -> manaRegenStat.getCurrent(t), System.currentTimeMillis()));

		return stats;
	}

}
