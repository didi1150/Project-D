package dev.bukkit.entity;

public class VanillaEntityMeta {

	private final int level;
	private final RelationType relation;
	private final String displayName;

	public VanillaEntityMeta(int level, RelationType relation) {
		this(level, relation, null);
	}

	public VanillaEntityMeta(int level, RelationType relation, String displayName) {
		this.level = level;
		this.relation = relation;
		this.displayName = displayName;
	}

	public int getLevel() {
		return level;
	}

	public RelationType getRelation() {
		return relation;
	}

	/** Optional custom display name from a mob definition (null = use type name). */
	public String getDisplayName() {
		return displayName;
	}

	public enum RelationType {
		FRIENDLY, NEUTRAL, HOSTILE
	}

}
