package dev.core.stat;

public enum ModifierStackPolicy {

	STACK,
	UNIQUE_BY_SOURCE, // Only one unique per source id
	REPLACE,		  // Replace the old stattype modifier, regardless of source id
	MAX_ONLY,
	MIN_ONLY
	
}
