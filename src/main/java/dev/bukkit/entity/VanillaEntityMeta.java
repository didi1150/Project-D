package dev.bukkit.entity;

public class VanillaEntityMeta {

	private final int level;
	private final RelationType relation;

	public VanillaEntityMeta(int level, RelationType relation) {
		this.level = level;
		this.relation = relation;
	}

	public int getLevel() {
		return level;
	}

	public RelationType getRelation() {
		return relation;
	}

	public enum RelationType {
		FRIENDLY, NEUTRAL, HOSTILE
	}

}
